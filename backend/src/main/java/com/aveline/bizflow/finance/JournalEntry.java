package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "journal_entries")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry extends AuditableEntity {

    @Column(name = "entry_number", nullable = false, length = 32)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(nullable = false, length = 500)
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JournalStatus status;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc")
    private final List<JournalLine> lines = new ArrayList<>();

    public static JournalEntry post(
            String entryNumber,
            LocalDate entryDate,
            String sourceType,
            String sourceId,
            String narration) {
        JournalEntry entry = new JournalEntry();
        entry.entryNumber = entryNumber;
        entry.entryDate = entryDate;
        entry.sourceType = sourceType;
        entry.sourceId = sourceId;
        entry.narration = narration;
        entry.status = JournalStatus.POSTED;
        return entry;
    }

    public void addLine(JournalLine line) {
        lines.add(line);
    }

    public void reverse() {
        this.status = JournalStatus.REVERSED;
    }
}
