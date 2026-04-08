package com.chainsentinel.infra.service;

import java.util.ArrayList;
import java.util.List;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.service.EventQueryService;
import com.chainsentinel.core.service.dto.EventQuery;
import com.chainsentinel.core.service.dto.EventView;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultEventQueryService implements EventQueryService {

	private final AssetEventRepository assetEventRepository;

	public DefaultEventQueryService(AssetEventRepository assetEventRepository) {
		this.assetEventRepository = assetEventRepository;
	}

	@Override
	public Page<EventView> query(EventQuery query, Pageable pageable) {
		Specification<AssetEventEntity> spec = (root, criteriaQuery, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (StringUtils.hasText(query.chain())) {
				predicates.add(cb.equal(root.get("chain"), query.chain()));
			}
			if (StringUtils.hasText(query.address())) {
				Predicate from = cb.equal(root.get("fromAddress"), query.address());
				Predicate to = cb.equal(root.get("toAddress"), query.address());
				predicates.add(cb.or(from, to));
			}
			EventStatus status = query.status();
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (query.startTime() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.startTime()));
			}
			if (query.endTime() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), query.endTime()));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};

		return assetEventRepository.findAll(spec, pageable).map(this::toView);
	}

	private EventView toView(AssetEventEntity entity) {
		return new EventView(
			entity.getId(),
			entity.getChain(),
			entity.getNetwork(),
			entity.getBlockNumber(),
			entity.getTxHash(),
			entity.getLogIndex(),
			entity.getFromAddress(),
			entity.getToAddress(),
			entity.getTokenType(),
			entity.getSymbol(),
			entity.getAmount(),
			entity.getStatus(),
			entity.getConfirmations(),
			entity.getOccurredAt()
		);
	}

}
