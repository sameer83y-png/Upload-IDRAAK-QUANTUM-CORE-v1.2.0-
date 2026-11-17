package com.idraak.quantum

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * IDRAAK - Gemini API Client
 *
 * Responsibility:
 * Connects Kotlin AI processing with the Python
 * Gemini implementation in idraak_core.py.
 *
 * Python owns HTTP communication, request creation,
 * response parsing, and Gemini API error details.
 */
internal class GeminiApiClient(
    context: Context
) {

    private val appContext = context.applicationContext

    suspend fun generateResponse(
        prompt: String,
        apiKey: String,
        model: String
    ): String = suspendCoroutine { continuation ->

        val completed = AtomicBoolean(false)

        fun fail(exception: Exception) {
            if (completed.compareAndSet(false, true)) {
                continuation.resumeWithException(exception)
            }
        }

        fun succeed(response: String) {
            if (completed.compareAndSet(false, true)) {
                continuation.resume(response)
            }
        }

        try {
            Thread(
                {
                    try {
                        val python = getPythonInstance()
                        val module = python.getModule("idraak_core")

                        val rawResult = module.callAttr(
                            "reply",
                            prompt,
                            apiKey,
                            model
                        ).toString()

                        val result = JSONObject(rawResult)
                        val status = result.optString("status")
                        val message = result.optString("message").trim()

                        when (status) {
                            "success" -> {
                                if (message.isEmpty()) {
                                    fail(
                                        IllegalStateException(
                                            "Gemini से खाली जवाब प्राप्त हुआ।"
                                        )
                                    )
                                } else {
                                    succeed(message)
                                }
                            }

                            "needs_api_key", "api_error", "error" -> {
                                fail(
                                    IllegalStateException(
                                        message.ifEmpty {
                                            "Gemini से संपर्क नहीं हो सका।"
                                        }
                                    )
                                )
                            }

                            else -> {
                                fail(
                                    IllegalStateException(
                                        "Python से अनजान Gemini response प्राप्त हुआ।"
                                    )
                                )
                            }
                        }

                    } catch (exception: Exception) {
                        fail(exception)
                    }
                },
                "IDRAAK-Gemini-Bridge"
            ).start()

        } catch (exception: Exception) {
            fail(exception)
        }
    }

    private fun getPythonInstance(): Python {
        synchronized(PYTHON_LOCK) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(appContext))
            }

            return Python.getInstance()
        }
    }

    private companion object {
        private val PYTHON_LOCK = Any()
    }
}
