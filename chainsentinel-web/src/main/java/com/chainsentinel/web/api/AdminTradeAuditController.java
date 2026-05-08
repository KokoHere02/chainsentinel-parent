package com.chainsentinel.web.api;

import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import com.chainsentinel.web.auth.AdminTradeAuditService;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.RequireRoles;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/trade-audit")
@Validated
@RequireRoles(AuthRole.ADMIN)
public class AdminTradeAuditController {

	private final AdminTradeAuditService adminTradeAuditService;

	public AdminTradeAuditController(AdminTradeAuditService adminTradeAuditService) {
		this.adminTradeAuditService = adminTradeAuditService;
	}

	@GetMapping("/order-create")
	@RateLimit(name = "admin.tradeAudit.orderCreate", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many trade audit requests")
	public List<AdminTradeAuditService.OrderCreateAuditView> orderCreateAudit(
		@RequestParam(name = "result", required = false) String result,
		@RequestParam(name = "username", required = false) String username,
		@RequestParam(name = "rejectCode", required = false) String rejectCode,
		@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
		@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
		@RequestParam(name = "page", defaultValue = "0") int page,
		@RequestParam(name = "size", defaultValue = "100") int size
	) {
		return adminTradeAuditService.listOrderCreateAudits(result, username, rejectCode, from, to, page, size);
	}

	@GetMapping("/order-create/summary")
	@RateLimit(name = "admin.tradeAudit.orderCreateSummary", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many trade audit summary requests")
	public AdminTradeAuditService.OrderCreateAuditSummaryView orderCreateAuditSummary(
		@RequestParam(name = "username", required = false) String username,
		@RequestParam(name = "rejectCode", required = false) String rejectCode,
		@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
		@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
		@RequestParam(name = "top", defaultValue = "10") int top
	) {
		return adminTradeAuditService.summarizeOrderCreateAudits(username, rejectCode, from, to, top);
	}

	@GetMapping("/order-create/trend")
	@RateLimit(name = "admin.tradeAudit.orderCreateTrend", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many trade audit trend requests")
	public List<AdminTradeAuditService.OrderCreateTrendPointView> orderCreateAuditTrend(
		@RequestParam(name = "username", required = false) String username,
		@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
		@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
		@RequestParam(name = "bucketSec", defaultValue = "3600") long bucketSec
	) {
		return adminTradeAuditService.trendOrderCreateAudits(username, from, to, bucketSec);
	}
}
