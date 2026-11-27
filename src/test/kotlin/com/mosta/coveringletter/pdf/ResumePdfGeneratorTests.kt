package com.mosta.coveringletter.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResumePdfGeneratorTests {
	private val generator = ResumePdfGenerator()

	@Test
	fun `creates readable formatted PDF with headings bullets and Unicode`() {
		val pdf = generator.create(
			"""
				Alex Morgan
				Senior Software Engineer

				## PROFESSIONAL SUMMARY
				Built reliable systems for Málaga customers.

				EXPERIENCE
				- Improved deployment time by 40%.
				- Led customer workshops and delivery.

				Education
				BSc Computer Science
			""".trimIndent()
		)

		Loader.loadPDF(pdf).use { document ->
			val text = PDFTextStripper().getText(document)
			assertEquals(1, document.numberOfPages)
			assertTrue(text.contains("Alex Morgan"))
			assertTrue(text.contains("PROFESSIONAL SUMMARY"))
			assertTrue(text.contains("Málaga"))
			assertTrue(text.contains("Improved deployment time by 40%"))
			assertTrue(text.contains("BSc Computer Science"))
			assertTrue(!text.contains("##"))
		}
	}

	@Test
	fun `draws a horizontal divider between experience entries only`() {
		val pdf = generator.create(
			"""
				Alex Morgan

				EXPERIENCE
				Senior Engineer at Acme
				- Improved deployment time by 40%.

				Staff Engineer at Globex
				- Led customer workshops.

				EDUCATION
				BSc Computer Science
			""".trimIndent()
		)

		Loader.loadPDF(pdf).use { document ->
			val parser = PDFStreamParser(document.getPage(0))
			val horizontalRuleCount = parser.parse().filterIsInstance<Operator>().count { it.name == "S" }

			assertEquals(3, horizontalRuleCount)
		}
	}

	@Test
	fun `creates additional pages rather than dropping longer resume content`() {
		val resume = buildString {
			appendLine("Alex Morgan")
			appendLine()
			appendLine("EXPERIENCE")
			(1..160).forEach { appendLine("- Delivered measurable project outcome number $it for customers.") }
		}

		val pdf = generator.create(resume)

		Loader.loadPDF(pdf).use { document ->
			assertTrue(document.numberOfPages > 1)
			val text = PDFTextStripper().getText(document)
			assertTrue(text.contains("outcome number 1"))
			assertTrue(text.contains("outcome number 160"))
		}
	}
}