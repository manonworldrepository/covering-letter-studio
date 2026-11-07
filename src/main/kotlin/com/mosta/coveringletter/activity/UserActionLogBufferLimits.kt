package com.mosta.coveringletter.activity

import java.time.format.DateTimeFormatter

internal object UserActionLogBufferLimits {
	const val MAX_ENTRIES = 200
	const val MAX_ACTION_LENGTH = 200
	val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
}
