package com.chainsentinel.price.api;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.util.Optional;

public interface PriceService {

    Optional<PriceQuote> getQuote(PriceQuery query);
}
