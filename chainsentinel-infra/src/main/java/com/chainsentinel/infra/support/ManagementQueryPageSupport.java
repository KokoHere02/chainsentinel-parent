package com.chainsentinel.infra.support;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class ManagementQueryPageSupport {

	public static final int MAX_PAGE_SIZE = 100;

	private ManagementQueryPageSupport() {
	}

	public static int normalizePage(int page) {
		return Math.max(0, page);
	}

	public static int normalizePageSize(int size) {
		return Math.max(1, Math.min(MAX_PAGE_SIZE, size));
	}

	public static PageRequest pageByIdDesc(int page, int size) {
		return PageRequest.of(
			normalizePage(page),
			normalizePageSize(size),
			Sort.by(Sort.Direction.DESC, "id")
		);
	}
}
