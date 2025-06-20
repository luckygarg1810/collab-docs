package com.project.collab_docs.repository;

import com.project.collab_docs.entities.EmailOtp;
import com.project.collab_docs.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long>{

    Optional<EmailOtp> findTopByEmailAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(
            String email, OtpPurpose purpose);

    List<EmailOtp> findByEmailAndPurposeAndCreatedAtAfter(
            String email, OtpPurpose purpose, LocalDateTime since);

    @Query("SELECT COUNT(e) FROM EmailOtp e WHERE e.email = :email AND e.purpose = :purpose AND e.createdAt > :since")
    long countByEmailAndPurposeAndCreatedAtAfter(
            @Param("email") String email,
            @Param("purpose") OtpPurpose purpose,
            @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM EmailOtp e WHERE e.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM EmailOtp e WHERE e.email = :email AND e.purpose = :purpose")
    void deleteByEmailAndPurpose(@Param("email") String email, @Param("purpose") OtpPurpose purpose);

    boolean existsByEmailAndPurposeAndVerifiedTrue(String email, OtpPurpose purpose);

}
