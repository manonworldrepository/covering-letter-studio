package com.mosta.coveringletter.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer

@Component
class ResumePdfGenerator {
	fun create(resume: String): ByteArray {
		val blocks = parseBlocks(resume)
		require(blocks.any { it.text.isNotBlank() }) { "Resume content is required." }

		PDDocument().use { document ->
			val fonts = loadFonts(document)
			val pageSize = PDRectangle.A4
			val margin = 52f
			val bottomMargin = 48f
			val maxWidth = pageSize.width - margin * 2
			document.addPage(PDPage(pageSize))
			var page = document.getPage(0)
			var stream: PDPageContentStream? = PDPageContentStream(document, page)
			var y = pageSize.height - margin

			fun startNewPage() {
				stream?.close()
				page = PDPage(pageSize)
				document.addPage(page)
				stream = PDPageContentStream(document, page)
				y = pageSize.height - margin
			}

			fun ensureSpace(height: Float) {
				if (y - height < bottomMargin) startNewPage()
			}

			fun drawRule() {
				val content = stream ?: error("PDF page stream is not available.")
				content.setStrokingColor(RULE_COLOR)
				content.setLineWidth(0.7f)
				content.moveTo(margin, y + 2f)
				content.lineTo(pageSize.width - margin, y + 2f)
				content.stroke()
			}

			try {
				blocks.forEach { block ->
					if (block.kind == BlockKind.DIVIDER) {
						ensureSpace(JOB_DIVIDER_HEIGHT)
						drawRule()
						y -= JOB_DIVIDER_HEIGHT
						return@forEach
					}

					if (block.kind == BlockKind.SPACER) {
						ensureSpace(5f)
						y -= 5f
						return@forEach
					}

					val fontSize = block.fontSize
					val lineHeight = fontSize * if (block.kind == BlockKind.NAME) 1.35f else 1.42f
					val font = if (block.kind == BlockKind.NAME || block.kind == BlockKind.SECTION) fonts.bold else fonts.regular
					val bulletIndent = if (block.kind == BlockKind.BULLET) 17f else 0f
					val wrappedLines = wrapText(font, block.text, maxWidth - bulletIndent, fontSize)
					val requiredHeight = block.spacingBefore + lineHeight + block.spacingAfter +
						if (block.kind == BlockKind.SECTION) lineHeight else 0f
					ensureSpace(requiredHeight)
					y -= block.spacingBefore

					wrappedLines.forEachIndexed { index, line ->
						ensureSpace(lineHeight)
						val content = stream ?: error("PDF page stream is not available.")
						val color = if (block.kind == BlockKind.NAME || block.kind == BlockKind.SECTION) ACCENT_COLOR else BODY_COLOR

						if (block.kind == BlockKind.BULLET && index == 0) {
							val marker = block.marker
							if (marker == null) {
								content.setNonStrokingColor(ACCENT_COLOR)
								content.addRect(margin + 3f, y - 7f, 3f, 3f)
								content.fill()
								content.setNonStrokingColor(color)
							} else {
								content.beginText()
								content.setFont(fonts.regular, fontSize)
								content.setNonStrokingColor(color)
								content.newLineAtOffset(margin, y)
								content.showText(toSupportedText(fonts.regular, marker))
								content.endText()
							}
						}

						content.beginText()
						content.setFont(font, fontSize)
						content.setNonStrokingColor(color)
						content.newLineAtOffset(margin + bulletIndent, y)
						content.showText(toSupportedText(font, line))
						content.endText()
						y -= lineHeight
					}

					if (block.kind == BlockKind.SECTION) {
						drawRule()
					}
					y -= block.spacingAfter
				}
			} finally {
				stream?.close()
			}

			document.pages.forEachIndexed { index, pdfPage ->
				PDPageContentStream(
					document,
					pdfPage,
					PDPageContentStream.AppendMode.APPEND,
					true,
					true
				).use { footer ->
					val pageNumber = "${index + 1}"
					val footerText = "Page $pageNumber"
					footer.beginText()
					footer.setFont(fonts.regular, 8f)
					footer.setNonStrokingColor(FOOTER_COLOR)
					footer.newLineAtOffset(pageSize.width - margin - textWidth(fonts.regular, footerText, 8f), 25f)
					footer.showText(toSupportedText(fonts.regular, footerText))
					footer.endText()
				}
			}

			return ByteArrayOutputStream().use { output ->
				document.save(output)
				output.toByteArray()
			}
		}
	}

