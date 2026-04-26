package com.chainsentinel.infra.service;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OkxBackfillAsyncTaskService {

	private static final Logger log = LoggerFactory.getLogger(OkxBackfillAsyncTaskService.class);

	private final OkxPriceTickBackfillService okxPriceTickBackfillService;
	private final Executor backfillExecutor;
	private final long finishedTaskRetentionMs;
	private final AtomicLong sequence = new AtomicLong(0L);
	private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

	public OkxBackfillAsyncTaskService(
		OkxPriceTickBackfillService okxPriceTickBackfillService,
		@Qualifier("priceTickBackfillExecutor") Executor backfillExecutor,
		@Value("${chainsentinel.price.backfill.task-retention-ms:600000}") long finishedTaskRetentionMs
	) {
		this.okxPriceTickBackfillService = okxPriceTickBackfillService;
		this.backfillExecutor = backfillExecutor;
		this.finishedTaskRetentionMs = Math.max(1000L, finishedTaskRetentionMs);
	}

	public TaskAccepted submit(
		String instId,
		long fromTs,
		long toTs,
		String bar,
		int pageLimit,
		int maxRounds,
		long sleepMs
	) {
		String taskId = buildTaskId();
		String normalizedInstId = normalizeInstId(instId);
		TaskState state = new TaskState(taskId, normalizedInstId, fromTs, toTs, bar, pageLimit, maxRounds, sleepMs);
		tasks.put(taskId, state);
		try {
			backfillExecutor.execute(() -> runTask(state));
		} catch (Exception ex) {
			state.markFailed(ex);
		}
		return state.toAccepted();
	}

	public TaskStatus query(String taskId) {
		TaskState state = tasks.get(taskId);
		if (state == null) {
			return null;
		}
		return state.toStatus();
	}

	public List<TaskStatus> list(int page, int size, String status, String instId) {
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(500, size));
		String normalizedStatus = normalizeStatus(status);
		String normalizedInstId = normalizeInstId(instId);

		List<TaskState> matched = tasks.values().stream()
			.filter(state -> normalizedStatus == null || normalizedStatus.equals(state.status()))
			.filter(state -> normalizedInstId == null || normalizedInstId.equals(state.instId()))
			.sorted(Comparator.comparing(TaskState::submittedAt).reversed())
			.toList();
		int fromIndex = Math.min(matched.size(), safePage * safeSize);
		int toIndex = Math.min(matched.size(), fromIndex + safeSize);
		List<TaskStatus> result = new ArrayList<>(Math.max(0, toIndex - fromIndex));
		for (TaskState state : matched.subList(fromIndex, toIndex)) {
			result.add(state.toStatus());
		}
		return result;
	}

	public TaskSummary summarize(Instant fromAt, Instant toAt) {
		List<TaskState> matched = tasks.values().stream()
			.filter(state -> withinRange(state.submittedAt(), fromAt, toAt))
			.toList();
		long queued = matched.stream().filter(state -> "QUEUED".equals(state.status())).count();
		long running = matched.stream().filter(state -> "RUNNING".equals(state.status())).count();
		long succeeded = matched.stream().filter(state -> "SUCCEEDED".equals(state.status())).count();
		long failed = matched.stream().filter(state -> "FAILED".equals(state.status())).count();
		long finished = succeeded + failed;

		List<Long> durations = matched.stream()
			.map(TaskState::durationMs)
			.filter(duration -> duration != null && duration >= 0L)
			.toList();
		long averageDurationMs = durations.isEmpty()
			? 0L
			: Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0D));
		double successRate = finished <= 0 ? 0D : (double) succeeded / (double) finished;

		List<FailureReasonCount> failureReasons = matched.stream()
			.filter(state -> "FAILED".equals(state.status()))
			.map(TaskState::error)
			.map(this::normalizeError)
			.collect(Collectors.groupingBy(reason -> reason, Collectors.counting()))
			.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
			.limit(20)
			.map(entry -> new FailureReasonCount(entry.getKey(), entry.getValue()))
			.toList();

		return new TaskSummary(
			matched.size(),
			queued,
			running,
			succeeded,
			failed,
			averageDurationMs,
			successRate,
			failureReasons
		);
	}

	public long runningTaskCount() {
		return tasks.values().stream().filter(state -> "RUNNING".equals(state.status())).count();
	}

	@Scheduled(fixedDelayString = "${chainsentinel.price.backfill.task-cleanup-interval-ms:60000}")
	public void cleanupFinishedTasks() {
		long nowMs = System.currentTimeMillis();
		long retentionMs = finishedTaskRetentionMs;
		int removed = 0;
		for (Map.Entry<String, TaskState> entry : tasks.entrySet()) {
			TaskState state = entry.getValue();
			if (!state.isFinished()) {
				continue;
			}
			Instant finishedAt = state.finishedAt();
			if (finishedAt == null) {
				continue;
			}
			long ageMs = nowMs - finishedAt.toEpochMilli();
			if (ageMs < retentionMs) {
				continue;
			}
			if (tasks.remove(entry.getKey(), state)) {
				removed++;
			}
		}
		if (removed > 0) {
			log.info("price.tick.backfill.task.cleanup removed={} retained={}", removed, tasks.size());
		}
	}

	private void runTask(TaskState state) {
		state.markRunning();
		try {
			OkxPriceTickBackfillService.BackfillResult result = okxPriceTickBackfillService.backfill(
				state.instId,
				state.fromTs,
				state.toTs,
				state.bar,
				state.pageLimit,
				state.maxRounds,
				state.sleepMs
			);
			state.markSucceeded(result);
		} catch (Exception ex) {
			state.markFailed(ex);
		}
	}

	private String buildTaskId() {
		long seq = sequence.incrementAndGet();
		return "okx-" + System.currentTimeMillis() + "-" + seq;
	}

	private String normalizeInstId(String instId) {
		if (!StringUtils.hasText(instId)) {
			return null;
		}
		return instId.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeStatus(String status) {
		if (!StringUtils.hasText(status)) {
			return null;
		}
		return status.trim().toUpperCase(Locale.ROOT);
	}

	private boolean withinRange(Instant value, Instant fromAt, Instant toAt) {
		if (value == null) {
			return false;
		}
		if (fromAt != null && value.isBefore(fromAt)) {
			return false;
		}
		if (toAt != null && value.isAfter(toAt)) {
			return false;
		}
		return true;
	}

	private String normalizeError(String error) {
		if (!StringUtils.hasText(error)) {
			return "unknown";
		}
		String normalized = error.trim();
		if (normalized.length() > 200) {
			return normalized.substring(0, 200);
		}
		return normalized;
	}

	public record TaskAccepted(
		String taskId,
		String status,
		String instId,
		long fromTs,
		long toTs,
		String bar,
		int pageLimit,
		int maxRounds,
		long sleepMs,
		Instant submittedAt
	) {
	}

	public record TaskStatus(
		String taskId,
		String status,
		String instId,
		long fromTs,
		long toTs,
		String bar,
		int pageLimit,
		int maxRounds,
		long sleepMs,
		Instant submittedAt,
		Instant startedAt,
		Instant finishedAt,
		String error,
		OkxPriceTickBackfillService.BackfillResult result
	) {
	}

	public record FailureReasonCount(String reason, long count) {
	}

	public record TaskSummary(
		long total,
		long queued,
		long running,
		long succeeded,
		long failed,
		long averageDurationMs,
		double successRate,
		List<FailureReasonCount> failureReasons
	) {
	}

	private static final class TaskState {
		private final String taskId;
		private final String instId;
		private final long fromTs;
		private final long toTs;
		private final String bar;
		private final int pageLimit;
		private final int maxRounds;
		private final long sleepMs;
		private final Instant submittedAt;

		private volatile String status;
		private volatile Instant startedAt;
		private volatile Instant finishedAt;
		private volatile String error;
		private volatile OkxPriceTickBackfillService.BackfillResult result;

		private TaskState(
			String taskId,
			String instId,
			long fromTs,
			long toTs,
			String bar,
			int pageLimit,
			int maxRounds,
			long sleepMs
		) {
			this.taskId = taskId;
			this.instId = instId;
			this.fromTs = fromTs;
			this.toTs = toTs;
			this.bar = bar;
			this.pageLimit = pageLimit;
			this.maxRounds = maxRounds;
			this.sleepMs = sleepMs;
			this.submittedAt = Instant.now();
			this.status = "QUEUED";
		}

		private void markRunning() {
			this.status = "RUNNING";
			this.startedAt = Instant.now();
		}

		private void markSucceeded(OkxPriceTickBackfillService.BackfillResult result) {
			this.result = result;
			this.status = "SUCCEEDED";
			this.finishedAt = Instant.now();
			log.info("price.tick.backfill.task.succeeded taskId={} instId={}", taskId, instId);
		}

		private void markFailed(Exception ex) {
			this.status = "FAILED";
			this.finishedAt = Instant.now();
			this.error = ex == null ? "unknown" : ex.getMessage();
			log.warn("price.tick.backfill.task.failed taskId={} instId={} error={}", taskId, instId, this.error);
		}

		private TaskAccepted toAccepted() {
			return new TaskAccepted(taskId, status, instId, fromTs, toTs, bar, pageLimit, maxRounds, sleepMs, submittedAt);
		}

		private TaskStatus toStatus() {
			return new TaskStatus(
				taskId,
				status,
				instId,
				fromTs,
				toTs,
				bar,
				pageLimit,
				maxRounds,
				sleepMs,
				submittedAt,
				startedAt,
				finishedAt,
				error,
				result
			);
		}

		private boolean isFinished() {
			return "SUCCEEDED".equals(status) || "FAILED".equals(status);
		}

		private Instant finishedAt() {
			return finishedAt;
		}

		private Instant submittedAt() {
			return submittedAt;
		}

		private String status() {
			return status;
		}

		private String instId() {
			return instId;
		}

		private Long durationMs() {
			if (startedAt == null || finishedAt == null) {
				return null;
			}
			return Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());
		}

		private String error() {
			return error;
		}
	}
}
