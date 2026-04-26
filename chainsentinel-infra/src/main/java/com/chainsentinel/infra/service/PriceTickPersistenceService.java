package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.PriceTickEntity;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceTickPersistenceService {

	private static final String INSERT_IGNORE_SQL = """
		insert ignore into price_tick
			(provider_name, inst_type, inst_id, base_symbol, quote_symbol, price, quote_ts)
		values
			(:providerName, :instType, :instId, :baseSymbol, :quoteSymbol, :price, :quoteTs)
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public PriceTickPersistenceService(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public int saveIgnoreDuplicate(List<PriceTickEntity> entities) {
		if (entities == null || entities.isEmpty()) {
			return 0;
		}
		MapSqlParameterSource[] params = entities.stream()
			.map(entity -> new MapSqlParameterSource()
				.addValue("providerName", entity.getProviderName())
				.addValue("instType", entity.getInstType())
				.addValue("instId", entity.getInstId())
				.addValue("baseSymbol", entity.getBaseSymbol())
				.addValue("quoteSymbol", entity.getQuoteSymbol())
				.addValue("price", entity.getPrice())
				.addValue("quoteTs", entity.getQuoteTs()))
			.toArray(MapSqlParameterSource[]::new);
		int[] affected = jdbcTemplate.batchUpdate(INSERT_IGNORE_SQL, params);
		int inserted = 0;
		for (int row : affected) {
			if (row > 0) {
				inserted += row;
			}
		}
		return inserted;
	}
}
