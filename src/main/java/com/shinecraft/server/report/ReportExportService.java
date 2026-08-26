package com.shinecraft.server.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {
    private final ReportService reportService;

    public ReportExportService(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Exports the advanced analytics sections and current operational alerts as XLSX. */
    public byte[] excel(ReportDtos.ReportRange range) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            writeStaffSheet(workbook, header, reportService.staffPerformance(range).staff());
            writeServiceTimeSheet(workbook, header, reportService.serviceTimes(range));
            writePromotionSheet(workbook, header, reportService.promotionEffectiveness(range));
            writeRetentionSheet(workbook, header, reportService.customerRetention(range));
            writeAlertSheet(workbook, header, reportService.operationalAlerts(LocalDateTime.now()).alerts());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create Excel report", exception);
        }
    }

    /** Exports the advanced analytics sections and current operational alerts as PDF. */
    public byte[] pdf(ReportDtos.ReportRange range) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(20, 55, 90));
        document.add(new Paragraph("ADMIN ANALYTICS REPORT", title));
        document.add(new Paragraph("Generated at: " + LocalDateTime.now()));
        document.add(new Paragraph(" "));

        addSection(document, "Staff performance", new String[] {"Staff", "Assigned", "Completed", "Rate", "Revenue", "Avg min"},
                reportService.staffPerformance(range).staff().stream().map(item -> new String[] {
                        item.staffName(), String.valueOf(item.assignedBookings()), String.valueOf(item.completedBookings()),
                        item.completionRate() + "%", item.attributedRevenue().toPlainString(), String.valueOf(item.averageServiceMinutes())
                }).toList());
        ReportDtos.ServiceTimeReport times = reportService.serviceTimes(range);
        addSection(document, "Service time", new String[] {"Metric", "Value"}, List.of(
                new String[] {"Average waiting minutes", String.valueOf(times.averageWaitingMinutes())},
                new String[] {"Average service minutes", String.valueOf(times.averageServiceMinutes())},
                new String[] {"On-time rate", times.onTimeRate() + "%"}));
        addSection(document, "Promotion effectiveness", new String[] {"Code", "Usage", "Customers", "Discount", "Revenue", "AOV"},
                reportService.promotionEffectiveness(range).promotions().stream().map(item -> new String[] {
                        item.code(), String.valueOf(item.usageCount()), String.valueOf(item.uniqueCustomers()),
                        item.totalDiscount().toPlainString(), item.revenue().toPlainString(), item.averageOrderValue().toPlainString()
                }).toList());
        ReportDtos.CustomerRetentionReport retention = reportService.customerRetention(range);
        addSection(document, "Customer retention", new String[] {"Segment", "Customers"},
                retention.segments().stream().map(item -> new String[] {item.segment(), String.valueOf(item.customers())}).toList());
        addSection(document, "Operational alerts", new String[] {"Severity", "Type", "Booking", "Message"},
                reportService.operationalAlerts(LocalDateTime.now()).alerts().stream().map(item -> new String[] {
                        item.severity(), item.type(), item.bookingId(), item.message()
                }).toList());
        document.close();
        return output.toByteArray();
    }

    private void writeStaffSheet(XSSFWorkbook workbook, CellStyle header, List<ReportDtos.StaffPerformanceItem> items) {
        Sheet sheet = sheet(workbook, "Staff performance", header,
                "Staff ID", "Staff name", "Assigned", "Completed", "Cancelled", "Active", "Completion rate", "Revenue", "Average minutes");
        int rowIndex = 1;
        for (ReportDtos.StaffPerformanceItem item : items) {
            Row row = sheet.createRow(rowIndex++);
            values(row, item.staffId(), item.staffName(), item.assignedBookings(), item.completedBookings(),
                    item.cancelledBookings(), item.activeBookings(), item.completionRate() / 100d,
                    item.attributedRevenue().doubleValue(), item.averageServiceMinutes());
        }
        autosize(sheet, 9);
    }

    private void writeServiceTimeSheet(XSSFWorkbook workbook, CellStyle header, ReportDtos.ServiceTimeReport report) {
        Sheet sheet = sheet(workbook, "Service time", header,
                "Period", "Bookings", "Average waiting minutes", "Average service minutes");
        int rowIndex = 1;
        for (ReportDtos.ServiceTimePeriodItem item : report.groupedByMonth()) {
            values(sheet.createRow(rowIndex++), item.period(), item.bookings(), item.averageWaitingMinutes(), item.averageServiceMinutes());
        }
        autosize(sheet, 4);
    }

    private void writePromotionSheet(XSSFWorkbook workbook, CellStyle header, ReportDtos.PromotionEffectivenessReport report) {
        Sheet sheet = sheet(workbook, "Promotions", header,
                "Code", "Title", "Usage", "Unique customers", "Total discount", "Revenue", "Average order");
        int rowIndex = 1;
        for (ReportDtos.PromotionEffectivenessItem item : report.promotions()) {
            values(sheet.createRow(rowIndex++), item.code(), item.title(), item.usageCount(), item.uniqueCustomers(),
                    item.totalDiscount().doubleValue(), item.revenue().doubleValue(), item.averageOrderValue().doubleValue());
        }
        autosize(sheet, 7);
    }

    private void writeRetentionSheet(XSSFWorkbook workbook, CellStyle header, ReportDtos.CustomerRetentionReport report) {
        Sheet sheet = sheet(workbook, "Customer retention", header, "Segment", "Customers");
        int rowIndex = 1;
        for (ReportDtos.CustomerSegmentItem item : report.segments()) {
            values(sheet.createRow(rowIndex++), item.segment(), item.customers());
        }
        autosize(sheet, 2);
    }

    private void writeAlertSheet(XSSFWorkbook workbook, CellStyle header, List<ReportDtos.OperationalAlertItem> alerts) {
        Sheet sheet = sheet(workbook, "Operational alerts", header, "Severity", "Type", "Booking ID", "Message", "Occurred at");
        int rowIndex = 1;
        for (ReportDtos.OperationalAlertItem item : alerts) {
            values(sheet.createRow(rowIndex++), item.severity(), item.type(), item.bookingId(), item.message(), item.occurredAt());
        }
        autosize(sheet, 5);
    }

    private Sheet sheet(XSSFWorkbook workbook, String name, CellStyle headerStyle, String... headers) {
        Sheet sheet = workbook.createSheet(name);
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            row.createCell(index).setCellValue(headers[index]);
            row.getCell(index).setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private void values(Row row, Object... values) {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) row.createCell(index).setCellValue(number.doubleValue());
            else row.createCell(index).setCellValue(value == null ? "" : String.valueOf(value));
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int index = 0; index < columns; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 512, 16000));
        }
    }

    private void addSection(Document document, String title, String[] headers, List<String[]> rows) {
        document.add(new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            cell.setBackgroundColor(new Color(20, 55, 90));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        for (String[] row : rows) for (String value : row) table.addCell(new Phrase(value == null ? "" : value, FontFactory.getFont(FontFactory.HELVETICA, 8)));
        document.add(table);
        document.add(new Paragraph(" "));
    }
}
