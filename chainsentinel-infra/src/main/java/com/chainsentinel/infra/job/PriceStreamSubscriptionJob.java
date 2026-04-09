package com.chainsentinel.infra.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.chainsentinel.infra.config.PriceStreamProperties;
import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.stream.PriceStreamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PriceStreamSubscriptionJob {

	private static final Logger log = LoggerFactory.getLogger(PriceStreamSubscriptionJob.class);

	private final PriceStreamManager priceStreamManager;
	private final PricePullTargetRepository pricePullTargetRepository;
	private final PriceStreamProperties priceStreamProperties;

	public PriceStreamSubscriptionJob(
		PriceStreamManager priceStreamManager,
		PricePullTargetRepository pricePullTargetRepository,
		PriceStreamProperties priceStreamProperties
	) {
		this.priceStreamManager = priceStreamManager;
		this.pricePullTargetRepository = pricePullTargetRepository;
		this.priceStreamProperties = priceStreamProperties;
	}

	public void refreshSubscriptions() {
		if (!priceStreamProperties.isEnabled()) {
			return;
		}
		List<PricePullTargetEntity> targets = pricePullTargetRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
		List<PriceQuery> queries = new ArrayList<>();
		for (PricePullTargetEntity target : targets) {
			PriceQuery query = toQuery(target);
			if (query != null) {
				queries.add(query);
			}
		}
		if (queries.isEmpty()) {
			log.info("price.ws.refresh.skip reason=no_targets");
			return;
		}
		priceStreamManager.refreshSubscriptions(queries);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void warmupSubscriptionsOnStartup() {
		try {
			log.info("price.ws.startup.refresh.begin");
			refreshSubscriptions();
			log.info("price.ws.startup.refresh.done");
		} catch (Exception ex) {
			log.warn("price.ws.startup.refresh.failed error={}", ex.getMessage());
		}
	}

	private PriceQuery toQuery(PricePullTargetEntity target) {
		if (target == null || !StringUtils.hasText(target.getInstType()) || !StringUtils.hasText(target.getInstId()) || !StringUtils.hasText(target.getQuoteSymbol())) {
			return null;
		}
		PriceInstType instType;
		try {
			instType = PriceInstType.fromValue(target.getInstType());
		} catch (Exception ex) {
			log.warn("price.ws.refresh.invalid_inst_type targetId={} instType={} error={}",
				target.getId(), target.getInstType(), ex.getMessage());
			return null;
		}
		String quoteSymbol = target.getQuoteSymbol().trim().toUpperCase(Locale.ROOT);
		String baseSymbol = resolveBaseSymbol(target.getInstId(), quoteSymbol);
		if (!StringUtils.hasText(baseSymbol)) {
			return null;
		}
		return new PriceQuery(
			"OFFCHAIN",
			instType,
			baseSymbol,
			quoteSymbol,
			null
		);
	}

	private String resolveBaseSymbol(String instId, String quoteSymbol) {
		if (!StringUtils.hasText(instId)) {
			return null;
		}
		String normalized = instId.trim().toUpperCase(Locale.ROOT);
		String suffix = "-" + quoteSymbol;
		if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
			return normalized.substring(0, normalized.length() - suffix.length());
		}
		int firstDash = normalized.indexOf('-');
		if (firstDash > 0) {
			return normalized.substring(0, firstDash);
		}
		return normalized;
	}

}
