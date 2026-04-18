package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceTickPersistenceService {

	private final PriceTickRepository priceTickRepository;

	public PriceTickPersistenceService(PriceTickRepository priceTickRepository) {
		this.priceTickRepository = priceTickRepository;
	}

	@Transactional
	public int saveIgnoreDuplicate(List<PriceTickEntity> entities) {
		if (entities == null || entities.isEmpty()) {
			return 0;
		}
		int inserted = 0;
		for (PriceTickEntity entity : entities) {
			try {
				priceTickRepository.save(entity);
				inserted++;
			} catch (DataIntegrityViolationException ignore) {
				// duplicate quoteTs for same provider+inst
			}
		}
		return inserted;
	}
}
