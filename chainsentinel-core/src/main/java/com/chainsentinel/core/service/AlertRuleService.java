package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;

public interface AlertRuleService {

    AlertRuleView create(AlertRuleCreateCommand command);
}
