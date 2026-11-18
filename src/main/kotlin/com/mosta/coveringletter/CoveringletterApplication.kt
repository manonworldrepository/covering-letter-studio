package com.mosta.coveringletter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.EnableAspectJAutoProxy

@SpringBootApplication
@EnableAspectJAutoProxy
class CoveringletterApplication

fun main(args: Array<String>) {
	runApplication<CoveringletterApplication>(*args)
}
