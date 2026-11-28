package com.mosta.coveringletter.aspects

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(GeminiServiceLoggingAspect::class.java)

/**
 * Centralized logging for Gemini generation operations.
 * Called from generation services instead of inline logging.
 */
@Component
class GeminiServiceLoggingAspect(
	private val userActions: UserActionLogBuffer
) {

	fun logModelFallback(model: String, attempt: Int, total: Int, accountEmail: String? = null) {
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

	fun logModelContacted(model: String, accountEmail: String? = null) {
		userActions.record("Contacting Gemini with model $model", accountEmail)
	}

	fun logModelReplied(model: String, accountEmail: String? = null) {
		userActions.record("Received a reply from Gemini using model $model", accountEmail)
	}

	fun logModelSwitched(model: String, accountEmail: String? = null) {
		userActions.record("Switched to Gemini model $model after a previous model failed", accountEmail)
	}

	fun logModelFailed(model: String, accountEmail: String? = null) {
		userActions.record("Gemini model $model failed to respond; trying another model", accountEmail)
	}

	fun logRetry(model: String, attempt: Int, maxRetries: Int, reason: String, accountEmail: String? = null) {
		logger.warn(
			"Gemini model {} request failed; retrying (attempt {}/{}) — {}.",
			model,
			attempt,
			maxRetries,
			reason
		)
		userActions.record("Retry $attempt/$maxRetries: $reason", accountEmail)
	}

	fun logCoverLetterGenerated(format: String, accountEmail: String? = null) {
		userActions.record(format, accountEmail)
	}

	fun logResumeTailored(accountEmail: String? = null) {
		userActions.record("Tailored a resume", accountEmail)
	}
}
