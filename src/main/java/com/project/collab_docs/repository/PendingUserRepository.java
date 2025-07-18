package com.project.collab_docs.repository;

import com.project.collab_docs.entities.PendingUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PendingUserRepository extends JpaRepository<PendingUser, Long> {

    Optional<PendingUser> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("DELETE FROM PendingUser p WHERE p.expiresAt < :now")
    void deleteExpiredPendingUsers(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM pending_users WHERE email = :email", nativeQuery = true)
    void deleteByEmail(@Param("email")String email);
}
