package com.mosta.coveringletter.api.request

import com.mosta.coveringletter.generation.GenerationLimits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class TailorResumeRequest(
	@field:NotBlank(message = "Enter the job description.")
	@field:Size(max = GenerationLimits.MAX_JOB_DESCRIPTION_LENGTH, message = "Job description must be 20,000 characters or fewer.")
	val jobDescription: String = ""
)