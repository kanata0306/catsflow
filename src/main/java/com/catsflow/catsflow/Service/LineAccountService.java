package com.catsflow.catsflow.Service;

import com.catsflow.catsflow.Entity.User;
import com.catsflow.catsflow.Repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LineAccountService {

    private final UserRepository userRepository;
    private final LineIdTokenVerifier lineIdTokenVerifier;
    private final LineUserLinkService lineUserLinkService;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LineAccountService(
            UserRepository userRepository,
            LineIdTokenVerifier lineIdTokenVerifier,
            LineUserLinkService lineUserLinkService,
            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.lineIdTokenVerifier = lineIdTokenVerifier;
        this.lineUserLinkService = lineUserLinkService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public LoginResult loginOrRegister(String lineIdToken, String email, String password, String name) {
        Optional<String> verifiedLineUserId = lineIdTokenVerifier.verify(lineIdToken);
        if (verifiedLineUserId.isEmpty()) {
            return LoginResult.failure("LINEログインを確認できませんでした。LIFFを開き直してください。");
        }

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return LoginResult.failure("有効なメールアドレスを入力してください。");
        }
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return LoginResult.failure("パスワードは12文字以上、UTF-8で72バイト以内にしてください。");
        }

        String lineUserId = verifiedLineUserId.get();
        Optional<Long> alreadyLinkedUser = lineUserLinkService.findByLineUserId(lineUserId)
                .map(link -> link.getUserId());
        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
        if (alreadyLinkedUser.isPresent()
                && (existingUser.isEmpty() || !alreadyLinkedUser.get().equals(existingUser.get().getId()))) {
            return LoginResult.failure("このLINEアカウントは別のCatsflowアカウントと連携済みです。");
        }

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                return LoginResult.failure("メールアドレスまたはパスワードが違います。");
            }
        } else {
            String normalizedName = name == null ? "" : name.trim();
            if (normalizedName.isBlank() || normalizedName.length() > 100) {
                return LoginResult.failure("新規登録には100文字以内の名前が必要です。");
            }

            user = userRepository.create(normalizedName, normalizedEmail, passwordEncoder.encode(password));
            jdbcTemplate.update(
                    "INSERT INTO accounts (user_id, name, type) VALUES (?, ?, ?)",
                    user.getId(), "現金", "現金");
        }

        lineUserLinkService.linkUser(user.getId(), lineUserId);
        return LoginResult.success(user.getName());
    }

    public record LoginResult(boolean successful, String message) {
        private static LoginResult failure(String message) {
            return new LoginResult(false, message);
        }

        private static LoginResult success(String name) {
            return new LoginResult(true, name + "さんのCatsflowアカウントと連携しました。LINEで『今月』と送ると家計を確認できます。");
        }
    }
}
