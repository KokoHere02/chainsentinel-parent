package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.EventQuery;
import com.chainsentinel.core.service.dto.EventView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventQueryService {

    Page<EventView> query(EventQuery query, Pageable pageable);
}

