#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(".")

vault_path = ROOT / "app/src/main/java/com/blackserv/passwdgen/VaultBackup.kt"
vault = vault_path.read_text(encoding="utf-8")
if "internal class VaultBackupKeyMaterial" not in vault:
    marker = "internal object VaultBackupCodec"
    if vault.count(marker) != 1:
        raise SystemExit("VaultBackupCodec marker mismatch")
    vault = vault.replace(marker, '\ninternal class VaultBackupKeyMaterial(\n    val salt: ByteArray,\n    val iterations: Int,\n    val keyBytes: ByteArray,\n) : AutoCloseable {\n    override fun close() {\n        keyBytes.fill(0)\n    }\n}\n\n' + marker, 1)

pattern = re.compile(
    r"    fun encode\(\n"
    r"        entries: List<VaultEntry>,\n"
    r"        passphrase: String,\n"
    r"        iterations: Int = DEFAULT_ITERATIONS,\n"
    r"    \): ByteArray \{.*?\n"
    r"    \}\n\n"
    r"    fun decode",
    re.DOTALL,
)
replacement = '''    fun prepareKey(
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): VaultBackupKeyMaterial {
        validatePassphrase(passphrase)
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Nieprawidłowy parametr zabezpieczenia kopii."
        }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return VaultBackupKeyMaterial(
            salt = salt,
            iterations = iterations,
            keyBytes = deriveKey(passphrase, salt, iterations),
        )
    }

    fun encode(
        entries: List<VaultEntry>,
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        val material = prepareKey(passphrase, iterations)
        return try {
            encodeWithKey(entries, material)
        } finally {
            material.close()
        }
    }

    fun encodeWithKey(
        entries: List<VaultEntry>,
        material: VaultBackupKeyMaterial,
    ): ByteArray {
        require(entries.size <= MAX_ENTRIES) { "Kopia zawiera zbyt wiele wpisów." }
        require(material.iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Nieprawidłowy parametr zabezpieczenia kopii."
        }
        require(material.salt.size == SALT_BYTES) { "Nieprawidłowa sól klucza kopii." }
        require(material.keyBytes.size == KEY_BITS / 8) { "Nieprawidłowy klucz kopii." }

        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val aad = associatedData(FORMAT_VERSION, material.iterations)
        val plainText = encodePayload(entries)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(material.keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.updateAAD(aad)
            val cipherText = cipher.doFinal(plainText)

            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.write(MAGIC)
                    output.writeInt(FORMAT_VERSION)
                    output.writeInt(KDF_ID_PBKDF2_SHA256)
                    output.writeInt(material.iterations)
                    output.writeInt(material.salt.size)
                    output.write(material.salt)
                    output.writeInt(CIPHER_ID_AES_256_GCM)
                    output.writeInt(iv.size)
                    output.write(iv)
                    output.writeInt(cipherText.size)
                    output.write(cipherText)
                }
                buffer.toByteArray().also {
                    require(it.size <= MAX_BACKUP_BYTES) { "Kopia jest zbyt duża." }
                }
            }
        } finally {
            plainText.fill(0)
        }
    }

    fun decode'''
vault, count = pattern.subn(replacement, vault, count=1)
if count != 1:
    raise SystemExit(f"Vault encode block replacement count: {count}")
vault_path.write_text(vault, encoding="utf-8")

versions_path = ROOT / "gradle/libs.versions.toml"
versions = versions_path.read_text(encoding="utf-8")
if 'work = "2.11.2"' not in versions:
    versions = versions.replace('junit = "4.13.2"\n', 'junit = "4.13.2"\nwork = "2.11.2"\n', 1)
if "androidx-work-runtime-ktx" not in versions:
    versions = versions.replace(
        'androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }\n',
        'androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }\n'
        'androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }\n',
        1,
    )
versions_path.write_text(versions, encoding="utf-8")

app_gradle_path = ROOT / "app/build.gradle.kts"
app_gradle = app_gradle_path.read_text(encoding="utf-8")
if "libs.androidx.work.runtime.ktx" not in app_gradle:
    app_gradle = app_gradle.replace(
        "    implementation(libs.androidx.biometric)\n",
        "    implementation(libs.androidx.biometric)\n    implementation(libs.androidx.work.runtime.ktx)\n",
        1,
    )
