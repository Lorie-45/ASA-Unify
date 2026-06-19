package com.asa.asaunify.services;


import com.asa.asaunify.dtos.RequestResponseDto;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    // ─── Excel Export ─────────────────────────────────────────

    public byte[] exportRequestsToExcel(
            List<RequestResponseDto> requests) throws IOException {

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Requests");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(
                    IndexedColors.GREEN.getIndex()
            );
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Use fully qualified POI Font to avoid conflict with iText Font
            org.apache.poi.ss.usermodel.Font headerFont =
                    workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            // Header row
            Row header = sheet.createRow(0);
            String[] columns = {
                    "Reference", "Type", "Title", "Status",
                    "Initiator", "Department", "Created At",
                    "Due Date", "Rejected By", "Rejection Reason"
            };

            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Data rows
            int rowNum = 1;
            for (RequestResponseDto r : requests) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(
                        r.getCaseId());
                row.createCell(1).setCellValue(
                        r.getType().name());
                row.createCell(2).setCellValue(
                        r.getTitle());
                row.createCell(3).setCellValue(
                        r.getStatus().name());
                row.createCell(4).setCellValue(
                        r.getInitiatorName());
                row.createCell(5).setCellValue(
                        r.getDepartmentName());
                row.createCell(6).setCellValue(
                        r.getCreatedAt() != null
                                ? r.getCreatedAt().toString() : "");
                row.createCell(7).setCellValue(
                        r.getDueDate() != null
                                ? r.getDueDate().toString() : "");
                row.createCell(8).setCellValue(
                        r.getRejectedByName() != null
                                ? r.getRejectedByName() : "");
                row.createCell(9).setCellValue(
                        r.getRejectionReason() != null
                                ? r.getRejectionReason() : "");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ─── CSV Export ───────────────────────────────────────────

    public byte[] exportRequestsToCsv(
            List<RequestResponseDto> requests) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(out))) {

            // Header
            writer.writeNext(new String[]{
                    "Reference", "Type", "Title", "Status",
                    "Initiator", "Department", "Created At",
                    "Due Date", "Rejected By", "Rejection Reason"
            });

            // Data rows
            for (RequestResponseDto r : requests) {
                writer.writeNext(new String[]{
                        r.getCaseId(),
                        r.getType().name(),
                        r.getTitle(),
                        r.getStatus().name(),
                        r.getInitiatorName(),
                        r.getDepartmentName(),
                        r.getCreatedAt() != null
                                ? r.getCreatedAt().toString() : "",
                        r.getDueDate() != null
                                ? r.getDueDate().toString() : "",
                        r.getRejectedByName() != null
                                ? r.getRejectedByName() : "",
                        r.getRejectionReason() != null
                                ? r.getRejectionReason() : ""
                });
            }
        }

        return out.toByteArray();
    }

    // ─── PDF Export ───────────────────────────────────────────

    public byte[] exportRequestsToPdf(
            List<RequestResponseDto> requests,
            String reportTitle) throws DocumentException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, out);
        document.open();

        // Title — fully qualified iText Font
        com.itextpdf.text.Font titleFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 16, BaseColor.DARK_GRAY
        );
        Paragraph title = new Paragraph(reportTitle, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Generated date
        com.itextpdf.text.Font subFont = FontFactory.getFont(
                FontFactory.HELVETICA, 10, BaseColor.GRAY
        );
        document.add(new Paragraph(
                "Generated: " + java.time.LocalDateTime.now(), subFont
        ));
        document.add(Chunk.NEWLINE);

        // Table — 6 columns
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 2f, 4f, 2f, 2f, 3f});

        // Table header row
        String[] headers = {
                "Reference", "Type", "Title",
                "Status", "Initiator", "Department"
        };

        com.itextpdf.text.Font headerFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 10, BaseColor.WHITE
        );

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new BaseColor(76, 153, 0));
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        // Table data rows
        com.itextpdf.text.Font cellFont = FontFactory.getFont(
                FontFactory.HELVETICA, 9, BaseColor.BLACK
        );

        boolean alternate = false;
        for (RequestResponseDto r : requests) {
            BaseColor rowColor = alternate
                    ? new BaseColor(240, 240, 240)
                    : BaseColor.WHITE;

            String[] values = {
                    r.getCaseId(),
                    r.getType().name(),
                    r.getTitle(),
                    r.getStatus().name(),
                    r.getInitiatorName(),
                    r.getDepartmentName()
            };

            for (String v : values) {
                PdfPCell cell = new PdfPCell(
                        new Phrase(v != null ? v : "", cellFont)
                );
                cell.setBackgroundColor(rowColor);
                cell.setPadding(5);
                table.addCell(cell);
            }

            alternate = !alternate;
        }

        document.add(table);
        document.close();

        return out.toByteArray();
    }
}