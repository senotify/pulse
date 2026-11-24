package com.senotify.pulse.identity;

import com.senotify.pulse.common.ApiException;
import com.senotify.pulse.common.OutboxWriter;
import com.senotify.pulse.identity.IdentityDtos.AuthResponse;
import com.senotify.pulse.identity.IdentityDtos.LoginRequest;
import com.senotify.pulse.identity.IdentityDtos.Me;
import com.senotify.pulse.identity.IdentityDtos.RegisterRequest;
import com.senotify.pulse.identity.IdentityDtos.UpdateProfileRequest;
import com.senotify.pulse.identity.IdentityDtos.UserProfile;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final OutboxWriter outbox;
    /** Compared against when the login is unknown, so response time does not reveal which users exist. */
    private final String dummyHash;

    IdentityService(UserRepository users, PasswordEncoder passwords, TokenService tokens, OutboxWriter outbox) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.outbox = outbox;
        this.dummyHash = passwords.encode("pulse-dummy-password");
    }

    @Transactional
    AuthResponse register(RegisterRequest request) {
        String username = UserRepository.normalize(request.username());
        String email = UserRepository.normalize(request.email());
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("Username is taken");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered");
        }
        long id = users.insert(username, email, request.displayName().strip(),
                passwords.encode(request.password()));
        outbox.append("user", id, "UserRegistered", Map.of("userId", id, "username", username));
        return authenticated(id, username);
    }

    @Transactional(readOnly = true)
    AuthResponse login(LoginRequest request) {
        var credentials = users.findCredentials(request.login());
        String hash = credentials.map(UserRepository.Credentials::passwordHash).orElse(dummyHash);
        boolean matches = passwords.matches(request.password(), hash);
        if (credentials.isEmpty() || !matches) {
            throw ApiException.unauthorized("Invalid username or password");
        }
        return authenticated(credentials.get().id(), credentials.get().username());
    }

    @Transactional(readOnly = true)
    Me me(long userId) {
        return users.findMe(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
    }

    @Transactional
    Me updateProfile(long userId, UpdateProfileRequest request) {
        String displayName = request.displayName() == null ? null : request.displayName().strip();
        if (displayName != null && displayName.isEmpty()) {
            throw ApiException.badRequest("Display name cannot be blank");
        }
        String bio = request.bio() == null ? null : request.bio().strip();
        users.updateProfile(userId, displayName, bio);
        return me(userId);
    }

    @Transactional(readOnly = true)
    UserProfile profile(String username, long viewer) {
        return users.findProfile(username, viewer).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private AuthResponse authenticated(long id, String username) {
        var token = tokens.issue(id, username);
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), me(id));
    }
}
