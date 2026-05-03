package com.babyphotos.archive.domain.recognizer

import com.babyphotos.archive.domain.model.BabyDetectionResult
import com.babyphotos.archive.util.SettingsManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BabyRecognizerImpl(
    private val apiBaseUrl: String,
    private val apiKey: String,
    private val modelName: String = "gpt-4o-mini",
    private val systemPrompt: String = SettingsManager.DEFAULT_SYSTEM_PROMPT,
    private val userPrompt: String = SettingsManager.DEFAULT_USER_PROMPT
) : BabyRecognizer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun recognize(base64Image: String): Result<BabyDetectionResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestBody = buildRequest(base64Image)
                val request = Request.Builder()
                    .url("${apiBaseUrl.trimEnd('/')}/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                    ?: throw IllegalStateException("Empty response body")

                if (!response.isSuccessful) {
                    throw IllegalStateException("API error ${response.code}: $responseBody")
                }

                parseResponse(responseBody)
            }
        }

    private fun buildRequest(base64Image: String): okhttp3.RequestBody {
        val json = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", 300)
            put("temperature", 0.1)
            put("messages", JSONArray().apply {
                // System message
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                // User message with image
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", userPrompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", base64Image)
                            })
                        })
                    })
                })
            })
        }

        return json.toString()
            .toRequestBody("application/json".toMediaType())
    }

    private fun parseResponse(responseBody: String): BabyDetectionResult {
        val responseJson = JSONObject(responseBody)
        val content = responseJson
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        // Extract JSON from content (may be wrapped in markdown code block)
        val jsonStr = extractJson(content)
        val resultJson = JSONObject(jsonStr)

        return BabyDetectionResult(
            containsBaby = resultJson.optBoolean("contains_baby", false),
            confidence = resultJson.optInt("confidence", 0),
            reason = resultJson.optString("reason", "")
        )
    }

    private fun extractJson(content: String): String {
        // Try to extract JSON from markdown code block: ```json ... ```
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val match = codeBlockRegex.find(content)
        if (match != null) return match.groupValues[1].trim()

        // Try to find raw JSON object
        val jsonRegex = Regex("\\{[\\s\\S]*\\}")
        val jsonMatch = jsonRegex.find(content)
        if (jsonMatch != null) return jsonMatch.value

        return content.trim()
    }
}
