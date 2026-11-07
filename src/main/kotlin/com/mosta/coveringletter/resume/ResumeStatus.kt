package com.mosta.coveringletter.resume

data class ResumeStatus(
	val configured: Boolean,
	val filename: String?,
	val resumes: List<ResumeOption> = emptyList()
)
