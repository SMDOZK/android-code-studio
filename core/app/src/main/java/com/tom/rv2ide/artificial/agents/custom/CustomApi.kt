/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.tom.rv2ide.artificial.agents.custom

import android.content.Context
import com.tom.rv2ide.app.BaseApplication
import com.tom.rv2ide.artificial.agents.AIAgent
import com.tom.rv2ide.artificial.agents.AIAgentRegistry
import com.tom.rv2ide.artificial.agents.ModificationAttempt
import com.tom.rv2ide.artificial.agents.local.ConversationMessage
import com.tom.rv2ide.artificial.exceptions.InvalidApiKeyException
import com.tom.rv2ide.artificial.exceptions.QuotaExceededException
import com.tom.rv2ide.artificial.exceptions.RateLimitException
import com.tom.rv2ide.artificial.file.AIFileWriter
import com.tom.rv2ide.artificial.file.FileWriteResult
import com.tom.rv2ide.artificial.project.awareness.ProjectTreeResult
import com.tom.rv2ide.artificial.rules.WritingRules
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Özel API sağlayıcısı: kullanıcının verdiği adres + API anahtarı + model ile çalışır.
 *
 * İki istek biçimi desteklenir:
 *  - "openai"    → POST {adres}/chat/completions, "Authorization: Bearer <anahtar>"
 *                  (OpenRouter, Groq, DeepSeek, Mistral, Together, xAI, Gemini'nin OpenAI uyumlu ucu,
 *                   Ollama, LM Studio, vLLM ... gibi OpenAI uyumlu tüm servisler)
 *  - "anthropic" → POST {adres}/v1/messages, "x-api-key" + "anthropic-version"
 *                  (Anthropic ve Anthropic uyumlu servisler)
 */
class CustomApi : AIAgent {

  private var baseUrl: String? = null
  private var apiKey: String = ""
  private var modelName: String? = null
  private var format: String = FORMAT_OPENAI
  private val httpClient =
      OkHttpClient.Builder()
          .connectTimeout(60, TimeUnit.SECONDS)
          .readTimeout(300, TimeUnit.SECONDS)
          .writeTimeout(60, TimeUnit.SECONDS)
          .build()
  private val writingRules = WritingRules.Instructions()
  private var projectTreeResult: ProjectTreeResult? = null
  private var fileWriter: AIFileWriter? = null
  private val conversationHistory = mutableListOf<ConversationMessage>()
  private val modificationHistory = mutableListOf<ModificationAttempt>()
  private var currentAttemptCount = 0
  private val maxRetryAttempts = 3

  override val providerId = PROVIDER_ID
  override val providerName = PROVIDER_NAME

