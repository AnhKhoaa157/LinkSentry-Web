package com.lyanhkhoa.linksentry.license.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data adapter contract; callers depend on the license domain port instead. */
public interface SpringDataLicenseRepository extends JpaRepository<LicenseEntity, UUID> {

    // PESSIMISTIC_WRITE renders as `SELECT ... FOR UPDATE` on PostgreSQL. Locking this
    // parent row also blocks a concurrent INSERT into device_license_assignment
    // referencing the same license_id: PostgreSQL's own foreign-key enforcement takes a
    // FOR KEY SHARE lock on the referenced row for every such insert, and FOR KEY SHARE
    // conflicts with FOR UPDATE. Holding this lock for the rest of the transaction is
    // therefore what makes a count-then-insert against maxDevices race-free.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LicenseEntity l where l.licenseId = :licenseId")
    Optional<LicenseEntity> findByIdForUpdate(@Param("licenseId") UUID licenseId);
}
