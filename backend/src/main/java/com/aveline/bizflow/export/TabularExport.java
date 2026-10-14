package com.aveline.bizflow.export;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class TabularExport {

    public byte[] write(ExportFormat format, TabularSheet sheet) {
        return switch (format) {
            case CSV -> csv(sheet).getBytes(StandardCharsets.UTF_8);
            case XLSX -> xlsx(sheet);
            case PDF -> pdf(sheet);
        };
    }

    private String csv(TabularSheet sheet) {
        List<String> lines = new ArrayList<>();
        lines.add(csvRow(sheet.headers()));
        for (List<String> row : sheet.rows()) {
            lines.add(csvRow(pad(row, sheet.headers().size())));
        }
        return "\uFEFF" + String.join("\n", lines) + "\n";
    }

    private String csvRow(List<String> cells) {
        List<String> encoded = new ArrayList<>();
        for (String cell : cells) {
            encoded.add(csvCell(cell));
        }
        return String.join(",", encoded);
    }

    private String csvCell(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private byte[] xlsx(TabularSheet sheet) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                entry(zip, "[Content_Types].xml", contentTypes());
                entry(zip, "_rels/.rels", rootRels());
                entry(zip, "xl/workbook.xml", workbook(sheet.title()));
                entry(zip, "xl/_rels/workbook.xml.rels", workbookRels());
                entry(zip, "xl/styles.xml", styles());
                entry(zip, "xl/worksheets/sheet1.xml", worksheet(sheet));
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Excel export failed");
        }
    }

    private byte[] pdf(TabularSheet sheet) {
        List<String> streams = pdfPages(sheet);
        int pageCount = streams.size();
        int[] pageIds = new int[pageCount];
        int[] contentIds = new int[pageCount];
        int next = 4;
        for (int index = 0; index < pageCount; index++) {
            pageIds[index] = next++;
            contentIds[index] = next++;
        }
        int objectCount = 3 + (pageCount * 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        StringBuilder kids = new StringBuilder();
        for (int pageId : pageIds) {
            if (!kids.isEmpty()) {
                kids.append(' ');
            }
            kids.append(pageId).append(" 0 R");
        }
        offsets.add(out.size());
        write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets.add(out.size());
        write(out, "2 0 obj\n<< /Type /Pages /Count " + pageCount + " /Kids [" + kids + "] >>\nendobj\n");
        offsets.add(out.size());
        write(out, "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n");
        for (int index = 0; index < pageCount; index++) {
            byte[] stream = streams.get(index).getBytes(StandardCharsets.US_ASCII);
            offsets.add(out.size());
            write(out, pageIds[index] + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R >> >> /Contents "
                    + contentIds[index] + " 0 R >>\nendobj\n");
            offsets.add(out.size());
            write(out, contentIds[index] + " 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
            out.writeBytes(stream);
            write(out, "endstream\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objectCount + 1) + "\n");
        write(out, "0000000000 65535 f \n");
        for (int index = 1; index <= objectCount; index++) {
            write(out, String.format("%010d 00000 n \n", offsets.get(index)));
        }
        write(out, "trailer\n<< /Size " + (objectCount + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private List<String> pdfPages(TabularSheet sheet) {
        int columns = sheet.headers().size();
        int width = Math.max(8, 140 / columns);
        List<String> body = new ArrayList<>();
        body.add(fit(sheet.title(), 100));
        body.add(join(sheet.headers(), width));
        if (sheet.rows().isEmpty()) {
            body.add("No records");
        }
        for (List<String> row : sheet.rows()) {
            body.add(join(pad(row, columns), width));
        }
        List<String> pages = new ArrayList<>();
        int perPage = 32;
        for (int start = 0; start < body.size(); start += perPage) {
            int end = Math.min(start + perPage, body.size());
            pages.add(pageStream(body.subList(start, end)));
        }
        if (pages.isEmpty()) {
            pages.add(pageStream(List.of(sheet.title())));
        }
        return pages;
    }

    private String pageStream(List<String> lines) {
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 9 Tf\n");
        int y = 560;
        for (String line : lines) {
            stream.append("1 0 0 1 28 ").append(y).append(" Tm\n(").append(pdfText(line)).append(") Tj\n");
            y -= 14;
        }
        stream.append("ET\n");
        return stream.toString();
    }

    private String join(List<String> cells, int width) {
        StringBuilder line = new StringBuilder();
        for (String cell : cells) {
            line.append(padRight(fit(cell, width), width + 1));
        }
        return line.toString().stripTrailing();
    }

    private String fit(String value, int width) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (text.length() <= width) {
            return text;
        }
        if (width <= 1) {
            return text.substring(0, width);
        }
        return text.substring(0, width - 1) + "~";
    }

    private String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    private String pdfText(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String worksheet(TabularSheet sheet) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        xml.append(xmlRow(1, sheet.headers()));
        int rowNumber = 2;
        for (List<String> row : sheet.rows()) {
            xml.append(xmlRow(rowNumber, pad(row, sheet.headers().size())));
            rowNumber++;
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private String xmlRow(int rowNumber, List<String> cells) {
        StringBuilder row = new StringBuilder();
        row.append("<row r=\"").append(rowNumber).append("\">");
        for (int index = 0; index < cells.size(); index++) {
            String ref = column(index) + rowNumber;
            row.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">");
            row.append(xml(cells.get(index)));
            row.append("</t></is></c>");
        }
        row.append("</row>");
        return row.toString();
    }

    private String column(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        do {
            name.insert(0, (char) ('A' + (value % 26)));
            value = (value / 26) - 1;
        } while (value >= 0);
        return name.toString();
    }

    private String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private List<String> pad(List<String> row, int size) {
        List<String> cells = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            cells.add(index < row.size() && row.get(index) != null ? row.get(index) : "");
        }
        return cells;
    }

    private void entry(ZipOutputStream zip, String name, String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """;
    }

    private String rootRels() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private String workbook(String title) {
        String sheetName = title.replaceAll("[^A-Za-z0-9 ]", " ").trim();
        if (sheetName.isEmpty()) {
            sheetName = "Export";
        }
        if (sheetName.length() > 31) {
            sheetName = sheetName.substring(0, 31).trim();
        }
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """.formatted(xml(sheetName));
    }

    private String workbookRels() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """;
    }

    private String styles() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
                  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                  <borders count="1"><border/></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
                </styleSheet>
                """;
    }

    private void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
    }
}
