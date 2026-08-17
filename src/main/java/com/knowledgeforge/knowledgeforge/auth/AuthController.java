package com.knowledgeforge.knowledgeforge.auth;

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
            @RequestBody AuthService.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        return authService.login(request, httpRequest, httpResponse);
    }

    public static class UserResponse {
        private String id;
        private String fullName;
        private String email;

        public UserResponse(String id, String fullName, String email) {
            this.id = id;
            this.fullName = fullName;
            this.email = email;
        }

        public String getId() {
            return id;
        }

        public String getFullname() {
            return fullName;
        }

        public String getEmail() {
            return email;
        }
    }

    public static class RegisterRequest {
        private String fullName;
        private String  email;
        private String  password;

        public String getFullName() {
            return fullName;
        }

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}