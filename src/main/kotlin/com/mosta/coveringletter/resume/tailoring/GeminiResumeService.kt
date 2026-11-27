package com.mosta.coveringletter.resume.tailoring

import com.mosta.coveringletter.generation.GeminiGenerationClient
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class GeminiResumeService(
	private val geminiGenerationClient: GeminiGenerationClient
) : ResumeGenerator {
	override fun generate(
		jobDescription: String,
		resume: String,
		supportingDocuments: String
	): Mono<GeneratedResume> {
		val prompt = """
			Identify the primary job title from the job description and create a complete, tailored
			resume for that role using clear section headings and concise, ATS-friendly plain text.
			Use only facts present in the resume and supporting documents; never invent experience,
			qualifications, metrics, names, or contact details. Extract the applicant's full name
			exactly as it appears in the resume; do not infer or invent a name.
			Reorder and rephrase existing information to make the strongest relevant evidence easy to
			find. Prioritize computer science foundations, hardware or physical engineering experience,
			agentic workflows, and direct customer collaboration only when supported by the source
			documents. Highlight measurable impact and before-and-after outcomes when the documents
			provide evidence. Never invent or estimate metrics, experience, skills, qualifications,
			employers, dates, or outcomes; do not imply an impact that the source documents do not
			establish. Preserve accurate career details and include a concise professional summary,
			skills, experience, education, and relevant projects where the source material supports them.
			Identify the hiring company from the job description. Return an empty companyName if it
			cannot be confidently identified; do not infer or invent an employer.
			Return a JSON object with exactly four string properties: "applicantName" (the full name as
			printed in the resume), "jobTitle" (the concise role title identified in the job description,
			without company/location), "companyName" (the employer name from the job description, or an
			empty string if unavailable), and "content" (the generated resume, without commentary or
			markdown fences).

			<Job description>
			$jobDescription
			</Job description>

			<Resume>
			$resume
			</Resume>

			<Supporting documents>
			${supportingDocuments.ifBlank { "No supporting documents provided." }}
			</Supporting documents>
		""".trimIndent()

		return geminiGenerationClient.generate(prompt)
			.map { GeneratedResume(it.content, it.jobTitle, it.applicantName, it.companyName) }
	}
}