package com.mosta.coveringletter.aspects

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(GeminiServiceLoggingAspect::class.java)

/**
 * Centralized logging for Gemini cover letter service operations.
 * Called from [com.mosta.coveringletter.generation.GeminiCoverLetterService] instead of inline logging.
 */
@Component
class GeminiServiceLoggingAspect(
	private val userActions: UserActionLogBuffer
) {

	fun logModelFallback(model: String, attempt: Int, total: Int) {
		logger.warn(
			"Gemini model {} failed; trying fallback ({}/{}).",
			model,
			attempt,
			total
		)
	}

	fun logHttpError(statusCode: Int) {
		logger.warn("Gemini request failed with HTTP {}.", statusCode)
	}

	fun logConnectionFailure(error: Throwable) {
		logger.error("Could not complete the Gemini request.", error)
	}

	// --- User-facing action log entries ---

	fun logModelContacted(model: String) {
		userActions.record("Contacting Gemini with model $model")
	}

	fun logModelReplied(model: String) {
		userActions.record("Received a reply from Gemini using model $model")
	}

	fun logModelSwitched(model: String) {
		userActions.record("Switched to Gemini model $model after a previous model failed")
	}

	fun logModelFailed(model: String) {
		userActions.record("Gemini model $model failed to respond; trying another model")
	}

	fun logRetry(model: String, attempt: Int, maxRetries: Int, reason: String) {
		logger.warn(
			"Gemini model {} request failed; retrying (attempt {}/{}) — {}.",
			model,
			attempt,
			maxRetries,
			reason
		)
		userActions.record("Retry $attempt/$maxRetries: $reason")
	}

	fun logCoverLetterGenerated(format: String) {
		userActions.record(format)
	}
}
