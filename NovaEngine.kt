// =================================================================
//                     NOVA ENGINE & AI ROUTER                     
//                  File: NovaEngine.kt                            
// =================================================================
package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CommandResult(
  val handled: Boolean,
  val responseText: String,
  val latencyMs: Long,
  val actionTag: String? = null
)

object NovaEngine {
  fun parseOfflineCommand(input: String, batteryLevel: Int, ramMB: Float): CommandResult {
    val startTime = System.currentTimeMillis()
    val clean = input.lowercase().trim()

    val (handled, response, tag) = when {
      clean.contains("flashlight on") -> Triple(true, "Flashlight activated for Adam.", "TORCH_ON")
      clean.contains("flashlight off") -> Triple(true, "Flashlight turned off.", "TORCH_OFF")
      clean.contains("battery") -> Triple(true, "Adam, your battery is at $batteryLevel%. Systems optimal.", null)
      else -> Triple(false, "Routing to Gemini Flash Neural Core...", null)
    }

    val latency = System.currentTimeMillis() - startTime
    return CommandResult(handled, response, latency, tag)
  }

  suspend fun queryGemini(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
    if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
      return@withContext "Nova AI: Subsystems nominal Adam. Configure your Gemini API key in Settings."
    }
    try {
      val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=$apiKey")
      val conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.setRequestProperty("Content-Type", "application/json")
      conn.doOutput = true

      val jsonBody = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().apply {
                put("text", "You are Nova AI, personal assistant for Adam. Answer concisely: $prompt")
              })
            })
          })
        })
      }

      conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray()) }

      if (conn.responseCode == 200) {
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val resJson = JSONObject(response)
        resJson.getJSONArray("candidates")
          .getJSONObject(0)
          .getJSONObject("content")
          .getJSONArray("parts")
          .getJSONObject(0)
          .getString("text").trim()
      } else {
        "Nova AI: Request processed locally."
      }
    } catch (_: Exception) {
      "Nova AI: Network offline. Local rules applied for Adam."
    }
  }
}
