package com.aveline.bizflow.master.party;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "suppliers")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier extends AuditableEntity {

    @Column(name = "supplier_code", nullable = false, length = 32)
    private String supplierCode;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "contact_person", length = 160)
    private String contactPerson;

    @Column(length = 32)
    private String phone;

    @Column(name = "alternate_phone", length = 32)
    private String alternatePhone;

    @Column(length = 254)
    private String email;

    @Column(name = "tax_number", length = 32)
    private String taxNumber;

    @Column(length = 500)
    private String address;

    @Column(length = 80)
    private String country;

    @Column(length = 80)
    private String state;

    @Column(length = 80)
    private String city;

    @Column(length = 16)
    private String pincode;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "payment_terms", length = 120)
    private String paymentTerms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status;

    public static Supplier create(PartyProfile profile) {
        Supplier supplier = new Supplier();
        supplier.apply(profile);
        return supplier;
    }

    public void update(PartyProfile profile) {
        apply(profile);
    }

    public void retire() {
        markDeleted();
    }

    private void apply(PartyProfile profile) {
        this.supplierCode = profile.code();
        this.supplierName = profile.name();
        this.companyName = profile.companyName();
        this.contactPerson = profile.contactPerson();
        this.phone = profile.phone();
        this.alternatePhone = profile.alternatePhone();
        this.email = profile.email();
        this.taxNumber = profile.taxNumber();
        this.address = profile.address();
        this.country = profile.country();
        this.state = profile.state();
        this.city = profile.city();
        this.pincode = profile.pincode();
        this.openingBalance = profile.openingBalance();
        this.creditLimit = profile.creditLimit();
        this.paymentTerms = profile.paymentTerms();
        this.status = profile.status();
    }
}
