package com.BC.Idopontfoglalo.service;

import com.BC.Idopontfoglalo.entity.PasswordResetToken;
import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.repository.PasswordResetTokenRepository;
import com.BC.Idopontfoglalo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Ha az email létezik a DB-ben: törli a régi tokeneket, generál újat,
     * elmenti és elküldi az emailt.
     * Ha nem létezik: csendben nem csinál semmit (security: ne áruljon el infót).
     */
    @Transactional
    public void initiatePasswordReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();

        // Korábbi tokenek törlése
        tokenRepository.deleteByUser(user);

        // Új token generálás (1 óra lejárat)
        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusHours(1);
        tokenRepository.save(new PasswordResetToken(token, user, expiry));

        // Email küldés
        String resetLink = baseUrl + "/reset-password?token=" + token;
        String emailBody = "Kedves " + user.getUsername() + "!\n\n"
                + "Jelszó visszaállítási kérelmet kaptunk a fiókodhoz.\n\n"
                + "Kattints az alábbi linkre az új jelszó beállításához:\n"
                + resetLink + "\n\n"
                + "Ez a link 1 óráig érvényes.\n\n"
                + "Ha nem te kérted a visszaállítást, hagyd figyelmen kívül ezt az emailt.";

        emailService.sendSimpleMessage(email, "Jelszó visszaállítás - Időpontfoglaló", emailBody);
    }

    /**
     * Token validálás: létezik és nem járt le?
     * Visszaad null-t ha érvénytelen, User-t ha érvényes.
     */
    public User validateToken(String token) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            return null;
        }
        PasswordResetToken resetToken = tokenOpt.get();
        if (resetToken.isExpired()) {
            return null;
        }
        return resetToken.getUser();
    }

    /**
     * Jelszó frissítés: token validálás, BCrypt kódolás, mentés, token törlés.
     * Visszaad false-t ha a token érvénytelen.
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);
        if (tokenOpt.isEmpty() || tokenOpt.get().isExpired()) {
            return false;
        }
        PasswordResetToken resetToken = tokenOpt.get();
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.delete(resetToken);
        return true;
    }
}
