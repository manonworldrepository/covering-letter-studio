package com.mosta.coveringletter

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<CoveringletterApplication>().with(TestcontainersConfiguration::class).run(*args)
}
