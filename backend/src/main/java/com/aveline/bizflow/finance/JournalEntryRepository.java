package com.aveline.bizflow.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @Query("""
            select e from JournalEntry e
            left join fetch e.lines
            where e.sourceType = :sourceType
              and e.sourceId = :sourceId
              and e.status = :status
            order by e.id asc
            """)
    List<JournalEntry> findWithLines(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("status") JournalStatus status);
}
