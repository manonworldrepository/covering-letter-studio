package com.mosta.coveringletter.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CoverLetterFilenameTests {
	@Test
	fun `creates title cased filename with normalized accents and date`() {
		assertEquals(
			"Alex_Morgan_Acme_Corp_Senior_Product_Designer_2025-03-08.pdf",
			CoverLetterFilename.create("alex mORGAN", "Acme Corp", "senior product designer", LocalDate.of(2025, 3, 8))
		)
		assertEquals(
			"Jose_Nunez_Qa_Engineer_2025-03-08.pdf",
			CoverLetterFilename.create("José Núñez", "", "QA Engineer", LocalDate.of(2025, 3, 8))
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
