package com.mosta.coveringletter.settings

import java.nio.file.Path
import java.nio.file.Paths

internal object AppSettingsServiceDefaults {
	const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
	val MODEL_ID = Regex("[A-Za-z0-9._-]+")
	val ENV_LINE = Regex("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)\\s*$")
	val envFile: Path = Paths.get(".env").toAbsolutePath().normalize()
}
