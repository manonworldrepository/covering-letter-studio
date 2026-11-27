package com.mosta.coveringletter.generation

import com.mosta.coveringletter.aspects.GeminiServiceLoggingAspect
import com.mosta.coveringletter.settings.AppSettingsService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

@Service
class GeminiGenerationClient(
	private val objectMapper: ObjectMapper,
	private val settings: AppSettingsService,
	private val loggingAspect: GeminiServiceLoggingAspect
) {
	private val webClient = WebClient.builder()
		.baseUrl("https://generativelanguage.googleapis.com")
		.build()

	fun generate(prompt: String): Mono<GeminiGeneratedContent> {
		val apiKey = settings.apiKey()
		val preferredModel = settings.model()
		if (apiKey.isBlank()) {
			return Mono.error(
				ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"Gemini is not configured. Complete setup in the app settings."
				)
			)
		}

		val request = objectMapper.createObjectNode()
		request.putArray("contents")
			.addObject()
			.putArray("parts")
			.addObject()
			.put("text", prompt)
		request.putObject("generationConfig").put("responseMimeType", "application/json")

		return resolveCandidateModels(apiKey, preferredModel)
			.flatMap { candidates -> generateWithFallback(candidates, apiKey, request) }
	}

	private fun resolveCandidateModels(apiKey: String, preferredModel: String): Mono<List<String>> =
		settings.availableModels(apiKey)
			.map { models -> GeminiModelFallback.candidates(preferredModel, models.map { it.id }) }
			.onErrorReturn(GeminiModelFallback.candidates(preferredModel, emptyList()))

	private fun generateWithFallback(
		candidates: List<String>,
		apiKey: String,
		request: JsonNode
	): Mono<GeminiGeneratedContent> {
		val lastError = AtomicReference<Throwable>()
		return Flux.fromIterable(candidates.withIndex())
			.concatMap { (index, model) ->
				callModel(model, apiKey, request)
					.doOnNext {
						if (index > 0) {
							loggingAspect.logModelSwitched(model)
						}
					}
					.onErrorResume { error ->
						lastError.set(error)
						val hasMore = index < candidates.lastIndex
						if (hasMore && GeminiModelFallback.isRetryable(error)) {
							loggingAspect.logModelFailed(model)
							loggingAspect.logModelFallback(model, index + 1, candidates.size)
							Mono.empty()
						} else {
							Mono.error(mapGenerationError(error))
						}
					}
			}
			.next()
			.switchIfEmpty(
				Mono.defer {
					Mono.error(mapGenerationError(lastError.get() ?: defaultFallbackFailure()))
				}
			)
	}

	private fun callModel(model: String, apiKey: String, request: JsonNode): Mono<GeminiGeneratedContent> =
		webClient.post()
			.uri { builder ->
				builder.path("/v1beta/models/{model}:generateContent")
					.queryParam("key", apiKey)
					.build(model)
			}
			.bodyValue(request)
			.retrieve()
			.bodyToMono(JsonNode::class.java)
			.doOnSubscribe { loggingAspect.logModelContacted(model) }
			.doOnNext { loggingAspect.logModelReplied(model) }
			.map(::readGeneratedContent)
			.retryWhen(
				Retry.backoff(GeminiGenerationClientSupport.MAX_RETRIES.toLong(), Duration.ofSeconds(1))
					.filter { GeminiModelFallback.isRetryable(it) }
					.doBeforeRetry { signal ->
						val attempt = signal.totalRetries().toInt()
						val reason = extractFailureReason(signal.failure())
						loggingAspect.logRetry(model, attempt, GeminiGenerationClientSupport.MAX_RETRIES, reason)
					}
			)

	private fun extractFailureReason(error: Throwable): String =
		when (error) {
			is WebClientResponseException -> when (error.statusCode.value()) {
				429 -> "Gemini is busy (rate limited)"
				408 -> "Request timed out"
				404 -> "Model not found"
				in 500..599 -> "Gemini server error (HTTP ${error.statusCode.value()})"
				else -> "Gemini error (HTTP ${error.statusCode.value()})"
			}
			else -> "Network error"
		}

	private fun mapGenerationError(error: Throwable): ResponseStatusException =
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
				loggingAspect.logHttpError(error.statusCode.value())
			}
			else -> ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Could not connect to Gemini. Check your network connection and try again.",
				error
			).also {
				loggingAspect.logConnectionFailure(error)
			}
		}

	private fun defaultFallbackFailure(): ResponseStatusException =
		ResponseStatusException(
			HttpStatus.BAD_GATEWAY,
			"All tried Gemini models failed to respond. ${GeminiFailureMessage.modelSwitchAdvice}"
		)

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

	private fun readGeneratedContent(response: JsonNode): GeminiGeneratedContent {
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
				finishReason.isNotBlank() -> "Gemini did not return generated content (reason: $finishReason)."
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
		val companyName = generated.path("companyName").asString("").trim()
		val content = generated.path("content").asString("").trim()
			.ifBlank { generated.path("letter").asString("").trim() }
		if (applicantName.isBlank() ||
			applicantName.length > GeminiGenerationClientSupport.MAX_APPLICANT_NAME_LENGTH ||
			title.isBlank() || title.length > GeminiGenerationClientSupport.MAX_JOB_TITLE_LENGTH ||
			companyName.length > GeminiGenerationClientSupport.MAX_COMPANY_NAME_LENGTH ||
			content.isBlank()
		) {
			throw ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Gemini could not identify your full name in the resume or did not return a job title and generated content. Check that the resume includes your name, then try again. ${GeminiFailureMessage.modelSwitchAdvice}"
			)
		}
		return GeminiGeneratedContent(content, title, applicantName, companyName)
	}
}