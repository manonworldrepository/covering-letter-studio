package com.mosta.coveringletter.aspects

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.security.Principal

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccountActionLogContextAspect(
	private val logs: UserActionLogBuffer
) {
	@Around("execution(* com.mosta.coveringletter.api.controller..*(..))")
	fun scopeUserActionLogs(joinPoint: ProceedingJoinPoint): Any? {
		val email = joinPoint.args.filterIsInstance<Principal>().lastOrNull()?.name
		return if (email == null) joinPoint.proceed() else logs.withAccount(email) { joinPoint.proceed() }
	}
}