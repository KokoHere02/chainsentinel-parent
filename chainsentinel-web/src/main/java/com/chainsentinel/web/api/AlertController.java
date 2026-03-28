package com.chainsentinel.web.api;

import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.core.service.AlertQueryService;
import com.chainsentinel.core.service.dto.AlertQuery;
import com.chainsentinel.core.service.dto.AlertView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertQueryService alertQueryService;
    private final AlertDispatchService alertDispatchService;

    public AlertController(AlertQueryService alertQueryService, AlertDispatchService alertDispatchService) {
        this.alertQueryService = alertQueryService;
        this.alertDispatchService = alertDispatchService;
    }

    @GetMapping
    public Page<AlertView> list(
            @RequestParam(name = "sendStatus", required = false) String sendStatus,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "ruleId", required = false) Long ruleId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return alertQueryService.query(new AlertQuery(sendStatus, severity, ruleId), pageable);
    }

    @PostMapping("/retry/{id}")
    public RetryResponse retry(@PathVariable("id") Long id) {
        boolean ok = alertDispatchService.retryOne(id);
        return new RetryResponse(ok);
    }

    public record RetryResponse(boolean success) {
    }
}
