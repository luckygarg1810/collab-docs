package com.project.collab_docs.controller;

import com.project.collab_docs.entities.User;
import com.project.collab_docs.repository.UserRepository;
import com.project.collab_docs.request.*;
import com.project.collab_docs.response.AuthResponse;
import com.project.collab_docs.response.MessageResponse;
import com.project.collab_docs.security.CustomUserDetails;
import com.project.collab_docs.security.JwtUtil;
import com.project.collab_docs.service.PasswordResetService;
import com.project.collab_docs.service.UserRegistrationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserRegistrationService userRegistrationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {

        try {
            userRegistrationService.initiateRegistration(registerRequest);
            return ResponseEntity.ok(new MessageResponse(
                    "Registration initiated successfully! Please check your email for the verification code."));
        }catch (RuntimeException e) {
            log.warn("Registration initiation failed for email {}: {}",
                    registerRequest.getEmail(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Registration initiation error for email {}: {}",
                    registerRequest.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to initiate registration!"));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtpAndCompleteRegistration(
            @Valid @RequestBody VerifyOtpRequest verifyOtpRequest) {
        try {
            User user = userRegistrationService.completeRegistration(
                    verifyOtpRequest.getEmail(), verifyOtpRequest.getOtp());

            log.info("User registration completed successfully: {}", user.getEmail());

            return ResponseEntity.ok(new MessageResponse(
                    "Email verified successfully! Your account has been created. You can now login."));

        } catch (RuntimeException e) {
            log.warn("OTP verification failed for email {}: {}",
                    verifyOtpRequest.getEmail(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("OTP verification error for email {}: {}",
                    verifyOtpRequest.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to verify OTP!"));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@Valid @RequestBody ResendOtpRequest resendOtpRequest) {
        try {
            userRegistrationService.resendOtp(resendOtpRequest.getEmail());

            return ResponseEntity.ok(new MessageResponse(
                    "Verification code has been resent to your email."));

        } catch (RuntimeException e) {
            log.warn("OTP resend failed for email {}: {}",
                    resendOtpRequest.getEmail(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("OTP resend error for email {}: {}",
                    resendOtpRequest.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to resend verification code!"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest loginRequest,
                                       HttpServletResponse response) {
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            // Generate JWT token
            String jwtToken = jwtUtil.generateToken(userDetails.getEmail());

            // Create HTTP-only cookie for JWT
           setJwtCookie(response,jwtToken);

            User user = userDetails.getUser();

            AuthResponse authResponse = AuthResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .message("Login successful. JWT Token is added to cookies.")
                    .build();

            return ResponseEntity.ok(authResponse);
        } catch (BadCredentialsException e) {
            log.warn("Login failed: Invalid credentials for email - {}", loginRequest.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Error: Invalid email or password!"));
        } catch (DisabledException e) {
            log.warn("Login failed: Account disabled for email - {}", loginRequest.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Error: Account is disabled!"));
        } catch (Exception e) {
            log.error("Login error for email {}: {}", loginRequest.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Login failed!"));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        try {
            passwordResetService.initiateForgotPassword(forgotPasswordRequest.getEmail());

            return ResponseEntity.ok(new MessageResponse(
                    "Password reset instructions have been sent to your email address."));

        } catch (RuntimeException e) {
            log.warn("Forgot password initiation failed for email {}: {}",
                    forgotPasswordRequest.getEmail(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Forgot password initiation error for email {}: {}",
                    forgotPasswordRequest.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to process password reset request!"));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        try {
            passwordResetService.resetPassword(
                    resetPasswordRequest.getEmail(),
                    resetPasswordRequest.getOtp(),
                    resetPasswordRequest.getNewPassword()
            );

            log.info("Password reset completed successfully for email: {}", resetPasswordRequest.getEmail());

            return ResponseEntity.ok(new MessageResponse(
                    "Password has been reset successfully! You can now login with your new password."));

        } catch (RuntimeException e) {
            log.warn("Password reset failed for email {}: {}",
                    resetPasswordRequest.getEmail(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Password reset error for email {}: {}",
                    resetPasswordRequest.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to reset password!"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletResponse response) {
        try {
            // Clear authentication context
            SecurityContextHolder.clearContext();

            // Clear JWT cookie
            ResponseCookie deleteCookie = ResponseCookie.from("jwt", "")
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(0)
                    .build();

            response.setHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
            log.info("User logged out successfully");
            return ResponseEntity.ok(new MessageResponse("Logout successful!"));

        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Logout failed!"));
        }
    }


    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("Error: User not authenticated!"));
            }

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            AuthResponse authResponse = AuthResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .message("User data retrieved successfully!")
                    .build();

            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {
            log.error("Error retrieving current user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Failed to retrieve user data!"));
        }
    }


    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletResponse response, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("Error: User not authenticated!"));
            }

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            // Generate new JWT token
            String newJwtToken = jwtUtil.generateToken(userDetails.getEmail());

            // Create new HTTP-only cookie for JWT
           setJwtCookie(response, newJwtToken);

            log.info("Token refreshed successfully for user: {}", userDetails.getEmail());
            return ResponseEntity.ok(new MessageResponse("Token refreshed successfully!"));

        } catch (Exception e) {
            log.error("Token refresh error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: Token refresh failed!"));
        }
    }


    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("Error: Invalid or expired token!"));
            }

            return ResponseEntity.ok(new MessageResponse("Token is valid!"));

        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Error: Invalid or expired token!"));
        }
    }


    private void setJwtCookie(HttpServletResponse response, String jwtToken) {
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", jwtToken)
                .httpOnly(true)
                .secure(true) // HTTPS only
                .path("/")
                .sameSite("Lax") // or "Strict" or "None"
                .maxAge(jwtUtil.getExpirationTime() / 1000) // in seconds
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
    }

}