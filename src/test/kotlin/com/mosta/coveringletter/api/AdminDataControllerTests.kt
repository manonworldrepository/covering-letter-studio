package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.controller.AdminDataController
import com.mosta.coveringletter.appliedjobs.AppliedJobStore
import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.settings.AllowedEmailsService
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Path

class AdminDataControllerTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	private val admin = java.security.Principal { "admin@example.com" }
	private val otherUser = java.security.Principal { "user@example.com" }
	private lateinit var resumes: ResumeStore
	private lateinit var documents: SupportingDocumentStore
	private lateinit var jobs: AppliedJobStore
	private lateinit var controller: AdminDataController

	@BeforeEach
	fun setUp() {
		val dataDirectory = temporaryDirectory.resolve("data")
		val allowedEmails = AllowedEmailsService("admin@example.com,user@example.com")
			.also { it.envFile = temporaryDirectory.resolve(".env") }
		resumes = ResumeStore(DocumentTextExtractor(), dataDirectory.toString())
		documents = SupportingDocumentStore(DocumentTextExtractor(), dataDirectory.toString())
		jobs = AppliedJobStore(dataDirectory.toString())
		controller = AdminDataController(allowedEmails, resumes, documents, jobs)
	}

	@Test
	fun `admin can inspect all workspaces while a regular user is denied`() {
		val pdf = CoverLetterPdfGenerator().create("Legacy document contents.")
		val sharedResume = resumes.saveBytes("shared-resume.pdf", pdf).resumes.single()
		val privateResume = resumes.saveBytes("private-resume.pdf", pdf, "user@example.com").resumes.first { !it.shared }
		val sharedDocument = documents.saveBytes("shared-document.pdf", pdf)
		val privateDocument = documents.saveBytes("private-document.pdf", pdf, "user@example.com")

		val workspaces = controller.workspaces(admin)
		val adminWorkspace = workspaces.single { it.email == admin.name }
		val userWorkspace = workspaces.single { it.email == otherUser.name }
		assertTrue(adminWorkspace.resumes.any { it.id == sharedResume.id && it.shared })
		assertTrue(userWorkspace.resumes.any { it.id == sharedResume.id && it.shared })
		assertTrue(userWorkspace.resumes.any { it.id == privateResume.id && !it.shared })
		assertTrue(userWorkspace.supportingDocuments.any { it.id == sharedDocument.id && it.shared })
		assertTrue(userWorkspace.supportingDocuments.any { it.id == privateDocument.id && !it.shared })

		val denied = assertThrows(ResponseStatusException::class.java) { controller.workspaces(otherUser) }
		assertEquals(HttpStatus.FORBIDDEN, denied.statusCode)
	}

	@Test
	fun `admin application list includes account owner beside each private application`() {
		val shared = jobs.add("Legacy Corp", "Shared Role", "Shared description")
		val userJob = jobs.add("User Corp", "User Role", "User description", otherUser.name)

		val allJobs = controller.appliedJobs("", "date", "desc", null, null, admin)
		assertEquals("Shared legacy data", allJobs.single { it.id == shared.id }.ownerEmail)
		assertEquals(otherUser.name, allJobs.single { it.id == userJob.id }.ownerEmail)

		val denied = assertThrows(ResponseStatusException::class.java) {
			controller.appliedJobs("", "date", "desc", null, null, otherUser)
		}
		assertEquals(HttpStatus.FORBIDDEN, denied.statusCode)
	}
}