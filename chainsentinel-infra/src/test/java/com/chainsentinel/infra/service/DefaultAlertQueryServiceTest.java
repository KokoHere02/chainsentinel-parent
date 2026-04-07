package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.AlertQuery;
import com.chainsentinel.core.service.dto.AlertView;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultAlertQueryServiceTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Test
    void shouldMapAlertEntityPageToViewPage() {
        DefaultAlertQueryService service = new DefaultAlertQueryService(alertEventRepository);

        AlertEventEntity entity = new AlertEventEntity();
        ReflectionTestUtils.setField(entity, "id", 11L);
        entity.setRuleId(21L);
        entity.setAssetEventId(31L);
        entity.setSeverity("HIGH");
        entity.setSendStatus("SENT");
        entity.setRetryCount(1);
        entity.setLastError(null);
        Instant sentAt = Instant.parse("2026-03-28T11:00:00Z");
        entity.setSentAt(sentAt);

        Pageable pageable = PageRequest.of(0, 10);
        Page<AlertEventEntity> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(alertEventRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        AlertQuery query = new AlertQuery(
            "SENT",
            "HIGH",
            21L,
            Instant.parse("2026-03-28T00:00:00Z"),
            Instant.parse("2026-03-29T00:00:00Z")
        );
        Page<AlertView> result = service.query(query, pageable);

        assertEquals(1, result.getTotalElements());
        AlertView view = result.getContent().get(0);
        assertEquals(11L, view.id());
        assertEquals(21L, view.ruleId());
        assertEquals(31L, view.assetEventId());
        assertEquals("HIGH", view.severity());
        assertEquals("SENT", view.sendStatus());
        assertEquals(1, view.retryCount());
        assertEquals(null, view.lastError());
        assertEquals(sentAt, view.sentAt());

        verify(alertEventRepository).findAll(any(Specification.class), eq(pageable));
    }
}
