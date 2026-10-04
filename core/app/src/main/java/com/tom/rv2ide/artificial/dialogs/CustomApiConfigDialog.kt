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

package com.tom.rv2ide.artificial.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.tom.rv2ide.R
import com.tom.rv2ide.app.BaseApplication
import com.tom.rv2ide.artificial.agents.custom.CustomApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Özel API ayarları: adres, API anahtarı, model ve istek biçimi. */
class CustomApiConfigDialog(
    private val onSave: (baseUrl: String, modelName: String) -> Unit
) : BottomSheetDialogFragment() {

  private data class Preset(val name: String, val url: String, val format: String, val model: String)

  private val presets =
      listOf(
          Preset("OpenRouter", "https://openrouter.ai/api/v1", CustomApi.FORMAT_OPENAI, "anthropic/claude-sonnet-4.5"),
          Preset("Anthropic", "https://api.anthropic.com", CustomApi.FORMAT_ANTHROPIC, "claude-sonnet-4-5"),
          Preset("OpenAI", "https://api.openai.com/v1", CustomApi.FORMAT_OPENAI, "gpt-4.1"),
          Preset("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", CustomApi.FORMAT_OPENAI, "gemini-2.5-pro"),
          Preset("DeepSeek", "https://api.deepseek.com", CustomApi.FORMAT_OPENAI, "deepseek-chat"),
          Preset("Groq", "https://api.groq.com/openai/v1", CustomApi.FORMAT_OPENAI, "llama-3.3-70b-versatile"),
          Preset("Mistral", "https://api.mistral.ai/v1", CustomApi.FORMAT_OPENAI, "codestral-latest"),
          Preset("Ollama (yerel)", "http://localhost:11434", CustomApi.FORMAT_OPENAI, "qwen2.5-coder"),
          Preset("LM Studio (yerel)", "http://localhost:1234", CustomApi.FORMAT_OPENAI, "local-model"),
      )

  private lateinit var baseUrlInput: TextInputEditText
  private lateinit var apiKeyInput: TextInputEditText
  private lateinit var modelInput: TextInputEditText
  private lateinit var formatGroup: RadioGroup
  private lateinit var testResult: MaterialTextView

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.dialog_custom_api_config, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    baseUrlInput = view.findViewById(R.id.customBaseUrlInput)
    apiKeyInput = view.findViewById(R.id.customApiKeyInput)
    modelInput = view.findViewById(R.id.customModelInput)
    formatGroup = view.findViewById(R.id.customFormatGroup)
    testResult = view.findViewById(R.id.customTestResult)
    val presetGroup = view.findViewById<ChipGroup>(R.id.presetGroup)

    loadSavedConfig()

    presets.forEach { preset ->
      val chip = Chip(requireContext())
      chip.text = preset.name
      chip.isCheckable = false
      chip.setOnClickListener {
        baseUrlInput.setText(preset.url)
        setFormat(preset.format)
        if (modelInput.text.isNullOrBlank()) modelInput.setText(preset.model)
        testResult.visibility = View.GONE
      }
      presetGroup.addView(chip)
    }

    view.findViewById<Button>(R.id.customTestButton).setOnClickListener { runTest() }
    view.findViewById<Button>(R.id.customCancelButton).setOnClickListener { dismiss() }
    view.findViewById<Button>(R.id.customSaveButton).setOnClickListener {
      val baseUrl = baseUrlInput.text?.toString()?.trim().orEmpty()
      val model = modelInput.text?.toString()?.trim().orEmpty()
      when {
        baseUrl.isEmpty() -> baseUrlInput.error = "Adres gerekli"
        model.isEmpty() -> modelInput.error = "Model adı gerekli"
        else -> {
          saveConfig(baseUrl, apiKeyInput.text?.toString()?.trim().orEmpty(), model, selectedFormat())
          onSave(baseUrl, model)
          dismiss()
        }
      }
    }
  }

  private fun selectedFormat(): String =
      if (formatGroup.checkedRadioButtonId == R.id.customFormatAnthropic) CustomApi.FORMAT_ANTHROPIC
      else CustomApi.FORMAT_OPENAI

  private fun setFormat(format: String) {
    formatGroup.check(
        if (format == CustomApi.FORMAT_ANTHROPIC) R.id.customFormatAnthropic else R.id.customFormatOpenAI,
    )
  }

  private fun runTest() {
    val baseUrl = baseUrlInput.text?.toString()?.trim().orEmpty()
    val model = modelInput.text?.toString()?.trim().orEmpty()
    if (baseUrl.isEmpty() || model.isEmpty()) {
      testResult.visibility = View.VISIBLE
      testResult.text = "Önce adres ve model adını girin."
      return
    }
    val key = apiKeyInput.text?.toString()?.trim().orEmpty()
    val format = selectedFormat()
    testResult.visibility = View.VISIBLE
    testResult.text = "Deneniyor…"
    viewLifecycleOwner.lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) { CustomApi.testConnection(baseUrl, key, model, format) }
      testResult.text = result
    }
  }

  private fun loadSavedConfig() {
    val prefs = BaseApplication.getBaseInstance().prefManager
    baseUrlInput.setText(prefs.getString(CustomApi.PREF_BASE_URL, "") ?: "")
    apiKeyInput.setText(prefs.getString(CustomApi.PREF_API_KEY, "") ?: "")
    modelInput.setText(prefs.getString(CustomApi.PREF_MODEL, "") ?: "")
    setFormat(prefs.getString(CustomApi.PREF_FORMAT, CustomApi.FORMAT_OPENAI) ?: CustomApi.FORMAT_OPENAI)
  }

  private fun saveConfig(baseUrl: String, apiKey: String, model: String, format: String) {
    val prefs = BaseApplication.getBaseInstance().prefManager
    prefs.putString(CustomApi.PREF_BASE_URL, baseUrl)
    prefs.putString(CustomApi.PREF_API_KEY, apiKey)
    prefs.putString(CustomApi.PREF_MODEL, model)
    prefs.putString(CustomApi.PREF_FORMAT, format)
  }
}
