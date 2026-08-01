package com.blackserv.passwdgen

import android.content.ContentValues
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class UpgradeContinuityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    @Test
    fun runRequestedUpgradePhase() {
        when (instrumentation.arguments.getString(ARG_PHASE)) {
            PHASE_SEED -> seedVersionN()
            PHASE_VERIFY -> verifyVersionNPlusOne()
            else -> error("Missing or invalid instrumentation argument: $ARG_PHASE")
        }
    }

    private fun seedVersionN() {
        keyStore.deleteEntry(KEY_ALIAS)
        context.deleteFile(PROBE_FILE)
        context.deleteDatabase(PROBE_DATABASE)
        context.getSharedPreferences(PROBE_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()

        val seedVersionCode = currentVersionCode()
        val secretKey = createProbeKey()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
            updateAAD(PROBE_AAD)
        }
        val cipherText = cipher.doFinal(PROBE_PLAINTEXT)

        DataOutputStream(context.openFileOutput(PROBE_FILE, Context.MODE_PRIVATE)).use { output ->
            output.writeInt(PROBE_FORMAT_VERSION)
            output.writeInt(cipher.iv.size)
            output.write(cipher.iv)
            output.writeInt(cipherText.size)
            output.write(cipherText)
        }

        assertTrue(
            context.getSharedPreferences(PROBE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_SEED_VERSION_CODE, seedVersionCode)
                .putString(PREF_MARKER, PROBE_MARKER)
                .commit(),
        )

        context.openOrCreateDatabase(PROBE_DATABASE, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS upgrade_probe (id INTEGER PRIMARY KEY, marker TEXT NOT NULL)",
            )
            database.delete("upgrade_probe", null, null)
            val values = ContentValues().apply {
                put("id", 1)
                put("marker", PROBE_MARKER)
            }
            assertTrue(database.insertOrThrow("upgrade_probe", null, values) > 0)
        }

        assertTrue(keyStore.containsAlias(KEY_ALIAS))
        assertTrue(context.getFileStreamPath(PROBE_FILE).isFile)
    }

    private fun verifyVersionNPlusOne() {
        val preferences = context.getSharedPreferences(PROBE_PREFERENCES, Context.MODE_PRIVATE)
        val seedVersionCode = preferences.getLong(PREF_SEED_VERSION_CODE, -1L)
        assertTrue("Seed versionCode was not preserved", seedVersionCode > 0L)
        assertTrue(
            "Installed versionCode did not increase",
            currentVersionCode() > seedVersionCode,
        )
        assertEquals(PROBE_MARKER, preferences.getString(PREF_MARKER, null))

        val storedKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        assertNotNull("Android Keystore key was lost during upgrade", storedKey)

        val (iv, cipherText) = DataInputStream(context.openFileInput(PROBE_FILE)).use { input ->
            assertEquals(PROBE_FORMAT_VERSION, input.readInt())
            val ivLength = input.readInt()
            require(ivLength in 12..32) { "Invalid IV length: $ivLength" }
            val iv = ByteArray(ivLength).also(input::readFully)
            val cipherTextLength = input.readInt()
            require(cipherTextLength in 16..4_096) { "Invalid ciphertext length: $cipherTextLength" }
            val cipherText = ByteArray(cipherTextLength).also(input::readFully)
            iv to cipherText
        }

        val plainText = Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, storedKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            updateAAD(PROBE_AAD)
            doFinal(cipherText)
        }
        assertArrayEquals(PROBE_PLAINTEXT, plainText)

        context.openOrCreateDatabase(PROBE_DATABASE, Context.MODE_PRIVATE, null).use { database ->
            database.query(
                "upgrade_probe",
                arrayOf("marker"),
                "id = ?",
                arrayOf("1"),
                null,
                null,
                null,
            ).use { cursor ->
                assertTrue("SQLite marker row was lost during upgrade", cursor.moveToFirst())
                assertEquals(PROBE_MARKER, cursor.getString(0))
            }
        }
    }

    private fun createProbeKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun currentVersionCode(): Long = context.packageManager
        .getPackageInfo(context.packageName, 0)
        .longVersionCode

    private companion object {
        const val ARG_PHASE = "upgradePhase"
        const val PHASE_SEED = "seed"
        const val PHASE_VERIFY = "verify"

        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "passwdgen.upgrade.instrumentation.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128

        const val PROBE_FILE = "upgrade-continuity.bin"
        const val PROBE_DATABASE = "upgrade-continuity.db"
        const val PROBE_PREFERENCES = "upgrade-continuity"
        const val PREF_SEED_VERSION_CODE = "seed_version_code"
        const val PREF_MARKER = "marker"
        const val PROBE_MARKER = "passwdgen-upgrade-continuity-v1"
        const val PROBE_FORMAT_VERSION = 1

        val PROBE_AAD = "passwdgen-upgrade-probe".encodeToByteArray()
        val PROBE_PLAINTEXT = "encrypted-data-survived-upgrade".encodeToByteArray()
    }
}
