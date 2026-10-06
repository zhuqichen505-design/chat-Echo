package com.aiassistant.data.remote

import com.aiassistant.domain.model.*
import retrofit2.Call
import retrofit2.http.*

interface AiApiService {
    // ============ OpenAI 兼容格式 ============

    @POST("chat/completions")
    fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Call<ChatCompletionResponse>

    @GET("models")
    fun getModels(
        @Header("Authorization") authorization: String
    ): Call<ModelsResponse>

    // ============ Anthropic 格式 ============

    @POST("messages")
    fun anthropicMessages(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: AnthropicRequest
    ): Call<AnthropicResponse>

}
