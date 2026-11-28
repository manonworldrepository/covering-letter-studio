package com.mosta.coveringletter.persistence

data class CloudStorageArchive(val bytes: ByteArray, val generation: Long)

interface CloudStorageArchiveClient {
	val enabled: Boolean

	fun downloadArchive(): CloudStorageArchive?

	fun uploadArchive(bytes: ByteArray, expectedGeneration: Long?): Long
}