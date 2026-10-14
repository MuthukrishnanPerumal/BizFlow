package com.aveline.bizflow.purchase;

import com.aveline.bizflow.common.exception.BusinessRuleException;

import java.math.BigDecimal;

public final class PurchaseWorkflow {

    private PurchaseWorkflow() {
    }

    public static boolean isPosted(PurchaseStatus status) {
        return status == PurchaseStatus.CONFIRMED
                || status == PurchaseStatus.COMPLETED
                || status == PurchaseStatus.PARTIALLY_RETURNED
                || status == PurchaseStatus.RETURNED;
    }

    public static void assertEditable(PurchaseStatus status) {
        if (status != PurchaseStatus.DRAFT && status != PurchaseStatus.CONFIRMED && status != PurchaseStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "STATUS",
                    "status",
                    "This purchase cannot be edited while it is " + status.name().toLowerCase().replace('_', ' '));
        }
    }

    public static void assertCancellable(PurchaseStatus status) {
        if (status != PurchaseStatus.DRAFT && status != PurchaseStatus.CONFIRMED && status != PurchaseStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "STATUS",
                    "status",
                    "This purchase cannot be cancelled while it is " + status.name().toLowerCase().replace('_', ' '));
        }
    }

    public static void assertReturnable(PurchaseStatus status) {
        if (status != PurchaseStatus.CONFIRMED
                && status != PurchaseStatus.COMPLETED
                && status != PurchaseStatus.PARTIALLY_RETURNED) {
            throw new BusinessRuleException(
                    "STATUS",
                    "status",
                    "Goods can be returned only from a confirmed purchase that is not fully returned");
        }
    }

    public static PurchaseStatus posted(BigDecimal balance) {
        return balance.signum() == 0 ? PurchaseStatus.COMPLETED : PurchaseStatus.CONFIRMED;
    }

    public static PurchaseStatus afterReturn(boolean anyReturned, boolean allReturned) {
        if (allReturned) {
            return PurchaseStatus.RETURNED;
        }
        if (anyReturned) {
            return PurchaseStatus.PARTIALLY_RETURNED;
        }
        throw new BusinessRuleException("RETURN_QUANTITY", "items", "Return at least one quantity");
    }
}
