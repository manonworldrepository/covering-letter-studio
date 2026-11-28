package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.activity.UserActionLogEntry
import com.mosta.coveringletter.api.request.UserActionRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
class LogsController(
	private val logs: UserActionLogBuffer
) {
	@GetMapping("/api/logs")
	fun getUserActions(principal: Principal) = logs.latest(principal.name)

	@DeleteMapping("/api/logs")
	fun clearUserActions(principal: Principal): List<UserActionLogEntry> {
		logs.clear(principal.name)
		return logs.latest(principal.name)
	}

	@PostMapping("/api/logs", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun recordUserAction(@Valid @RequestBody request: UserActionRequest, principal: Principal) = logs.record(request.action, principal.name)
}
