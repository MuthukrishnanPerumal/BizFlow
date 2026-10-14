package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.master.RecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    @Query(
            value = """
                    select b from Brand b
                    where (:status is null or b.status = :status)
                      and (
                        :search is null
                        or lower(b.code) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(b.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(b.description) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(b) from Brand b
                    where (:status is null or b.status = :status)
                      and (
                        :search is null
                        or lower(b.code) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(b.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(b.description) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """
    )
    Page<Brand> search(@Param("search") String search, @Param("status") RecordStatus status, Pageable pageable);

    @Query(value = "select count(*) from brands where is_deleted = 0 and code = :code and id <> :id", nativeQuery = true)
    long countLiveCode(@Param("code") String code, @Param("id") long id);

    @Query(value = "select count(*) from brands where is_deleted = 0 and name = :name and id <> :id", nativeQuery = true)
    long countLiveName(@Param("name") String name, @Param("id") long id);
}
