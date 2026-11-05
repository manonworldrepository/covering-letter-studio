package com.mosta.coveringletter

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class GeminiCoverLetterService(
	private val objectMapper: ObjectMapper,
	private val settings: AppSettingsService,
	private val userActions: UserActionLogBuffer
) {
	private val webClient = WebClient.builder()
		.baseUrl("https://generativelanguage.googleapis.com")
		.build()

	fun generate(
		jobDescription: String,
		resume: String,
		supportingDocuments: String,
		format: GenerationFormat = GenerationFormat.COVER_LETTER
	): Mono<GeneratedCoverLetter> {
		val apiKey = settings.apiKey()
		val model = settings.model()
		val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
		if (apiKey.isBlank()) {
			return Mono.error(
				ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"Gemini is not configured. Complete setup in the app settings."
				)
			)
		}

		val formatInstructions = when (format) {
			GenerationFormat.COVER_LETTER -> """
				Write a tailored, professional cover letter. Connect relevant evidence to the job
				requirements, use a natural first-person voice, and avoid generic claims. Format it
				as a polished business letter: include the applicant's name at the top, then the
				current date ($currentDate), recipient/company details only when available from the
				provided documents or job description, and a concise subject line for the identified role.
				Never invent missing contact details. Follow with a greeting, several clearly separated
				body paragraphs, a professional sign-off, and the applicant's name. Use plain text with
				blank lines between sections; do not use markdown or add commentary.
			""".trimIndent()
			GenerationFormat.MOTIVATION_MESSAGE -> """
				Write a concise motivation message suitable for pasting into a job application's
				motivation or supporting-information field. Keep it to 80-120 words, in one compact
				paragraph, with no address, date, subject line, greeting, sign-off, markdown, or
				commentary. Explain why the applicant is interested in this role and connect their
				most relevant experience or strengths to the job. Make it personal and specific,
				not a complete covering letter.
			""".trimIndent()
		}
		val prompt = """
			Identify the primary job title from the job description and write a tailored response
			for that role.
			Use only facts present in the resume and supporting documents; never invent experience,
			qualifications, metrics, names, or contact details. Extract the applicant's full name
			exactly as it appears in the resume; do not infer or invent a name.
			$formatInstructions
			Return a JSON object with exactly three string properties: "applicantName" (the full
			name as printed in the resume), "jobTitle" (the concise role title identified in the
			job description, without company/location), and "letter" (the generated response).

			<Job description>
			$jobDescription
			</Job description>

			<Resume>
			$resume
			</Resume>

			<Supporting documents>
			${supportingDocuments.ifBlank { "No supporting documents provided." }}
			</Supporting documents>
		""".trimIndent()

		val request = objectMapper.createObjectNode()
		request.putArray("contents")
			.addObject()
			.putArray("parts")
			.addObject()
			.put("text", prompt)
		request.putObject("generationConfig").put("responseMimeType", "application/json")

		return webClient.post()
			.uri { builder ->
				builder.path("/v1beta/models/{model}:generateContent")
					.queryParam("key", apiKey)
					.build(model)
			}
			.bodyValue(request)
			.retrieve()
			.bodyToMono(JsonNode::class.java)
			.doOnSubscribe { userActions.record("Contacting Gemini with model $model") }
			.doOnNext { userActions.record("Received a reply from Gemini using model $model") }
			.map(::readGeneratedLetter)
			.onErrorMap { error ->
				when (error) {
					is ResponseStatusException -> error
					is WebClientResponseException -> ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						GeminiFailureMessage.fromHttpResponse(
							error.statusCode.value(),
							geminiErrorMessage(error)
						),
						error
					).also {
						logger.warn("Gemini request failed with HTTP {}.", error.statusCode.value())
					}
					else -> ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						"Could not connect to Gemini. Check your network connection and try again.",
						error
					).also {
						logger.error("Could not complete the Gemini request.", error)
					}
				}
			}
	}

	private fun geminiErrorMessage(error: WebClientResponseException): String {
		val message = try {
			objectMapper.readTree(error.responseBodyAsString)
				.path("error")
				.path("message")
				.asString("")
				.takeIf(String::isNotBlank)
		} catch (_: Exception) {
			null
		}
		return if (message == null) {
			"Gemini rejected the request (HTTP ${error.statusCode.value()}). Check your API key, model, quota, and request size."
		} else {
			"Gemini rejected the request (HTTP ${error.statusCode.value()}): $message"
		}
	}

	private fun readGeneratedLetter(response: JsonNode): GeneratedCoverLetter {
		val candidate = response.path("candidates").path(0)
		val responseText = candidate.path("content").path("parts")
			.mapNotNull { it.path("text").takeIf(JsonNode::isString)?.asString() }
			.joinToString("\n")
			.trim()
		if (responseText.isBlank()) {
			val blockReason = response.path("promptFeedback").path("blockReason").asString("")
			val finishReason = candidate.path("finishReason").asString("")
			val reason = when {
				blockReason.isNotBlank() -> "The request was blocked by Gemini ($blockReason)."
				finishReason.isNotBlank() -> "Gemini did not return a letter (reason: $finishReason)."
				else -> "Gemini returned an empty response."
			}
			throw ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"${GeminiFailureMessage.modelSwitchAdvice} $reason Please try again or adjust the documents."
			)
		}
		val generated = try {
			objectMapper.readTree(responseText)
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Gemini returned an unreadable response. ${GeminiFailureMessage.modelSwitchAdvice}",
				exception
			)
		}
		val title = generated.path("jobTitle").asString("").trim()
		val applicantName = generated.path("applicantName").asString("").trim()
		val letter = generated.path("letter").asString("").trim()
		if (applicantName.isBlank() || applicantName.length > MAX_APPLICANT_NAME_LENGTH ||
			title.isBlank() || title.length > MAX_JOB_TITLE_LENGTH || letter.isBlank()
		) {
			throw ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Gemini could not identify your full name in the resume or did not return a job title and cover letter. Check that the resume includes your name, then try again. ${GeminiFailureMessage.modelSwitchAdvice}"
			)
		}
		return GeneratedCoverLetter(letter, title, applicantName)
	}

	private companion object {
		const val MAX_APPLICANT_NAME_LENGTH = 120
		const val MAX_JOB_TITLE_LENGTH = 160
		val logger = LoggerFactory.getLogger(GeminiCoverLetterService::class.java)
	}
}

data class GeneratedCoverLetter(val letter: String, val jobTitle: String, val applicantName: String)

enum class GenerationFormat {
	COVER_LETTER,
	MOTIVATION_MESSAGE
}
