package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OkxBackfillAsyncTaskServiceTest {

	@Test
	void shouldClampBackfillTaskPageBounds() {
		OkxBackfillAsyncTaskService service = new OkxBackfillAsyncTaskService(null, noopExecutor(), 60_000L);
		registerTask(service, taskState("okx-2", "RUNNING", "BTC-USDT", Instant.parse("2026-05-03T10:00:00Z")));
		registerTask(service, taskState("okx-1", "RUNNING", "BTC-USDT", Instant.parse("2026-05-03T09:00:00Z")));

		List<OkxBackfillAsyncTaskService.TaskStatus> result = service.list(-1, 999, "running", "btc-usdt");

		assertEquals(2, result.size());
		assertEquals("okx-2", result.get(0).taskId());
		assertEquals("okx-1", result.get(1).taskId());
	}

	@Test
	void shouldUseStableSortWhenSubmittedAtMatches() {
		OkxBackfillAsyncTaskService service = new OkxBackfillAsyncTaskService(null, noopExecutor(), 60_000L);
		Instant sameTime = Instant.parse("2026-05-03T10:00:00Z");
		registerTask(service, taskState("okx-1", "RUNNING", "BTC-USDT", sameTime));
		registerTask(service, taskState("okx-2", "RUNNING", "BTC-USDT", sameTime));

		List<OkxBackfillAsyncTaskService.TaskStatus> result = service.list(0, 10, null, null);

		assertEquals(2, result.size());
		assertEquals("okx-2", result.get(0).taskId());
		assertEquals("okx-1", result.get(1).taskId());
	}

	private void registerTask(OkxBackfillAsyncTaskService service, Object taskState) {
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> tasks = (java.util.Map<String, Object>) ReflectionTestUtils.getField(service, "tasks");
		assertTrue(tasks != null);
		tasks.put((String) ReflectionTestUtils.getField(taskState, "taskId"), taskState);
	}

	private Object taskState(String taskId, String status, String instId, Instant submittedAt) {
		Object taskState = instantiateTaskState(taskId, instId);
		ReflectionTestUtils.setField(taskState, "submittedAt", submittedAt);
		ReflectionTestUtils.setField(taskState, "status", status);
		return taskState;
	}

	private Object instantiateTaskState(String taskId, String instId) {
		try {
			Constructor<?> constructor = findTaskStateClass().getDeclaredConstructor(
				String.class,
				String.class,
				long.class,
				long.class,
				String.class,
				int.class,
				int.class,
				long.class
			);
			constructor.setAccessible(true);
			return constructor.newInstance(taskId, instId, 1L, 2L, "1m", 100, 10, 10L);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private Class<?> findTaskStateClass() {
		for (Class<?> nestedClass : OkxBackfillAsyncTaskService.class.getDeclaredClasses()) {
			if ("TaskState".equals(nestedClass.getSimpleName())) {
				return nestedClass;
			}
		}
		throw new IllegalStateException("TaskState class not found");
	}

	private Executor noopExecutor() {
		return command -> {
		};
	}
}
