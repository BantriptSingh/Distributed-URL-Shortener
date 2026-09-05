package com.shorty.analytics;

import com.shorty.analytics.AnalyticsQueryService.DashboardSummary;
import com.shorty.auth.SecuritySupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsSummaryController {

    private final AnalyticsQueryService analytics;

    public AnalyticsSummaryController(AnalyticsQueryService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return analytics.summary(SecuritySupport.requireUser().userId());
    }
}
