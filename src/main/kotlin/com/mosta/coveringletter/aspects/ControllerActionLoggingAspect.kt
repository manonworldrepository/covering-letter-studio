package com.mosta.coveringletter.aspects

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.response.DocumentViewResponse
import com.mosta.coveringletter.appliedjobs.AppliedJob
import com.mosta.coveringletter.appliedjobs.AppliedJobStore
import com.mosta.coveringletter.resume.ResumeOption
import com.mosta.coveringletter.resume.ResumeStore
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class ControllerActionLoggingAspect(
	private val logs: UserActionLogBuffer,
	private val appliedJobStore: AppliedJobStore,
	private val resumeStore: ResumeStore
) {

	// --- AppliedJobsController ---

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.AppliedJobsController.add(..))",
		returning = "result"
	)
	fun logAddAppliedJob(result: Any?) {
		val job = result as? AppliedJob ?: return
		logs.record("Added applied job: ${job.jobTitle} at ${job.company}")
	}

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.AppliedJobsController.delete(..))")
	fun logDeleteAppliedJob() {
		logs.record("Removed applied job entry")
	}

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.AppliedJobsController.updateStatus(..)) && args(id, statusParam)",
		returning = "result"
	)
	fun logUpdateStatus(id: String, statusParam: String, result: Any?) {
		val updated = appliedJobStore.list().find { it.id == id } ?: return
		val label = statusParam.lowercase().replaceFirstChar { it.uppercase() }
		logs.record("Updated job status to $label: ${updated.jobTitle} at ${updated.company}")
	}

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.AppliedJobsController.deleteAll(..))",
		returning = "result"
	)
	fun logDeleteAllAppliedJobs(result: Any?) {
		@Suppress("UNCHECKED_CAST")
		val resultMap = result as? Map<String, Int> ?: return
		val count = resultMap["deleted"] ?: 0
		if (count > 0) {
			logs.record("Removed applied jobs ($count entries)")
		}
	}

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.AppliedJobsController.downloadSingleJobPdf(..)) && args(id, ..)",
		returning = "result"
	)
	fun logDownloadSingleJobPdf(id: String, result: Any?) {
		val job = appliedJobStore.list().find { it.id == id } ?: return
		logs.record("Downloaded applied job PDF: ${job.jobTitle} at ${job.company}")
	}

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.AppliedJobsController.downloadJobListPdf(..))")
	fun logDownloadJobListPdf() {
		logs.record("Downloaded applied jobs list PDF")
	}

	// --- ResumeController ---

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.ResumeController.saveResume(..))")
	fun logSaveResume() {
		// Reactive method (Mono<ResumeStatus>) — record after resolution
		val status = resumeStore.status()
		status.filename?.let { logs.record("Saved resume: $it") }
			?: logs.record("Saved resume")
	}

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.ResumeController.selectResume(..))",
		returning = "result"
	)
	fun logSelectResume(result: Any?) {
		val status = result as? com.mosta.coveringletter.resume.ResumeStatus ?: return
		status.filename?.let { logs.record("Selected resume: $it") }
	}

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.ResumeController.clearResumes(..))")
	fun logClearResumes() {
		logs.record("Cleared resumes")
	}

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.ResumeController.viewResume(..))",
		returning = "result"
	)
	fun logViewResume(result: Any?) {
		val view = result as? DocumentViewResponse ?: return
		logs.record("Viewed resume: ${view.filename}")
	}

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.ResumeController.deleteResume(..))",
		returning = "result"
	)
	fun logDeleteResume(result: Any?) {
		val option = result as? ResumeOption ?: return
		logs.record("Removed resume: ${option.filename}")
	}

	// --- SupportingDocumentsController ---

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.SupportingDocumentsController.saveSupportingDocuments(..))")
	fun logSaveSupportingDocuments() {
		logs.record("Saved supporting document(s)")
	}

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.SupportingDocumentsController.selectSupportingDocuments(..))")
	fun logSelectSupportingDocuments() {
		logs.record("Updated supporting document selections")
	}

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.SupportingDocumentsController.clearSupportingDocuments(..))")
	fun logClearSupportingDocuments() {
		logs.record("Cleared supporting documents")
	}

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.SupportingDocumentsController.viewSupportingDocument(..))",
		returning = "result"
	)
	fun logViewSupportingDocument(result: Any?) {
		val view = result as? DocumentViewResponse ?: return
		logs.record("Viewed supporting document: ${view.filename}")
	}

	@AfterReturning(
		pointcut = "execution(* com.mosta.coveringletter.api.controller.SupportingDocumentsController.deleteSupportingDocument(..))",
		returning = "result"
	)
	fun logDeleteSupportingDocument(result: Any?) {
		// Reactive method (Mono<SupportingDocument>) — result is Mono, not the document
		// Use fixed message
		logs.record("Removed supporting document")
	}

	// --- SettingsController ---

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.AllowedEmailsController.updateAllowedEmails(..))")
	fun logUpdateAllowedEmails() {
		logs.record("Updated allowed sign-in email addresses")
	}

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.SettingsController.saveApiKey(..))")
	fun logSaveApiKey() {
		// Reactive method (Mono<AppSettingsStatus>) — use fixed message
		logs.record("Saved Gemini API key during setup")
	}

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.SettingsController.saveSettings(..))")
	fun logSaveSettings() {
		// Reactive method (Mono<AppSettingsStatus>) — use fixed message
		logs.record("Updated Gemini API settings")
	}

	// --- PdfController ---

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.PdfController.downloadPdf(..))")
	fun logDownloadPdf() {
		logs.record("Downloaded a cover-letter PDF")
	}

	@AfterReturning("execution(* com.mosta.coveringletter.api.controller.ResumePdfController.downloadPdf(..))")
	fun logDownloadResumePdf() {
		logs.record("Downloaded a tailored resume PDF")
	}
}
