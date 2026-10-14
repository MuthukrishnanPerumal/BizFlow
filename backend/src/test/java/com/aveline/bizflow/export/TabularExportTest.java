package com.aveline.bizflow.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TabularExportTest {

    @Test
    void formatsIncludeEveryFilteredRow() throws Exception {
        TabularExport export = new TabularExport();
        TabularSheet sheet = new TabularSheet(
                "Audit log",
                List.of("user", "action"),
                List.of(List.of("alpha", "login"), List.of("beta", "settings")));
        String csv = new String(export.write(ExportFormat.CSV, sheet), StandardCharsets.UTF_8);
        assertTrue(csv.contains("alpha"));
        assertTrue(csv.contains("beta"));
        assertTrue(csv.indexOf("alpha") < csv.indexOf("beta"));

        byte[] workbook = export.write(ExportFormat.XLSX, sheet);
        assertTrue(workbook[0] == 'P' && workbook[1] == 'K');
        String xml = workbookXml(workbook);
        assertTrue(xml.contains("alpha"));
        assertTrue(xml.contains("beta"));

        String pdf = new String(export.write(ExportFormat.PDF, sheet), StandardCharsets.US_ASCII);
        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.contains("alpha"));
        assertTrue(pdf.contains("beta"));
        assertTrue(pdf.contains("%%EOF"));
    }

    private String workbookXml(byte[] workbook) throws Exception {
        StringBuilder xml = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(workbook))) {
            while (zip.getNextEntry() != null) {
                xml.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return xml.toString();
    }
}
