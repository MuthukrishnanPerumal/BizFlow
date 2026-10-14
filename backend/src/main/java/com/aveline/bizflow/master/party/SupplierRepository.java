package com.aveline.bizflow.master.party;

import com.aveline.bizflow.master.RecordStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    @Query(
            value = """
                    select s from Supplier s
                    where (:status is null or s.status = :status)
                      and (
                        :search is null
                        or lower(s.supplierCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.companyName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.phone) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.email) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.city) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.taxNumber) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(s) from Supplier s
                    where (:status is null or s.status = :status)
                      and (
                        :search is null
                        or lower(s.supplierCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.companyName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.phone) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.email) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.city) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.taxNumber) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """
    )
    Page<Supplier> search(@Param("search") String search, @Param("status") RecordStatus status, Pageable pageable);

    @Query(value = "select count(*) from suppliers where is_deleted = 0 and supplier_code = :code and id <> :id", nativeQuery = true)
    long countLiveCode(@Param("code") String code, @Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Supplier s where s.id = :id")
    Optional<Supplier> lockById(@Param("id") Long id);
}
