package com.mosta.coveringletter

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverLetterPdfGeneratorTests {
	private val generator = CoverLetterPdfGenerator()

	@Test
	fun `creates a readable PDF with Unicode and wrapped text`() {
		val longWord = "A".repeat(300)
		val pdf = generator.create("Dear Hiring Manager,\n\nI am interested in the Málaga role.\n$longWord\n\nSincerely,\nCandidate")

		Loader.loadPDF(pdf).use { document ->
			val text = PDFTextStripper().getText(document)
			assertTrue(text.contains("Dear Hiring Manager"))
			assertTrue(text.contains("Málaga"))
			assertTrue(text.replace(Regex("\\s+"), "").contains(longWord))
		}
	}

	@Test
	fun `splits long letters across pages`() {
		val letter = (1..100).joinToString("\n") { "This is paragraph number $it in a longer cover letter." }
		val pdf = generator.create(letter)

		Loader.loadPDF(pdf).use { document ->
			assertTrue(document.numberOfPages > 1)
			assertTrue(PDFTextStripper().getText(document).contains("paragraph number 100"))
		}
	}
}
