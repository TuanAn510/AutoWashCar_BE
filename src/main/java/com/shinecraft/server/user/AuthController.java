package com.shinecraft.server.user;

import com.shinecraft.server.common.ApiResponse;
import com.shinecraft.server.common.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ApiResponse<UserDtos.AuthResponse> register(@Valid @RequestBody UserDtos.RegisterRequest request) {
        return ApiResponse.ok("Registration successful", authService.register(request));
    }

    @PostMapping("/login")
    ApiResponse<UserDtos.AuthResponse> login(@Valid @RequestBody UserDtos.LoginRequest request) {
        return ApiResponse.ok("Login successful", authService.login(request));
    }

    @PostMapping("/signin")
    ApiResponse<UserDtos.AuthResponse> signin(
            @Valid @RequestBody UserDtos.LoginRequest request, HttpServletResponse response) {
        UserDtos.AuthResponse auth = authService.login(request);
        addRefreshCookie(response, auth.refreshToken());
        return ApiResponse.ok("Login successful", auth);
    }

    @PostMapping("/signup")
    ApiResponse<UserDtos.UserResponse> signup(@Valid @RequestBody UserDtos.SignupRequest request) {
        return ApiResponse.ok("Signup successful", authService.signup(request));
    }

    @PostMapping("/refresh")
    ApiResponse<UserDtos.AuthResponse> refresh(@Valid @RequestBody UserDtos.RefreshTokenRequest request) {
        return ApiResponse.ok("Token refreshed successfully", authService.refresh(request));
    }

    @PostMapping("/refresh-token")
    ApiResponse<UserDtos.AuthResponse> refreshToken(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }
        UserDtos.AuthResponse auth = authService.refresh(new UserDtos.RefreshTokenRequest(refreshToken));
        addRefreshCookie(response, auth.refreshToken());
        return ApiResponse.ok("Token refreshed successfully", auth);
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@Valid @RequestBody UserDtos.LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.ok("Logout successful", null);
    }

    @PostMapping("/signout")
    ApiResponse<Void> signout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(new UserDtos.LogoutRequest(refreshToken));
        }
        clearRefreshCookie(response);
        return ApiResponse.ok("Logout successful", null);
    }

    @GetMapping("/me")
    ApiResponse<UserDtos.UserResponse> me() {
        return ApiResponse.ok("Current user retrieved successfully", UserDtos.UserResponse.from(authService.currentUser()));
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(30 * 24 * 60 * 60);
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
