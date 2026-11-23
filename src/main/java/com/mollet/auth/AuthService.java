package com.mollet.auth;

import com.mollet.account.AccountService;
import com.mollet.auth.dto.LoginRequest;
import com.mollet.auth.dto.RegisterRequest;
import com.mollet.auth.dto.TokenResponse;
import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import com.mollet.user.Role;
import com.mollet.user.User;
import com.mollet.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, AccountService accountService,
            PasswordEncoder passwordEncoder, TokenService tokenService, Clock clock) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    /** Crea el usuario y su cuenta en la misma transacción: o se crean ambos, o ninguno. */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Ya existe un usuario con ese email");
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), Role.USER, Instant.now(clock)));
        accountService.openAccountFor(user);
        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        // Mismo mensaje si el email no existe o si la clave es incorrecta:
        // así no le revelamos a un atacante qué emails están registrados.
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Email o contraseña incorrectos"));
        return tokenService.issue(user);
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
