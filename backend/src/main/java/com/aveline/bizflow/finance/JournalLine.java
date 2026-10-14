package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "journal_lines")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalLine extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry entry;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "account_code", nullable = false, length = 16)
    private String accountCode;

    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount;

    @Column(name = "party_type", length = 16)
    private String partyType;

    @Column(name = "party_id")
    private Long partyId;

    public static JournalLine debit(
            JournalEntry entry,
            int lineNumber,
            GlAccount account,
            BigDecimal amount,
            String partyType,
            Long partyId) {
        return line(entry, lineNumber, account, amount, FinanceMath.ZERO, partyType, partyId);
    }

    public static JournalLine credit(
            JournalEntry entry,
            int lineNumber,
            GlAccount account,
            BigDecimal amount,
            String partyType,
            Long partyId) {
        return line(entry, lineNumber, account, FinanceMath.ZERO, amount, partyType, partyId);
    }

    private static JournalLine line(
            JournalEntry entry,
            int lineNumber,
            GlAccount account,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String partyType,
            Long partyId) {
        JournalLine line = new JournalLine();
        line.entry = entry;
        line.lineNumber = lineNumber;
        line.accountCode = account.code();
        line.debitAmount = debitAmount;
        line.creditAmount = creditAmount;
        line.partyType = partyType;
        line.partyId = partyId;
        return line;
    }
}
