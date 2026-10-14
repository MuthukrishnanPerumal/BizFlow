package com.aveline.bizflow.document;

import com.aveline.bizflow.system.service.BusinessSettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class DocumentNumberService {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentSequenceRepository sequenceRepository;
    private final BusinessSettingsService businessSettingsService;

    public DocumentNumberService(
            JdbcTemplate jdbcTemplate,
            DocumentSequenceRepository sequenceRepository,
            BusinessSettingsService businessSettingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sequenceRepository = sequenceRepository;
        this.businessSettingsService = businessSettingsService;
    }

    @Transactional
    public String next(DocumentSeries series, int year) {
        jdbcTemplate.update(
                """
                INSERT INTO document_sequence (document_type, sequence_year, last_number)
                VALUES (?, ?, 0)
                ON DUPLICATE KEY UPDATE last_number = last_number
                """,
                series.name(),
                year);
        DocumentSequence sequence = sequenceRepository.lock(series.name(), year)
                .orElseThrow(() -> new IllegalStateException("Document sequence was not created"));
        long value = sequence.next();
        return businessSettingsService.prefixFor(series) + "-" + year + "-" + String.format(Locale.ROOT, "%06d", value);
    }
}
