package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.TradeOrderCancelView;
import com.chainsentinel.core.service.dto.TradeOrderCreateCommand;
import com.chainsentinel.core.service.dto.TradeFillView;
import com.chainsentinel.core.service.dto.TradeOrderQuery;
import com.chainsentinel.core.service.dto.TradeOrderView;
import java.util.List;

public interface TradeOrderService {

	TradeOrderView create(TradeOrderCreateCommand command, Long operatorUserId);

	TradeOrderCancelView cancel(Long orderId, Long operatorUserId);

	TradeOrderView get(Long orderId);

	List<TradeOrderView> list(TradeOrderQuery query);

	TradeOrderView refresh(Long orderId, Long operatorUserId);

	List<TradeFillView> listFills(Long orderId);
}
