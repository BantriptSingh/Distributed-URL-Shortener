package com.shorty.auth;

import com.shorty.auth.AuthService.Credentials;
import com.shorty.auth.AuthService.ForgotRequest;
import com.shorty.auth.AuthService.ForgotResponse;
import com.shorty.auth.AuthService.MeResponse;
import com.shorty.auth.AuthService.RefreshRequest;
import com.shorty.auth.AuthService.ResetRequest;
import com.shorty.auth.AuthService.TokenResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(
            @Email @NotBlank String email, @NotBlank @Size(min = 8, max = 128) String password) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest body) {
        return auth.register(body.email(), body.password());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody Credentials body) {
        return auth.login(body.email(), body.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest body) {
        return auth.refresh(body.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshRequest body) {
        if (body != null && body.refreshToken() != null) {
            auth.logout(body.refreshToken());
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotResponse> forgot(@Valid @RequestBody ForgotRequest body) {
        ForgotResponse res = auth.forgot(body.email());
        if (res.devResetToken() == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest body) {
        auth.reset(body.token(), body.password());
    }

    @GetMapping("/me")
    public MeResponse me() {
        return auth.me(SecuritySupport.requireUser().userId());
    }
}
