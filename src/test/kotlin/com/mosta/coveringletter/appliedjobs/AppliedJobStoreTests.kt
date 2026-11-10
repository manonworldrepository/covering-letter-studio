package com.mosta.coveringletter.appliedjobs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AppliedJobStoreTests {

    @TempDir
    lateinit var temporaryDirectory: Path

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @Test
    fun `adds a job and persists it to a file`() {
        val store = store()
        val job = store.add("Acme Corp", "Software Engineer", "Build great things")

        assertTrue(job.id.isNotEmpty())
        assertEquals("Acme Corp", job.company)
        assertEquals("Software Engineer", job.jobTitle)
        assertEquals("Build great things", job.jobDescription)

        val jobs = store.list()
        assertEquals(1, jobs.size)
        assertEquals(job.id, jobs.single().id)
    }

    @Test
    fun `rejects blank company name`() {
        val store = store()

        try {
            store.add("", "Software Engineer", "Description")
            assertFalse(true, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Company"))
        }
    }

    @Test
    fun `rejects blank job title`() {
        val store = store()

        try {
            store.add("Acme Corp", "", "Description")
            assertFalse(true, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("title"))
        }
    }

    @Test
    fun `trims whitespace from company and job title`() {
        val store = store()
        val job = store.add("  Acme Corp  ", "  Software Engineer  ", "  Description  ")

        assertEquals("Acme Corp", job.company)
        assertEquals("Software Engineer", job.jobTitle)
        assertEquals("Description", job.jobDescription)
    }

    @Test
    fun `returns empty list when no jobs exist`() {
        val store = store()
        val jobs = store.list()

        assertTrue(jobs.isEmpty())
    }

    @Test
    fun `searches by company name case insensitively`() {
        val store = store()
        store.add("Acme Corp", "Engineer", "")
        store.add("Beta Inc", "Designer", "")

        val results = store.list(search = "acme")
        assertEquals(1, results.size)
        assertEquals("Acme Corp", results.single().company)
    }

    @Test
    fun `searches by job title case insensitively`() {
        val store = store()
        store.add("Acme Corp", "Software Engineer", "")
        store.add("Beta Inc", "Data Analyst", "")

        val results = store.list(search = "engineer")
        assertEquals(1, results.size)
        assertEquals("Software Engineer", results.single().jobTitle)
    }

    @Test
    fun `search matches either company or job title`() {
        val store = store()
        store.add("Acme Corp", "Engineer", "")
        store.add("Beta Inc", "Designer", "")

        val results = store.list(search = "beta")
        assertEquals(1, results.size)
        assertEquals("Beta Inc", results.single().company)
    }

    @Test
    fun `sorts by date descending by default`() {
        val store = store()
        val first = store.add("First", "Role A", "")
        Thread.sleep(10)
        val second = store.add("Second", "Role B", "")

        val jobs = store.list()
        assertEquals(2, jobs.size)
        assertEquals(second.id, jobs[0].id)
        assertEquals(first.id, jobs[1].id)
    }

    @Test
    fun `sorts by date ascending when requested`() {
        val store = store()
        val first = store.add("First", "Role A", "")
        Thread.sleep(10)
        val second = store.add("Second", "Role B", "")

        val jobs = store.list(sortOrder = "asc")
        assertEquals(2, jobs.size)
        assertEquals(first.id, jobs[0].id)
        assertEquals(second.id, jobs[1].id)
    }

    @Test
    fun `sorts by company name`() {
        val store = store()
        store.add("Zebra Corp", "Engineer", "")
        store.add("Alpha Inc", "Designer", "")

        val jobs = store.list(sortBy = "company", sortOrder = "asc")
        assertEquals(2, jobs.size)
        assertEquals("Alpha Inc", jobs[0].company)
        assertEquals("Zebra Corp", jobs[1].company)
    }

    @Test
    fun `sorts by job title`() {
        val store = store()
        store.add("Acme", "Zoo Keeper", "")
        store.add("Acme", "Alpha Tester", "")

        val jobs = store.list(sortBy = "title", sortOrder = "asc")
        assertEquals(2, jobs.size)
        assertEquals("Alpha Tester", jobs[0].jobTitle)
        assertEquals("Zoo Keeper", jobs[1].jobTitle)
    }

    @Test
    fun `filters by single date using dateFrom and dateTo`() {
        val store = store()
        val today = LocalDate.now()
        store.add("Today Corp", "Role", "")
        val yesterday = today.minusDays(1)
        writeJob(store, "Yesterday Corp", "Old Role", yesterday)

        val jobs = store.list(dateFrom = today, dateTo = today)
        assertEquals(1, jobs.size)
        assertEquals("Today Corp", jobs.single().company)
    }

    @Test
    fun `filters by dateFrom only`() {
        val store = store()
        val today = LocalDate.now()
        store.add("Today Corp", "Role", "")
        val yesterday = today.minusDays(1)
        writeJob(store, "Yesterday Corp", "Old Role", yesterday)

        val jobs = store.list(dateFrom = today)
        assertEquals(1, jobs.size)
        assertEquals("Today Corp", jobs.single().company)
    }

    @Test
    fun `filters by dateTo only`() {
        val store = store()
        val yesterday = LocalDate.now().minusDays(1)
        store.add("Today Corp", "Role", "")
        writeJob(store, "Yesterday Corp", "Old Role", yesterday)

        val jobs = store.list(dateTo = yesterday)
        assertEquals(1, jobs.size)
        assertEquals("Yesterday Corp", jobs.single().company)
    }

    private fun writeJob(store: AppliedJobStore, company: String, title: String, date: LocalDate) {
        val jobsDir = temporaryDirectory.resolve("applied-jobs")
        java.io.File(jobsDir.toFile(), "${java.util.UUID.randomUUID()}.txt").writeText(
            "id\n$company\n$title\n${date.atStartOfDay().format(dateFormat)}\n"
        )
    }

    @Test
    fun `combines search and date filter`() {
        val store = store()
        store.add("Acme Corp", "Engineer", "")
        store.add("Acme Corp", "Designer", "")

        val jobs = store.list(search = "engineer", dateFrom = LocalDate.now())
        assertEquals(1, jobs.size)
        assertEquals("Engineer", jobs.single().jobTitle)
    }

    @Test
    fun `deletes an existing job`() {
        val store = store()
        val job = store.add("Acme Corp", "Engineer", "")

        val deleted = store.delete(job.id)
        assertTrue(deleted)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `delete returns false for non-existent id`() {
        val store = store()

        val deleted = store.delete("non-existent-id")
        assertFalse(deleted)
    }

    @Test
    fun `survives corrupted file content`() {
        val store = store()
        store.add("Valid Corp", "Valid Role", "Valid description")

        val jobsDir = temporaryDirectory.resolve("applied-jobs")
        java.io.File(jobsDir.toFile(), "corrupted-id.txt").writeText("this is not valid format")

        val jobs = store.list()
        assertEquals(1, jobs.size)
        assertEquals("Valid Corp", jobs.single().company)
    }

    @Test
    fun `persists job description with newlines`() {
        val store = store()
        val job = store.add("Acme Corp", "Engineer", "Line one\nLine two\nLine three")

        val loaded = store.list().single()
        assertEquals("Line one\nLine two\nLine three", loaded.jobDescription)
    }

    @Test
    fun `survives missing data directory on list`() {
        val freshDir = temporaryDirectory.resolve("fresh")
        val store = AppliedJobStore(freshDir.toString())

        val jobs = store.list()
        assertTrue(jobs.isEmpty())
    }

    @Test
    fun `multiple jobs are all listed`() {
        val store = store()
        store.add("Company A", "Role A", "")
        store.add("Company B", "Role B", "")
        store.add("Company C", "Role C", "")

        val jobs = store.list()
        assertEquals(3, jobs.size)
    }

    private fun store() = AppliedJobStore(temporaryDirectory.toString())
}
