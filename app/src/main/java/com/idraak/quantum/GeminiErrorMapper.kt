
package com.idraak.quantum

/**
 * IDRAAK - Gemini Error Mapper
 *
 * Responsibility:
 * Converts Gemini API errors into understandable
 * IDRAAK error messages.
 *
 * Current architecture:
 * Python idraak_core.py currently handles Gemini
 * HTTP errors and creates readable error messages.
 *
 * This Kotlin component remains separate so that
 * error mapping can be moved here if the architecture
 * later assigns that responsibility to Kotlin.
 *
 * Does not send API requests, parse successful
 * responses, or execute Android commands.
 */
internal class GeminiErrorMapper
