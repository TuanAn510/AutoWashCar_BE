package com.shinecraft.server.report;

import java.util.List;

public final class ProjectReportDtos {
    private ProjectReportDtos() {}

    public record ProjectReportResponse(
            String projectName,
            String scope,
            List<String> roles,
            List<ReportSection> sections,
            List<LinkItem> links) {}

    public record ReportSection(String title, List<String> points) {}

    public record LinkItem(String label, String url) {}
}
