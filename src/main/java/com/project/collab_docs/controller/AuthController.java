package com.project.collab_docs.controller;

import com.project.collab_docs.dto.request.*;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.dto.response.AuthResponse;
import com.project.collab_docs.dto.response.MessageResponse;
import com.project.collab_docs.exception.InvalidRefreshTokenException;
import com.project.collab_docs.security.CustomUserDetails;
import com.project.collab_docs.security.JwtUtil;
import com.project.collab_docs.service.PasswordResetService;
import com.project.collab_docs.service.RefreshTokenService;
import com.project.collab_docs.service.UserRegistrationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

        private static final String REFRESH_COOKIE_NAME = "refresh_token";

        private final AuthenticationManager authenticationManager;
        private final JwtUtil jwtUtil;
        private final UserRegistrationService userRegistrationService;
        private final PasswordResetService passwordResetService;
        private final RefreshTokenService refreshTokenService;

        @Value("${app.jwt.refresh-expiration-ms}")
        private long refreshExpirationMs;

        @PostMapping("/register")
        public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {

                try {
                        userRegistrationService.initiateRegistration(registerRequest);
                        return ResponseEntity.ok(new MessageResponse(
                                        "Registration initiated successfully! Please check your email for the verification code."));
                } catch (RuntimeException e) {
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
        public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest,
                        HttpServletResponse response) {
                try {
                        // Authenticate user
                        Authentication authentication = authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(
                                                        loginRequest.getEmail(),
                                                        loginRequest.getPassword()));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

                        // Generate JWT token
                        String jwtToken = jwtUtil.generateToken(userDetails);

                        // Create HTTP-only cookie for JWT
                        setJwtCookie(response, jwtToken);

                        // Issue a long-lived, server-tracked refresh token so the access token
                        // above can be short-lived and silently renewed later
                        User user = userDetails.getUser();
                        String rawRefreshToken = refreshTokenService.issueToken(user);
                        setRefreshTokenCookie(response, rawRefreshToken);

                        AuthResponse authResponse = AuthResponse.builder()
                                        .id(user.getId())
                                        .email(user.getEmail())
                                        .firstName(user.getFirstName())
                                        .lastName(user.getLastName())
                                        .message("Login successful. JWT Token is added to cookies.")
                                        .token(jwtToken) // Included for WebSocket auth (Yjs service cannot read
                                                         // HttpOnly cookies)
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
                                        resetPasswordRequest.getNewPassword());

                        log.info("Password reset completed successfully for email: {}",
                                        resetPasswordRequest.getEmail());

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
        public ResponseEntity<?> logoutUser(HttpServletRequest request, HttpServletResponse response) {
                try {
                        // Revoke the refresh token server-side so a copy of the cookie
                        // (e.g. captured before logout) can't be replayed afterwards
                        String rawRefreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
                        refreshTokenService.revoke(rawRefreshToken);

                        // Clear authentication context
                        SecurityContextHolder.clearContext();

                        clearCookie(response, "jwt");
                        clearCookie(response, REFRESH_COOKIE_NAME);

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

        /**
         * Silently renew an expired (or about to expire) access token.
         *
         * Deliberately does NOT depend on {@link Authentication} — that would
         * require the access JWT to still be valid, defeating the point of a
         * refresh token, which exists specifically to recover once it isn't.
         * Instead this reads the separate, longer-lived refresh_token cookie.
         *
         * Any failure (missing/expired/revoked/reused token) throws
         * InvalidRefreshTokenException, mapped to 401 by GlobalExceptionHandler —
         * the frontend interceptor treats that as "session is really over."
         */
        @PostMapping("/refresh")
        public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
                String rawRefreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
                if (rawRefreshToken == null) {
                        throw new InvalidRefreshTokenException("No refresh token present");
                }

                RefreshTokenService.RotationResult rotation = refreshTokenService.validateAndRotate(rawRefreshToken);
                User user = rotation.user();

                String newJwtToken = jwtUtil.generateToken(new CustomUserDetails(user));
                setJwtCookie(response, newJwtToken);
                setRefreshTokenCookie(response, rotation.rawToken());

                log.info("Token refreshed successfully for user: {}", user.getEmail());

                AuthResponse authResponse = AuthResponse.builder()
                                .id(user.getId())
                                .email(user.getEmail())
                                .firstName(user.getFirstName())
                                .lastName(user.getLastName())
                                .message("Token refreshed successfully!")
                                .token(newJwtToken) // Included for WebSocket auth (Yjs service cannot read
                                                    // HttpOnly cookies)
                                .build();

                return ResponseEntity.ok(authResponse);
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
                                .secure(false) // Set to true in production (HTTPS only)
                                .path("/")
                                .sameSite("Lax") // or "Strict" or "None"
                                .maxAge(jwtUtil.getExpirationTime() / 1000) // in seconds
                                .build();

                response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        }

        private void setRefreshTokenCookie(HttpServletResponse response, String rawRefreshToken) {
                ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                                .httpOnly(true)
                                .secure(false) // Set to true in production (HTTPS only) — must match setJwtCookie
                                // Scoped to /api/auth so this long-lived token is never sent on
                                // ordinary API calls, only to the refresh/logout endpoints that need it
                                .path("/api/auth")
                                .sameSite("Lax")
                                .maxAge(refreshExpirationMs / 1000)
                                .build();

                response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }

        /**
         * Overwrite a cookie with an immediately-expired one of the same name/path
         * so the browser deletes it. Must mirror the attributes (path, secure) used
         * when the cookie was originally set, or the browser will treat it as a
         * different cookie and leave the original in place.
         */
        private void clearCookie(HttpServletResponse response, String name) {
                String path = REFRESH_COOKIE_NAME.equals(name) ? "/api/auth" : "/";
                ResponseCookie deleteCookie = ResponseCookie.from(name, "")
                                .httpOnly(true)
                                .secure(false) // must match the flag used when the cookie was set
                                .path(path)
                                .sameSite("Lax")
                                .maxAge(0)
                                .build();

                response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
        }

        private String extractCookie(HttpServletRequest request, String name) {
                if (request.getCookies() == null) {
                        return null;
                }
                return Arrays.stream(request.getCookies())
                                .filter(cookie -> name.equals(cookie.getName()))
                                .findFirst()
                                .map(Cookie::getValue)
                                .orElse(null);
        }

}