package com.mosta.coveringletter.appliedjobs

import java.time.LocalDate

data class AppliedJobsQuery(
	val search: String = "",
	val sortBy: String = "date",
	val sortOrder: String = "desc",
	val dateFrom: LocalDate? = null,
	val dateTo: LocalDate? = null
)
