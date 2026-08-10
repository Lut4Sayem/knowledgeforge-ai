package com.knowledgeforge.knowledgeforge.auth;

import com.knowledgeforge.knowledgeforge.auth.dto.LoginRequest;
import com.knowledgeforge.knowledgeforge.auth.dto.RegisterRequest;
import com.knowledgeforge.knowledgeforge.auth.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public UserResponse register(@RequestBody RegisterRequest request) {
//        System.out.println("Register method called");
        return authService.register(request);

    }

    @PostMapping("/login")
    public UserResponse login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        return authService.login(request, httpRequest, httpResponse);
    }
}