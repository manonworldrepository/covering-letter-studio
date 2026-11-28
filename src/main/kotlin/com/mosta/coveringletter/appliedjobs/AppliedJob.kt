package com.mosta.coveringletter.appliedjobs

import java.time.LocalDateTime

enum class AppliedJobStatus {
	PROGRESSING,
	REJECTED
}

data class AppliedJob(
	val id: String,
	val company: String,
	val jobTitle: String,
	val appliedDate: LocalDateTime,
	val jobDescription: String,
	val status: AppliedJobStatus = AppliedJobStatus.PROGRESSING,
	val ownerEmail: String? = null,
	val shared: Boolean = false
)
