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

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> lockById(@Param("id") Long id);

    @Query(
            value = """
                    select c from Customer c
                    where (:status is null or c.status = :status)
                      and (
                        :search is null
                        or lower(c.customerCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.companyName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.phone) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.email) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.city) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.taxNumber) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(c) from Customer c
                    where (:status is null or c.status = :status)
                      and (
                        :search is null
                        or lower(c.customerCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.companyName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.phone) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.email) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.city) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.taxNumber) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """
    )
    Page<Customer> search(@Param("search") String search, @Param("status") RecordStatus status, Pageable pageable);

    @Query(value = "select count(*) from customers where is_deleted = 0 and customer_code = :code and id <> :id", nativeQuery = true)
    long countLiveCode(@Param("code") String code, @Param("id") long id);
}
