package com.aveline.bizflow.document;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequenceKey> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.id.documentType = :type and s.id.sequenceYear = :year")
    Optional<DocumentSequence> lock(@Param("type") String type, @Param("year") int year);
}
