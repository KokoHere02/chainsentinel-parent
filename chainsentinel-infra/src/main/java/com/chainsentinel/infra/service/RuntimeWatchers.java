package com.chainsentinel.infra.service;

import java.util.List;
import java.util.Set;

record RuntimeWatchers(
	List<String> watchAddressTopics,
	Set<String> watchAddressSet
) {
}

