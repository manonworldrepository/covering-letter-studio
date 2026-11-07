package com.mosta.coveringletter.activity

import com.mosta.coveringletter.activity.UserActionLogEntry
import com.mosta.coveringletter.activity.UserActionLogBufferLimits

import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.LinkedBlockingDeque

@Component
class UserActionLogBuffer {
	private val entries = LinkedBlockingDeque<UserActionLogEntry>(UserActionLogBufferLimits.MAX_ENTRIES)

	fun record(action: String): UserActionLogEntry {
		val entry = UserActionLogEntry(
			time = UserActionLogBufferLimits.TIME_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault())),
			action = action.take(UserActionLogBufferLimits.MAX_ACTION_LENGTH)
		)
		while (!entries.offerLast(entry)) entries.pollFirst()
		return entry
	}

	fun latest(): List<UserActionLogEntry> = entries.toList()
}
