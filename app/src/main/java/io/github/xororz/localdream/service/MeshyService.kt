package io.github.xororz.localdream.service

import android.graphics.Bitmap
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import android.util.Base64
import io.github.xororz.localdream.BuildConfig

private const val TAG = "MeshyService"

data class MeshyModelUrls(
    val glb: String,
    val fbx: String,
    val usdz: String,
    val obj: String
)

object MeshyService {
    private const val API_BASE = "https://api.meshy.ai/openapi/v1/image-to-3d"
    private val API_KEY = BuildConfig.MESHY_API_KEY
    private val client = OkHttpClient()

    @Throws(IOException::class)
    suspend fun createTask(bitmap: Bitmap): String {
        // 1) PNG → Base64
        val base64 = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
            baos.toByteArray()
        }.let { bytes ->
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        // 2) data URI
        val dataUri = "data:image/png;base64,$base64"

        val payload = JSONObject().apply {
            put("image_url", dataUri)
            put("preset", "standard")
        }
        Log.d(TAG, "createTask() payload: $payload")

        val body = payload.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        val req = Request.Builder()
            .url(API_BASE)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            Log.d(TAG, "createTask() response code=${resp.code}, body=$respBody")
            if (!resp.isSuccessful) {
                throw IOException("Meshy createTask failed: HTTP ${resp.code} → $respBody")
            }
            val result = JSONObject(respBody).getString("result")
            Log.d(TAG, "createTask() taskId=$result")
            return result
        }
    }

    @Throws(IOException::class, RuntimeException::class)
    suspend fun pollTask(
        taskId: String,
        intervalMs: Long = 5_000,
        timeoutMs: Long = 600_000 //10분
    ): MeshyModelUrls {
        val url = "$API_BASE/$taskId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $API_KEY")
            .build()

        Log.d(TAG, "pollTask() start polling for taskId=$taskId")
        val startTime = System.currentTimeMillis()

        while (true) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "pollTask() HTTP ${response.code} → $body")
                if (!response.isSuccessful) {
                    throw IOException("Meshy pollTask failed: HTTP ${response.code}")
                }

                val data = JSONObject(body)
                val status = data.getString("status")
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "pollTask() status=$status, elapsed=${elapsed}ms")

                when (status) {
                    "SUCCEEDED" -> {
                        val urls = data.getJSONObject("model_urls")
                        val result = MeshyModelUrls(
                            glb = urls.getString("glb"),
                            fbx = urls.getString("fbx"),
                            usdz = urls.getString("usdz"),
                            obj = urls.getString("obj")
                        )
                        Log.d(TAG, "pollTask() SUCCEEDED, urls=$result")
                        return result
                    }
                    "FAILED", "ERROR" -> {
                        val errMsg = data
                            .optJSONObject("task_error")
                            ?.optString("message", "Unknown error")
                            ?: "Unknown error"
                        Log.e(TAG, "pollTask() task failed: $errMsg")
                        throw RuntimeException("Meshy task failed: $errMsg")
                    }
                    else -> {
                        if (elapsed > timeoutMs) {
                            Log.e(TAG, "pollTask() timeout after ${elapsed}ms")
                            throw RuntimeException("3D 모델 준비 시간 초과")
                        }
                        delay(intervalMs)
                    }
                }
            }
        }
    }
}
