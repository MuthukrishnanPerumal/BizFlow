package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.finance.dto.FinanceDtos.PayableReport;
import com.aveline.bizflow.finance.dto.FinanceDtos.ReceivableReport;
import com.aveline.bizflow.master.MasterText;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class PositionQueryService {

    private final PositionReadModel readModel;
    private final Clock clock;

    public PositionQueryService(PositionReadModel readModel, Clock clock) {
        this.readModel = readModel;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReceivableReport receivables(
            Long customerId,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            PositionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int page,
            int size,
            String sort) {
        Range range = range(minAmount, maxAmount);
        return readModel.receivables(
                customerId,
                LikePatterns.contain(search),
                fromDate,
                toDate,
                toDate == null ? LocalDate.now(clock) : toDate,
                status,
                range.minimum(),
                range.maximum(),
                page,
                size,
                sort);
    }

    @Transactional(readOnly = true)
    public PayableReport payables(
            Long supplierId,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            PositionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int page,
            int size,
            String sort) {
        Range range = range(minAmount, maxAmount);
        return readModel.payables(
                supplierId,
                LikePatterns.contain(search),
                fromDate,
                toDate,
                toDate == null ? LocalDate.now(clock) : toDate,
                status,
                range.minimum(),
                range.maximum(),
                page,
                size,
                sort);
    }

    private Range range(BigDecimal minAmount, BigDecimal maxAmount) {
        BigDecimal minimum = minAmount == null ? null : MasterText.money(minAmount, "minAmount", "Minimum amount", true);
        BigDecimal maximum = maxAmount == null ? null : MasterText.money(maxAmount, "maxAmount", "Maximum amount", true);
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new BusinessRuleException("AMOUNT_RANGE", "minAmount", "Minimum amount cannot exceed the maximum");
        }
        return new Range(minimum, maximum);
    }

    private record Range(BigDecimal minimum, BigDecimal maximum) {
    }
}
