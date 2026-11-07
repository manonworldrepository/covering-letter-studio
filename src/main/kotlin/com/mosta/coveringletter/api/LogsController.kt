package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.request.UserActionRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class LogsController(
	private val logs: UserActionLogBuffer
) {
	@GetMapping("/api/logs")
	fun getUserActions() = logs.latest()

	@PostMapping("/api/logs", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun recordUserAction(@Valid @RequestBody request: UserActionRequest) = logs.record(request.action)
}
