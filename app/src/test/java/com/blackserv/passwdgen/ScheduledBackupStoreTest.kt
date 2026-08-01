package com.blackserv.passwdgen

import android.content.SharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ScheduledBackupStoreTest {
    @Test
    fun saveAndLoadRoundTripPreservesKeyMaterial() {
        val preferences = InMemorySharedPreferences()
        val cipher = TestScheduledBackupKeyCipher()
        val store = ScheduledBackupStore(preferences, cipher)
        val material = sampleMaterial()

        store.saveConfiguration(
            treeUri = "content://documents/tree/backup",
            targetLabel = "Drive / PasswdGen",
            wifiOnly = false,
            material = material,
        )

        val restored = store.loadKeyMaterial()
        assertNotNull(restored)
        requireNotNull(restored)
        try {
            assertArrayEquals(material.salt, restored.salt)
            assertEquals(material.iterations, restored.iterations)
            assertArrayEquals(material.keyBytes, restored.keyBytes)
            assertTrue(store.status().configured)
            assertTrue(store.status().enabled)
            assertFalse(store.status().wifiOnly)
        } finally {
            restored.close()
            material.close()
        }
    }

    @Test
    fun corruptedWrappedKeyDisablesSchedule() {
        val preferences = InMemorySharedPreferences()
        val store = ScheduledBackupStore(preferences, TestScheduledBackupKeyCipher())
        val material = sampleMaterial()
        store.saveConfiguration("content://documents/tree/backup", "Backup", true, material)
        material.close()

        val encoded = requireNotNull(preferences.getString("wrapped_key", null))
        val corrupted = Base64.getDecoder().decode(encoded).also { it[it.lastIndex] = (it.last() xor 0x01) }
        preferences.edit().putString("wrapped_key", Base64.getEncoder().encodeToString(corrupted)).commit()

        assertNull(store.loadKeyMaterial())
        assertFalse(store.status().enabled)
        assertEquals(
            "Zapisany klucz automatycznej kopii jest uszkodzony lub niedostępny. Wybierz folder ponownie.",
            store.status().lastError,
        )
    }

    @Test
    fun invalidatedWrappingKeyDisablesSchedule() {
        val preferences = InMemorySharedPreferences()
        val cipher = TestScheduledBackupKeyCipher()
        val store = ScheduledBackupStore(preferences, cipher)
        val material = sampleMaterial()
        store.saveConfiguration("content://documents/tree/backup", "Backup", true, material)
        material.close()
        cipher.invalidate()

        assertNull(store.loadKeyMaterial())
        assertFalse(store.status().enabled)
        assertNotNull(store.status().lastError)
    }

    @Test
    fun missingRequiredFieldIsRejected() {
        val preferences = InMemorySharedPreferences()
        val store = ScheduledBackupStore(preferences, TestScheduledBackupKeyCipher())
        val material = sampleMaterial()
        store.saveConfiguration("content://documents/tree/backup", "Backup", true, material)
        material.close()
        preferences.edit().remove("salt").commit()

        assertFalse(store.status().configured)
        assertNull(store.loadKeyMaterial())
        assertFalse(store.status().enabled)
    }

    @Test
    fun legacyConfigurationWithoutVersionIsMigratedAfterSuccessfulUnwrap() {
        val preferences = InMemorySharedPreferences()
        val store = ScheduledBackupStore(preferences, TestScheduledBackupKeyCipher())
        val material = sampleMaterial()
        store.saveConfiguration("content://documents/tree/backup", "Backup", true, material)
        material.close()
        preferences.edit().remove("configuration_version").commit()

        assertTrue(store.status().configured)
        val restored = requireNotNull(store.loadKeyMaterial())
        restored.close()
        assertEquals(1, preferences.getInt("configuration_version", -1))
        assertTrue(store.status().enabled)
    }

    @Test
    fun incompatibleConfigurationVersionIsRejected() {
        val preferences = InMemorySharedPreferences()
        val store = ScheduledBackupStore(preferences, TestScheduledBackupKeyCipher())
        val material = sampleMaterial()
        store.saveConfiguration("content://documents/tree/backup", "Backup", true, material)
        material.close()
        preferences.edit().putInt("configuration_version", 999).commit()

        assertFalse(store.status().configured)
        assertNull(store.loadKeyMaterial())
        assertFalse(store.status().enabled)
        assertEquals(
            "Nieobsługiwana wersja konfiguracji automatycznej kopii. Wybierz folder ponownie.",
            store.status().lastError,
        )
    }

    @Test
    fun clearRemovesConfigurationAndWrappingKey() {
        val preferences = InMemorySharedPreferences()
        val cipher = TestScheduledBackupKeyCipher()
        val store = ScheduledBackupStore(preferences, cipher)
        val material = sampleMaterial()
        store.saveConfiguration("content://documents/tree/backup", "Backup", true, material)
        material.close()

        store.clear()

        assertTrue(preferences.all.isEmpty())
        assertTrue(cipher.deleted)
        assertFalse(store.status().configured)
    }

    private fun sampleMaterial() = VaultBackupKeyMaterial(
        salt = ByteArray(16) { (it + 1).toByte() },
        iterations = VaultBackupCodec.DEFAULT_ITERATIONS,
        keyBytes = ByteArray(32) { (it * 3 + 7).toByte() },
    )
}

private class TestScheduledBackupKeyCipher : ScheduledBackupKeyCipher {
    private var key: SecretKey? = SecretKeySpec(ByteArray(32) { (it + 11).toByte() }, "AES")
    var deleted: Boolean = false
        private set

    override fun wrap(value: ByteArray): WrappedScheduledBackupKey {
        val activeKey = key ?: throw GeneralSecurityException("Wrapping key unavailable")
        val iv = ByteArray(12) { (it + 21).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, activeKey, GCMParameterSpec(128, iv))
        cipher.updateAAD("passwdgen-scheduled-backup-key-v1".encodeToByteArray())
        return WrappedScheduledBackupKey(cipher.doFinal(value), iv)
    }

    override fun unwrap(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val activeKey = key ?: throw GeneralSecurityException("Wrapping key unavailable")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, activeKey, GCMParameterSpec(128, iv))
        cipher.updateAAD("passwdgen-scheduled-backup-key-v1".encodeToByteArray())
        return cipher.doFinal(cipherText)
    }

    override fun deleteKey() {
        deleted = true
        key = null
    }

    fun invalidate() {
        key = null
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners -= listener
    }

    private inner class Editor : SharedPreferences.Editor {
        private val updates = linkedMapOf<String, Any?>()
        private val removals = linkedSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
            removals -= key
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = values?.toSet()
            removals -= key
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
            removals -= key
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
            removals -= key
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
            removals -= key
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            requireNotNull(key)
            updates[key] = value
            removals -= key
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            requireNotNull(key)
            removals += key
            updates -= key
        }

        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() = applyChanges()

        private fun applyChanges() {
            val changed = linkedSetOf<String>()
            if (clearRequested) {
                changed += values.keys
                values.clear()
            }
            removals.forEach { key ->
                if (values.remove(key) != null) changed += key
            }
            updates.forEach { (key, value) ->
                if (value == null) {
                    if (values.remove(key) != null) changed += key
                } else if (values[key] != value) {
                    values[key] = value
                    changed += key
                }
            }
            changed.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) }
            }
        }
    }
}

private infix fun Byte.xor(other: Int): Byte = (toInt() xor other).toByte()
