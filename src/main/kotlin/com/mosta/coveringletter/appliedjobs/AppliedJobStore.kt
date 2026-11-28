package com.mosta.coveringletter.appliedjobs

import com.mosta.coveringletter.persistence.AccountDataDirectories
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class AppliedJobStore(@Value("\${coverletter.storage.directory:.data}") private val dataDir: String = ".data") {

    private val rootDirectory = Paths.get(dataDir).toAbsolutePath().normalize()
    private val accountDirectories = AccountDataDirectories(rootDirectory)
    private val sharedJobsDir = File(dataDir, "applied-jobs")
    private val RETENTION_DAYS = 30L
    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    init {
        cleanupExpired(sharedJobsDir)
    }

    @Synchronized
    fun add(company: String, jobTitle: String, jobDescription: String, accountEmail: String? = null): AppliedJob {
        require(company.isNotBlank()) { "Company name is required" }
        require(jobTitle.isNotBlank()) { "Job title is required" }

        val normalizedCompany = normalizeForDuplicateCheck(company)
        val normalizedJobTitle = normalizeForDuplicateCheck(jobTitle)
        val normalizedJobDescription = normalizeForDuplicateCheck(jobDescription)
        val alreadyAdded = allJobs(accountEmail).any { job ->
            normalizeForDuplicateCheck(job.company) == normalizedCompany &&
                normalizeForDuplicateCheck(job.jobTitle) == normalizedJobTitle &&
                normalizeForDuplicateCheck(job.jobDescription) == normalizedJobDescription
        }
        if (alreadyAdded) throw DuplicateAppliedJobException()

        val id = UUID.randomUUID().toString()
        val now = LocalDateTime.now()
        val job = AppliedJob(
            id = id,
            company = company.trim(),
            jobTitle = jobTitle.trim(),
            appliedDate = now,
            jobDescription = jobDescription.trim()
        )

        val jobsDir = accountEmail?.let(::accountJobsDirectory) ?: sharedJobsDir
        jobsDir.mkdirs()
        val file = File(jobsDir, "$id.txt")
        file.writeText(serialize(job))
        return job.copy(ownerEmail = accountEmail)
    }

    private fun normalizeForDuplicateCheck(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").lowercase()

    fun list(
        search: String = "",
        sortBy: String = "date",
        sortOrder: String = "desc",
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
        accountEmail: String? = null
    ): List<AppliedJob> {
        return filterAndSort(allJobs(accountEmail), search, sortBy, sortOrder, dateFrom, dateTo)
    }

    fun listAllForAdmin(
        emails: Collection<String>,
        search: String = "",
        sortBy: String = "date",
        sortOrder: String = "desc",
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null
    ): List<AppliedJob> {
        val shared = loadAll(sharedJobsDir)
        val sharedIds = shared.map { it.id }.toSet()
        val all = (emails.flatMap { email ->
            loadAll(accountJobsDirectory(email)).map { it.copy(ownerEmail = email, shared = it.id in sharedIds) }
        } + shared.map { it.copy(ownerEmail = "Shared legacy data", shared = true) }).distinctBy { it.id }
        return filterAndSort(all, search, sortBy, sortOrder, dateFrom, dateTo)
    }

    private fun filterAndSort(
        jobs: List<AppliedJob>,
        search: String,
        sortBy: String,
        sortOrder: String,
        dateFrom: LocalDate?,
        dateTo: LocalDate?
    ): List<AppliedJob> {
        if (jobs.isEmpty()) return emptyList()
        var filtered = if (search.isBlank()) {
            jobs
        } else {
            val lower = search.lowercase()
            jobs.filter {
                it.company.lowercase().contains(lower) ||
                    it.jobTitle.lowercase().contains(lower)
            }
        }
        if (dateFrom != null) {
            filtered = filtered.filter { it.appliedDate.toLocalDate() >= dateFrom }
        }
        if (dateTo != null) {
            filtered = filtered.filter { it.appliedDate.toLocalDate() <= dateTo }
        }

        val ascending = sortOrder == "asc"
        val sorted = when (sortBy) {
            "company" -> filtered.sortedWith(compareBy { it.company.lowercase() })
            "title" -> filtered.sortedWith(compareBy { it.jobTitle.lowercase() })
            else -> filtered.sortedBy { it.appliedDate }
        }

        return if (ascending) sorted else sorted.asReversed()
    }


    fun delete(id: String, accountEmail: String? = null): Boolean {
        val sharedFile = File(sharedJobsDir, "$id.txt")
        if (sharedFile.exists()) {
            val deleted = sharedFile.delete()
            if (deleted) deleteSharedCopies(id)
            return deleted
        }
        val personalFile = accountEmail?.let { File(accountJobsDirectory(it), "$id.txt") }
        val file = if (accountEmail == null) File(sharedJobsDir, "$id.txt") else personalFile!!
        return file.exists() && file.delete()
    }

    fun deleteAll(ids: Collection<String>, accountEmail: String? = null): Int {
        return ids.count { delete(it, accountEmail) }
    }

    fun updateStatus(id: String, status: AppliedJobStatus, accountEmail: String? = null): AppliedJob? {
        if (accountEmail != null && File(sharedJobsDir, "$id.txt").exists()) return null
        val personalFile = accountEmail?.let { File(accountJobsDirectory(it), "$id.txt") }
        val isPersonal = personalFile?.exists() == true
        val file = if (accountEmail == null) File(sharedJobsDir, "$id.txt") else personalFile!!
        if (!file.exists()) return null
        val job = deserialize(file.readText()) ?: return null
        val updated = AppliedJob(
            id = job.id,
            company = job.company,
            jobTitle = job.jobTitle,
            appliedDate = job.appliedDate,
            jobDescription = job.jobDescription,
            status = status
        )
        file.writeText(serialize(updated))
        return updated.copy(ownerEmail = if (isPersonal) accountEmail else "Shared legacy data")
    }

    private fun serialize(job: AppliedJob): String {
        return "${job.id}\n${job.company}\n${job.jobTitle}\n${job.appliedDate.format(dateFormat)}\n${job.status.name}\n${job.jobDescription}"
    }

    private fun deserialize(text: String): AppliedJob? {
        val lines = text.split('\n', limit = 5)
        if (lines.size < 5) return null
        return try {
            val statusAndDescription = lines[4]
            val firstNewline = statusAndDescription.indexOf('\n')
            val (statusLine, jobDescription) = if (firstNewline >= 0) {
                statusAndDescription.substring(0, firstNewline).trim() to statusAndDescription.substring(firstNewline + 1)
            } else {
                // lines[4] has no newline — check if it's a valid status enum value
                val trimmed = statusAndDescription.trim()
                if (AppliedJobStatus.values().any { it.name == trimmed }) {
                    trimmed to ""
                } else {
                    // Old format: lines[4] is the job description, no status line
                    AppliedJobStatus.PROGRESSING.name to statusAndDescription
                }
            }
            val status = if (statusLine.isBlank()) {
                AppliedJobStatus.PROGRESSING
            } else {
                AppliedJobStatus.valueOf(statusLine)
            }
            AppliedJob(
                id = lines[0],
                company = lines[1],
                jobTitle = lines[2],
                appliedDate = LocalDateTime.parse(lines[3], dateFormat),
                jobDescription = jobDescription,
                status = status
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun loadAll(jobsDir: File): List<AppliedJob> {
        if (!jobsDir.exists()) return emptyList()
        return jobsDir.listFiles { it.name.endsWith(".txt") }
            ?.mapNotNull { file ->
                try {
                    deserialize(file.readText())
                } catch (e: Exception) {
                    null
                }
            }
            ?: emptyList()
    }

    private fun allJobs(accountEmail: String?): List<AppliedJob> {
        val shared = loadAll(sharedJobsDir)
        if (accountEmail == null) return shared
        val privateDirectory = accountJobsDirectory(accountEmail)
        cleanupExpired(privateDirectory)
        val sharedIds = shared.map { it.id }.toSet()
        return (loadAll(privateDirectory).map { it.copy(ownerEmail = accountEmail, shared = it.id in sharedIds) } +
            shared.map { it.copy(ownerEmail = "Shared legacy data", shared = true) }).distinctBy { it.id }
    }

    private fun accountJobsDirectory(email: String): File =
        File(accountDirectories.forEmail(email).toFile(), "applied-jobs")

    private fun deleteSharedCopies(id: String) {
        if (!Files.isDirectory(rootDirectory.resolve("accounts"))) return
        Files.list(rootDirectory.resolve("accounts")).use { accountDirectories ->
            accountDirectories.filter(Files::isDirectory).forEach { accountDirectory ->
                File(accountDirectory.toFile(), "applied-jobs/$id.txt").delete()
            }
        }
    }

    private fun cleanupExpired(jobsDir: File) {
        val cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS)
        loadAll(jobsDir).forEach { job ->
            if (job.appliedDate.isBefore(cutoff)) {
                File(jobsDir, "${job.id}.txt").delete()
            }
        }
    }
}
