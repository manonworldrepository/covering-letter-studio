package com.mosta.coveringletter.documents

import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class DocumentTextExtractorTests {

    private val extractor = DocumentTextExtractor()
    private val pdfGenerator = CoverLetterPdfGenerator()

    @Test
    fun `extracts text from a valid PDF`() {
        val pdf = pdfGenerator.create("Hello from PDF document.")
        val text = extractor.extractText("resume.pdf", pdf)

        assertTrue(text.contains("Hello from PDF document"))
    }

    @Test
    fun `rejects unsupported file types`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            extractor.extractText("image.png", byteArrayOf(1, 2, 3))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertTrue(exception.message.contains("Unsupported file type"))
    }

    @Test
    fun `rejects oversized files`() {
        val largeBytes = ByteArray(DocumentTextExtractorLimits.MAX_FILE_BYTES + 1)

        val exception = assertThrows(ResponseStatusException::class.java) {
            extractor.extractText("huge.pdf", largeBytes)
        }

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.statusCode)
        assertTrue(exception.message.contains("10 MB"))
    }

    @Test
    fun `accepts files at the exact size limit`() {
        val exactBytes = ByteArray(DocumentTextExtractorLimits.MAX_FILE_BYTES)
        // This will fail at PDF parsing, but should not fail at size check
        try {
            extractor.extractText("exact.pdf", exactBytes)
            assertTrue(false, "Should have thrown for invalid PDF content")
        } catch (e: ResponseStatusException) {
            assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
            assertTrue(e.message.contains("Could not read"))
        }
    }

    @Test
    fun `rejects corrupted PDF content`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            extractor.extractText("corrupted.pdf", "not a pdf".toByteArray())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertTrue(exception.message.contains("Could not read"))
    }

    @Test
    fun `rejects empty bytes for PDF`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            extractor.extractText("empty.pdf", ByteArray(0))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `extracts text with Unicode from PDF`() {
        val pdf = pdfGenerator.create("Málaga café résumé.")
        val text = extractor.extractText("unicode.pdf", pdf)

        assertTrue(text.contains("Málaga") || text.contains("Malaga"))
    }

    @Test
    fun `rejects corrupted DOCX content`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            extractor.extractText("corrupted.docx", "not a docx".toByteArray())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertTrue(exception.message.contains("Could not read"))
    }

    @Test
    fun `rejects unsupported extension case insensitively`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            extractor.extractText("file.TXT", "hello".toByteArray())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertTrue(exception.message.contains("Unsupported file type"))
    }

    @Test
    fun `handles filename with multiple dots`() {
        val pdf = pdfGenerator.create("Multi dot filename test.")
        val text = extractor.extractText("my.resume.2026.pdf", pdf)

        assertTrue(text.contains("Multi dot filename test"))
    }
}
