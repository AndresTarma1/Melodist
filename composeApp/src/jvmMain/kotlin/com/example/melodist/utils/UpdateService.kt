//package com.example.melodist.utils
//
//import io.ktor.client.*
//import io.ktor.client.call.*
//import io.ktor.client.engine.cio.*
//import io.ktor.client.plugins.contentnegotiation.*
//import io.ktor.client.request.*
//import io.ktor.client.statement.*
//import io.ktor.http.*
//import io.ktor.serialization.kotlinx.json.*
//import io.ktor.utils.io.ByteReadChannel
//import io.ktor.utils.io.readAvailable
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import kotlinx.serialization.SerialName
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.json.Json
//import java.io.File
//import java.io.FileOutputStream
//
//@Serializable
//data class GitHubRelease(
//    @SerialName("tag_name") val tagName: String = "",
//    val body: String = "",
//    val assets: List<GitHubAsset> = emptyList()
//)
//
//@Serializable
//data class GitHubAsset(
//    val name: String = "",
//    @SerialName("browser_download_url") val downloadUrl: String = ""
//)
//
//object UpdateService {
//    private const val CURRENT_VERSION = "1.0.0"
//    private const val REPO_URL = "https://api.github.com/repos/AndresTarma1/Melodist/releases/latest"
//
//    private val client = HttpClient(CIO) {
//        install(ContentNegotiation) {
//            json(Json {
//                ignoreUnknownKeys = true
//                coerceInputValues = true
//            })
//        }
//    }
//
//    suspend fun checkForUpdates(): GitHubRelease? = withContext(Dispatchers.IO) {
//        try {
//            val response = client.get(REPO_URL)
//            if (response.status == HttpStatusCode.OK) {
//                val release: GitHubRelease = response.body()
//                val latestVersion = release.tagName.removePrefix("v").trim()
//
//                if (isNewer(latestVersion, CURRENT_VERSION)) {
//                    release
//                } else null
//            } else null
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//    private fun isNewer(latest: String, current: String): Boolean {
//        return try {
//            val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
//            val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
//
//            for (i in 0 until minOf(latestParts.size, currentParts.size)) {
//                if (latestParts[i] > currentParts[i]) return true
//                if (latestParts[i] < currentParts[i]) return false
//            }
//            latestParts.size > currentParts.size
//        } catch (e: Exception) {
//            false
//        }
//    }
//
//    suspend fun downloadAndInstall(asset: GitHubAsset) = withContext(Dispatchers.IO) {
//        try {
//            val tempFile = File(System.getProperty("java.io.tmpdir"), asset.name)
//            val response: HttpResponse = client.get(asset.downloadUrl)
//
//            response.bodyAsChannel().copyTo(FileOutputStream(tempFile))
//
//            if (asset.name.endsWith(".msi")) {
//                ProcessBuilder("msiexec", "/i", tempFile.absolutePath).start()
//                System.exit(0)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//}
//
//private suspend fun ByteReadChannel.copyTo(output: java.io.OutputStream) {
//    val buffer = ByteArray(8192)
//    while (!isClosedForRead) {
//        val read = readAvailable(buffer, 0, buffer.size)
//        if (read <= 0) break
//        output.write(buffer, 0, read)
//    }
//    output.close()
//}
