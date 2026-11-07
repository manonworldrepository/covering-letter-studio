package com.mosta.coveringletter.settings

data class AppSettingsStatus(
	val configured: Boolean,
	val apiKeyConfigured: Boolean,
	val modelConfigured: Boolean,
	val model: String,
	val resumeConfigured: Boolean,
	val resumeFilename: String?
)
