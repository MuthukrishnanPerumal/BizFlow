package com.aveline.bizflow.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DocumentSequenceKey implements Serializable {

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType;

    @Column(name = "sequence_year", nullable = false)
    private int sequenceYear;
}
