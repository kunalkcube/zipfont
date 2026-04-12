package com.kunalkcube.zipfont.update

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val OWNER = "kunalkcube"
    private const val REPO = "zipfont"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val RELEASES_URL = "https://github.com/$OWNER/$REPO/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val htmlUrl: String,
        val releaseNotes: String
    )

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion(context) ?: return@withContext null

            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ZipFont-UpdateChecker")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode != 200) {
                connection.disconnect()
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val htmlUrl = json.optString("html_url", RELEASES_URL)
            val body = json.optString("body", "")

            if (tagName.isEmpty() || tagName == currentVersion) {
                return@withContext null
            }

            if (!isNewerVersion(currentVersion, tagName)) {
                return@withContext null
            }

            UpdateInfo(
                latestVersion = tagName,
                currentVersion = currentVersion,
                htmlUrl = htmlUrl,
                releaseNotes = body.ifBlank { "A new version is available." }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getCurrentVersion(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun isNewerVersion(current: String, remote: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
