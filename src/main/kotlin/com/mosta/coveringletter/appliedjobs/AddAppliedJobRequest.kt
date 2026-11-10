package com.mosta.coveringletter.appliedjobs

data class AddAppliedJobRequest(
	val company: String = "",
	val jobTitle: String = "",
	val jobDescription: String = ""
)
