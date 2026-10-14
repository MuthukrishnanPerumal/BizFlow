package com.aveline.bizflow.master.party;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.RecordStatus;

import java.math.BigDecimal;

public final class PartyProfiles {

    private PartyProfiles() {
    }

    public static PartyProfile normalize(
            String code,
            String codeField,
            String name,
            String nameField,
            String nameLabel,
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
            RecordStatus status) {
        BigDecimal opening = MasterText.money(openingBalance, "openingBalance", "Opening balance", true);
        BigDecimal credit = MasterText.money(creditLimit, "creditLimit", "Credit limit", true);
        if (credit.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("CREDIT_LIMIT", "creditLimit", "Credit limit cannot be negative");
        }
        return new PartyProfile(
                MasterText.code(code, codeField),
                MasterText.required(name, 200, nameField, nameLabel),
                MasterText.optional(companyName, 200, "companyName", "Company name"),
                MasterText.optional(contactPerson, 160, "contactPerson", "Contact person"),
                MasterText.phone(phone, "phone"),
                MasterText.phone(alternatePhone, "alternatePhone"),
                MasterText.email(email),
                MasterText.taxNumber(taxNumber),
                MasterText.optional(address, 500, "address", "Address"),
                MasterText.optional(country, 80, "country", "Country"),
                MasterText.optional(state, 80, "state", "State"),
                MasterText.optional(city, 80, "city", "City"),
                MasterText.pincode(pincode),
                opening,
                credit,
                MasterText.optional(paymentTerms, 120, "paymentTerms", "Payment terms"),
                status);
    }

    public static BigDecimal supplierDebit(BigDecimal opening) {
        return opening.signum() < 0 ? opening.abs() : scaledZero();
    }

    public static BigDecimal supplierCredit(BigDecimal opening) {
        return opening.signum() > 0 ? opening.abs() : scaledZero();
    }

    public static BigDecimal customerDebit(BigDecimal opening) {
        return opening.signum() > 0 ? opening.abs() : scaledZero();
    }

    public static BigDecimal customerCredit(BigDecimal opening) {
        return opening.signum() < 0 ? opening.abs() : scaledZero();
    }

    public static BigDecimal scaledZero() {
        return BigDecimal.ZERO.setScale(4);
    }
}
