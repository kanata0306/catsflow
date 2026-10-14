package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Entity.User;
import com.catsflow.catsflow.Repository.UserRepository;
import com.catsflow.catsflow.Service.LineAccountService;
import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import com.catsflow.catsflow.Service.LineUserLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LineAccountServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LineIdTokenVerifier tokenVerifier = mock(LineIdTokenVerifier.class);
    private final LineUserLinkService lineUserLinkService = mock(LineUserLinkService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final LineAccountService service = new LineAccountService(
            userRepository, tokenVerifier, lineUserLinkService, jdbcTemplate);

    @BeforeEach
    void verifyLineIdentity() {
        when(tokenVerifier.verify("valid-line-token")).thenReturn(java.util.Optional.of("U_verified"));
    }

    @Test
    void registersAccountAndLinksVerifiedLineUser() {
        User created = new User();
        created.setId(42L);
        created.setName("Test User");
        when(userRepository.findByEmail("new@example.com")).thenReturn(java.util.Optional.empty());
        when(userRepository.create(org.mockito.ArgumentMatchers.eq("Test User"),
                org.mockito.ArgumentMatchers.eq("new@example.com"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(created);

        var result = service.loginOrRegister("valid-line-token", "NEW@example.com", "correct horse battery",
                "Test User");

        assertThat(result.successful()).isTrue();
        verify(userRepository).create(org.mockito.ArgumentMatchers.eq("Test User"),
                org.mockito.ArgumentMatchers.eq("new@example.com"), org.mockito.ArgumentMatchers
                        .argThat(hash -> new BCryptPasswordEncoder().matches("correct horse battery", hash)));
        verify(jdbcTemplate).update("INSERT INTO accounts (user_id, name, type) VALUES (?, ?, ?)", 42L, "現金", "現金");
        verify(lineUserLinkService).linkUser(42L, "U_verified");
    }

    @Test
    void rejectsWrongPasswordWithoutLinkingLineAccount() {
        User user = new User();
        user.setId(7L);
        user.setName("Existing User");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("a different secure password"));
        when(userRepository.findByEmail("existing@example.com")).thenReturn(java.util.Optional.of(user));

        var result = service.loginOrRegister("valid-line-token", "existing@example.com", "incorrect password",
                null);

        assertThat(result.successful()).isFalse();
        verify(lineUserLinkService, never()).linkUser(7L, "U_verified");
    }

    @Test
    void rejectsUnverifiedLineIdentity() {
        when(tokenVerifier.verify("forged-token")).thenReturn(java.util.Optional.empty());

        var result = service.loginOrRegister("forged-token", "new@example.com", "correct horse battery",
                "Test User");

        assertThat(result.successful()).isFalse();
        verify(userRepository, never()).create(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
