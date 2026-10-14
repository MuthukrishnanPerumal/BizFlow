package com.aveline.bizflow.audit;

import com.aveline.bizflow.audit.dto.AuditDtos.AuditDetail;
import com.aveline.bizflow.audit.dto.AuditDtos.AuditSummary;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.export.ExportFormat;
import com.aveline.bizflow.export.TabularExport;
import com.aveline.bizflow.export.TabularSheet;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class AuditQueryService {

    static final int EXPORT_LIMIT = 10_000;
    private static final Set<String> SORTS = Set.of("createdAt", "actor", "action", "moduleCode");

    private final AuditEventRepository auditEventRepository;
    private final TabularExport tabularExport;

    public AuditQueryService(AuditEventRepository auditEventRepository, TabularExport tabularExport) {
        this.auditEventRepository = auditEventRepository;
        this.tabularExport = tabularExport;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditSummary> search(
            LocalDate from,
            LocalDate to,
            String user,
            String module,
            String action,
            String entity,
            String search,
            int page,
            int size,
            String sort) {
        Page<AuditEvent> result = auditEventRepository.findAll(
                specification(from, to, user, module, action, entity, search),
                PageRequests.of(page, size, sort, SORTS, "createdAt"));
        List<AuditSummary> items = result.getContent().stream().map(this::summary).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AuditDetail get(Long id) {
        AuditEvent event = auditEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event was not found"));
        return detail(event);
    }

    @Transactional(readOnly = true)
    public byte[] export(
            ExportFormat format,
            LocalDate from,
            LocalDate to,
            String user,
            String module,
            String action,
            String entity,
            String search) {
        List<AuditEvent> events = auditEventRepository.findAll(
                specification(from, to, user, module, action, entity, search),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        if (events.size() > EXPORT_LIMIT) {
            throw new BusinessRuleException(
                    "EXPORT_LIMIT",
                    "from",
                    "Narrow the filters before exporting more than 10,000 audit events");
        }
        List<List<String>> rows = new ArrayList<>();
        for (AuditEvent event : events) {
            rows.add(List.of(
                    text(event.getCreatedAt()),
                    text(event.getActor()),
                    text(event.getAction()),
                    text(event.getModuleCode()),
                    text(event.getResourceType()),
                    text(event.getResourceId()),
                    text(event.getOutcome()),
                    text(event.getDetail()),
                    text(event.getIpAddress()),
                    text(event.getOldValue()),
                    text(event.getNewValue())));
        }
        return tabularExport.write(format, new TabularSheet(
                "Audit log",
                List.of("timestamp", "user", "action", "module", "entity", "entityId", "outcome", "detail", "ip", "oldValue", "newValue"),
                rows));
    }

    private Specification<AuditEvent> specification(
            LocalDate from,
            LocalDate to,
            String user,
            String module,
            String action,
            String entity,
            String search) {
        Instant start = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        if (start != null && end != null && !start.isBefore(end)) {
            throw new BusinessRuleException("DATE_RANGE", "from", "The start date must be on or before the end date");
        }
        String actor = blank(user);
        String moduleCode = blank(module);
        String actionCode = blank(action);
        String entityType = blank(entity);
        String term = LikePatterns.contain(search);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (start != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), end));
            }
            if (actor != null) {
                predicates.add(builder.equal(builder.lower(root.get("actor")), actor.toLowerCase()));
            }
            if (moduleCode != null) {
                predicates.add(builder.equal(builder.lower(root.get("moduleCode")), moduleCode.toLowerCase()));
            }
            if (actionCode != null) {
                predicates.add(builder.equal(builder.lower(root.get("action")), actionCode.toLowerCase()));
            }
            if (entityType != null) {
                predicates.add(builder.equal(builder.lower(root.get("resourceType")), entityType.toLowerCase()));
            }
            if (term != null) {
                String like = "%" + term.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("resourceType")), like, '\\'),
                        builder.like(builder.lower(root.get("resourceId")), like, '\\'),
                        builder.like(builder.lower(root.get("detail")), like, '\\')));
            }
            if (predicates.isEmpty()) {
                return builder.conjunction();
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AuditSummary summary(AuditEvent event) {
        return new AuditSummary(
                event.getId(),
                event.getActor(),
                event.getAction(),
                event.getModuleCode(),
                event.getResourceType(),
                event.getResourceId(),
                event.getOutcome().name(),
                event.getDetail(),
                event.getIpAddress(),
                event.getCreatedAt());
    }

    private AuditDetail detail(AuditEvent event) {
        return new AuditDetail(
                event.getId(),
                event.getActor(),
                event.getAction(),
                event.getModuleCode(),
                event.getResourceType(),
                event.getResourceId(),
                event.getEventType(),
                event.getOutcome().name(),
                event.getDetail(),
                event.getOldValue(),
                event.getNewValue(),
                event.getIpAddress(),
                event.getCreatedAt());
    }

    private String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
