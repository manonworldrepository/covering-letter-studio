package com.mosta.coveringletter.activity

import com.mosta.coveringletter.activity.UserActionLogEntry
import com.mosta.coveringletter.activity.UserActionLogBufferLimits

import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ConcurrentHashMap

@Component
class UserActionLogBuffer {
	private val entriesByAccount = ConcurrentHashMap<String, LinkedBlockingDeque<UserActionLogEntry>>()
	private val activeAccount = ThreadLocal<String?>()

	fun record(action: String, accountEmail: String? = null): UserActionLogEntry {
		val entries = entriesFor(accountEmail ?: activeAccount.get())
		val entry = UserActionLogEntry(
			time = UserActionLogBufferLimits.TIME_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault())),
			action = action.take(UserActionLogBufferLimits.MAX_ACTION_LENGTH)
		)
		while (!entries.offerLast(entry)) entries.pollFirst()
		return entry
	}

	fun latest(accountEmail: String? = null): List<UserActionLogEntry> = entriesFor(accountEmail).toList()

	fun currentAccountEmail(): String? = activeAccount.get()

	fun clear(accountEmail: String? = null) {
		if (accountEmail == null) entriesByAccount.clear() else entriesByAccount.remove(accountEmail.trim().lowercase())
	}

	fun <T> withAccount(accountEmail: String, action: () -> T): T {
		val previous = activeAccount.get()
		activeAccount.set(accountEmail.trim().lowercase())
		return try {
			action()
		} finally {
			if (previous == null) activeAccount.remove() else activeAccount.set(previous)
		}
	}

	private fun entriesFor(accountEmail: String?): LinkedBlockingDeque<UserActionLogEntry> {
		val key = accountEmail?.trim()?.lowercase() ?: SHARED_ACCOUNT
		return entriesByAccount.computeIfAbsent(key) { LinkedBlockingDeque(UserActionLogBufferLimits.MAX_ENTRIES) }
	}

	private companion object {
		const val SHARED_ACCOUNT = "__shared__"
	}
}
