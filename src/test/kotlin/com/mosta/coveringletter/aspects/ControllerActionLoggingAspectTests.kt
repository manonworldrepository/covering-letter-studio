package com.mosta.coveringletter.aspects

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.response.DocumentViewResponse
import com.mosta.coveringletter.appliedjobs.AppliedJob
import com.mosta.coveringletter.appliedjobs.AppliedJobStatus
import com.mosta.coveringletter.appliedjobs.AppliedJobStore
import com.mosta.coveringletter.resume.ResumeOption
import com.mosta.coveringletter.resume.ResumeStatus
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.documents.DocumentTextExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime

class ControllerActionLoggingAspectTests {

	@TempDir
	lateinit var temporaryDirectory: Path

	private val logs = UserActionLogBuffer()
	private lateinit var appliedJobStore: AppliedJobStore
	private lateinit var resumeStore: ResumeStore
	private lateinit var aspect: ControllerActionLoggingAspect

	@BeforeEach
	fun setup() {
		appliedJobStore = AppliedJobStore(temporaryDirectory.resolve("applied-jobs").toString())
		resumeStore = ResumeStore(DocumentTextExtractor(), temporaryDirectory.resolve("resumes").toString())
		aspect = ControllerActionLoggingAspect(logs, appliedJobStore, resumeStore)
		logs.latest() // clear
	}

	// --- AppliedJobsController aspects ---

	@Test
	fun `logAddAppliedJob records added job message`() {
		val job = AppliedJob("id-1", "Acme Corp", "Software Engineer", LocalDateTime.now(), "", AppliedJobStatus.PROGRESSING)
		aspect.logAddAppliedJob(job)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Added applied job: Software Engineer at Acme Corp", entries.single().action)
	}

