package com.mosta.coveringletter.generation

import org.slf4j.LoggerFactory

internal object GeminiCoverLetterServiceSupport {
	const val MAX_APPLICANT_NAME_LENGTH = 120
	const val MAX_COMPANY_NAME_LENGTH = 120
	const val MAX_JOB_TITLE_LENGTH = 160
	const val MAX_RETRIES = 2
	val logger = LoggerFactory.getLogger(GeminiCoverLetterService::class.java)
}
