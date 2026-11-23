package com.mollet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mollet.TestData;
import com.mollet.account.AccountService;
import com.mollet.auth.dto.LoginRequest;
import com.mollet.auth.dto.RegisterRequest;
import com.mollet.auth.dto.TokenResponse;
import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import com.mollet.user.User;
import com.mollet.user.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, accountService, passwordEncoder, tokenService, Clock.systemUTC());
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndOpensAccount() {
        when(userRepository.existsByEmail("ana@correo.com")).thenReturn(false);
        when(passwordEncoder.encode("ClaveSegura123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.issue(any())).thenReturn(TokenResponse.bearer("jwt", 3600));

        TokenResponse response = service.register(new RegisterRequest("  Ana@Correo.com ", "ClaveSegura123", "Ana"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@correo.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        verify(accountService).openAccountFor(saved.getValue());
        assertThat(response.accessToken()).isEqualTo("jwt");
    }

    @Test
    void registerRejectsDuplicatedEmail() {
        when(userRepository.existsByEmail("ana@correo.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("ana@correo.com", "ClaveSegura123", "Ana")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithWrongPasswordFailsWithGenericMessage() {
        User user = TestData.user(1, "ana@correo.com");
        when(userRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@correo.com", "mala")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void loginWithUnknownEmailFailsWithSameError() {
        when(userRepository.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nadie@correo.com", "x")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }
}
