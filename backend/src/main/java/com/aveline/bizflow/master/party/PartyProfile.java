package com.aveline.bizflow.master.party;

import com.aveline.bizflow.master.RecordStatus;

import java.math.BigDecimal;

public record PartyProfile(
        String code,
        String name,
        String companyName,
        String contactPerson,
        String phone,
        String alternatePhone,
        String email,
        String taxNumber,
        String address,
        String country,
        String state,
        String city,
        String pincode,
        BigDecimal openingBalance,
        BigDecimal creditLimit,
        String paymentTerms,
        RecordStatus status
) {
}
