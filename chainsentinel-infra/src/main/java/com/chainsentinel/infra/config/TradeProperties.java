package com.chainsentinel.infra.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.trade")
public class TradeProperties {

	private boolean enabled = false;
	private boolean sandboxOnly = true;
	private BigDecimal maxOrderQuantity;
	private BigDecimal maxOrderNotional;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isSandboxOnly() {
		return sandboxOnly;
	}

	public void setSandboxOnly(boolean sandboxOnly) {
		this.sandboxOnly = sandboxOnly;
	}

	public BigDecimal getMaxOrderQuantity() {
		return maxOrderQuantity;
	}

	public void setMaxOrderQuantity(BigDecimal maxOrderQuantity) {
		this.maxOrderQuantity = maxOrderQuantity;
	}

	public BigDecimal getMaxOrderNotional() {
		return maxOrderNotional;
	}

	public void setMaxOrderNotional(BigDecimal maxOrderNotional) {
		this.maxOrderNotional = maxOrderNotional;
	}
}
