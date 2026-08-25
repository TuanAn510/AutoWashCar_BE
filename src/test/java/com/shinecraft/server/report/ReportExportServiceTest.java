package com.shinecraft.server.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lowagie.text.pdf.PdfReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ReportExportServiceTest {
    @Test
    void exportsValidExcelAndPdfDocuments() throws Exception {
        ReportService reports = mock(ReportService.class);
        when(reports.staffPerformance(any())).thenReturn(new ReportDtos.StaffPerformanceReport(List.of(
                new ReportDtos.StaffPerformanceItem("1", "Staff A", 2, 2, 0, 0, 100, new BigDecimal("190000"), 45))));
        when(reports.serviceTimes(any())).thenReturn(new ReportDtos.ServiceTimeReport(2, 2, 15, 45, 1, 50, List.of()));
        when(reports.promotionEffectiveness(any())).thenReturn(new ReportDtos.PromotionEffectivenessReport(
                1, 1, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, List.of()));
        when(reports.customerRetention(any())).thenReturn(new ReportDtos.CustomerRetentionReport(
                1, 0, 1, 0, 0, 0, 100, List.of(new ReportDtos.CustomerSegmentItem("returning", 1)), List.of()));
        when(reports.operationalAlerts(any())).thenReturn(new ReportDtos.OperationalAlertReport(0, Map.of(), List.of()));
        ReportExportService exporter = new ReportExportService(reports);
        ReportDtos.ReportRange range = new ReportDtos.ReportRange(null, null);

        byte[] excel = exporter.excel(range);
        byte[] pdf = exporter.pdf(range);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(excel))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
            assertThat(workbook.getSheet("Staff performance").getRow(1).getCell(1).getStringCellValue()).isEqualTo("Staff A");
        }
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        assertThat(pdf).startsWith(new byte[] {'%', 'P', 'D', 'F'});
        reader.close();
    }
}
