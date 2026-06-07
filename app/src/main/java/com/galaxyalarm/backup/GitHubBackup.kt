package com.galaxyalarm.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubBackupSettings(
    val token: String,
    val gistId: String,
)

data class GitHubBackupResult(
    val gistId: String,
    val url: String,
)

class GitHubBackupStore(context: Context) {
    private val prefs = context.getSharedPreferences("github_backup", Context.MODE_PRIVATE)

    fun load(): GitHubBackupSettings =
        GitHubBackupSettings(
            token = prefs.getString("token", "") ?: "",
            gistId = prefs.getString("gist_id", "") ?: "",
        )

    fun save(settings: GitHubBackupSettings) {
        prefs.edit()
            .putString("token", settings.token.trim())
            .putString("gist_id", settings.gistId.trim())
            .apply()
    }

    fun saveGistId(gistId: String) {
        prefs.edit().putString("gist_id", gistId.trim()).apply()
    }
}

object GitHubBackupClient {
    const val FILE_NAME = "galaxy-alarm-backup.json"

    suspend fun upload(token: String, gistId: String, backupJson: String): GitHubBackupResult =
        withContext(Dispatchers.IO) {
            require(token.isNotBlank()) { "GitHub token is required" }
            if (gistId.isBlank()) createGist(token, backupJson) else updateGist(token, gistId, backupJson)
        }

    suspend fun download(token: String, gistId: String): String =
        withContext(Dispatchers.IO) {
            require(token.isNotBlank()) { "GitHub token is required" }
            require(gistId.isNotBlank()) { "Gist ID is required" }
            val conn = open("https://api.github.com/gists/$gistId", "GET", token)
            val body = conn.readBody()
            if (conn.responseCode !in 200..299) error("GitHub ${conn.responseCode}: $body")
            val file = JSONObject(body)
                .getJSONObject("files")
                .optJSONObject(FILE_NAME)
                ?: error("Backup file not found in gist")
            file.getString("content")
        }

    private fun createGist(token: String, backupJson: String): GitHubBackupResult {
        val body = JSONObject()
            .put("description", "Galaxy Alarm backup")
            .put("public", false)
            .put("files", filesJson(backupJson))
            .toString()
        val conn = open("https://api.github.com/gists", "POST", token)
        conn.writeBody(body)
        val response = conn.readBody()
        if (conn.responseCode !in 200..299) error("GitHub ${conn.responseCode}: $response")
        val json = JSONObject(response)
        return GitHubBackupResult(json.getString("id"), json.getString("html_url"))
    }

    private fun updateGist(token: String, gistId: String, backupJson: String): GitHubBackupResult {
        val body = JSONObject()
            .put("description", "Galaxy Alarm backup")
            .put("files", filesJson(backupJson))
            .toString()
        val conn = open("https://api.github.com/gists/$gistId", "PATCH", token)
        conn.writeBody(body)
        val response = conn.readBody()
        if (conn.responseCode !in 200..299) error("GitHub ${conn.responseCode}: $response")
        val json = JSONObject(response)
        return GitHubBackupResult(json.getString("id"), json.getString("html_url"))
    }

    private fun filesJson(backupJson: String): JSONObject =
        JSONObject().put(FILE_NAME, JSONObject().put("content", backupJson))

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (method == "POST" || method == "PATCH") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

    private fun HttpURLConnection.writeBody(body: String) {
        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }

    private fun HttpURLConnection.readBody(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }
}
