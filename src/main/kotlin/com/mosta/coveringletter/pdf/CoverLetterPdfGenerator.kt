package com.mosta.coveringletter.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer

@Component
class CoverLetterPdfGenerator {
	fun create(letter: String): ByteArray {
		PDDocument().use { document ->
			val font = loadFont(document)
			val pageSize = PDRectangle.A4
			val margin = 54f
			val maxWidth = pageSize.width - margin * 2
			val layout = (11 downTo 8).asSequence()
				.map { it.toFloat() }
				.mapNotNull { fontSize ->
					val lineHeight = fontSize * 1.45f
					val lines = letter.lineSequence().flatMap { paragraph ->
						if (paragraph.isBlank()) {
							sequenceOf(PdfLine("", lineHeight * 0.6f))
						} else {
							wrapParagraph(font, paragraph, maxWidth, fontSize)
								.map { PdfLine(it, lineHeight) }
								.asSequence() + sequenceOf(PdfLine("", lineHeight * 0.4f))
						}
					}.toList()
					if (lines.sumOf { it.spacingAfter.toDouble() } <= pageSize.height - margin * 2) {
						fontSize to lines
					} else {
						null
					}
				}
				.firstOrNull()
				?: throw IllegalArgumentException("This letter is too long to fit on one A4 page. Shorten it and try again.")

			val (fontSize, lines) = layout
			val lineHeight = fontSize * 1.45f
			document.addPage(PDPage(pageSize))
			PDPageContentStream(document, document.getPage(0)).use { stream ->
				var y = pageSize.height - margin
				lines.forEach { line ->
					if (line.text.isNotEmpty()) {
						stream.beginText()
						stream.setFont(font, fontSize)
						stream.newLineAtOffset(margin, y)
						stream.showText(toSupportedText(font, line.text))
						stream.endText()
					}
					y -= line.spacingAfter
				}
			}
			return ByteArrayOutputStream().use { output ->
				document.save(output)
				output.toByteArray()
			}
		}
	}

	private fun wrapParagraph(font: PDFont, paragraph: String, maxWidth: Float, fontSize: Float): List<String> {
		val lines = mutableListOf<String>()
		var currentLine = ""
		paragraph.trim().split(Regex("\\s+")).forEach { word ->
			val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
			if (width(font, candidate, fontSize) <= maxWidth) {
				currentLine = candidate
			} else {
				if (currentLine.isNotEmpty()) lines.add(currentLine)
				currentLine = ""
				word.forEach { character ->
					val extended = currentLine + character
					if (width(font, extended, fontSize) > maxWidth && currentLine.isNotEmpty()) {
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

	private fun loadFont(document: PDDocument): PDFont {
		val fontFiles = listOf(
			"C:\\Windows\\Fonts\\arial.ttf",
			"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
			"/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
			"/System/Library/Fonts/Supplemental/Arial.ttf"
		)
		val fontFile = fontFiles.map(::File).firstOrNull(File::isFile)
		return if (fontFile != null) {
			PDType0Font.load(document, fontFile)
		} else {
			PDType1Font(Standard14Fonts.FontName.HELVETICA)
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
				.replace("•", "-")
		}
		return buildString {
			candidate.forEach { character ->
				val value = character.toString()
				append(if (runCatching { font.encode(value) }.isSuccess) value else "?")
			}
		}
	}

	private fun width(font: PDFont, text: String, fontSize: Float): Float =
		font.getStringWidth(toSupportedText(font, text)) / 1000 * fontSize

	private data class PdfLine(val text: String, val spacingAfter: Float)
}
