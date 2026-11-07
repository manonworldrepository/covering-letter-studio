package com.mosta.coveringletter.generation

object GeminiFailureMessage {
	const val modelSwitchAdvice =
		"Open Settings in the top-right, select another available Gemini model, and try again."

	fun fromHttpResponse(status: Int, providerMessage: String): String {
		if (status == 503) {
			return "The selected Gemini model is busy or temporarily unavailable (HTTP 503). $modelSwitchAdvice"
		}
		val detail = providerMessage.takeIf(String::isNotBlank)
			?: "Gemini could not process the request (HTTP $status)."
		return "$detail $modelSwitchAdvice"
	}
}
