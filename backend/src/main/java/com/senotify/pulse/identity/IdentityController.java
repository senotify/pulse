package com.senotify.pulse.identity;

import com.senotify.pulse.common.Viewer;
import com.senotify.pulse.identity.IdentityDtos.AuthResponse;
import com.senotify.pulse.identity.IdentityDtos.LoginRequest;
import com.senotify.pulse.identity.IdentityDtos.Me;
import com.senotify.pulse.identity.IdentityDtos.RegisterRequest;
import com.senotify.pulse.identity.IdentityDtos.UpdateProfileRequest;
import com.senotify.pulse.identity.IdentityDtos.UserProfile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class IdentityController {

    private final IdentityService identity;

    IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return identity.register(request);
    }

    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return identity.login(request);
    }

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal Jwt jwt) {
        return identity.me(Viewer.id(jwt));
    }

    @PatchMapping("/me")
    Me updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return identity.updateProfile(Viewer.id(jwt), request);
    }

    @GetMapping("/users/{username}")
    UserProfile profile(@PathVariable String username, @AuthenticationPrincipal Jwt jwt) {
        return identity.profile(username, Viewer.idOrAnonymous(jwt));
    }
}
