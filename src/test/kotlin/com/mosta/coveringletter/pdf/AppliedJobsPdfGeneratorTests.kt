package com.mosta.coveringletter.pdf

import com.mosta.coveringletter.appliedjobs.AppliedJob
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AppliedJobsPdfGeneratorTests {

    private val generator = AppliedJobsPdfGenerator()

    private fun sampleJob(
        id: String = "test-id",
        company: String = "Acme Corp",
        jobTitle: String = "Software Engineer",
        appliedDate: LocalDateTime = LocalDateTime.of(2026, 11, 9, 10, 0),
        jobDescription: String = "Build great software"
    ) = AppliedJob(id, company, jobTitle, appliedDate, jobDescription)

    @Test
    fun `creates a readable single job PDF with all fields`() {
        val job = sampleJob()
        val pdf = generator.createSingleJob(job)

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Applied Job"))
            assertTrue(text.contains("Job Title: Software Engineer"))
            assertTrue(text.contains("Company: Acme Corp"))
            assertTrue(text.contains("Applied Date: 09 Nov 2026"))
            assertTrue(text.contains("Build great software"))
        }
    }

    @Test
    fun `single job PDF omits description section when blank`() {
        val job = sampleJob(jobDescription = "")
        val pdf = generator.createSingleJob(job)

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Job Title: Software Engineer"))
            assertTrue(text.contains("Company: Acme Corp"))
            assertTrue(text.contains("Applied Date: 09 Nov 2026"))
            assertTrue(text.contains("Job Description") == false)
        }
    }

    @Test
    fun `single job PDF handles Unicode characters`() {
        val job = sampleJob(
            company = "Málaga Tech",
            jobTitle = "Développeur",
            jobDescription = "Café résumé naïve"
        )
        val pdf = generator.createSingleJob(job)

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Málaga") || text.contains("Malaga"))
            assertTrue(text.contains("Développeur") || text.contains("Developpeur"))
        }
    }

    @Test
    fun `creates a job list PDF with header and all jobs`() {
        val jobs = listOf(
            sampleJob(id = "1", company = "Alpha Inc", jobTitle = "Designer"),
            sampleJob(id = "2", company = "Beta Corp", jobTitle = "Engineer")
        )
        val pdf = generator.createJobList(jobs)

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Applied Jobs (2)"))
            assertTrue(text.contains("Job Title"))
            assertTrue(text.contains("Company"))
            assertTrue(text.contains("Date Applied"))
            assertTrue(text.contains("Alpha Inc"))
            assertTrue(text.contains("Beta Corp"))
            assertTrue(text.contains("Designer"))
            assertTrue(text.contains("Engineer"))
        }
    }

    @Test
    fun `job list PDF creates multiple pages for many jobs`() {
        val jobs = (1..100).map { i ->
            sampleJob(id = "$i", company = "Company $i", jobTitle = "Role $i")
        }
        val pdf = generator.createJobList(jobs)

        Loader.loadPDF(pdf).use { document ->
            assertTrue(document.numberOfPages > 1, "Should create multiple pages for 100 jobs")
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Company 1"))
            assertTrue(text.contains("Company 100"))
        }
    }

    @Test
    fun `job list PDF handles empty list`() {
        val pdf = generator.createJobList(emptyList())

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Applied Jobs (0)"))
            assertEquals(1, document.numberOfPages)
        }
    }

    @Test
    fun `single job PDF is valid A4 page`() {
        val job = sampleJob()
        val pdf = generator.createSingleJob(job)

        Loader.loadPDF(pdf).use { document ->
            assertEquals(1, document.numberOfPages)
            assertEquals(595.28f, document.getPage(0).mediaBox.width, 1f)
            assertEquals(841.89f, document.getPage(0).mediaBox.height, 1f)
        }
    }

    @Test
    fun `job list PDF is valid A4 page`() {
        val jobs = listOf(sampleJob())
        val pdf = generator.createJobList(jobs)

        Loader.loadPDF(pdf).use { document ->
            assertEquals(1, document.numberOfPages)
            assertEquals(595.28f, document.getPage(0).mediaBox.width, 1f)
            assertEquals(841.89f, document.getPage(0).mediaBox.height, 1f)
        }
    }

    @Test
    fun `single job PDF handles long job description with wrapping`() {
        val job = sampleJob(jobDescription = "A".repeat(500))
        val pdf = generator.createSingleJob(job)

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            val strippedText = text.replace(Regex("\\s+"), "")
            assertTrue(strippedText.contains("A".repeat(500)))
        }
    }

    @Test
    fun `job list PDF handles long company and title names`() {
        val jobs = listOf(
            sampleJob(
                company = "A".repeat(80),
                jobTitle = "B".repeat(80)
            )
        )
        val pdf = generator.createJobList(jobs)

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("A".repeat(80)))
            assertTrue(text.contains("B".repeat(80)))
        }
    }

    @Test
    fun `single job PDF creates multiple pages when job description exceeds single page and extracts complete text`() {
        val longDescription = buildString {
            for (i in 1..80) {
                appendLine("Section $i: Detailed requirement description text for paragraph $i with some additional words.")
            }
        }
        val job = sampleJob(jobDescription = longDescription)
        val pdf = generator.createSingleJob(job)

        Loader.loadPDF(pdf).use { document ->
            assertTrue(document.numberOfPages > 1, "Should create multiple pages when description exceeds single page")
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Section 1:"))
            assertTrue(text.contains("Section 40:"))
            assertTrue(text.contains("Section 80:"))
            for (pageIndex in 0 until document.numberOfPages) {
                val page = document.getPage(pageIndex)
                assertEquals(595.28f, page.mediaBox.width, 1f)
                assertEquals(841.89f, page.mediaBox.height, 1f)
            }
        }
    }

    @Test
    fun `single job PDF preserves paragraphs and line breaks in job description`() {
        val description = "First paragraph here.\n\nSecond paragraph here.\n\nThird paragraph here."
        val job = sampleJob(jobDescription = description)
        val pdf = generator.createSingleJob(job)

        Loader.loadPDF(pdf).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("First paragraph here."))
            assertTrue(text.contains("Second paragraph here."))
            assertTrue(text.contains("Third paragraph here."))
        }
    }
}
