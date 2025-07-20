package com.project.collab_docs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.name:Collab Docs}")
    private String appName;

    @Async
    public void sendOtpEmail(String toEmail, String otp, String firstName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Email Verification - " + appName);

            String emailBody = String.format("""
                Dear %s,
                
                Thank you for registering with %s!
                
                Please use the following verification code to complete your registration:
                
                %s
                
                This code will expire in 10 minutes for security reasons.
                
                If you didn't request this verification, please ignore this email.
                
                Best regards,
                %s Team
                """, firstName, appName, otp, appName);

            message.setText(emailBody);

            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String firstName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Welcome to " + appName + "!");

            String emailBody = String.format("""
                Dear %s,
                
                Welcome to %s! Your email has been successfully verified and your account is now active.
                
                You can now start collaborating on documents with your team.
                
                If you have any questions or need help getting started, don't hesitate to contact our support team.
                
                Best regards,
                %s Team
                """, firstName, appName, appName);

            message.setText(emailBody);

            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
            // Don't throw exception for welcome email failure
        }
    }

    @Async
    public void sendPasswordResetOtpEmail(String toEmail, String otp, String firstName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Password Reset Request - " + appName);

            String emailBody = String.format("""
                Dear %s,
                
                We received a request to reset your password for your %s account.
                
                Please use the following verification code to reset your password:
                
                %s
                
                This code will expire in 10 minutes for security reasons.
                
                If you didn't request a password reset, please ignore this email or contact our support team if you have concerns.
                
                For security reasons, please do not share this code with anyone.
                
                Best regards,
                %s Team
                """, firstName, appName, otp, appName);

            message.setText(emailBody);

            mailSender.send(message);
            log.info("Password reset OTP email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send password reset OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    @Async
    public void sendPasswordResetConfirmationEmail(String toEmail, String firstName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Password Reset Successful - " + appName);

            String emailBody = String.format("""
                Dear %s,
                
                Your password for %s has been successfully reset.
                
                If you didn't make this change, please contact our support team immediately as your account may be compromised.
                
                For your security, we recommend:
                - Using a strong, unique password
                - Enabling two-factor authentication if available
                - Regularly updating your password
                
                If you have any questions or concerns, please don't hesitate to contact our support team.
                
                Best regards,
                %s Team
                """, firstName, appName, appName);

            message.setText(emailBody);

            mailSender.send(message);
            log.info("Password reset confirmation email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send password reset confirmation email to {}: {}", toEmail, e.getMessage());
            // Don't throw exception for confirmation email failure
        }
    }
}
