package com.example.chatbot.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.chatbot.models.GcsObjectItem
import com.example.chatbot.models.GcsObjectsResponse
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

class MemoryBankFileStore(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val versionPattern = Regex("""(?:^|[-_/ ])v(?:ersion)?[-_ ]?(\d+)$""", RegexOption.IGNORE_CASE)

    fun fetchAvailableFiles(onComplete: (List<String>) -> Unit) {
        if (!isNetworkAvailable()) {
            showNetworkErrorToast("Internet connection required to fetch available files")
            onComplete(emptyList())
            return
        }

        val request = Request.Builder()
            .url("https://storage.googleapis.com/storage/v1/b/apexai-bucket/o")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch available files", e)
                showNetworkErrorToast("Failed to connect to server")
                onComplete(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, "Unexpected response $response: $responseBody")
                    onComplete(emptyList())
                    return
                }

                try {
                    val files = gson.fromJson(responseBody, GcsObjectsResponse::class.java)
                        .items
                        ?.filter { it.name.endsWith("latest.json") }
                        ?.filterNot { it.name.contains("backup", ignoreCase = true) }
                        ?.latestMemoryVersions()
                        ?.map { it.name }
                        ?: emptyList()
                    onComplete(files)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse available files", e)
                    onComplete(emptyList())
                }
            }
        })
    }

    fun downloadMemoryBank(filePath: String, onComplete: (File?) -> Unit) {
        if (!isNetworkAvailable()) {
            showNetworkErrorToast("Internet connection required to download memory bank")
            onComplete(null)
            return
        }

        val request = Request.Builder()
            .url("https://storage.googleapis.com/apexai-bucket/${Uri.encode(filePath, "/")}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to download memory bank", e)
                showNetworkErrorToast("Failed to connect to server")
                onComplete(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, "Unexpected response $response: $responseBody")
                    onComplete(null)
                    return
                }

                try {
                    onComplete(saveMemoryBankFile(filePath, responseBody))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save memory bank", e)
                    onComplete(null)
                }
            }
        })
    }

    fun getDownloadedMemoryBankFiles(): List<String> {
        val directory = memoryBankDirectory()
        if (!directory.exists()) return emptyList()

        return directory
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .map { it.absolutePath }
            .sorted()
            .toList()
    }

    fun removeDownloadedMemoryBankFile(filePath: String): Boolean {
        val directory = memoryBankDirectory().canonicalFile
        val file = File(filePath).canonicalFile
        if (!file.path.startsWith(directory.path)) return false
        return file.delete()
    }

    private fun saveMemoryBankFile(filePath: String, json: String): File {
        val localPath = filePath
            .replace("\\", "/")
            .split("/")
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString(File.separator)
            .ifBlank { "memory_bank_latest.json" }

        val file = File(memoryBankDirectory(), localPath)
        file.parentFile?.mkdirs()
        file.writeText(json)
        return file
    }

    private fun memoryBankDirectory(): File {
        return File(context.filesDir, "memory_bank")
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNetworkErrorToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun List<GcsObjectItem>.latestMemoryVersions(): List<GcsObjectItem> {
        return groupBy { it.memoryFamilyName() }
            .values
            .mapNotNull { files ->
                files.maxWithOrNull(
                    compareBy<GcsObjectItem> { it.memoryVersionNumber() ?: Int.MIN_VALUE }
                        .thenBy { it.updated.orEmpty() }
                        .thenBy { it.name }
                )
            }
            .sortedBy { it.name }
    }

    private fun GcsObjectItem.memoryFamilyName(): String {
        return versionPattern.replace(name.removeSuffix(".json"), "")
    }

    private fun GcsObjectItem.memoryVersionNumber(): Int? {
        return versionPattern
            .find(name.removeSuffix(".json"))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private companion object {
        private const val TAG = "MemoryBankFileStore"
    }
}