	private fun parseBlocks(resume: String): List<TextBlock> {
		val blocks = mutableListOf<TextBlock>()
		var firstContent = true
		resume.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { sourceLine ->
			val trimmed = sourceLine.trim()
			if (trimmed.isEmpty()) {
				if (blocks.isNotEmpty() && blocks.last().kind != BlockKind.SPACER) {
					blocks.add(TextBlock("", BlockKind.SPACER))
				}
				return@forEach
			}

			val markdownHeading = MARKDOWN_HEADING.matchEntire(trimmed)?.groupValues?.get(1)
			val cleaned = stripMarkdown(markdownHeading ?: trimmed)
			when {
				firstContent -> {
					blocks.add(TextBlock(cleaned, BlockKind.NAME))
					firstContent = false
				}
				markdownHeading != null || isSectionHeading(cleaned) ->
					blocks.add(TextBlock(cleaned, BlockKind.SECTION))
				else -> {
					val bullet = BULLET.matchEntire(cleaned)
					if (bullet != null) {
						val marker = bullet.groupValues[1].takeIf { it.firstOrNull()?.isDigit() == true }
						blocks.add(TextBlock(stripMarkdown(bullet.groupValues[2]), BlockKind.BULLET, marker))
					} else {
						blocks.add(TextBlock(cleaned, BlockKind.BODY))
					}
				}
			}
		}
		return insertExperienceDividers(blocks)
	}

	private fun insertExperienceDividers(blocks: List<TextBlock>): List<TextBlock> {
		val nextContentKinds = MutableList<BlockKind?>(blocks.size) { null }
		var nextContentKind: BlockKind? = null
		for (index in blocks.indices.reversed()) {
			nextContentKinds[index] = nextContentKind
			if (blocks[index].kind != BlockKind.SPACER) nextContentKind = blocks[index].kind
		}

		var inExperienceSection = false
		var hasExperienceContent = false
		return blocks.mapIndexed { index, block ->
			when (block.kind) {
				BlockKind.SECTION -> {
					inExperienceSection = block.text.trim().trimEnd(':').lowercase() in EXPERIENCE_HEADINGS
					hasExperienceContent = false
					block
				}
				BlockKind.SPACER -> {
					if (inExperienceSection && hasExperienceContent && nextContentKinds[index] != null &&
						nextContentKinds[index] != BlockKind.SECTION
					) {
						block.copy(kind = BlockKind.DIVIDER)
					} else {
						block
					}
				}
				else -> {
					if (inExperienceSection) hasExperienceContent = true
					block
				}
			}
		}
	}

	private fun isSectionHeading(text: String): Boolean {
		val normalized = text.trim().trimEnd(':')
		if (normalized.lowercase() in KNOWN_HEADINGS) return true
		return normalized.length in 2..56 &&
			normalized.any(Char::isLetter) &&
			normalized == normalized.uppercase() &&
			!normalized.any { it in ".!?" }
	}

	private fun stripMarkdown(text: String): String = text
		.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
		.replace(Regex("__(.+?)__"), "$1")
		.replace('`', ' ')
		.trim()

