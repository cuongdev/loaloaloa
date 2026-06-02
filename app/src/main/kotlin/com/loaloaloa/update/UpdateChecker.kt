package com.loaloaloa.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer build. The APK is distributed via GitHub Releases (CI publishes
 * `vX.Y.Z` with `loaloaloa.apk` attached), so "check for update" compares the running
 * [android version name][current] against the latest release tag — no Play Store involved.
 *
 * Uses a plain [HttpURLConnection] on [Dispatchers.IO] (org.json ships with Android) to avoid pulling
 * the Retrofit stack in for a single unauthenticated GET. Never throws; failures map to [Result.Failed].
 */
object UpdateChecker {
    private const val LATEST_API = "https://api.github.com/repos/cuongdev/loaloaloa/releases/latest"

    /** Public-facing page to open when an update is available (always the newest release + APK). */
    const val RELEASES_URL = "https://github.com/cuongdev/loaloaloa/releases/latest"

    sealed interface Result {
        data class UpToDate(val current: String) : Result
        data class Available(val latest: String, val url: String) : Result
        data object Failed : Result
    }

    suspend fun check(current: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext Result.Failed
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val tag = JSONObject(body).optString("tag_name").removePrefix("v")
                val cur = current.removePrefix("v")
                if (tag.isNotEmpty() && isNewer(tag, cur)) Result.Available(tag, RELEASES_URL)
                else Result.UpToDate(cur)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { Result.Failed }
    }

    /** Numeric, dot-segment SemVer compare; missing segments count as 0 (e.g. "1.2" == "1.2.0"). */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
