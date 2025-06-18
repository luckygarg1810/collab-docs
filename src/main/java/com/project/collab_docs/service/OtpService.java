package com.project.collab_docs.service;

import com.project.collab_docs.entities.EmailOtp;
import com.project.collab_docs.enums.OtpPurpose;
import com.project.collab_docs.repository.EmailOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final EmailOtpRepository emailOtpRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiry.minutes:10}")
    private int otpExpiryMinutes;

    @Value("${app.otp.max.attempts:3}")
    private int maxAttempts;

    @Value("${app.otp.block.minutes:30}")
    private int blockDurationMinutes;

    @Value("${app.otp.max.requests.per.hour:5}")
    private int maxRequestsPerHour;

    @Transactional
    public void generateAndSendOtp(String email, String firstName, OtpPurpose purpose) {
        // Check rate limiting
        checkRateLimit(email, purpose);

        // Generate OTP
        String otp = generateOtp();

        // Clean up existing OTPs for this email and purpose
        emailOtpRepository.deleteByEmailAndPurpose(email, purpose);

        // Create new OTP record
        EmailOtp emailOtp = EmailOtp.builder()
                .email(email)
                .otp(otp)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .build();

        emailOtpRepository.save(emailOtp);

        // Send OTP via email
        sendOtpByPurpose(email, otp, firstName, purpose);

        log.info("OTP generated and sent for email: {} with purpose: {}", email, purpose);
    }

    private void sendOtpByPurpose(String email, String otp, String firstName, OtpPurpose purpose) {
        switch (purpose) {
            case REGISTRATION -> emailService.sendOtpEmail(email, otp, firstName);
            case PASSWORD_RESET -> emailService.sendPasswordResetOtpEmail(email, otp, firstName);
            default -> throw new IllegalArgumentException("Unsupported OTP purpose: " + purpose);
        }
    }

    @Transactional
    public boolean verifyOtp(String email, String otp, OtpPurpose purpose) {
        Optional<EmailOtp> optionalEmailOtp = emailOtpRepository
                .findTopByEmailAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(email, purpose);

        if (optionalEmailOtp.isEmpty()) {
            log.warn("No active OTP found for email: {} with purpose: {}", email, purpose);
            return false;
        }

        EmailOtp emailOtp = optionalEmailOtp.get();

        // Check if OTP is blocked
        if (emailOtp.isBlocked()) {
            log.warn("OTP verification blocked for email: {} until: {}", email, emailOtp.getBlockedUntil());
            return false;
        }

        // Check if OTP is expired
        if (emailOtp.isExpired()) {
            log.warn("OTP expired for email: {}", email);
            return false;
        }

        // Increment attempt count
        emailOtp.setAttemptCount(emailOtp.getAttemptCount() + 1);

        // Check if OTP matches
        if (!emailOtp.getOtp().equals(otp)) {
            // Check if max attempts reached
            if (emailOtp.getAttemptCount() >= maxAttempts) {
                emailOtp.setBlockedUntil(LocalDateTime.now().plusMinutes(blockDurationMinutes));
                log.warn("Max OTP attempts reached for email: {}. Blocked until: {}",
                        email, emailOtp.getBlockedUntil());
            }
            emailOtpRepository.save(emailOtp);
            return false;
        }

        // OTP is valid
        emailOtp.setVerified(true);
        emailOtpRepository.save(emailOtp);

        log.info("OTP verified successfully for email: {} for {} ", email, purpose);
        return true;
    }

    public boolean isOtpVerified(String email, OtpPurpose purpose) {
        return emailOtpRepository.existsByEmailAndPurposeAndVerifiedTrue(email, purpose);
    }

    @Transactional
    public void cleanupVerifiedOtp(String email, OtpPurpose purpose) {
        emailOtpRepository.deleteByEmailAndPurpose(email, purpose);
    }

    private void checkRateLimit(String email, OtpPurpose purpose) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long requestCount = emailOtpRepository.countByEmailAndPurposeAndCreatedAtAfter(
                email, purpose, oneHourAgo);

        if (requestCount >= maxRequestsPerHour) {
            log.warn("Rate limit exceeded for email: {} with purpose: {}. Count: {}",
                    email, purpose, requestCount);
            throw new RuntimeException("Too many OTP requests. Please try again later.");
        }
    }

    private String generateOtp() {
        // Generate 6-digit OTP
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    @Scheduled(fixedRate = 7200000) // 1 hour in milliseconds
    @Transactional
    public void cleanupExpiredOtps() {
        try {
            emailOtpRepository.deleteExpiredOtps(LocalDateTime.now());
            log.debug("Expired OTPs cleaned up successfully");
        } catch (Exception e) {
            log.error("Error cleaning up expired OTPs: {}", e.getMessage());
        }
    }
}