	private fun wrapText(font: PDFont, text: String, maxWidth: Float, fontSize: Float): List<String> {
		val lines = mutableListOf<String>()
		var currentLine = ""
		text.trim().split(Regex("\\s+")).forEach { word ->
			val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
			if (textWidth(font, candidate, fontSize) <= maxWidth) {
				currentLine = candidate
			} else {
				if (currentLine.isNotEmpty()) lines.add(currentLine)
				currentLine = ""
				word.forEach { character ->
					val extended = currentLine + character
					if (textWidth(font, extended, fontSize) > maxWidth && currentLine.isNotEmpty()) {
						lines.add(currentLine)
						currentLine = character.toString()
					} else {
						currentLine = extended
					}
				}
			}
		}
		if (currentLine.isNotEmpty()) lines.add(currentLine)
		return lines
	}

	private fun loadFonts(document: PDDocument): FontPair {
		val fontFiles = listOf(
			"C:\\Windows\\Fonts\\arial.ttf" to "C:\\Windows\\Fonts\\arialbd.ttf",
			"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf" to "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
			"/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf" to "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
			"/System/Library/Fonts/Supplemental/Arial.ttf" to "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
		)
		val fontPair = fontFiles.firstOrNull { (regular, bold) -> File(regular).isFile && File(bold).isFile }
		return if (fontPair != null) {
			FontPair(PDType0Font.load(document, File(fontPair.first)), PDType0Font.load(document, File(fontPair.second)))
		} else {
			FontPair(
				PDType1Font(Standard14Fonts.FontName.HELVETICA),
				PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
			)
		}
	}

	private fun toSupportedText(font: PDFont, text: String): String {
		var candidate = text
		if (font is PDType1Font) {
			candidate = Normalizer.normalize(text, Normalizer.Form.NFKD)
				.replace(Regex("\\p{M}+"), "")
				.replace("’", "'")
				.replace("‘", "'")
				.replace("“", "\"")
				.replace("”", "\"")
				.replace("–", "-")
				.replace("—", "-")
		}
		return buildString {
			candidate.forEach { character ->
				val value = character.toString()
				append(if (runCatching { font.encode(value) }.isSuccess) value else "?")
			}
		}
	}

	private fun textWidth(font: PDFont, text: String, fontSize: Float): Float =
		font.getStringWidth(toSupportedText(font, text)) / 1000 * fontSize

	private data class FontPair(val regular: PDFont, val bold: PDFont)
	private data class TextBlock(
		val text: String,
		val kind: BlockKind,
		val marker: String? = null,
		val fontSize: Float = when (kind) {
			BlockKind.NAME -> 20f
			BlockKind.SECTION -> 11.5f
			else -> 10.5f
		},
		val spacingBefore: Float = if (kind == BlockKind.SECTION) 7f else 0f,
		val spacingAfter: Float = when (kind) {
			BlockKind.NAME -> 7f
			BlockKind.SECTION -> 3f
			BlockKind.BODY -> 3f
			BlockKind.BULLET -> 2f
			BlockKind.SPACER -> 0f
			BlockKind.DIVIDER -> 0f
		}
	)

	private enum class BlockKind { NAME, SECTION, BODY, BULLET, SPACER, DIVIDER }

	private companion object {
		const val JOB_DIVIDER_HEIGHT = 12f
		val ACCENT_COLOR = Color(31, 78, 91)
		val BODY_COLOR = Color(38, 45, 54)
		val RULE_COLOR = Color(196, 213, 216)
		val FOOTER_COLOR = Color(115, 124, 132)
		val MARKDOWN_HEADING = Regex("^#{1,6}\\s+(.+)$")
		val BULLET = Regex("^(?:([0-9]+[.)])\\s+|[•●▪*–-]\\s+)(.+)$")
		val EXPERIENCE_HEADINGS = setOf(
			"experience", "work experience", "professional experience", "employment history", "selected experience",
			"volunteer experience"
		)
		val KNOWN_HEADINGS = setOf(
			"summary", "professional summary", "profile", "career summary", "skills", "technical skills",
			"key skills", "core competencies", "experience", "work experience", "professional experience",
			"employment history", "selected experience", "education", "projects", "selected projects",
			"certifications", "certificates", "achievements", "selected achievements", "publications",
			"volunteer experience", "languages", "training"
		)
	}
}