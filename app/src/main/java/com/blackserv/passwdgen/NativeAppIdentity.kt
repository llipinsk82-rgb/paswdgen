package com.blackserv.passwdgen

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

internal data class NativeAppIdentity(
    val packageName: String,
    val appLabel: String,
    val signerSha256: String,
)

internal object NativeAppBindingPolicy {
    fun matches(binding: AndroidAppBinding, identity: NativeAppIdentity): Boolean =
        binding.packageName == identity.packageName &&
            binding.signerSha256 == identity.signerSha256
}

internal object NativeAppIdentityResolver {
    fun resolve(context: Context, requestedPackage: String): NativeAppIdentity? {
        val packageName = requestedPackage.trim().lowercase(Locale.ROOT)
        if (packageName.isBlank() || packageName == context.packageName.lowercase(Locale.ROOT)) return null

        val packageManager = context.packageManager
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }

        val signingInfo = packageInfo.signingInfo ?: return null
        val currentSigners = signingInfo.apkContentsSigners.orEmpty()
        if (currentSigners.isEmpty()) return null

        val signerSet = currentSigners
            .map { certificate -> sha256(certificate.toByteArray()) }
            .distinct()
            .sorted()
            .joinToString(",")
        if (signerSet.isBlank()) return null

        val applicationInfo = packageInfo.applicationInfo ?: return null
        val appLabel = packageManager.getApplicationLabel(applicationInfo)
            .toString()
            .trim()
            .takeIf(String::isNotBlank)
            ?.take(MAX_LABEL_LENGTH)
            ?: packageName

        return NativeAppIdentity(
            packageName = packageName,
            appLabel = appLabel,
            signerSha256 = signerSet,
        )
    }

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }

    private const val MAX_LABEL_LENGTH = 120
}
