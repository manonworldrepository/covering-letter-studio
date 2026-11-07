package com.mosta.coveringletter.resume

internal object ResumeStoreLimits {
	const val MAX_FILE_BYTES = 10 * 1024 * 1024
	const val MAX_FILENAME_LENGTH = 180
	val UUID_PATTERN = Regex("[0-9a-fA-F-]{36}")
}
