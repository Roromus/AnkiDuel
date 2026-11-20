package com.ankiduel.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Downloading(
        val versionName: String,
        val versionCode: Int,
        val progressPercent: Int?
    ) : UpdateState

    data class ReadyToInstall(
        val versionName: String,
        val versionCode: Int,
        val apkFile: File,
        val needsInstallPermission: Boolean
    ) : UpdateState
}

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
internal data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L
)

data class ParsedReleaseVersion(
    val versionName: String,
    val versionCode: Int
)

class AppUpdateManager(
    private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val checkMutex = Mutex()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var lastCheckMs: Long = 0L
    private var autoTriggeredVersionCode: Int? = null

    suspend fun checkAndUpdateIfAvailable(force: Boolean = false) {
        checkMutex.withLock {
            val currentState = _state.value
            if (currentState is UpdateState.Downloading || currentState is UpdateState.ReadyToInstall) {
                return
            }

            val now = System.currentTimeMillis()
            if (!force && now - lastCheckMs < CHECK_INTERVAL_MS) {
                return
            }

            _state.value = UpdateState.Checking

            val release = withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease() }.getOrNull()
            }
            if (release == null) {
                // Ne pas figer lastCheckMs si la requête réseau a échoué afin de réessayer au prochain resume
                _state.value = UpdateState.Idle
                return
            }
            lastCheckMs = now
            if (release.draft || release.prerelease) {
                _state.value = UpdateState.Idle
                return
            }

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAsset == null) {
                _state.value = UpdateState.Idle
                return
            }

            val parsed = parseReleaseVersion(release.tagName, apkAsset.name)
            if (parsed == null) {
                _state.value = UpdateState.Idle
                return
            }

            val currentVersionCode = getCurrentVersionCode()
            if (parsed.versionCode <= currentVersionCode) {
                _state.value = UpdateState.Idle
                return
            }

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetApk = File(updatesDir, "AnkiDuel-v${parsed.versionName}-${parsed.versionCode}.apk")

            // Clean older cached APKs
            updatesDir.listFiles()?.forEach { existing ->
                if (existing.name != targetApk.name) {
                    existing.delete()
                }
            }

            val downloadedApk = if (targetApk.exists() && (apkAsset.size <= 0L || targetApk.length() == apkAsset.size)) {
                targetApk
            } else {
                _state.value = UpdateState.Downloading(
                    versionName = parsed.versionName,
                    versionCode = parsed.versionCode,
                    progressPercent = 0
                )
                val ok = withContext(Dispatchers.IO) {
                    downloadApk(apkAsset.browserDownloadUrl, targetApk, apkAsset.size) { percent ->
                        _state.value = UpdateState.Downloading(
                            versionName = parsed.versionName,
                            versionCode = parsed.versionCode,
                            progressPercent = percent
                        )
                    }
                }
                if (!ok) {
                    _state.value = UpdateState.Idle
                    return
                }
                targetApk
            }

            val canInstall = canRequestPackageInstalls()
            _state.value = UpdateState.ReadyToInstall(
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                apkFile = downloadedApk,
                needsInstallPermission = !canInstall
            )

            if (canInstall && autoTriggeredVersionCode != parsed.versionCode) {
                autoTriggeredVersionCode = parsed.versionCode
                withContext(Dispatchers.Main) {
                    launchInstaller(downloadedApk)
                }
            }
        }
    }

    fun onResumeCheckPermission() {
        val current = _state.value as? UpdateState.ReadyToInstall ?: return
        if (getCurrentVersionCode() >= current.versionCode) {
            _state.value = UpdateState.Idle
            return
        }
        if (!current.apkFile.exists()) {
            _state.value = UpdateState.Idle
            return
        }
        val canInstall = canRequestPackageInstalls()
        if (current.needsInstallPermission && canInstall) {
            _state.value = current.copy(needsInstallPermission = false)
            if (autoTriggeredVersionCode != current.versionCode) {
                autoTriggeredVersionCode = current.versionCode
                launchInstaller(current.apkFile)
            }
        } else if (!current.needsInstallPermission && !canInstall) {
            _state.value = current.copy(needsInstallPermission = true)
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun launchInstaller(apkFile: File) {
        if (!apkFile.exists()) {
            _state.value = UpdateState.Idle
            return
        }
        runCatching {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun dismissPrompt() {
        _state.value = UpdateState.Idle
    }

    private fun getCurrentVersionCode(): Long {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return PackageInfoCompat.getLongVersionCode(info)
    }

    private fun fetchLatestRelease(): GitHubRelease? {
        val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AnkiDuel-Android")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<GitHubRelease>(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadApk(
        downloadUrl: String,
        targetFile: File,
        expectedSize: Long,
        onProgress: (Int?) -> Unit
    ): Boolean {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        var currentUrl = downloadUrl
        var redirects = 0

        while (redirects < 5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "AnkiDuel-Android")
            }

            try {
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location") ?: return false
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299) return false

                val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: expectedSize
                var downloaded = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                val pct = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct)
                                }
                            } else if (lastReported != -2) {
                                lastReported = -2
                                onProgress(null)
                            }
                        }
                    }
                }

                if (downloaded <= 0L || (totalBytes > 0L && downloaded != totalBytes)) {
                    tempFile.delete()
                    return false
                }

                if (targetFile.exists()) targetFile.delete()
                return tempFile.renameTo(targetFile)
            } catch (_: Exception) {
                tempFile.delete()
                return false
            } finally {
                conn.disconnect()
            }
        }
        return false
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Roromus/AnkiDuel/releases/latest"
        private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L

        private val ASSET_REGEX = Regex("""AnkiDuel-v(.+)-(\d+)\.apk$""", RegexOption.IGNORE_CASE)
        private val TAG_REGEX = Regex("""^v([^-]+)-(\d+)(?:\.\d+)?$""")

        fun parseReleaseVersion(tagName: String, assetName: String): ParsedReleaseVersion? {
            ASSET_REGEX.find(assetName)?.let { match ->
                val vName = match.groupValues[1]
                val vCode = match.groupValues[2].toIntOrNull()
                if (vCode != null) return ParsedReleaseVersion(vName, vCode)
            }
            TAG_REGEX.find(tagName.trim())?.let { match ->
                val vName = match.groupValues[1]
                val vCode = match.groupValues[2].toIntOrNull()
                if (vCode != null) return ParsedReleaseVersion(vName, vCode)
            }
            return null
        }
    }
}
