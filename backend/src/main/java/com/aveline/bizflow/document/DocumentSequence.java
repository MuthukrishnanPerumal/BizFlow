package com.aveline.bizflow.document;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "document_sequence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentSequence {

    @EmbeddedId
    private DocumentSequenceKey id;

    @Column(name = "last_number", nullable = false)
    private long lastValue;

    public long next() {
        lastValue += 1;
        return lastValue;
    }
}
