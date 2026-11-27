package com.mosta.coveringletter.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TailoredResumeFilenameTests {
	@Test
	fun `creates filename with resume suffix`() {
		assertEquals(
			"Alex_Morgan_Acme_Corp_Forward_Deployed_Engineer_Resume.pdf",
			TailoredResumeFilename.create("Alex Benjamin Morgan", "Acme Corp", "Forward Deployed Engineer")
		)
	}

	@Test
	fun `uses only the first two words when job title exceeds 25 characters`() {
		assertEquals(
			"Alex_Morgan_Acme_Corp_Senior_Software_Resume.pdf",
			TailoredResumeFilename.create("Alex Morgan", "Acme Corp", "Senior Software Engineer - Platform")
		)
	}

	@Test
	fun `keeps the full job title when it is exactly 25 characters`() {
		assertEquals(
			"Alex_Morgan_Acme_Corp_Senior_Product_Designer_X_Resume.pdf",
			TailoredResumeFilename.create("Alex Morgan", "Acme Corp", "Senior Product Designer X")
		)
	}
}