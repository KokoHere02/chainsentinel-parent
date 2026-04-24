package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.MonitorAddressTreeView;
import java.util.List;

public interface MonitorTreeQueryService {

	List<MonitorAddressTreeView> tree(Boolean enabledOnly, int limit);
}

