package com.gzl.aitranslator

import android.content.Context
import android.content.SharedPreferences

object AppConfig {
    private const val PREFS_NAME = "ai_translator_config"
    private const val KEY_TRANSLATION_PROMPT = "translation_prompt"
    private const val KEY_MODEL_SRC_PATH = "model_src_path"

    private const val DEFAULT_PROMPT = "帮我把如下内容翻译成中文\n\n{text}"
    private const val DEFAULT_MODEL_PATH = "/storage/emulated/0/AiTranslator/gemma4_4b.litertlm"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var translationPrompt: String
        get() = prefs.getString(KEY_TRANSLATION_PROMPT, DEFAULT_PROMPT) ?: DEFAULT_PROMPT
        set(value) { prefs.edit().putString(KEY_TRANSLATION_PROMPT, value).apply() }

    var modelSrcPath: String
        get() = prefs.getString(KEY_MODEL_SRC_PATH, DEFAULT_MODEL_PATH) ?: DEFAULT_MODEL_PATH
        set(value) { prefs.edit().putString(KEY_MODEL_SRC_PATH, value).apply() }

    fun buildPrompt(text: String): String = translationPrompt.replace("{text}", text)

    fun resetPrompt() { translationPrompt = DEFAULT_PROMPT }

    fun resetModelPath() { modelSrcPath = DEFAULT_MODEL_PATH }
}
