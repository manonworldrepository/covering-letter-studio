package com.mosta.coveringletter.resume

import com.mosta.coveringletter.documents.DocumentTextExtractor
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path

internal class ResumeStoreReader(
	private val extractor: DocumentTextExtractor,
	private val directory: Path,
	private val resumesDirectory: Path,
	private val filenameMetadata: Path,
	private val selectionMetadata: Path
) {
	fun status(): ResumeStatus {
		val resumes = storedResumes().toMutableList()
		legacyResume()?.let { legacy ->
			if (resumes.none { it.id == legacy.id }) resumes.add(legacy)
		}
		val selectedId = if (Files.isRegularFile(selectionMetadata)) {
			Files.readString(selectionMetadata).trim()
		} else {
			legacyResume()?.id ?: resumes.firstOrNull()?.id
		}
		val selected = resumes.firstOrNull { it.id == selectedId } ?: resumes.firstOrNull()
		return ResumeStatus(
			selected != null,
			selected?.filename,
			resumes.map { it.copy(selected = it.id == selected?.id) }
		)
	}

	fun extractSelected(): String {
		val resume = status().resumes.firstOrNull { it.selected }
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload and select a resume before generating a letter.")
		return extractor.extractText(resume.filename, Files.readAllBytes(resolve(resume)))
	}

	fun storedResumes(): List<ResumeOption> {
		if (!Files.isDirectory(resumesDirectory)) return emptyList()
		return Files.list(resumesDirectory).use { paths ->
			paths.filter(Files::isRegularFile)
				.map { path ->
					val storedName = path.fileName.toString()
					val separator = storedName.indexOf("__")
					if (separator <= 0) null else {
						val id = storedName.substring(0, separator)
						val filename = storedName.substring(separator + 2)
						if (!id.matches(ResumeStoreLimits.UUID_PATTERN) || !isSafeFilename(filename)) null
						else ResumeOption(id, filename, selected = false)
					}
				}
				.filter { it != null }
				.map { it!! }
				.sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.filename })
				.toList()
		}
	}

	fun legacyResume(): ResumeOption? {
		val savedFilename = if (Files.isRegularFile(filenameMetadata)) {
			Files.readString(filenameMetadata).takeIf(::isSafeFilename)
		} else {
			null
		}
		val filename = savedFilename?.takeIf { Files.isRegularFile(directory.resolve(it)) }
			?: listOf("resume.pdf", "resume.docx").firstOrNull { Files.isRegularFile(directory.resolve(it)) }
			?: return null
		return ResumeOption("legacy:$filename", filename, selected = false)
	}

	fun legacyResumeCandidates(): List<String> {
		val metadataFilename = if (Files.isRegularFile(filenameMetadata)) {
			Files.readString(filenameMetadata).takeIf(::isSafeFilename)
		} else {
			null
		}
		return (listOfNotNull(metadataFilename) + listOf("resume.pdf", "resume.docx"))
			.distinct()
			.filter { Files.isRegularFile(directory.resolve(it)) }
	}

	fun resolve(resume: ResumeOption): Path {
		if (resume.id.startsWith("legacy:")) {
			val filename = resume.id.removePrefix("legacy:")
			if (filename != resume.filename || !isSafeFilename(filename)) {
				throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume.")
			}
			return directory.resolve(filename).normalize().also { path ->
				if (!path.startsWith(directory)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume path.")
			}
		}
		if (!resume.id.matches(ResumeStoreLimits.UUID_PATTERN) || !isSafeFilename(resume.filename)) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume.")
		}
		return resumesDirectory.resolve("${resume.id}__${resume.filename}").normalize().also { path ->
			if (!path.startsWith(resumesDirectory)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume path.")
		}
	}

	fun isSafeFilename(filename: String): Boolean =
		filename.isNotBlank() &&
			filename.length <= ResumeStoreLimits.MAX_FILENAME_LENGTH &&
			filename != "." &&
			filename != ".." &&
			!filename.contains('/') &&
			!filename.contains('\\') &&
			!filename.contains(':') &&
			!filename.contains('*') &&
			!filename.contains('?') &&
			!filename.contains('"') &&
			!filename.contains('<') &&
			!filename.contains('>') &&
			!filename.contains('|') &&
			!filename.endsWith('.') &&
			!filename.endsWith(' ') &&
			filename != filenameMetadata.fileName.toString()
}