  companion object {
    const val PROVIDER_ID = "custom"
    const val PROVIDER_NAME = "Özel API"

    const val PREF_BASE_URL = "custom_api_base_url"
    const val PREF_API_KEY = "custom_api_key"
    const val PREF_MODEL = "custom_api_model_name"
    const val PREF_FORMAT = "custom_api_format"

    const val FORMAT_OPENAI = "openai"
    const val FORMAT_ANTHROPIC = "anthropic"

    fun savedModel(): String =
        BaseApplication.getBaseInstance().prefManager.getString(PREF_MODEL, "") ?: ""

    fun registerAgent() {
      AIAgentRegistry.register(
          PROVIDER_ID,
          object : AIAgentRegistry.AgentFactory {
            override fun create(context: Context): AIAgent = CustomApi()

            override fun hasValidApiKey(): Boolean {
              val prefs = BaseApplication.getBaseInstance().prefManager
              val url = prefs.getString(PREF_BASE_URL, null)
              val model = prefs.getString(PREF_MODEL, null)
              return !url.isNullOrBlank() && !model.isNullOrBlank()
            }

            // Yerel sunucularda anahtar boş olabilir; yöneticinin initialize çağırabilmesi için boş olmayan bir değer döner.
            override fun getApiKey(): String {
              val key = BaseApplication.getBaseInstance().prefManager.getString(PREF_API_KEY, "") ?: ""
              return key.ifBlank { "none" }
            }
          },
      )
    }

    /**
     * Ayar penceresindeki "Bağlantıyı test et" için küçük bir istek gönderir (arka planda çağrılmalı).
     * Başarılıysa "✓ ..." ile başlayan, değilse hatayı anlatan bir metin döner.
     */
    fun testConnection(base: String, key: String, model: String, format: String): String {
      val anthropic = format == FORMAT_ANTHROPIC
      val messages =
          JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", "Reply with OK.")
          })
      val body =
          JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 16)
            if (!anthropic) put("stream", false)
          }
      val builder =
          Request.Builder()
              .url(endpointFor(base, format))
              .post(body.toString().toRequestBody("application/json".toMediaType()))
      if (anthropic) {
        if (key.isNotBlank()) builder.header("x-api-key", key)
        builder.header("anthropic-version", "2023-06-01")
      } else if (key.isNotBlank()) {
        builder.header("Authorization", "Bearer $key")
      }
      val client =
          OkHttpClient.Builder()
              .connectTimeout(20, TimeUnit.SECONDS)
              .readTimeout(60, TimeUnit.SECONDS)
              .build()
      return try {
        client.newCall(builder.build()).execute().use { r ->
          val text = r.body?.string() ?: ""
          if (r.isSuccessful) {
            "✓ Bağlantı başarılı (${r.code}) — ${endpointFor(base, format)}"
          } else {
            val detail =
                try {
                  val json = JSONObject(text)
                  when (val e = json.opt("error")) {
                    is JSONObject -> e.optString("message", text)
                    is String -> e
                    else -> json.optString("message", text)
                  }
                } catch (ex: Exception) {
                  text.take(300)
                }
            "✗ Hata ${r.code}: $detail"
          }
        }
      } catch (e: Exception) {
        "✗ Bağlanılamadı: ${e.message ?: e.javaClass.simpleName}"
      }
    }

    /** Kullanıcının yazdığı adresten tam uç noktayı üretir. */
    fun endpointFor(base: String, format: String): String {
      val b = base.trim().trimEnd('/')
      return if (format == FORMAT_ANTHROPIC) {
        when {
          b.endsWith("/messages") -> b
          b.endsWith("/v1") -> "$b/messages"
          else -> "$b/v1/messages"
        }
      } else {
        when {
          b.endsWith("/chat/completions") -> b
          Regex(".*/v\\d+[a-z0-9]*$").matches(b) || b.endsWith("/openai") -> "$b/chat/completions"
          else -> "$b/v1/chat/completions"
        }
      }
    }
  }

  override fun initialize(apiKey: String, context: Context) {
    val prefs = BaseApplication.getBaseInstance().prefManager
    baseUrl = prefs.getString(PREF_BASE_URL, null)?.trim()
    modelName = prefs.getString(PREF_MODEL, null)?.trim()
    format = prefs.getString(PREF_FORMAT, FORMAT_OPENAI) ?: FORMAT_OPENAI
    this.apiKey = (prefs.getString(PREF_API_KEY, "") ?: "").trim()
    android.util.Log.d("CustomApi", "Initialized: url=$baseUrl, model=$modelName, format=$format")
    if (baseUrl.isNullOrEmpty() || modelName.isNullOrEmpty()) {
      throw IllegalStateException("Özel API yapılandırılmadı. Adres ve model adını girin.")
    }
  }

  override fun isInitialized(): Boolean = !baseUrl.isNullOrEmpty() && !modelName.isNullOrEmpty()

  override fun reinitializeWithNewModel(apiKey: String, context: Context) {
    initialize(apiKey, context)
  }

  override fun setContext(context: Context) {
    fileWriter = AIFileWriter(context)
  }

  override fun setProjectData(projectTreeResult: ProjectTreeResult) {
    this.projectTreeResult = projectTreeResult
  }

  override fun clearConversation() {
    conversationHistory.clear()
    modificationHistory.clear()
    currentAttemptCount = 0
  }

  override fun recordModification(filePath: String, oldContent: String?, newContent: String, success: Boolean) {
    modificationHistory.add(
        ModificationAttempt(
            timestamp = System.currentTimeMillis(),
            filePath = filePath,
            previousContent = oldContent,
            newContent = newContent,
            attemptNumber = currentAttemptCount,
            success = success,
        ),
    )
  }

  override fun undoLastModification(): Boolean {
    val lastMod = modificationHistory.lastOrNull { it.success } ?: return false
    if (lastMod.previousContent != null) {
      val result = writeFile(lastMod.filePath, lastMod.previousContent)
      if (result is FileWriteResult.Success) {
        modificationHistory.removeAt(modificationHistory.lastIndexOf(lastMod))
        return true
      }
      return false
    }
    return try {
      File(lastMod.filePath).delete()
      modificationHistory.removeAt(modificationHistory.lastIndexOf(lastMod))
      true
    } catch (e: Exception) {
      false
    }
  }

  override fun getModificationHistory(): List<ModificationAttempt> = modificationHistory.toList()

  override fun resetAttemptCount() {
    currentAttemptCount = 0
  }

  override fun incrementAttemptCount() {
    currentAttemptCount++
  }

  override fun getCurrentAttemptCount(): Int = currentAttemptCount

  override fun canRetry(): Boolean = currentAttemptCount < maxRetryAttempts

  private fun isUserRequestingCorrection(message: String): Boolean {
    val keywords =
        listOf(
            "wrong", "not what", "mistake", "error", "incorrect", "that's not", "not right", "fix", "undo",
            "revert", "different", "try again", "not working", "yanlış", "hata", "düzelt", "olmadı", "geri al",
        )
    val lower = message.lowercase()
    return keywords.any { lower.contains(it) }
  }

  override suspend fun generateCode(
      prompt: String,
      context: String?,
      language: String,
      projectStructure: String?,
  ): Result<String> =
      withContext(Dispatchers.IO) {
        try {
          val base = baseUrl
          val model = modelName
          if (base.isNullOrEmpty() || model.isNullOrEmpty()) {
            return@withContext Result.failure(IllegalStateException("Özel API yapılandırılmadı"))
          }

          val fullPrompt = buildPrompt(prompt, context)
          val anthropic = format == FORMAT_ANTHROPIC
          val body =
              if (anthropic) {
                JSONObject().apply {
                  put("model", model)
                  put("max_tokens", 8192)
                  put("system", writingRules.useThis())
                  put(
                      "messages",
                      JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", fullPrompt)
                      }),
                  )
                }
              } else {
                JSONObject().apply {
                  put("model", model)
                  put(
                      "messages",
                      JSONArray()
                          .put(JSONObject().apply {
                            put("role", "system")
                            put("content", writingRules.useThis())
                          })
                          .put(JSONObject().apply {
                            put("role", "user")
                            put("content", fullPrompt)
                          }),
                  )
                  put("stream", false)
                }
              }

          val builder =
              Request.Builder()
                  .url(endpointFor(base, format))
                  .post(body.toString().toRequestBody("application/json".toMediaType()))
          if (anthropic) {
            if (apiKey.isNotBlank()) builder.header("x-api-key", apiKey)
            builder.header("anthropic-version", "2023-06-01")
          } else if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
          }
          // OpenRouter gibi servisler uygulamayı bu başlıklarla tanır (zararsız)
          builder.header("HTTP-Referer", "https://github.com/AndroidCSOfficial/android-code-studio")
          builder.header("X-Title", "Android Code Studio")

          val response =
              try {
                httpClient.newCall(builder.build()).execute()
              } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("timeout", ignoreCase = true) || msg.contains("connect", ignoreCase = true)) {
                  throw RateLimitException("Bağlantı kurulamadı: $msg")
                }
                throw e
              }

          response.use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) {
              val detail = errorMessage(text)
              when (r.code) {
                401, 403 -> throw InvalidApiKeyException("API anahtarı geçersiz veya yetkisiz (${r.code}): $detail")
                402 -> throw QuotaExceededException("Bakiye / kota yetersiz (402): $detail")
                429 -> throw RateLimitException("İstek sınırı aşıldı (429): $detail")
                else -> return@withContext Result.failure(Exception("Özel API hatası ${r.code}: $detail"))
              }
            }

            val json = JSONObject(text)
            val generated = if (anthropic) parseAnthropic(json) else parseOpenAI(json)
            if (generated.isBlank()) {
              return@withContext Result.failure(Exception("Özel API boş yanıt döndü"))
            }

            conversationHistory.add(ConversationMessage("user", prompt))
            conversationHistory.add(ConversationMessage("assistant", generated))
            while (conversationHistory.size > 20) {
              conversationHistory.removeAt(0)
            }
            Result.success(generated)
          }
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

  private fun buildPrompt(prompt: String, context: String?): String {
    val fileContents = readRelevantFiles()
    val needsCorrection = isUserRequestingCorrection(prompt)
    return buildString {
      append("=== PROJECT STRUCTURE (THESE ARE THE EXACT PATHS YOU MUST USE) ===\n")
      projectTreeResult?.let {
        append(it.tree)
        append("\n\n")
        append("CRITICAL: Use ONLY the paths shown above. Do NOT make up fake paths.\n\n")
      }
      if (fileContents.isNotEmpty()) {
        append("=== CURRENT FILES CONTENT ===\n")
        fileContents.forEach { (path, content) ->
          append("FILE: $path\nCONTENT:\n")
          append(content)
          append("\n\n")
        }
      }
      if (context != null) {
        append("=== ADDITIONAL CONTEXT ===\n")
        append(context)
        append("\n\n")
      }
      if (conversationHistory.isNotEmpty()) {
        append("=== CONVERSATION HISTORY ===\n")
        conversationHistory.forEach { msg -> append("${msg.role.uppercase()}: ${msg.content}\n\n") }
      }
      if (needsCorrection && modificationHistory.isNotEmpty()) {
        append("=== CORRECTION REQUIRED ===\n")
        append("The user indicated the previous modification was WRONG.\n")
        modificationHistory.takeLast(3).forEach { attempt ->
          append("Attempt ${attempt.attemptNumber}: ${attempt.filePath}\n")
          append("Result: ${if (attempt.success) "Applied but user rejected" else "Failed"}\n\n")
        }
        append("You MUST try a DIFFERENT approach.\n\n")
      }
      if (currentAttemptCount > 0) {
        append("=== RETRY ATTEMPT $currentAttemptCount/$maxRetryAttempts ===\n")
        append("Previous attempts did not satisfy the user. Provide a different solution.\n\n")
      }
      append("=== USER REQUEST ===\n")
      append(prompt)
    }
  }

  /** OpenAI biçimi: choices[0].message.content (metin ya da parça dizisi). */
  private fun parseOpenAI(json: JSONObject): String {
    val choices = json.optJSONArray("choices") ?: return ""
    if (choices.length() == 0) return ""
    val message = choices.getJSONObject(0).optJSONObject("message") ?: return ""
    val content = message.opt("content")
    return when (content) {
      is String -> content
      is JSONArray -> buildString {
        for (i in 0 until content.length()) {
          val part = content.optJSONObject(i) ?: continue
          append(part.optString("text", ""))
        }
      }
      else -> ""
    }
  }

  /** Anthropic biçimi: content[] içindeki "text" blokları. */
  private fun parseAnthropic(json: JSONObject): String {
    val content = json.optJSONArray("content") ?: return ""
    return buildString {
      for (i in 0 until content.length()) {
        val block = content.optJSONObject(i) ?: continue
        if (block.optString("type") == "text") append(block.optString("text", ""))
      }
    }
  }

  private fun errorMessage(body: String): String {
    return try {
      val json = JSONObject(body)
      val error = json.opt("error")
      when (error) {
        is JSONObject -> error.optString("message", body)
        is String -> error
        else -> json.optString("message", body)
      }
    } catch (e: Exception) {
      body.take(500)
    }
  }

  private fun readRelevantFiles(): Map<String, String> {
    val filesContent = mutableMapOf<String, String>()
    val tree = projectTreeResult?.tree ?: return filesContent
    tree.lines().filter { it.isNotBlank() }.forEach { line ->
      val path = line.trim()
      val file = File(path)
      if (
          file.isFile &&
              (path.endsWith(".kt") || path.endsWith(".java") || path.endsWith(".xml") ||
                  path.endsWith(".gradle") || path.endsWith(".gradle.kts")) &&
              !path.contains("/build/") &&
              !path.contains("/.gradle/")
      ) {
        try {
          filesContent[path] = file.readText()
        } catch (e: Exception) {
          // okunamayan dosyayı atla
        }
      }
    }
    return filesContent
  }

  override fun writeFile(filePath: String, content: String): FileWriteResult {
    val writer = fileWriter ?: return FileWriteResult.Error("File writer not initialized")
    return writer.writeFile(filePath, content, createBackup = true)
  }
}
