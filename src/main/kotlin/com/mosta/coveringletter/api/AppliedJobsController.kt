package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.appliedjobs.AddAppliedJobRequest
import com.mosta.coveringletter.appliedjobs.AppliedJobStore
import com.mosta.coveringletter.pdf.AppliedJobsPdfGenerator
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@RestController
class AppliedJobsController(
    private val store: AppliedJobStore,
    private val logs: UserActionLogBuffer,
    private val pdfGenerator: AppliedJobsPdfGenerator
) {
    @PostMapping("/api/applied-jobs", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun add(@Valid @RequestBody request: AddAppliedJobRequest) =
        store.add(request.company, request.jobTitle, request.jobDescription)
            .also { logs.record("Added applied job: ${it.jobTitle} at ${it.company}") }

    @GetMapping("/api/applied-jobs")
    fun list(
        @RequestParam(value = "search", defaultValue = "") search: String,
        @RequestParam(value = "sortBy", defaultValue = "date") sortBy: String,
        @RequestParam(value = "sortOrder", defaultValue = "desc") sortOrder: String,
        @RequestParam(value = "dateFrom", required = false) dateFrom: LocalDate?,
        @RequestParam(value = "dateTo", required = false) dateTo: LocalDate?
    ) = store.list(search, sortBy, sortOrder, dateFrom, dateTo)

    @DeleteMapping("/api/applied-jobs/{id}")
    fun delete(@PathVariable id: String) {
        val deleted = store.delete(id)
        if (deleted) logs.record("Removed applied job entry")
    }

    @GetMapping("/api/applied-jobs/{id}/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun downloadSingleJobPdf(@PathVariable id: String, response: ServerHttpResponse): ByteArray {
        val jobs = store.list()
        val job = jobs.find { it.id == id }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Applied job not found")
        val pdf = pdfGenerator.createSingleJob(job)
        logs.record("Downloaded applied job PDF: ${job.jobTitle} at ${job.company}")
        response.headers.contentType = MediaType.APPLICATION_PDF
        response.headers.set(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"${job.company}-${job.jobTitle}.pdf\""
        )
        return pdf
    }

    @GetMapping("/api/applied-jobs/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun downloadJobListPdf(
        @RequestParam(value = "search", defaultValue = "") search: String,
        @RequestParam(value = "sortBy", defaultValue = "date") sortBy: String,
        @RequestParam(value = "sortOrder", defaultValue = "desc") sortOrder: String,
        @RequestParam(value = "dateFrom", required = false) dateFrom: LocalDate?,
        @RequestParam(value = "dateTo", required = false) dateTo: LocalDate?,
        response: ServerHttpResponse
    ): ByteArray {
        val jobs = store.list(search, sortBy, sortOrder, dateFrom, dateTo)
        if (jobs.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No jobs to export")
        }
        val pdf = pdfGenerator.createJobList(jobs)
        logs.record("Downloaded applied jobs list PDF (${jobs.size} jobs)")
        response.headers.contentType = MediaType.APPLICATION_PDF
        response.headers.set(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"applied-jobs.pdf\""
        )
        return pdf
    }
}
