package com.aveline.bizflow.system.service;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.system.DataResetCatalog;
import com.aveline.bizflow.system.DataResetCatalog.ResetTable;
import com.aveline.bizflow.system.dto.DataResetDtos.DataResetResult;
import com.aveline.bizflow.system.dto.DataResetDtos.ResetDeletion;
import com.aveline.bizflow.system.dto.DataResetDtos.ResetTableView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DataResetService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditRecorder auditRecorder;

    public DataResetService(JdbcTemplate jdbcTemplate, AuditRecorder auditRecorder) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<ResetTableView> list() {
        return DataResetCatalog.tables().stream()
                .map(table -> new ResetTableView(
                        table.key(),
                        table.label(),
                        table.group(),
                        table.tableName(),
                        count(table),
                        table.requires()))
                .toList();
    }

    @Transactional
    public DataResetResult reset(List<String> keys) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String raw : keys) {
            String key = raw == null ? "" : raw.trim();
            if (DataResetCatalog.find(key).isEmpty()) {
                throw new BusinessRuleException("DATA_RESET", "tables", "Select only the tables shown in the list.");
            }
            selected.add(key);
        }
        if (selected.isEmpty()) {
            throw new BusinessRuleException("DATA_RESET", "tables", "Select at least one table.");
        }
        List<String> blockers = blockingLabels(selected);
        if (!blockers.isEmpty()) {
            throw new BusinessRuleException(
                    "DATA_RESET",
                    "tables",
                    "Also select " + String.join(", ", blockers) + ". Those tables still have rows linked to this selection.");
        }
        List<ResetDeletion> deleted = new ArrayList<>();
        long total = 0;
        for (ResetTable table : DataResetCatalog.deletionOrder(selected)) {
            int rows = jdbcTemplate.update("DELETE FROM " + table.tableName());
            deleted.add(new ResetDeletion(table.key(), table.label(), rows));
            total += rows;
        }
        auditRecorder.record(
                "DATA_RESET",
                "reset",
                AuditOutcome.SUCCESS,
                "settings",
                "data",
                "Hard-deleted " + total + " rows from " + deleted.size() + " tables");
        return new DataResetResult(total, List.copyOf(deleted));
    }

    private List<String> blockingLabels(Set<String> selected) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (String key : selected) {
            ResetTable table = DataResetCatalog.find(key).orElseThrow();
            for (String childKey : table.requires()) {
                if (selected.contains(childKey)) {
                    continue;
                }
                ResetTable child = DataResetCatalog.find(childKey).orElseThrow();
                if (count(child) > 0) {
                    labels.add(child.label());
                }
            }
        }
        return List.copyOf(labels);
    }

    private long count(ResetTable table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table.tableName(), Long.class);
        return value == null ? 0L : value;
    }
}
