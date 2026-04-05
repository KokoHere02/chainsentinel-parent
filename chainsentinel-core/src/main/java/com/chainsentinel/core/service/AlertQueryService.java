package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.AlertQuery;
import com.chainsentinel.core.service.dto.AlertView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AlertQueryService {

	Page<AlertView> query(AlertQuery query, Pageable pageable);
}
