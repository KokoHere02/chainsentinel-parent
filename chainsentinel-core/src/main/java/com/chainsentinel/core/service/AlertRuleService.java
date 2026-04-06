package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleQueryCommand;
import com.chainsentinel.core.service.dto.AlertRuleUpdateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import java.util.List;

public interface AlertRuleService {

	AlertRuleView create(AlertRuleCreateCommand command);

	AlertRuleView update(AlertRuleUpdateCommand command);

	List<AlertRuleView> list(AlertRuleQueryCommand command);

	AlertRuleView delete(Long id);

	AlertRuleView getById(Long id);

	AlertRuleView setEnabled(Long id, boolean enabled);
}
