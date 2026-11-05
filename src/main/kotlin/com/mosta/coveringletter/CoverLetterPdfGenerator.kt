package com.mosta.coveringletter

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
			val fontSize = 11f
			val lineHeight = 16f
			val maxWidth = pageSize.width - margin * 2
			var page = PDPage(pageSize)
			document.addPage(page)
			var stream = PDPageContentStream(document, page)
			var y = pageSize.height - margin

			fun newPage() {
				stream.close()
				page = PDPage(pageSize)
				document.addPage(page)
				stream = PDPageContentStream(document, page)
				y = pageSize.height - margin
			}

			fun writeLine(line: String) {
				if (y < margin + lineHeight) newPage()
				stream.beginText()
				stream.setFont(font, fontSize)
				stream.newLineAtOffset(margin, y)
				stream.showText(toSupportedText(font, line))
				stream.endText()
				y -= lineHeight
			}

			letter.lineSequence().forEach { paragraph ->
				if (paragraph.isBlank()) {
					y -= lineHeight * 0.6f
					return@forEach
				}
				var currentLine = ""
				paragraph.trim().split(Regex("\\s+")).forEach { word ->
					val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
					if (width(font, candidate, fontSize) <= maxWidth) {
						currentLine = candidate
					} else {
						if (currentLine.isNotEmpty()) writeLine(currentLine)
						currentLine = ""
						word.forEach { character ->
							val extended = currentLine + character
							if (width(font, extended, fontSize) > maxWidth && currentLine.isNotEmpty()) {
								writeLine(currentLine)
								currentLine = character.toString()
							} else {
								currentLine = extended
							}
						}
					}
				}
				if (currentLine.isNotEmpty()) writeLine(currentLine)
				y -= lineHeight * 0.4f
			}

			stream.close()
			return ByteArrayOutputStream().use { output ->
				document.save(output)
				output.toByteArray()
			}
		}
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
}
