package com.aveline.bizflow.finance;

import java.math.BigDecimal;

public record PostedTotal(BigDecimal amount, BigDecimal advance, long count) {
}
