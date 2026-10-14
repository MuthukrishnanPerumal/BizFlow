package com.aveline.bizflow.sale;

import com.aveline.bizflow.common.exception.BusinessRuleException;

import java.math.BigDecimal;

public final class SaleWorkflow {

    private SaleWorkflow() {
    }

    public static boolean isPosted(SaleStatus status) {
        return status == SaleStatus.CONFIRMED
                || status == SaleStatus.COMPLETED
                || status == SaleStatus.PARTIALLY_RETURNED
                || status == SaleStatus.RETURNED;
    }

    public static void assertEditable(SaleStatus status) {
        if (status != SaleStatus.DRAFT && status != SaleStatus.CONFIRMED && status != SaleStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "STATUS",
                    "status",
                    "This sale cannot be edited while it is " + status.name().toLowerCase().replace('_', ' '));
        }
    }

    public static void assertCancellable(SaleStatus status) {
        if (status != SaleStatus.DRAFT && status != SaleStatus.CONFIRMED && status != SaleStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "STATUS",
                    "status",
                    "This sale cannot be cancelled while it is " + status.name().toLowerCase().replace('_', ' '));
        }
    }

    public static void assertReturnable(SaleStatus status) {
        if (status != SaleStatus.CONFIRMED
                && status != SaleStatus.COMPLETED
                && status != SaleStatus.PARTIALLY_RETURNED) {
            throw new BusinessRuleException(
                    "STATUS",
                    "status",
                    "Goods can be returned only from a completed sale that is not fully returned");
        }
    }

    public static SaleStatus posted(BigDecimal balance) {
        return balance.signum() == 0 ? SaleStatus.COMPLETED : SaleStatus.CONFIRMED;
    }

    public static SaleStatus afterReturn(boolean anyReturned, boolean allReturned) {
        if (allReturned) {
            return SaleStatus.RETURNED;
        }
        if (anyReturned) {
            return SaleStatus.PARTIALLY_RETURNED;
        }
        throw new BusinessRuleException("RETURN_QUANTITY", "items", "Return at least one quantity");
    }
}
