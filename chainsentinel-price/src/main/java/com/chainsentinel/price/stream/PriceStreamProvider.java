package com.chainsentinel.price.stream;

import com.chainsentinel.price.api.dto.PriceQuery;
import java.util.List;

public interface PriceStreamProvider {

	String name();

	boolean enabled();

	boolean supports(PriceQuery query);

	void start(PriceStreamSink sink);

	void subscribe(List<PriceQuery> queries);

	void stop();
}