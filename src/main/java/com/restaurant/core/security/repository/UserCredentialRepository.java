package com.restaurant.core.security.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.security.entity.UserCredential;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM UserCredential c WHERE c.employeeId = :employeeId")
    Optional<UserCredential> findByEmployeeIdForUpdate(@Param("employeeId") Long employeeId);
}
