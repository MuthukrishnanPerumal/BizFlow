package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.master.RecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitRepository extends JpaRepository<UnitOfMeasure, Long> {

    @Query(
            value = """
                    select u from UnitOfMeasure u
                    where (:status is null or u.status = :status)
                      and (
                        :search is null
                        or lower(u.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(u.shortName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(u.description) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(u) from UnitOfMeasure u
                    where (:status is null or u.status = :status)
                      and (
                        :search is null
                        or lower(u.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(u.shortName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(u.description) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """
    )
    Page<UnitOfMeasure> search(@Param("search") String search, @Param("status") RecordStatus status, Pageable pageable);

    @Query(value = "select count(*) from units where is_deleted = 0 and name = :name and id <> :id", nativeQuery = true)
    long countLiveName(@Param("name") String name, @Param("id") long id);

    @Query(value = "select count(*) from units where is_deleted = 0 and short_name = :shortName and id <> :id", nativeQuery = true)
    long countLiveShortName(@Param("shortName") String shortName, @Param("id") long id);
}
