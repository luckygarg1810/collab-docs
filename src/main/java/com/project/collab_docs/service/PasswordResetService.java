package com.project.collab_docs.service;

import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.OtpPurpose;
import com.project.collab_docs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void initiateForgotPassword(String email) {
        // Check if user exists
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email address"));

        // Check if account is locked
        if (!user.getAccountNonLocked()) {
            throw new RuntimeException("Account is locked. Please contact support.");
        }

        // Generate and send OTP for password reset
        otpService.generateAndSendOtp(email, user.getFirstName(), OtpPurpose.PASSWORD_RESET);
        log.info("Password reset OTP sent to email: {}", email);
    }


    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        // Verify user exists
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email address"));

        // Verify OTP
        boolean isOtpValid = otpService.verifyOtp(email, otp, OtpPurpose.PASSWORD_RESET);
        if (!isOtpValid) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        // Validate new password
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters long");
        }

        // Check if new password is different from current password
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("New password must be different from your current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Clean up OTP records
        otpService.cleanupVerifiedOtp(email, OtpPurpose.PASSWORD_RESET);

        // Send password reset confirmation email
        sendPasswordResetConfirmationEmail(user.getEmail(), user.getFirstName());

        log.info("Password reset completed successfully for user: {}", email);
    }

    private void sendPasswordResetConfirmationEmail(String email, String firstName) {
        try {
            emailService.sendPasswordResetConfirmationEmail(email, firstName);
        } catch (Exception e) {
            log.error("Failed to send password reset confirmation email to {}: {}", email, e.getMessage());
            // Don't throw exception as password reset was successful
        }
    }
}
