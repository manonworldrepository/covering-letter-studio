package com.mosta.coveringletter.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CoverLetterFilenameTests {
	@Test
	fun `creates title cased filename with normalized accents and document type`() {
		assertEquals(
			"Alex_Morgan_Acme_Corp_Senior_Product_Designer_Cover_Letter.pdf",
			CoverLetterFilename.create("alex mORGAN", "Acme Corp", "senior product designer")
		)
		assertEquals(
			"Jose_Nunez_Qa_Engineer_Cover_Letter.pdf",
			CoverLetterFilename.create("José Núñez", "", "QA Engineer")
		)
		assertEquals(
			"Alex_Morgan_Acme_Corp_Senior_Engineer_Cover_Letter.pdf",
			CoverLetterFilename.create("Alex Benjamin Morgan", "Acme Corp", "Senior Engineer")
		)
	}

	@Test
	fun `uses only the first two words when job title exceeds 25 characters`() {
		assertEquals(
			"Alex_Morgan_Acme_Corp_Senior_Software_Cover_Letter.pdf",
			CoverLetterFilename.create("Alex Morgan", "Acme Corp", "Senior Software Engineer - Platform")
		)
	}

	@Test
	fun `keeps the full job title when it is exactly 25 characters`() {
		assertEquals(
			"Alex_Morgan_Acme_Corp_Senior_Product_Designer_X_Cover_Letter.pdf",
			CoverLetterFilename.create("Alex Morgan", "Acme Corp", "Senior Product Designer X")
		)
	}

	@Test
	fun `rejects names and titles without filename characters`() {
		assertThrows(IllegalArgumentException::class.java) {
			CoverLetterFilename.create("...", "", "Software Engineer")
		}
		assertThrows(IllegalArgumentException::class.java) {
			CoverLetterFilename.create("Alex Morgan", "", "---")
		}
	}
}
