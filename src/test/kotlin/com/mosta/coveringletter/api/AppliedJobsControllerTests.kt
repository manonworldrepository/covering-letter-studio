package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.appliedjobs.AddAppliedJobRequest
import com.mosta.coveringletter.appliedjobs.AppliedJobStore
import com.mosta.coveringletter.pdf.AppliedJobsPdfGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Path

class AppliedJobsControllerTests {

    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var store: AppliedJobStore
    private val logs = UserActionLogBuffer()
    private val pdfGenerator = AppliedJobsPdfGenerator()
    private lateinit var controller: AppliedJobsController

    @BeforeEach
    fun setup() {
        store = AppliedJobStore(temporaryDirectory.toString())
        controller = AppliedJobsController(store, logs, pdfGenerator)
    }

    @Test
    fun `add creates a new applied job entry`() {
        val request = AddAppliedJobRequest("Acme Corp", "Software Engineer", "Build things")
        val job = controller.add(request)

        assertEquals("Acme Corp", job.company)
        assertEquals("Software Engineer", job.jobTitle)
        assertEquals("Build things", job.jobDescription)
        assertTrue(job.id.isNotEmpty())
    }

    @Test
    fun `add records user action log entry`() {
        val request = AddAppliedJobRequest("Acme Corp", "Software Engineer", "")
        controller.add(request)

        val entries = logs.latest()
        assertTrue(entries.any { it.action.contains("Added applied job") })
    }

    @Test
    fun `list returns all added jobs`() {
        controller.add(AddAppliedJobRequest("Company A", "Role A", ""))
        controller.add(AddAppliedJobRequest("Company B", "Role B", ""))

        val jobs = controller.list("", "date", "desc", null, null)

        assertEquals(2, jobs.size)
    }

    @Test
    fun `list filters by search term`() {
        controller.add(AddAppliedJobRequest("Acme Corp", "Engineer", ""))
        controller.add(AddAppliedJobRequest("Beta Inc", "Designer", ""))

        val jobs = controller.list("acme", "date", "desc", null, null)

        assertEquals(1, jobs.size)
        assertEquals("Acme Corp", jobs.single().company)
    }

    @Test
    fun `list sorts by company name`() {
        controller.add(AddAppliedJobRequest("Zebra Corp", "Role", ""))
        controller.add(AddAppliedJobRequest("Alpha Inc", "Role", ""))

        val jobs = controller.list("", "company", "asc", null, null)

        assertEquals("Alpha Inc", jobs[0].company)
        assertEquals("Zebra Corp", jobs[1].company)
    }

    @Test
    fun `delete removes a job entry`() {
        val job = controller.add(AddAppliedJobRequest("Acme Corp", "Engineer", ""))

        controller.delete(job.id)

        assertTrue(controller.list("", "date", "desc", null, null).isEmpty())
    }

    @Test
    fun `delete does not throw for non-existent id`() {
        controller.delete("non-existent-id")
        assertTrue(true)
    }

    @Test
    fun `download single job PDF returns valid PDF bytes`() {
        val job = controller.add(AddAppliedJobRequest("Acme Corp", "Engineer", "Description"))
        val response = MockServerHttpResponse()
        val pdf = controller.downloadSingleJobPdf(job.id, response)

        assertTrue(pdf.isNotEmpty())
        assertEquals("application/pdf", response.headers.contentType.toString())
        assertTrue(response.headers.getContentDisposition().toString().contains("attachment"))
    }

    @Test
    fun `download single job PDF throws 404 for non-existent id`() {
        val response = MockServerHttpResponse()

        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.downloadSingleJobPdf("non-existent-id", response)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `download job list PDF returns valid PDF bytes`() {
        controller.add(AddAppliedJobRequest("Acme Corp", "Engineer", ""))
        controller.add(AddAppliedJobRequest("Beta Inc", "Designer", ""))
        val response = MockServerHttpResponse()
        val pdf = controller.downloadJobListPdf("", "date", "desc", null, null, response)

        assertTrue(pdf.isNotEmpty())
        assertEquals("application/pdf", response.headers.contentType.toString())
        assertTrue(response.headers.getContentDisposition().toString().contains("applied-jobs.pdf"))
    }

    @Test
    fun `download job list PDF throws 400 when no jobs`() {
        val response = MockServerHttpResponse()

        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.downloadJobListPdf("", "date", "desc", null, null, response)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun `download job list PDF respects search filter`() {
        controller.add(AddAppliedJobRequest("Acme Corp", "Engineer", ""))
        controller.add(AddAppliedJobRequest("Beta Inc", "Designer", ""))
        val response = MockServerHttpResponse()
        val pdf = controller.downloadJobListPdf("acme", "date", "desc", null, null, response)

        assertTrue(pdf.isNotEmpty())
    }

    @Test
    fun `list with empty search returns all jobs`() {
        controller.add(AddAppliedJobRequest("Acme Corp", "Engineer", ""))
        controller.add(AddAppliedJobRequest("Beta Inc", "Designer", ""))

        val jobs = controller.list("", "date", "desc", null, null)

        assertEquals(2, jobs.size)
    }

    @Test
    fun `add trims whitespace from company and title`() {
        val request = AddAppliedJobRequest("  Acme Corp  ", "  Engineer  ", "  Description  ")
        val job = controller.add(request)

        assertEquals("Acme Corp", job.company)
        assertEquals("Engineer", job.jobTitle)
        assertEquals("Description", job.jobDescription)
    }

    @Test
    fun `add rejects blank company`() {
        val request = AddAppliedJobRequest("", "Engineer", "")

        try {
            controller.add(request)
            assertFalse(true, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Company"))
        }
    }

    @Test
    fun `add rejects blank job title`() {
        val request = AddAppliedJobRequest("Acme Corp", "", "")

        try {
            controller.add(request)
            assertFalse(true, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("title"))
        }
    }
}
