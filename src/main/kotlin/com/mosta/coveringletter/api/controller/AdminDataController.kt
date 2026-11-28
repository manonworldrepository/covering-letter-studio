package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.api.response.AdminWorkspaceResponse
import com.mosta.coveringletter.api.response.DocumentViewResponse
import com.mosta.coveringletter.appliedjobs.AppliedJobStore
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.settings.AllowedEmailsService
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.time.LocalDate
import org.springframework.web.util.UriUtils

@RestController
class AdminDataController(
	private val allowedEmails: AllowedEmailsService,
	private val resumeStore: ResumeStore,
	private val supportingDocumentStore: SupportingDocumentStore,
	private val appliedJobStore: AppliedJobStore
) {
	@GetMapping("/api/admin/workspaces")
	fun workspaces(principal: Principal): List<AdminWorkspaceResponse> {
		allowedEmails.requireAdmin(principal.name)
		return allowedEmails.allowedEmails().sorted().map { email ->
			AdminWorkspaceResponse(
				email = email,
				resumes = resumeStore.status(email).resumes,
				supportingDocuments = supportingDocumentStore.list(email)
			)
		}
	}

	@GetMapping("/api/admin/applied-jobs")
	fun appliedJobs(
		@RequestParam(value = "search", defaultValue = "") search: String,
		@RequestParam(value = "sortBy", defaultValue = "date") sortBy: String,
		@RequestParam(value = "sortOrder", defaultValue = "desc") sortOrder: String,
		@RequestParam(value = "dateFrom", required = false) dateFrom: LocalDate?,
		@RequestParam(value = "dateTo", required = false) dateTo: LocalDate?,
		principal: Principal
	) : List<com.mosta.coveringletter.appliedjobs.AppliedJob> {
		allowedEmails.requireAdmin(principal.name)
		return appliedJobStore.listAllForAdmin(allowedEmails.allowedEmails(), search, sortBy, sortOrder, dateFrom, dateTo)
	}

	@GetMapping("/api/admin/resumes/{id}/view")
	fun viewResume(
		@PathVariable id: String,
		@RequestParam email: String,
		principal: Principal
	): DocumentViewResponse {
		requireAdminAndKnownAccount(principal.name, email)
		val view = resumeStore.getDocumentView(id, email)
		return view.copy(rawUrl = "/api/admin/resumes/$id/raw?email=${encode(email)}")
	}

	@GetMapping("/api/admin/resumes/{id}/raw")
	fun getResumeRaw(@PathVariable id: String, @RequestParam email: String, principal: Principal): ResponseEntity<ByteArray> {
		requireAdminAndKnownAccount(principal.name, email)
		val (filename, bytes) = resumeStore.getDocumentBytes(id, email)
		return DocumentResponseHelper.createInlineResponseEntity(filename, bytes)
	}

	@GetMapping("/api/admin/supporting-documents/{id}/view")
	fun viewSupportingDocument(
		@PathVariable id: String,
		@RequestParam email: String,
		principal: Principal
	): DocumentViewResponse {
		requireAdminAndKnownAccount(principal.name, email)
		val view = supportingDocumentStore.getDocumentView(id, email)
		return view.copy(rawUrl = "/api/admin/supporting-documents/$id/raw?email=${encode(email)}")
	}

	@GetMapping("/api/admin/supporting-documents/{id}/raw")
	fun getSupportingDocumentRaw(
		@PathVariable id: String,
		@RequestParam email: String,
		principal: Principal
	): ResponseEntity<ByteArray> {
		requireAdminAndKnownAccount(principal.name, email)
		val (filename, bytes) = supportingDocumentStore.getDocumentBytes(id, email)
		return DocumentResponseHelper.createInlineResponseEntity(filename, bytes)
	}

	private fun requireAdminAndKnownAccount(adminEmail: String, ownerEmail: String) {
		allowedEmails.requireAdmin(adminEmail)
		if (ownerEmail.trim().lowercase() !in allowedEmails.allowedEmails()) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account data was not found.")
		}
	}

	private fun encode(value: String): String = UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8)
}