package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.controller.ResumePdfController
import com.mosta.coveringletter.api.request.TailoredResumePdfRequest
import com.mosta.coveringletter.pdf.ResumePdfGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpResponse

class ResumePdfControllerTests {
	private val controller = ResumePdfController(ResumePdfGenerator())

	@Test
	fun `downloads edited tailored resume as a PDF attachment`() {
		val response = MockServerHttpResponse()
		val pdf = controller.downloadPdf(
			TailoredResumePdfRequest(
				resume = "Alex Morgan\n\nEXPERIENCE\nBuilt customer workflows.",
				applicantName = "Alex Benjamin Morgan",
				companyName = "Acme Corp",
				jobTitle = "Forward Deployed Engineer"
			),
			response
		)

		assertTrue(pdf.isNotEmpty())
		assertEquals("application/pdf", response.headers.contentType.toString())
		val disposition = response.headers.getContentDisposition()
		assertEquals("attachment", disposition.type)
		assertTrue(
			disposition.filename?.matches(
				Regex("""Alex_Morgan_Acme_Corp_Forward_Deployed_Engineer_\d{4}-\d{2}-\d{2}\.pdf""")
			) == true
		)
	}
}