package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.AlertQueryService;
import com.chainsentinel.core.service.dto.AlertQuery;
import com.chainsentinel.core.service.dto.AlertView;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultAlertQueryService implements AlertQueryService {

private final AlertEventRepository alertEventRepository;

public DefaultAlertQueryService(AlertEventRepository alertEventRepository) {
this.alertEventRepository = alertEventRepository;
}

@Override
public Page<AlertView> query(AlertQuery query, Pageable pageable) {
Specification<AlertEventEntity> spec = (root, cq, cb) -> {
List<Predicate> predicates = new ArrayList<>();
if (StringUtils.hasText(query.sendStatus())) {
predicates.add(cb.equal(root.get("sendStatus"), query.sendStatus()));
}
if (StringUtils.hasText(query.severity())) {
predicates.add(cb.equal(root.get("severity"), query.severity()));
}
if (query.ruleId() != null) {
predicates.add(cb.equal(root.get("ruleId"), query.ruleId()));
}
if (query.sentAtFrom() != null) {
predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), query.sentAtFrom()));
}
if (query.sentAtTo() != null) {
predicates.add(cb.lessThanOrEqualTo(root.get("sentAt"), query.sentAtTo()));
}
return cb.and(predicates.toArray(new Predicate[0]));
};

return alertEventRepository.findAll(spec, pageable).map(this::toView);
}

private AlertView toView(AlertEventEntity entity) {
return new AlertView(
entity.getId(),
entity.getRuleId(),
entity.getAssetEventId(),
entity.getSeverity(),
entity.getSendStatus(),
entity.getRetryCount(),
entity.getLastError(),
entity.getSentAt()
);
}
}
