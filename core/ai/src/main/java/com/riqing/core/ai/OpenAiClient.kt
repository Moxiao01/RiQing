package com.riqing.core.ai

import com.riqing.core.datastore.AiSettings
import com.riqing.core.model.RiQingError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiClient @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private fun client(timeoutSec: Int): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .build()

    private fun baseUrl(settings: AiSettings): String =
        settings.endpointUrl.trim().trimEnd('/')

    suspend fun chat(
        settings: AiSettings,
        request: ChatCompletionRequest,
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        if (settings.endpointUrl.isBlank() || settings.apiKey.isBlank() || settings.model.isBlank()) {
            throw RiQingError.Unauthorized("请先完成 AI 配置")
        }
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request.copy(model = settings.model))
            .toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url("${baseUrl(settings)}/chat/completions")
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        try {
            client(settings.timeoutSec).newCall(httpRequest).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw when (resp.code) {
                        401, 403 -> RiQingError.Unauthorized("鉴权失败 HTTP ${resp.code}")
                        408 -> RiQingError.Timeout()
                        in 500..599 -> RiQingError.Internal("服务端错误 HTTP ${resp.code}")
                        else -> RiQingError.Internal("请求失败 HTTP ${resp.code}: ${raw.take(200)}")
                    }
                }
                json.decodeFromString(ChatCompletionResponse.serializer(), raw)
            }
        } catch (e: IOException) {
            throw RiQingError.Timeout("网络异常：${e.message ?: "timeout"}")
        }
    }

    suspend fun connectionTest(settings: AiSettings): Result<Long> = withContext(Dispatchers.IO) {
        if (settings.endpointUrl.isBlank() || settings.apiKey.isBlank() || settings.model.isBlank()) {
            return@withContext Result.failure(RiQingError.Unauthorized("请填写 Endpoint / API Key / 模型"))
        }
        val started = System.currentTimeMillis()
        try {
            val chatReq = Request.Builder()
                .url("${baseUrl(settings)}/chat/completions")
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(
                    """{"model":"${settings.model}","messages":[{"role":"user","content":"ping"}],"max_tokens":1}"""
                        .toRequestBody("application/json".toMediaType()),
                )
                .build()
            client(settings.timeoutSec.coerceAtMost(30)).newCall(chatReq).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - started
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = raw.replace('\n', ' ').take(180)
                    val hint = when (resp.code) {
                        401, 403 -> "鉴权失败，请检查 API Key"
                        404 -> "端点或模型名可能不对，请检查 URL 与模型"
                        429 -> "触发限流，请稍后重试"
                        else -> "请求失败"
                    }
                    return@withContext Result.failure(
                        RiQingError.Internal("HTTP ${resp.code} · $hint${if (detail.isNotEmpty()) " · $detail" else ""}"),
                    )
                }
                Result.success(elapsed)
            }
        } catch (e: IOException) {
            Result.failure(RiQingError.Timeout(e.message ?: "网络异常"))
        }
    }
}
