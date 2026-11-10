package com.mosta.coveringletter.appliedjobs

import java.time.LocalDateTime

data class AppliedJob(
	val id: String,
	val company: String,
	val jobTitle: String,
	val appliedDate: LocalDateTime,
	val jobDescription: String
)
