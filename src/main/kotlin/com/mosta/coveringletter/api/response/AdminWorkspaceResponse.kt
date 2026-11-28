package com.mosta.coveringletter.api.response

import com.mosta.coveringletter.resume.ResumeOption
import com.mosta.coveringletter.supportingdocument.SupportingDocument

data class AdminWorkspaceResponse(
	val email: String,
	val resumes: List<ResumeOption>,
	val supportingDocuments: List<SupportingDocument>
)