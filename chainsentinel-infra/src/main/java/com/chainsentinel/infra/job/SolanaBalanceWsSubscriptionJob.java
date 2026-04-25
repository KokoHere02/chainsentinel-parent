package com.chainsentinel.infra.job;

import com.chainsentinel.infra.config.HoldingSnapshotProperties;
import com.chainsentinel.infra.service.SolanaBalanceWsSubscriptionService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chainsentinel.holding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SolanaBalanceWsSubscriptionJob {

	private static final Logger log = LoggerFactory.getLogger(SolanaBalanceWsSubscriptionJob.class);

	private final SolanaBalanceWsSubscriptionService subscriptionService;
	private final HoldingSnapshotProperties properties;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public SolanaBalanceWsSubscriptionJob(
		SolanaBalanceWsSubscriptionService subscriptionService,
		HoldingSnapshotProperties properties
	) {
		this.subscriptionService = subscriptionService;
		this.properties = properties;
	}

	@Scheduled(
		fixedDelayString = "${chainsentinel.holding.sol-ws-refresh-interval-ms:10000}",
		initialDelayString = "${chainsentinel.holding.sol-ws-initial-delay-ms:5000}"
	)
	public void run() {
		execute("scheduled");
	}

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		execute("startup");
	}

	private void execute(String trigger) {
		if (!properties.isSolWsEnabled()) {
			return;
		}
		if (!running.compareAndSet(false, true)) {
			log.debug("sol.ws.balance.refresh.skip trigger={} reason=previous_run_in_progress", trigger);
			return;
		}
		try {
			subscriptionService.refreshSubscriptions();
		} catch (Exception ex) {
			log.warn("sol.ws.balance.refresh.failed trigger={} error={}", trigger, ex.getMessage());
		} finally {
			running.set(false);
		}
	}
}
