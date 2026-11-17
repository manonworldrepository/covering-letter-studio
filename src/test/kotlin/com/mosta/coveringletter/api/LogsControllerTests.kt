package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.request.UserActionRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LogsControllerTests {

	private lateinit var logs: UserActionLogBuffer
	private lateinit var controller: LogsController

	@BeforeEach
	fun setup() {
		logs = UserActionLogBuffer()
		controller = LogsController(logs)
	}

	@Test
	fun `getUserActions returns empty list when no actions recorded`() {
		val actions = controller.getUserActions()

		assertTrue(actions.isEmpty())
	}

	@Test
	fun `getUserActions returns recorded actions`() {
		logs.record("Generated a cover letter")
		logs.record("Uploaded resume")

		val actions = controller.getUserActions()

		assertEquals(2, actions.size)
		assertEquals("Generated a cover letter", actions[0].action)
		assertEquals("Uploaded resume", actions[1].action)
	}

	@Test
	fun `recordUserAction logs a new action`() {
		controller.recordUserAction(UserActionRequest("Tested the application"))

		val actions = logs.latest()
		assertEquals(1, actions.size)
		assertEquals("Tested the application", actions.single().action)
	}

	@Test
	fun `recordUserAction records action with exact text`() {
		controller.recordUserAction(UserActionRequest("Exact action text"))

		val actions = logs.latest()
		assertEquals("Exact action text", actions.single().action)
	}

	@Test
	fun `getUserActions returns actions in chronological order`() {
		logs.record("First action")
		logs.record("Second action")
		logs.record("Third action")

		val actions = controller.getUserActions()

		assertEquals(3, actions.size)
		assertEquals("First action", actions[0].action)
		assertEquals("Third action", actions[2].action)
	}
}