app_gradle_path.write_text(app_gradle, encoding="utf-8")

manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")
if "android.permission.ACCESS_NETWORK_STATE" not in manifest:
    manifest = manifest.replace(
        '    <uses-permission android:name="android.permission.INTERNET" />\n',
        '    <uses-permission android:name="android.permission.INTERNET" />\n'
        '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n',
        1,
    )
manifest_path.write_text(manifest, encoding="utf-8")

app_ui_path = ROOT / "app/src/main/java/com/blackserv/passwdgen/AppUi.kt"
app_ui = app_ui_path.read_text(encoding="utf-8")
old = """        VaultBackupControls(
            viewModel = viewModel,
            enabled = !state.vaultBusy,
        )"""
new = """        VaultBackupControls(
            viewModel = viewModel,
            enabled = !state.vaultBusy,
            scheduledBackup = state.scheduledBackup,
        )"""
if app_ui.count(old) != 1:
    raise SystemExit(f"VaultBackupControls marker count: {app_ui.count(old)}")
app_ui_path.write_text(app_ui.replace(old, new, 1), encoding="utf-8")

codec_test_path = ROOT / "app/src/test/java/com/blackserv/passwdgen/VaultBackupCodecTest.kt"
codec_test = codec_test_path.read_text(encoding="utf-8")
if "import org.junit.Assert.assertFalse" not in codec_test:
    codec_test = codec_test.replace(
        "import org.junit.Assert.assertEquals\n",
        "import org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\n",
        1,
    )
if "preparedKeyProducesPortableSnapshotsWithFreshIvs" not in codec_test:
    test = '''
    @Test
    fun preparedKeyProducesPortableSnapshotsWithFreshIvs() {
        val passphrase = "very strong backup passphrase"
        val material = VaultBackupCodec.prepareKey(passphrase, 100_000)
        try {
            val first = VaultBackupCodec.encodeWithKey(entries, material)
            val second = VaultBackupCodec.encodeWithKey(entries, material)

            assertFalse(first.contentEquals(second))
            assertEquals(entries, VaultBackupCodec.decode(first, passphrase))
            assertEquals(entries, VaultBackupCodec.decode(second, passphrase))
        } finally {
            material.close()
        }
    }
'''
    position = codec_test.rfind("\n}")
    if position < 0:
        raise SystemExit("VaultBackupCodecTest closing brace not found")
    codec_test = codec_test[:position] + test + codec_test[position:]
codec_test_path.write_text(codec_test, encoding="utf-8")

policy_test_path = ROOT / "app/src/test/java/com/blackserv/passwdgen/ScheduledBackupPolicyTest.kt"
policy_test_path.write_text('''package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class ScheduledBackupPolicyTest {
    @Test
    fun generatedNameIsStableAndRecognizable() {
        assertEquals(
            "PasswdGen-auto-19700101-000000.pgvault",
            ScheduledBackupNaming.fileName(0L, ZoneOffset.UTC),
        )
    }

    @Test
    fun rotationKeepsFourNewestApplicationBackupsOnly() {
        val files = listOf(
            BackupDocumentRef("oldest", "PasswdGen-auto-20260101-000000.pgvault", 1_000),
            BackupDocumentRef("older", "PasswdGen-auto-20260201-000000.pgvault", 2_000),
            BackupDocumentRef("keep-1", "PasswdGen-auto-20260301-000000.pgvault", 3_000),
            BackupDocumentRef("keep-2", "PasswdGen-auto-20260401-000000.pgvault", 4_000),
            BackupDocumentRef("keep-3", "PasswdGen-auto-20260501-000000.pgvault", 5_000),
            BackupDocumentRef("keep-4", "PasswdGen-auto-20260601-000000.pgvault", 6_000),
            BackupDocumentRef("manual", "PasswdGen-backup-20260101.pgvault", 500),
            BackupDocumentRef("other", "notes.txt", 100),
        )

        assertEquals(
            listOf("older", "oldest"),
            ScheduledBackupNaming.selectForDeletion(files).map(BackupDocumentRef::documentId),
        )
    }
}
''', encoding="utf-8")
