package com.mosta.coveringletter.generation

object GeminiFailureMessage {
	const val modelSwitchAdvice =
		"The app already tried other available Gemini models. Open Settings in the top-right to pick a different model, or try again later."

	fun fromHttpResponse(status: Int, providerMessage: String): String {
		if (status == 503) {
			return "Gemini models were busy or temporarily unavailable (HTTP 503). $modelSwitchAdvice"
		}
		val detail = providerMessage.takeIf(String::isNotBlank)
			?: "Gemini could not process the request (HTTP $status)."
		return "$detail $modelSwitchAdvice"
	}
}
