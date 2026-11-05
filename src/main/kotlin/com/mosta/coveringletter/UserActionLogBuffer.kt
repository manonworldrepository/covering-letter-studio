package com.mosta.coveringletter

import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

data class UserActionLogEntry(val time: String, val action: String)

@Component
class UserActionLogBuffer {
	private val entries = ArrayDeque<UserActionLogEntry>()

	fun record(action: String) {
		val entry = UserActionLogEntry(
			time = TIME_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault())),
			action = action.take(MAX_ACTION_LENGTH)
		)
		synchronized(entries) {
			entries.addLast(entry)
			while (entries.size > MAX_ENTRIES) entries.removeFirst()
		}
	}

	fun latest(): List<UserActionLogEntry> = synchronized(entries) { entries.toList() }

	private companion object {
		const val MAX_ENTRIES = 200
		const val MAX_ACTION_LENGTH = 200
		val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
	}
}
