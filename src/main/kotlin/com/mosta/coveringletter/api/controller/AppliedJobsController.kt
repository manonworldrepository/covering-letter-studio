package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.appliedjobs.AddAppliedJobRequest
import com.mosta.coveringletter.appliedjobs.AppliedJobStatus
import com.mosta.coveringletter.appliedjobs.AppliedJobStore
import com.mosta.coveringletter.appliedjobs.DuplicateAppliedJobException
import com.mosta.coveringletter.pdf.AppliedJobsPdfGenerator
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.security.Principal

@RestController
class AppliedJobsController(
    private val store: AppliedJobStore,
    private val pdfGenerator: AppliedJobsPdfGenerator
) {
    @PostMapping("/api/applied-jobs", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun add(@Valid @RequestBody request: AddAppliedJobRequest, principal: Principal) =
        try {
            store.add(request.company, request.jobTitle, request.jobDescription, principal.name)
        } catch (exception: DuplicateAppliedJobException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, exception.message)
        }

    @GetMapping("/api/applied-jobs")
    fun list(
        @RequestParam(value = "search", defaultValue = "") search: String,
        @RequestParam(value = "sortBy", defaultValue = "date") sortBy: String,
        @RequestParam(value = "sortOrder", defaultValue = "desc") sortOrder: String,
        @RequestParam(value = "dateFrom", required = false) dateFrom: LocalDate?,
        @RequestParam(value = "dateTo", required = false) dateTo: LocalDate?,
        principal: Principal
    ) = store.list(search, sortBy, sortOrder, dateFrom, dateTo, principal.name)

    @DeleteMapping("/api/applied-jobs/{id}")
    fun delete(@PathVariable id: String, principal: Principal) {
        store.delete(id, principal.name)
    }

    @PatchMapping("/api/applied-jobs/{id}/status")
    fun updateStatus(@PathVariable id: String, @RequestParam status: String, principal: Principal) {
        val jobStatus = try {
            AppliedJobStatus.valueOf(status.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: $status")
        }
        store.updateStatus(id, jobStatus, principal.name)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Applied job not found")
    }

    @DeleteMapping("/api/applied-jobs")
    fun deleteAll(
        @RequestParam(value = "search", defaultValue = "") search: String,
        @RequestParam(value = "dateFrom", required = false) dateFrom: LocalDate?,
        @RequestParam(value = "dateTo", required = false) dateTo: LocalDate?,
        @RequestParam(value = "ids", required = false) ids: List<String>? = null,
        principal: Principal
    ): Map<String, Int> {
        val targetIds = if (!ids.isNullOrEmpty()) {
            ids
        } else {
            store.list(search = search, dateFrom = dateFrom, dateTo = dateTo, accountEmail = principal.name).map { it.id }
        }
        val count = store.deleteAll(targetIds, principal.name)
        return mapOf("deleted" to count)
    }

    @GetMapping("/api/applied-jobs/{id}/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun downloadSingleJobPdf(@PathVariable id: String, response: ServerHttpResponse, principal: Principal): ByteArray {
        val jobs = store.list(accountEmail = principal.name)
        val job = jobs.find { it.id == id }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Applied job not found")
        val pdf = pdfGenerator.createSingleJob(job)
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
        response: ServerHttpResponse,
        principal: Principal
    ): ByteArray {
        val jobs = store.list(search, sortBy, sortOrder, dateFrom, dateTo, principal.name)
        if (jobs.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No jobs to export")
        }
        val pdf = pdfGenerator.createJobList(jobs)
        response.headers.contentType = MediaType.APPLICATION_PDF
        response.headers.set(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"applied-jobs.pdf\""
        )
        return pdf
    }
}
