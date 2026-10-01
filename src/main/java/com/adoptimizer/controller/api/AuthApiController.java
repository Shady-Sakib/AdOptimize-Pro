package com.adoptimizer.controller.api;

import com.adoptimizer.dto.request.AdminLoginRequest;
import com.adoptimizer.dto.request.AdminRegisterRequest;
import com.adoptimizer.dto.request.LoginRequest;
import com.adoptimizer.dto.request.RegisterRequest;
import com.adoptimizer.dto.response.AuthResponse;
import com.adoptimizer.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.loginAdvertiser(request, httpRequest, httpResponse);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.registerAdvertiser(request, httpRequest, httpResponse);
    }

    @PostMapping("/admin/login")
    public AuthResponse adminLogin(@Valid @RequestBody AdminLoginRequest request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.loginAdmin(request, httpRequest, httpResponse);
    }

    @PostMapping("/admin/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse adminRegister(@Valid @RequestBody AdminRegisterRequest request,
                                      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.registerAdmin(request, httpRequest, httpResponse);
    }
}
