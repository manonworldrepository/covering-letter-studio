package com.mosta.coveringletter.appliedjobs

import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class AppliedJobStore(private val dataDir: String = ".data") {

    private val jobsDir = File(dataDir, "applied-jobs")
    private val RETENTION_DAYS = 30L
    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    init {
        cleanupExpired()
    }

    fun add(company: String, jobTitle: String, jobDescription: String): AppliedJob {
        require(company.isNotBlank()) { "Company name is required" }
        require(jobTitle.isNotBlank()) { "Job title is required" }

        val id = UUID.randomUUID().toString()
        val now = LocalDateTime.now()
        val job = AppliedJob(
            id = id,
            company = company.trim(),
            jobTitle = jobTitle.trim(),
            appliedDate = now,
            jobDescription = jobDescription.trim()
        )

        jobsDir.mkdirs()
        val file = File(jobsDir, "$id.txt")
        file.writeText(serialize(job))
        return job
    }

    fun list(
        search: String = "",
        sortBy: String = "date",
        sortOrder: String = "desc",
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null
    ): List<AppliedJob> {
        if (!jobsDir.exists()) return emptyList()

        val jobs = loadAll()
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

    fun delete(id: String): Boolean {
        val file = File(jobsDir, "$id.txt")
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    fun deleteAll(ids: Collection<String>): Int {
        return ids.count { delete(it) }
    }

    fun updateStatus(id: String, status: AppliedJobStatus): AppliedJob? {
        val file = File(jobsDir, "$id.txt")
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
        return updated
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
                AppliedJobStatus.PROGRESSING.name to ""
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

    private fun loadAll(): List<AppliedJob> {
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

    private fun cleanupExpired() {
        val cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS)
        loadAll().forEach { job ->
            if (job.appliedDate.isBefore(cutoff)) {
                File(jobsDir, "${job.id}.txt").delete()
            }
        }
    }
}