	@Test
	fun `logAddAppliedJob ignores null result`() {
		aspect.logAddAppliedJob(null)
		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logAddAppliedJob ignores wrong type result`() {
		aspect.logAddAppliedJob("not a job")
		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logDeleteAppliedJob records removed job message`() {
		aspect.logDeleteAppliedJob()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Removed applied job entry", entries.single().action)
	}

	@Test
	fun `logUpdateStatus records status change message`() {
		val job = appliedJobStore.add("Acme Corp", "Engineer", "")
		aspect.logUpdateStatus(job.id, "REJECTED", null)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Updated job status to Rejected: Engineer at Acme Corp", entries.single().action)
	}

	@Test
	fun `logUpdateStatus records progressing status change`() {
		val job = appliedJobStore.add("Acme Corp", "Engineer", "")
		aspect.logUpdateStatus(job.id, "PROGRESSING", null)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Updated job status to Progressing: Engineer at Acme Corp", entries.single().action)
	}

	@Test
	fun `logUpdateStatus with lowercase status capitalizes first letter`() {
		val job = appliedJobStore.add("Acme Corp", "Engineer", "")
		aspect.logUpdateStatus(job.id, "rejected", null)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Updated job status to Rejected: Engineer at Acme Corp", entries.single().action)
	}

	@Test
	fun `logDeleteAllAppliedJobs records count of deleted jobs`() {
		val result: Map<String, Int> = mapOf("deleted" to 5)
		aspect.logDeleteAllAppliedJobs(result)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Removed applied jobs (5 entries)", entries.single().action)
	}

	@Test
	fun `logDeleteAllAppliedJobs skips when count is zero`() {
		val result: Map<String, Int> = mapOf("deleted" to 0)
		aspect.logDeleteAllAppliedJobs(result)

		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logDeleteAllAppliedJobs ignores null result`() {
		aspect.logDeleteAllAppliedJobs(null)
		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logDownloadSingleJobPdf records download message`() {
		val job = appliedJobStore.add("Acme Corp", "Engineer", "")
		aspect.logDownloadSingleJobPdf(job.id, null)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Downloaded applied job PDF: Engineer at Acme Corp", entries.single().action)
	}

	@Test
	fun `logDownloadSingleJobPdf skips when job not found`() {
		aspect.logDownloadSingleJobPdf("non-existent", null)
		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logDownloadJobListPdf records list download message`() {
		aspect.logDownloadJobListPdf()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Downloaded applied jobs list PDF", entries.single().action)
	}

	// --- ResumeController aspects ---

	@Test
	fun `logSelectResume records selected resume message`() {
		val status = ResumeStatus(configured = true, filename = "MyResume.pdf", emptyList())
		aspect.logSelectResume(status)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Selected resume: MyResume.pdf", entries.single().action)
	}

	@Test
	fun `logSelectResume ignores null result`() {
		aspect.logSelectResume(null)
		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logSelectResume ignores wrong type`() {
		aspect.logSelectResume("not a status")
		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logClearResumes records cleared message`() {
		aspect.logClearResumes()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Cleared resumes", entries.single().action)
	}

	@Test
	fun `logViewResume records viewed resume message`() {
		val view = DocumentViewResponse("id-1", "MyResume.pdf", "pdf", true, "/raw", "text")
		aspect.logViewResume(view)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Viewed resume: MyResume.pdf", entries.single().action)
	}

	@Test
	fun `logViewResume ignores null result`() {
		aspect.logViewResume(null)
		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logDeleteResume records removed resume message`() {
		val option = ResumeOption("id-1", "MyResume.pdf", false)
		aspect.logDeleteResume(option)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Removed resume: MyResume.pdf", entries.single().action)
	}

	@Test
	fun `logDeleteResume ignores null result`() {
		aspect.logDeleteResume(null)
		assertEquals(0, logs.latest().size)
	}

	// --- SupportingDocumentsController aspects ---

	@Test
	fun `logSaveSupportingDocuments records saved message`() {
		aspect.logSaveSupportingDocuments()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Saved supporting document(s)", entries.single().action)
	}

	@Test
	fun `logSelectSupportingDocuments records selection update message`() {
		aspect.logSelectSupportingDocuments()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Updated supporting document selections", entries.single().action)
	}

	@Test
	fun `logClearSupportingDocuments records cleared message`() {
		aspect.logClearSupportingDocuments()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Cleared supporting documents", entries.single().action)
	}

	@Test
	fun `logViewSupportingDocument records viewed document message`() {
		val view = DocumentViewResponse("id-1", "AWS-Cert.pdf", "pdf", true, "/raw", "text")
		aspect.logViewSupportingDocument(view)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Viewed supporting document: AWS-Cert.pdf", entries.single().action)
	}

	@Test
	fun `logViewSupportingDocument ignores null result`() {
		aspect.logViewSupportingDocument(null)
		assertEquals(0, logs.latest().size)
	}

	@Test
	fun `logDeleteSupportingDocument records removed message`() {
		aspect.logDeleteSupportingDocument(null)

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Removed supporting document", entries.single().action)
	}

	// --- SettingsController aspects ---

	@Test
	fun `logSaveApiKey records API key saved message`() {
		aspect.logSaveApiKey()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Saved Gemini API key during setup", entries.single().action)
	}

	@Test
	fun `logSaveSettings records settings updated message`() {
		aspect.logSaveSettings()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Updated application settings", entries.single().action)
	}

	@Test
	fun `logResetSettings records reset message`() {
		aspect.logResetSettings()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Reset application settings", entries.single().action)
	}

	// --- PdfController aspects ---

	@Test
	fun `logDownloadPdf records cover letter PDF download message`() {
		aspect.logDownloadPdf()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Downloaded a cover-letter PDF", entries.single().action)
	}

	@Test
	fun `logDownloadResumePdf records tailored resume PDF download message`() {
		aspect.logDownloadResumePdf()

		val entries = logs.latest()
		assertEquals(1, entries.size)
		assertEquals("Downloaded a tailored resume PDF", entries.single().action)
	}

	// --- Integration: multiple actions recorded in order ---

	@Test
	fun `multiple controller actions are recorded in order`() {
		val job = appliedJobStore.add("Acme Corp", "Engineer", "")
		aspect.logAddAppliedJob(job)
		aspect.logUpdateStatus(job.id, "REJECTED", null)
		aspect.logDeleteAppliedJob()

		val entries = logs.latest()
		assertEquals(3, entries.size)
		assertTrue(entries[0].action.contains("Added applied job"))
		assertTrue(entries[1].action.contains("Updated job status"))
		assertTrue(entries[2].action.contains("Removed applied job"))
	}
}
