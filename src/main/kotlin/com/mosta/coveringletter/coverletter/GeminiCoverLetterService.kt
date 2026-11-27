package com.mosta.coveringletter.coverletter

import com.mosta.coveringletter.generation.GeminiGenerationClient
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class GeminiCoverLetterService(
	private val geminiGenerationClient: GeminiGenerationClient
) : CoverLetterGenerator {
	override fun generate(
		jobDescription: String,
		resume: String,
		supportingDocuments: String,
		format: GenerationFormat
	): Mono<GeneratedCoverLetter> {
		val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
		val formatInstructions = when (format) {
			GenerationFormat.COVER_LETTER -> """
				Write a tailored, professional cover letter. Connect relevant evidence to the job
				requirements, use a natural first-person voice, and avoid generic claims. Format it
				as a polished business letter: include the applicant's name at the top, then the
				current date ($currentDate), recipient/company details only when available from the
				provided documents or job description, and a concise subject line for the identified role.
				Never invent missing contact details. Follow with a greeting, several clearly separated
				body paragraphs, a professional sign-off, and the applicant's name. Use plain text with
				blank lines between sections; do not use markdown or add commentary. Keep the entire
				letter, including heading, greeting, and sign-off, to no more than 300 words so it fits
				on one A4 page.
			""".trimIndent()
			GenerationFormat.MOTIVATION_MESSAGE -> """
				Write a concise motivation message suitable for pasting into a job application's
				motivation or supporting-information field. Keep it to 80-120 words, in one compact
				paragraph, with no address, date, subject line, greeting, sign-off, markdown, or
				commentary. Explain why the applicant is interested in this role and connect their
				most relevant experience or strengths to the job. Make it personal and specific,
				not a complete covering letter.
			""".trimIndent()
		}
		val prompt = """
			Identify the primary job title from the job description and write the requested tailored
			content for that role.
			Use only facts present in the resume and supporting documents; never invent experience,
			qualifications, metrics, names, or contact details. Extract the applicant's full name
			exactly as it appears in the resume; do not infer or invent a name.
			$formatInstructions
			Identify the hiring company from the job description. Return an empty companyName if it
			cannot be confidently identified; do not infer or invent an employer.
			Return a JSON object with exactly four string properties: "applicantName" (the full name as
			printed in the resume), "jobTitle" (the concise role title identified in the job description,
			without company/location), "companyName" (the employer name from the job description, or an
			empty string if unavailable), and "content" (the generated text).

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
			.map { GeneratedCoverLetter(it.content, it.jobTitle, it.applicantName, it.companyName) }
	}
}