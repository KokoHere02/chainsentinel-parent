package com.chainsentinel.web.api;

import com.chainsentinel.core.service.TradeOrderService;
import com.chainsentinel.core.service.dto.TradeFillView;
import com.chainsentinel.core.service.dto.TradeOrderCancelView;
import com.chainsentinel.core.service.dto.TradeOrderCreateCommand;
import com.chainsentinel.core.service.dto.TradeOrderQuery;
import com.chainsentinel.core.service.dto.TradeOrderView;
import com.chainsentinel.web.auth.AuthContext;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.RequireRoles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

	private final TradeOrderService tradeOrderService;

	public OrderController(TradeOrderService tradeOrderService) {
		this.tradeOrderService = tradeOrderService;
	}

	@PostMapping
	@RequireRoles({ AuthRole.ADMIN, AuthRole.TRADER })
	public TradeOrderView create(@RequestBody @Valid OrderCreateRequest request) {
		return tradeOrderService.create(toCreateCommand(request), currentUserId());
	}

	@PostMapping("/{id}/cancel")
	@RequireRoles({ AuthRole.ADMIN, AuthRole.TRADER })
	public TradeOrderCancelView cancel(@PathVariable("id") Long id) {
		try {
			return tradeOrderService.cancel(id, currentUserId());
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping("/{id}")
	@RequireRoles({ AuthRole.ADMIN, AuthRole.TRADER })
	public TradeOrderView get(@PathVariable("id") Long id) {
		try {
			return tradeOrderService.get(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping
	@RequireRoles({ AuthRole.ADMIN, AuthRole.TRADER })
	public List<TradeOrderView> list(
		@RequestParam(name = "accountId", required = false) Long accountId,
		@RequestParam(name = "status", required = false) String status,
		@RequestParam(name = "symbol", required = false) String symbol,
		@RequestParam(name = "limit", defaultValue = "100") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return tradeOrderService.list(new TradeOrderQuery(accountId, status, symbol, limit));
	}

	@PostMapping("/{id}/refresh")
	@RequireRoles({ AuthRole.ADMIN, AuthRole.TRADER })
	public TradeOrderView refresh(@PathVariable("id") Long id) {
		try {
			return tradeOrderService.refresh(id, currentUserId());
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping("/{id}/fills")
	@RequireRoles({ AuthRole.ADMIN, AuthRole.TRADER })
	public List<TradeFillView> fills(@PathVariable("id") Long id) {
		try {
			return tradeOrderService.listFills(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	private TradeOrderCreateCommand toCreateCommand(OrderCreateRequest request) {
		return new TradeOrderCreateCommand(
			request.accountId(),
			request.symbol(),
			request.side(),
			request.orderType(),
			request.price(),
			request.quantity(),
			request.quoteAmount(),
			request.clientOrderId()
		);
	}

	private Long currentUserId() {
		AuthPrincipal principal = AuthContext.get();
		return principal == null ? null : principal.userId();
	}

	public record OrderCreateRequest(
		@NotNull Long accountId,
		@NotBlank String symbol,
		@NotBlank String side,
		@NotBlank String orderType,
		BigDecimal price,
		@NotNull @Positive BigDecimal quantity,
		BigDecimal quoteAmount,
		String clientOrderId
	) {
	}
}
