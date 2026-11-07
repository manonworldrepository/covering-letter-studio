package com.mosta.coveringletter.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
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
	fun `fits a long letter on one A4 page by reducing the font size`() {
		val letter = (1..45).joinToString("\n") { "This is paragraph number $it in a longer cover letter." }
		val pdf = generator.create(letter)

		Loader.loadPDF(pdf).use { document ->
			assertEquals(1, document.numberOfPages)
			assertEquals(595.28f, document.getPage(0).mediaBox.width, 1f)
			assertEquals(841.89f, document.getPage(0).mediaBox.height, 1f)
			assertTrue(PDFTextStripper().getText(document).contains("paragraph number 45"))
		}
	}

	@Test
	fun `rejects letters that cannot fit on one A4 page`() {
		val letter = (1..1_000).joinToString("\n") { "This is paragraph number $it in an exceptionally long letter." }

		val exception = assertThrows(IllegalArgumentException::class.java) {
			generator.create(letter)
		}
		assertTrue(exception.message!!.contains("one A4 page"))
	}
}
