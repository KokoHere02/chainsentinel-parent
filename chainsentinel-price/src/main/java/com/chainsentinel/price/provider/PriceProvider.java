package com.chainsentinel.price.provider;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.util.Optional;

public interface PriceProvider {

    String name();

    boolean supports(PriceQuery query);

    Optional<PriceQuote> getQuote(PriceQuery query);
}
