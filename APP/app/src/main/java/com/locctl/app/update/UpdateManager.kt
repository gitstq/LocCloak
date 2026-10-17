package com.locctl.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val downloadUrl: String,
    val htmlUrl: String
)

sealed class UpdateCheckResult {
    data class HasUpdate(val info: UpdateInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val latestVersion: String) : UpdateCheckResult()
    data class Error(val message: String, val fallbackUrl: String) : UpdateCheckResult()
}

object UpdateManager {
    private const val PRIMARY_REPO_API = "https://api.github.com/repos/gitstq/LocCloak/releases/latest"
    private const val FALLBACK_REPO_API = "https://api.github.com/repos/gitstq/locctl/releases/latest"
    const val GITHUB_RELEASES_PAGE = "https://github.com/gitstq/LocCloak/releases"

    /**
     * 发起真实网络请求，查询 GitHub Releases 远端最新版本
     */
    suspend fun checkForUpdates(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            var url = URL(PRIMARY_REPO_API)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "LocCloak-android-client")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                instanceFollowRedirects = true
            }

            var responseCode = connection.responseCode
            // 若用户尚未在 GitHub 完成改名，无缝回退至旧仓库路径
            if (responseCode == 404) {
                connection.disconnect()
                url = URL(FALLBACK_REPO_API)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "LocCloak-android-client")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    instanceFollowRedirects = true
                }
                responseCode = connection.responseCode
            }

            if (responseCode !in 200..299) {
                return@withContext UpdateCheckResult.Error(
                    message = "GitHub API 响应异常 (HTTP $responseCode)",
                    fallbackUrl = GITHUB_RELEASES_PAGE
                )
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "暂无更新日志说明")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", GITHUB_RELEASES_PAGE)

            // 解析 assets 附件列表，优先匹配 APK 直链
            var apkDownloadUrl = ""
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val assetObj = assetsArray.getJSONObject(i)
                    val assetName = assetObj.optString("name", "")
                    val downloadUrl = assetObj.optString("browser_download_url", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        // 优先选择包含 universal 或 android 的安装包
                        if (assetName.contains("universal", ignoreCase = true) || assetName.contains("android", ignoreCase = true)) {
                            apkDownloadUrl = downloadUrl
                            break
                        }
                        if (apkDownloadUrl.isEmpty()) {
                            apkDownloadUrl = downloadUrl
                        }
                    }
                }
            }

            // 如果没有直接提取到 APK 直链，则降级为发布页面网页链接
            val finalDownloadUrl = if (apkDownloadUrl.isNotEmpty()) apkDownloadUrl else htmlUrl

            val hasUpdate = compareVersion(tagName, currentVersion) > 0

            val info = UpdateInfo(
                hasUpdate = hasUpdate,
                currentVersion = currentVersion,
                latestVersion = tagName.ifEmpty { "v$currentVersion" },
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
                downloadUrl = finalDownloadUrl,
                htmlUrl = htmlUrl
            )

            if (hasUpdate) {
                UpdateCheckResult.HasUpdate(info)
            } else {
                UpdateCheckResult.UpToDate(currentVersion, tagName)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(
                message = e.localizedMessage ?: "网络请求失败，请检查手机网络",
                fallbackUrl = GITHUB_RELEASES_PAGE
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 语义化版本号比较算法
     * 返回 > 0 表示 v1 > v2 (存在更新)；0 表示相同；< 0 表示 v1 < v2
     */
    fun compareVersion(v1: String, v2: String): Int {
        val clean1 = v1.trim().removePrefix("v").removePrefix("V").split("-")[0]
        val clean2 = v2.trim().removePrefix("v").removePrefix("V").split("-")[0]

        val parts1 = clean1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = clean2.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}

