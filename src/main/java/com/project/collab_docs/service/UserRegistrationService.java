package com.project.collab_docs.service;

import com.project.collab_docs.entities.PendingUser;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.OtpPurpose;
import com.project.collab_docs.repository.PendingUserRepository;
import com.project.collab_docs.repository.UserRepository;
import com.project.collab_docs.request.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PendingUserRepository pendingUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;

    @Transactional
    public void initiateRegistration(RegisterRequest registerRequest) {

        String email = registerRequest.getEmail().toLowerCase().trim();

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration attempt with existing email: {}", email);
            throw new RuntimeException("Email is already registered");
        }

        if(pendingUserRepository.existsByEmail(email)) {
            // Clean up any existing pending registration for this email
            System.out.println("Pending User Exists");
            pendingUserRepository.deleteByEmail(registerRequest.getEmail());
        }

        // Create pending user
        PendingUser pendingUser = PendingUser.builder()
                .firstName(registerRequest.getFirstName().trim())
                .lastName(registerRequest.getLastName().trim())
                .email(email)
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .build();

        pendingUserRepository.save(pendingUser);

        // Generate and send OTP
        otpService.generateAndSendOtp(email, registerRequest.getFirstName(),
                OtpPurpose.REGISTRATION);

        log.info("Registration initiated for email: {}", email);
    }

    @Transactional
    public User completeRegistration(String email, String otp) {
        email = email.toLowerCase().trim();

        // Verify OTP
        if (!otpService.verifyOtp(email, otp, OtpPurpose.REGISTRATION)) {
            log.warn("Invalid OTP provided for email: {}", email);
            throw new RuntimeException("Invalid or expired OTP");
        }

        // Find pending user
        Optional<PendingUser> optionalPendingUser = pendingUserRepository.findByEmail(email);
        if (optionalPendingUser.isEmpty()) {
            log.warn("No pending registration found for email: {}", email);
            throw new RuntimeException("No pending registration found");
        }

        PendingUser pendingUser = optionalPendingUser.get();

        // Check if pending registration is expired
        if (pendingUser.isExpired()) {
            log.warn("Pending registration expired for email: {}", email);
            pendingUserRepository.delete(pendingUser);
            throw new RuntimeException("Registration has expired. Please start over.");
        }

        // Double-check user doesn't exist (race condition protection)
        if (userRepository.existsByEmail(email)) {
            log.warn("User already exists during registration completion: {}", email);
            pendingUserRepository.delete(pendingUser);
            otpService.cleanupVerifiedOtp(email, OtpPurpose.REGISTRATION);
            throw new RuntimeException("User already exists");
        }

        // Create actual user
        User user = User.builder()
                .firstName(pendingUser.getFirstName())
                .lastName(pendingUser.getLastName())
                .email(pendingUser.getEmail())
                .password(pendingUser.getPassword())
                .build();

        User savedUser = userRepository.save(user);

        // Clean up
        pendingUserRepository.delete(pendingUser);
        otpService.cleanupVerifiedOtp(email, OtpPurpose.REGISTRATION);

        // Send welcome email
        emailService.sendWelcomeEmail(email, user.getFirstName());

        log.info("Registration completed successfully for email: {}", email);
        return savedUser;
    }

    public boolean canResendOtp(String email) {
        email = email.toLowerCase().trim();

        // Check if there's a pending registration
        if (!pendingUserRepository.existsByEmail(email)) {
            return false;
        }

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            return false;
        }

        return true;
    }

    @Transactional
    public void resendOtp(String email) {
        email = email.toLowerCase().trim();

        if (!canResendOtp(email)) {
            log.warn("Cannot resend OTP for email: {}", email);
            throw new RuntimeException("Cannot resend OTP");
        }

        Optional<PendingUser> optionalPendingUser = pendingUserRepository.findByEmail(email);
        if (optionalPendingUser.isEmpty()) {
            throw new RuntimeException("No pending registration found");
        }

        PendingUser pendingUser = optionalPendingUser.get();

        if (pendingUser.isExpired()) {
            pendingUserRepository.delete(pendingUser);
            throw new RuntimeException("Registration has expired. Please start over.");
        }

        // Generate and send new OTP
        otpService.generateAndSendOtp(email, pendingUser.getFirstName(), OtpPurpose.REGISTRATION);

        log.info("OTP resent for email: {}", email);
    }

    // Cleanup expired pending users every hour
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    @Transactional
    public void cleanupExpiredPendingUsers() {
        try {
            pendingUserRepository.deleteExpiredPendingUsers(LocalDateTime.now());
            log.debug("Expired pending users cleaned up successfully");
        } catch (Exception e) {
            log.error("Error cleaning up expired pending users: {}", e.getMessage());
        }
    }
}
