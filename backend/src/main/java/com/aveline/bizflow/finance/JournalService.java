package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.document.DocumentNumberService;
import com.aveline.bizflow.document.DocumentSeries;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class JournalService {

    private final JournalEntryRepository journalEntryRepository;
    private final DocumentNumberService documentNumberService;

    public JournalService(JournalEntryRepository journalEntryRepository, DocumentNumberService documentNumberService) {
        this.journalEntryRepository = journalEntryRepository;
        this.documentNumberService = documentNumberService;
    }

    public JournalEntry post(LocalDate date, String sourceType, String sourceId, String narration, List<DraftLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new BusinessRuleException("JOURNAL", "amount", "A ledger entry needs at least two lines");
        }
        BigDecimal debit = FinanceMath.ZERO;
        BigDecimal credit = FinanceMath.ZERO;
        JournalEntry entry = JournalEntry.post(
                documentNumberService.next(DocumentSeries.JOURNAL, date.getYear()),
                date,
                sourceType,
                sourceId,
                narration);
        int lineNumber = 1;
        for (DraftLine line : lines) {
            if (line.debit().signum() < 0 || line.credit().signum() < 0) {
                throw new BusinessRuleException("JOURNAL", "amount", "Ledger amounts cannot be negative");
            }
            if (line.debit().signum() > 0 && line.credit().signum() > 0) {
                throw new BusinessRuleException("JOURNAL", "amount", "A ledger line cannot be both a debit and a credit");
            }
            if (line.debit().signum() == 0 && line.credit().signum() == 0) {
                continue;
            }
            JournalLine journalLine = line.debit().signum() > 0
                    ? JournalLine.debit(entry, lineNumber, line.account(), line.debit(), line.partyType(), line.partyId())
                    : JournalLine.credit(entry, lineNumber, line.account(), line.credit(), line.partyType(), line.partyId());
            entry.addLine(journalLine);
            debit = debit.add(line.debit());
            credit = credit.add(line.credit());
            lineNumber++;
        }
        if (debit.compareTo(credit) != 0 || debit.signum() == 0) {
            throw new BusinessRuleException("JOURNAL", "amount", "The ledger entry does not balance");
        }
        return journalEntryRepository.save(entry);
    }

    public void reverse(String sourceType, String sourceId) {
        List<JournalEntry> posted = journalEntryRepository.findWithLines(sourceType, sourceId, JournalStatus.POSTED);
        for (JournalEntry entry : posted) {
            entry.reverse();
        }
        journalEntryRepository.flush();
    }

    public JournalEntry latest(String sourceType, String sourceId) {
        List<JournalEntry> posted = journalEntryRepository.findWithLines(sourceType, sourceId, JournalStatus.POSTED);
        if (!posted.isEmpty()) {
            return posted.get(posted.size() - 1);
        }
        List<JournalEntry> reversed = journalEntryRepository.findWithLines(sourceType, sourceId, JournalStatus.REVERSED);
        if (reversed.isEmpty()) {
            return null;
        }
        return reversed.get(reversed.size() - 1);
    }

    public record DraftLine(
            GlAccount account,
            BigDecimal debit,
            BigDecimal credit,
            String partyType,
            Long partyId) {

        public static DraftLine debit(GlAccount account, BigDecimal amount, String partyType, Long partyId) {
            return new DraftLine(account, amount, FinanceMath.ZERO, partyType, partyId);
        }

        public static DraftLine credit(GlAccount account, BigDecimal amount, String partyType, Long partyId) {
            return new DraftLine(account, FinanceMath.ZERO, amount, partyType, partyId);
        }
    }
}
