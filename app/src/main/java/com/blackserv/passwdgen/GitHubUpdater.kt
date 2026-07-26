package com.blackserv.passwdgen

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

internal data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String,
)

internal object GitHubUpdater {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/llipinsk82-rgb/paswdgen/releases/latest"
    private const val UPDATE_MANIFEST_NAME = "update.json"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val PREFS = "github_updater"
    private const val LAST_CHECK = "last_check"

    private val executor = Executors.newSingleThreadExecutor()

    fun checkOnLaunch(activity: Activity) {
        val preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - preferences.getLong(LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        preferences.edit().putLong(LAST_CHECK, now).apply()

        executor.execute {
            val update = runCatching { fetchLatestUpdate(activity) }.getOrNull() ?: return@execute
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                AlertDialog.Builder(activity)
                    .setTitle("Dostępna aktualizacja ${update.versionName}")
                    .setMessage(
                        update.releaseNotes.ifBlank {
                            "Nowa wersja PasswdGen jest gotowa do pobrania z GitHub Releases."
                        },
                    )
                    .setNegativeButton("Później", null)
                    .setPositiveButton("Aktualizuj") { _, _ ->
                        activity.startActivity(
                            Intent(activity, UpdateActivity::class.java)
                                .putExtra(UpdateActivity.EXTRA_VERSION_CODE, update.versionCode)
                                .putExtra(UpdateActivity.EXTRA_VERSION_NAME, update.versionName)
                                .putExtra(UpdateActivity.EXTRA_APK_URL, update.apkUrl)
                                .putExtra(UpdateActivity.EXTRA_SHA256, update.sha256),
                        )
                    }
                    .show()
            }
        }
    }

    private fun fetchLatestUpdate(context: Context): AppUpdate? {
        val release = JSONObject(downloadText(LATEST_RELEASE_API))
        val assets = release.getJSONArray("assets")
        var manifestUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.optString("name") == UPDATE_MANIFEST_NAME) {
                manifestUrl = asset.getString("browser_download_url")
                break
            }
        }
        val manifest = JSONObject(downloadText(requireNotNull(manifestUrl) {
            "Release nie zawiera pliku $UPDATE_MANIFEST_NAME."
        }))

        val latestVersionCode = manifest.getLong("versionCode")
        val installedVersionCode = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .longVersionCode
        if (latestVersionCode <= installedVersionCode) return null

        val apkUrl = manifest.getString("apkUrl")
        requireHttps(apkUrl)
        val sha256 = manifest.getString("sha256").lowercase()
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Nieprawidłowa suma SHA-256." }

        return AppUpdate(
            versionCode = latestVersionCode,
            versionName = manifest.getString("versionName"),
            apkUrl = apkUrl,
            sha256 = sha256,
            releaseNotes = release.optString("body"),
        )
    }

    internal fun downloadApk(url: String, destination: File, expectedSha256: String) {
        requireHttps(url)
        destination.parentFile?.mkdirs()
        val connection = open(url)
        connection.inputStream.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        val actual = sha256(destination)
        check(actual.equals(expectedSha256, ignoreCase = true)) {
            destination.delete()
            "Suma kontrolna pobranego APK jest nieprawidłowa."
        }
    }

    private fun downloadText(url: String): String {
        requireHttps(url)
        val connection = open(url)
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PasswdGen-Android-Updater")
            connect()
            check(responseCode in 200..299) { "HTTP $responseCode podczas pobierania aktualizacji." }
        }

    private fun requireHttps(url: String) {
        require(Uri.parse(url).scheme.equals("https", ignoreCase = true)) {
            "Aktualizacje muszą używać HTTPS."
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class UpdateActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var apkFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: run {
            finish()
            return
        }
        val apkUrl = intent.getStringExtra(EXTRA_APK_URL) ?: run {
            finish()
            return
        }
        val sha256 = intent.getStringExtra(EXTRA_SHA256) ?: run {
            finish()
            return
        }

        apkFile = File(getExternalFilesDir(null), "updates/PasswdGen-$versionName.apk")
        val progress = AlertDialog.Builder(this)
            .setTitle("Pobieranie aktualizacji")
            .setMessage("Wersja $versionName jest pobierana i weryfikowana…")
            .setCancelable(false)
            .create()
        progress.show()

        executor.execute {
            val result = runCatching { GitHubUpdater.downloadApk(apkUrl, apkFile, sha256) }
            runOnUiThread {
                progress.dismiss()
                result.fold(
                    onSuccess = { requestInstall() },
                    onFailure = {
                        AlertDialog.Builder(this)
                            .setTitle("Aktualizacja nieudana")
                            .setMessage(it.message ?: "Nie udało się pobrać aktualizacji.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .show()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::apkFile.isInitialized && apkFile.exists() && packageManager.canRequestPackageInstalls()) {
            requestInstall()
        }
    }

    private fun requestInstall() {
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                this,
                "Zezwól PasswdGen na instalowanie aktualizacji z tego źródła.",
                Toast.LENGTH_LONG,
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }

        val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        finish()
    }

    internal companion object {
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_APK_URL = "apk_url"
        const val EXTRA_SHA256 = "sha256"
    }
}
