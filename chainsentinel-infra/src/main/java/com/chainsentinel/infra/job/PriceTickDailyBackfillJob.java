package com.chainsentinel.infra.job;

import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.infra.service.PriceTickBackfillDispatchService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PriceTickDailyBackfillJob {

	private static final Logger log = LoggerFactory.getLogger(PriceTickDailyBackfillJob.class);
	private static final String PROVIDER_OKX = "okx";

	private final PricePullTargetRepository pricePullTargetRepository;
	private final PriceTickBackfillDispatchService backfillDispatchService;

	public PriceTickDailyBackfillJob(
		PricePullTargetRepository pricePullTargetRepository,
		PriceTickBackfillDispatchService backfillDispatchService
	) {
		this.pricePullTargetRepository = pricePullTargetRepository;
		this.backfillDispatchService = backfillDispatchService;
	}

	@Scheduled(cron = "${chainsentinel.price.tick-backfill.daily-cron:0 0 3 * * *}", zone = "Asia/Shanghai")
	public void runDaily() {
		List<PricePullTargetEntity> targets = pricePullTargetRepository.findEnabledByProviderName(PROVIDER_OKX);
		if (targets.isEmpty()) {
			log.info("price.tick.backfill.daily.skip reason=no_enabled_targets provider={}", PROVIDER_OKX);
			return;
		}
		Set<String> uniqueInstIds = new LinkedHashSet<>();
		for (PricePullTargetEntity target : targets) {
			if (target == null || !StringUtils.hasText(target.getInstId())) {
				continue;
			}
			uniqueInstIds.add(target.getInstId().trim().toUpperCase(Locale.ROOT));
		}
		for (String instId : uniqueInstIds) {
			backfillDispatchService.submitLast30Days(instId, "daily");
		}
		log.info("price.tick.backfill.daily.submitted provider={} count={} instIds={}", PROVIDER_OKX, uniqueInstIds.size(), uniqueInstIds);
	}
}