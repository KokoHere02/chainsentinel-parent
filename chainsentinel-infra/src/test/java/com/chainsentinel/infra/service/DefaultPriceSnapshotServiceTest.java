package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.PriceSnapshotUpsertCommand;
import com.chainsentinel.core.service.dto.PriceSnapshotView;
import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultPriceSnapshotServiceTest {

  @Mock
  private AssetPriceSnapshotRepository assetPriceSnapshotRepository;

  @Test
  void shouldUpsertSnapshot() {
    when(assetPriceSnapshotRepository.findByAssetIdAndProviderNameAndInstTypeAndInstIdAndBucketTs(
      1L, "okx", "SPOT", "BTC-USDT", LocalDateTime.of(2026, 4, 3, 12, 0)
    )).thenReturn(Optional.empty());

    when(assetPriceSnapshotRepository.save(any(AssetPriceSnapshotEntity.class))).thenAnswer(invocation -> {
      AssetPriceSnapshotEntity entity = invocation.getArgument(0);
      ReflectionTestUtils.setField(entity, "id", 10L);
      return entity;
    });

    DefaultPriceSnapshotService service = new DefaultPriceSnapshotService(assetPriceSnapshotRepository);
    PriceSnapshotView view = service.upsertMinuteSnapshot(new PriceSnapshotUpsertCommand(
      1L,
      "okx",
      "SPOT",
      "BTC-USDT",
      "USDT",
      new BigDecimal("70000.12"),
      LocalDateTime.of(2026, 4, 3, 12, 0),
      LocalDateTime.of(2026, 4, 3, 12, 0, 5)
    ));

    assertEquals(10L, view.id());
    assertEquals("BTC-USDT", view.instId());
    assertEquals(new BigDecimal("70000.12"), view.price());
  }

  @Test
  void shouldFindLatestByAssetId() {
    AssetPriceSnapshotEntity entity = new AssetPriceSnapshotEntity();
    ReflectionTestUtils.setField(entity, "id", 20L);
    entity.setAssetId(2L);
    entity.setProviderName("okx");
    entity.setInstType("SPOT");
    entity.setInstId("ETH-USDT");
    entity.setQuoteSymbol("USDT");
    entity.setPrice(new BigDecimal("3500"));
    entity.setBucketTs(LocalDateTime.of(2026, 4, 3, 12, 1));

    when(assetPriceSnapshotRepository.findTopByAssetIdOrderByBucketTsDesc(2L)).thenReturn(Optional.of(entity));

    DefaultPriceSnapshotService service = new DefaultPriceSnapshotService(assetPriceSnapshotRepository);
    Optional<PriceSnapshotView> result = service.findLatestByAssetId(2L);

    assertTrue(result.isPresent());
    assertEquals("ETH-USDT", result.get().instId());
  }
}