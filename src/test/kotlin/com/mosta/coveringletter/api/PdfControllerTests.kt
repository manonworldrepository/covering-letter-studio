package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.request.CoverLetterRequest
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import org.springframework.web.server.ResponseStatusException

class PdfControllerTests {

    private val logs = UserActionLogBuffer()
    private val pdfGenerator = CoverLetterPdfGenerator()
    private val controller = PdfController(logs, pdfGenerator)

    @Test
    fun `downloads a valid PDF with correct headers`() {
        val request = CoverLetterRequest(
            letter = "Dear Hiring Manager, I am interested.",
            personName = "Alex Morgan",
            jobTitle = "Designer"
        )
        val response = MockServerHttpResponse()
        val pdf = controller.downloadPdf(request, response)

        assertTrue(pdf.isNotEmpty())
        assertEquals("application/pdf", response.headers.contentType.toString())
        assertTrue(response.headers.getContentDisposition().toString().contains("attachment"))
    }

    @Test
    fun `records user action log on PDF download`() {
        val request = CoverLetterRequest(
            letter = "Dear Hiring Manager, I am interested.",
            personName = "Alex Morgan",
            jobTitle = "Designer"
        )
        val response = MockServerHttpResponse()
        controller.downloadPdf(request, response)

        val entries = logs.latest()
        assertTrue(entries.any { it.action.contains("Downloaded a cover-letter PDF") })
    }

    @Test
    fun `rejects request with blank person name`() {
        val request = CoverLetterRequest(
            letter = "Dear Hiring Manager, I am interested.",
            personName = "",
            jobTitle = "Designer"
        )
        val response = MockServerHttpResponse()

        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.downloadPdf(request, response)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertTrue(exception.message.contains("Person name"))
    }

    @Test
    fun `rejects request with blank job title`() {
        val request = CoverLetterRequest(
            letter = "Dear Hiring Manager, I am interested.",
            personName = "Alex Morgan",
            jobTitle = ""
        )
        val response = MockServerHttpResponse()

        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.downloadPdf(request, response)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertTrue(exception.message.contains("job title"))
    }

    @Test
    fun `rejects overly long letter that cannot fit on one page`() {
        val request = CoverLetterRequest(
            letter = (1..1_000).joinToString("\n") { "This is paragraph number $it in an exceptionally long letter." },
            personName = "Alex Morgan",
            jobTitle = "Designer"
        )
        val response = MockServerHttpResponse()

        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.downloadPdf(request, response)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertTrue(exception.message.contains("one A4 page"))
    }

    @Test
    fun `PDF filename includes company name when provided`() {
        val request = CoverLetterRequest(
            letter = "Dear Hiring Manager, I am interested.",
            personName = "Alex Morgan",
            companyName = "Acme Corp",
            jobTitle = "Designer"
        )
        val response = MockServerHttpResponse()
        controller.downloadPdf(request, response)

        assertTrue(response.headers.getContentDisposition().toString().contains("Acme"))
    }

    @Test
    fun `PDF filename handles Unicode characters`() {
        val request = CoverLetterRequest(
            letter = "Dear Hiring Manager, I am interested.",
            personName = "Málaga",
            companyName = "Café Corp",
            jobTitle = "Développeur"
        )
        val response = MockServerHttpResponse()
        controller.downloadPdf(request, response)

        val disposition = response.headers.getContentDisposition().toString()
        assertTrue(disposition.contains("attachment"))
    }
}
