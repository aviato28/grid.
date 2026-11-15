package com.nbansal.grid.update

import com.nbansal.grid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

/** Version helpers. Same formula as app/build.gradle.kts: major*10000 + minor*100 + patch. */
object Versions {
    private val TAG = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")

    fun versionCodeFromTag(tag: String): Int? {
        val m = TAG.matchEntire(tag.trim()) ?: return null
        val (major, minor, patch) = m.destructured
        return major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
    }

    fun versionFromTag(tag: String): String = tag.trim().removePrefix("v")
}

class UpdateChecker(
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val endpoint: String = "https://api.github.com/repos/aviato28/grid./releases/latest",
) {
    /** Returns the newer release, or null when up to date (including "no releases yet"). Throws on failure. */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Grid-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            when (val code = conn.responseCode) {
                404 -> null
                200 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                else -> throw java.io.IOException("GitHub returned HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(json: String): ReleaseInfo? {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name")
        val code = Versions.versionCodeFromTag(tag) ?: return null
        if (code <= currentVersionCode) return null
        val assets = obj.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return ReleaseInfo(
                    tag = tag,
                    version = Versions.versionFromTag(tag),
                    versionCode = code,
                    notes = obj.optString("body"),
                    apkUrl = a.getString("browser_download_url"),
                    apkSize = a.optLong("size", -1),
                )
            }
        }
        return null
    }
}
