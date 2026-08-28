package com.example.aisecretary.ai

import com.example.aisecretary.BuildConfig

/**
 * Builds request URLs for Vertex AI's "Express" endpoint - the same generateContent/
 * streamGenerateContent Gemini API AI Studio uses, but billed against Google Cloud credit
 * instead of the AI Studio free tier (which throttles to 20 requests before 429ing for minutes
 * at a time; see agent.md 4).
 *
 * Auth is a single API key (VERTEX_API_KEY) bound server-side to a service account - no OAuth
 * flow or credentials file on the device.
 */
object VertexAi {
    private const val HOST = "https://aiplatform.googleapis.com"
    private const val LOCATION = "global"

    fun generateContentUrl(model: String): String = baseUrl(model) + ":generateContent?key=" + BuildConfig.VERTEX_API_KEY

    fun streamGenerateContentUrl(model: String): String =
        baseUrl(model) + ":streamGenerateContent?alt=sse&key=" + BuildConfig.VERTEX_API_KEY

    private fun baseUrl(model: String): String =
        "$HOST/v1/projects/${BuildConfig.VERTEX_PROJECT_ID}/locations/$LOCATION/publishers/google/models/$model"
}
