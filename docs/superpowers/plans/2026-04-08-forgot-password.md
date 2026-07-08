# Elfelejtett jelszó & pre-generált felhasználók emailjei — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Token-alapú jelszó-visszaállítás emailen keresztül, "Elfelejtett jelszó?" link a bejelentkezési oldalon, és @idopontfoglalo.hu emailek az összes pre-generált felhasználóhoz.

**Architecture:** Új `PasswordResetToken` JPA entitás tárolja az UUID tokent és lejáratát (1 óra). A `PasswordResetService` kezeli a token generálást, validálást és a jelszó frissítést. A `PasswordResetController` két Thymeleaf oldalon keresztül kezeli a flow-t (`forgot-password.html`, `reset-password.html`). Az email küldés a meglévő `EmailService.sendSimpleMessage()` metóduson keresztül történik. A `DataInitializer` idempotens email-beállítással frissíti a pre-generált felhasználókat.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Spring Security, Thymeleaf, Bootstrap 5, H2 (dev) / PostgreSQL 15 (Docker), JavaMailSender (Mailtrap)

**PostgreSQL kompatibilitás:** Standard JPA annotációk (`@GeneratedValue(strategy = GenerationType.IDENTITY)`, `LocalDateTime` → TIMESTAMP), `ddl-auto=update` automatikusan létrehozza az új táblát mindkét DB-ben. A `app.base-url` property Docker env variableként is felülírható.

---

## Fájlstruktúra

| Státusz | Fájl | Felelősség |
|---------|------|------------|
| ÚJ | `entity/PasswordResetToken.java` | Token entitás (UUID, user FK, lejárat) |
| ÚJ | `repository/PasswordResetTokenRepository.java` | Token CRUD + keresés |
| ÚJ | `service/PasswordResetService.java` | Token generálás, validálás, jelszó reset |
| ÚJ | `controller/PasswordResetController.java` | HTTP endpoint-ok (4 db) |
| ÚJ | `templates/forgot-password.html` | Email bekérő form |
| ÚJ | `templates/reset-password.html` | Új jelszó form |
| MÓD | `resources/application.properties` | `app.base-url` hozzáadása |
| MÓD | `security/SecurityConfig.java` | Publikus route-ok bővítése |
| MÓD | `config/DataInitializer.java` | Email mezők hozzáadása (idempotens) |
| MÓD | `templates/login.html` | "Elfelejtett jelszó?" link + `?passwordReset` alert |

---

## Task 1: `app.base-url` property hozzáadása

**Fájlok:**
- Módosítás: `src/main/resources/application.properties`

Ez a property szükséges az emailben küldött reset link generálásához. Dev-ben `http://localhost:8080`, Docker-ben env variable-lel felülírható.

- [ ] **Step 1: Sor hozzáadása az application.properties végéhez**

A fájl végéhez (az `app.email.from` sor után) add hozzá:

```properties
app.base-url=http://localhost:8080
```

- [ ] **Step 2: Ellenőrzés**

Nyisd meg `src/main/resources/application.properties` — az `app.base-url=http://localhost:8080` sor szerepeljen benne.

---

## Task 2: `PasswordResetToken` entitás létrehozása

**Fájlok:**
- Létrehozás: `src/main/java/com/BC/Idopontfoglalo/entity/PasswordResetToken.java`

Standard JPA annotációkat használ — H2 és PostgreSQL kompatibilis. A `password_reset_tokens` tábla `ddl-auto=update` révén automatikusan jön létre.

- [ ] **Step 1: Entitás fájl létrehozása**

```java
package com.BC.Idopontfoglalo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    public PasswordResetToken() {}

    public PasswordResetToken(String token, User user, LocalDateTime expiryDate) {
        this.token = token;
        this.user = user;
        this.expiryDate = expiryDate;
    }

    public Long getId() { return id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}
```

---

## Task 3: `PasswordResetTokenRepository` létrehozása

**Fájlok:**
- Létrehozás: `src/main/java/com/BC/Idopontfoglalo/repository/PasswordResetTokenRepository.java`

- [ ] **Step 1: Repository interfész létrehozása**

```java
package com.BC.Idopontfoglalo.repository;

import com.BC.Idopontfoglalo.entity.PasswordResetToken;
import com.BC.Idopontfoglalo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUser(User user);
}
```

---

## Task 4: `PasswordResetService` létrehozása

**Fájlok:**
- Létrehozás: `src/main/java/com/BC/Idopontfoglalo/service/PasswordResetService.java`

A service kezeli a teljes reset flow üzleti logikáját: token generálás + mentés, email küldés, token validálás, jelszó frissítés.

- [ ] **Step 1: Service fájl létrehozása**

```java
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
```

---

## Task 5: `PasswordResetController` létrehozása

**Fájlok:**
- Létrehozás: `src/main/java/com/BC/Idopontfoglalo/controller/PasswordResetController.java`

4 endpoint: `GET /forgot-password`, `POST /forgot-password`, `GET /reset-password`, `POST /reset-password`.

- [ ] **Step 1: Controller fájl létrehozása**

```java
package com.BC.Idopontfoglalo.controller;

import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

    @Autowired
    private PasswordResetService passwordResetService;

    // ---- Elfelejtett jelszó oldal ----

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(@RequestParam(required = false) String sent, Model model) {
        if ("true".equals(sent)) {
            model.addAttribute("sent", true);
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email) {
        passwordResetService.initiatePasswordReset(email);
        // Mindig ugyanoda irányítunk (biztonsági okokból)
        return "redirect:/forgot-password?sent=true";
    }

    // ---- Jelszó visszaállítás oldal ----

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        User user = passwordResetService.validateToken(token);
        if (user == null) {
            model.addAttribute("invalidToken", true);
            return "reset-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
                                       @RequestParam String newPassword,
                                       @RequestParam String confirmPassword,
                                       Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("passwordMismatch", true);
            return "reset-password";
        }
        if (newPassword.length() < 6) {
            model.addAttribute("token", token);
            model.addAttribute("passwordTooShort", true);
            return "reset-password";
        }
        boolean success = passwordResetService.resetPassword(token, newPassword);
        if (!success) {
            model.addAttribute("invalidToken", true);
            return "reset-password";
        }
        return "redirect:/login?passwordReset=true";
    }
}
```

---

## Task 6: `SecurityConfig` frissítése

**Fájlok:**
- Módosítás: `src/main/java/com/BC/Idopontfoglalo/security/SecurityConfig.java` (28. sor)

A `/forgot-password` és `/reset-password` útvonalakat publikusra kell állítani, különben a Spring Security login oldalra irányítja a nem autentikált felhasználókat.

- [ ] **Step 1: Publikus route-ok bővítése**

A `SecurityConfig.java` 28. sorában a meglévő:

```java
.requestMatchers("/login","/css/**","/js/**").permitAll()
.requestMatchers("/register").permitAll()
```

Cseréld erre:

```java
.requestMatchers("/login","/css/**","/js/**").permitAll()
.requestMatchers("/register").permitAll()
.requestMatchers("/forgot-password", "/reset-password").permitAll()
```

---

## Task 7: `forgot-password.html` létrehozása

**Fájlok:**
- Létrehozás: `src/main/resources/templates/forgot-password.html`

A `login.html` stílusát és CSS változóit követi (navy/teal design system). Két állapot: email bekérő form, és "email elküldve" visszajelzés.

- [ ] **Step 1: Template fájl létrehozása**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Elfelejtett jelszó – Időpontfoglaló</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@600;700&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
    <style>
        :root {
            --navy: #0f1f3d; --navy-soft: #1a2f52; --teal: #00a896; --teal-light: #02c9b3;
            --teal-dim: rgba(0,168,150,.12); --red: #e84545; --green: #2ecc71;
            --bg: #f0f3f8; --surface: #ffffff; --surface-2: #f7f9fc;
            --border: #e2e8f0; --text-primary: #0f1f3d; --text-muted: #64748b; --text-light: #94a3b8;
            --shadow-sm: 0 1px 4px rgba(15,31,61,.06); --shadow-md: 0 4px 16px rgba(15,31,61,.10);
            --shadow-lg: 0 8px 32px rgba(15,31,61,.14); --r-sm: 6px; --r-md: 10px; --r-lg: 16px; --r-xl: 24px;
            --t: .25s cubic-bezier(.4,0,.2,1);
        }
        *, *::before, *::after { box-sizing: border-box; }
        body { font-family: 'DM Sans', sans-serif; background: var(--bg); color: var(--text-primary); min-height: 100vh; margin: 0; }
        .hero-section {
            background: linear-gradient(135deg, var(--navy) 0%, var(--navy-soft) 60%, #1e3a6e 100%);
            color: white; padding: 4rem 0; margin-bottom: 3rem; position: relative; overflow: hidden;
        }
        .hero-section::before {
            content: ''; position: absolute; inset: 0;
            background: radial-gradient(ellipse at 70% 50%, rgba(0,168,150,.2) 0%, transparent 65%);
        }
        .hero-section::after {
            content: ''; position: absolute; bottom: -1px; left: 0; right: 0;
            height: 36px; background: var(--bg); clip-path: ellipse(55% 100% at 50% 100%);
        }
        .hero-section h1 { font-family: 'Playfair Display', serif; font-weight: 700; letter-spacing: -.5px; }
        .hero-section .lead { opacity: .8; font-weight: 300; }
        .hero-logo { font-size: 2.5rem; margin-bottom: .75rem; color: var(--teal-light); }
        .auth-card {
            background: var(--surface); border: 1px solid var(--border) !important;
            border-radius: var(--r-xl) !important; box-shadow: var(--shadow-lg);
            max-width: 480px; margin: 0 auto; transition: var(--t);
        }
        .auth-card:hover { transform: translateY(-4px); box-shadow: 0 16px 48px rgba(15,31,61,.16); }
        .auth-card .card-body { padding: 2.5rem; }
        .title-decoration { position: relative; padding-bottom: 14px; margin-bottom: 1.75rem; font-family: 'Playfair Display', serif; }
        .title-decoration::after {
            content: ''; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%);
            width: 40px; height: 3px; background: var(--teal); border-radius: 2px;
        }
        .form-label { font-weight: 500; color: var(--text-primary); font-size: .9rem; margin-bottom: .4rem; }
        .form-control {
            background: var(--surface-2); border: 1.5px solid var(--border);
            border-radius: var(--r-sm); color: var(--text-primary); padding: .65rem 1rem; transition: var(--t);
        }
        .form-control:focus { border-color: var(--teal); box-shadow: 0 0 0 3px rgba(0,168,150,.15); background: var(--surface); }
        .btn-primary {
            background: var(--teal); border: none; font-family: 'DM Sans', sans-serif;
            font-weight: 600; padding: .75rem 2rem; border-radius: var(--r-sm); letter-spacing: .02em; transition: var(--t);
        }
        .btn-primary:hover { background: var(--teal-light); transform: translateY(-1px); box-shadow: 0 4px 16px rgba(0,168,150,.3); }
        .alert { border-radius: var(--r-md); border: 0; border-left: 4px solid; padding: .9rem 1.1rem; font-size: .9rem; }
        .alert-success { background: #e8faf5; border-color: var(--green); color: #1a6b45; }
        a { color: var(--teal); text-decoration: none; }
        a:hover { color: var(--teal-light); }
        footer { background: var(--surface) !important; border-top: 1px solid var(--border); padding: 1.5rem 0; color: var(--text-muted); font-size: .88rem; }
    </style>
</head>
<body>
<div class="hero-section">
    <div class="container text-center position-relative">
        <div class="hero-logo"><i class="fas fa-key"></i></div>
        <h1 class="display-4 fw-bold mb-2">Jelszó visszaállítás</h1>
        <p class="lead mb-0">Megküldjük a visszaállítási linket emailben</p>
    </div>
</div>

<div class="container">
    <div class="row justify-content-center">
        <div class="col-lg-6">
            <div class="card auth-card">
                <div class="card-body">

                    <!-- Email elküldve állapot -->
                    <div th:if="${sent}">
                        <div class="text-center mb-4">
                            <div style="font-size:3rem; color:var(--teal); margin-bottom:1rem;">
                                <i class="fas fa-envelope-open-text"></i>
                            </div>
                            <h2 class="title-decoration">Email elküldve</h2>
                            <p class="text-muted">Ha a megadott email cím regisztrált a rendszerben, hamarosan megkap egy visszaállítási linket.</p>
                            <p class="text-muted" style="font-size:.88rem;">A link <strong>1 óráig</strong> érvényes.</p>
                        </div>
                        <div class="d-grid">
                            <a th:href="@{/login}" class="btn btn-primary btn-lg">
                                <i class="fas fa-arrow-left me-2"></i>Vissza a bejelentkezéshez
                            </a>
                        </div>
                    </div>

                    <!-- Email bekérő form -->
                    <div th:unless="${sent}">
                        <h2 class="text-center title-decoration">Elfelejtett jelszó</h2>
                        <p class="text-muted text-center mb-4" style="font-size:.9rem;">
                            Add meg a regisztrált email címed, és küldünk egy visszaállítási linket.
                        </p>
                        <form th:action="@{/forgot-password}" method="post">
                            <div class="mb-4">
                                <label for="email" class="form-label">Email cím</label>
                                <input type="email" class="form-control" name="email" id="email"
                                       placeholder="pelda@email.hu" required>
                            </div>
                            <div class="d-grid">
                                <button type="submit" class="btn btn-primary btn-lg">
                                    <i class="fas fa-paper-plane me-2"></i>Link küldése
                                </button>
                            </div>
                        </form>
                        <p class="text-center text-muted mt-4 mb-0" style="font-size:.9rem;">
                            <a th:href="@{/login}"><i class="fas fa-arrow-left me-1"></i>Vissza a bejelentkezéshez</a>
                        </p>
                    </div>

                </div>
            </div>
        </div>
    </div>
</div>

<footer class="mt-5">
    <div class="container text-center">
        <p class="mb-0"><i class="fas fa-calendar-check me-2" style="color:var(--teal)"></i>Időpontfoglaló Rendszer &copy; <span th:text="${#dates.year(#dates.createNow())}">2025</span></p>
    </div>
</footer>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
```

---

## Task 8: `reset-password.html` létrehozása

**Fájlok:**
- Létrehozás: `src/main/resources/templates/reset-password.html`

Három állapotot kezel: érvényes token (új jelszó form), jelszó nem egyezik (hibaüzenet + form), érvénytelen/lejárt token (hibaüzenet + link).

- [ ] **Step 1: Template fájl létrehozása**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Új jelszó beállítása – Időpontfoglaló</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@600;700&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
    <style>
        :root {
            --navy: #0f1f3d; --navy-soft: #1a2f52; --teal: #00a896; --teal-light: #02c9b3;
            --red: #e84545; --green: #2ecc71;
            --bg: #f0f3f8; --surface: #ffffff; --surface-2: #f7f9fc;
            --border: #e2e8f0; --text-primary: #0f1f3d; --text-muted: #64748b;
            --shadow-lg: 0 8px 32px rgba(15,31,61,.14); --r-sm: 6px; --r-md: 10px; --r-xl: 24px;
            --t: .25s cubic-bezier(.4,0,.2,1);
        }
        *, *::before, *::after { box-sizing: border-box; }
        body { font-family: 'DM Sans', sans-serif; background: var(--bg); color: var(--text-primary); min-height: 100vh; margin: 0; }
        .hero-section {
            background: linear-gradient(135deg, var(--navy) 0%, var(--navy-soft) 60%, #1e3a6e 100%);
            color: white; padding: 4rem 0; margin-bottom: 3rem; position: relative; overflow: hidden;
        }
        .hero-section::before {
            content: ''; position: absolute; inset: 0;
            background: radial-gradient(ellipse at 70% 50%, rgba(0,168,150,.2) 0%, transparent 65%);
        }
        .hero-section::after {
            content: ''; position: absolute; bottom: -1px; left: 0; right: 0;
            height: 36px; background: var(--bg); clip-path: ellipse(55% 100% at 50% 100%);
        }
        .hero-section h1 { font-family: 'Playfair Display', serif; font-weight: 700; letter-spacing: -.5px; }
        .hero-section .lead { opacity: .8; font-weight: 300; }
        .hero-logo { font-size: 2.5rem; margin-bottom: .75rem; color: var(--teal-light); }
        .auth-card {
            background: var(--surface); border: 1px solid var(--border) !important;
            border-radius: var(--r-xl) !important; box-shadow: var(--shadow-lg);
            max-width: 480px; margin: 0 auto; transition: var(--t);
        }
        .auth-card:hover { transform: translateY(-4px); box-shadow: 0 16px 48px rgba(15,31,61,.16); }
        .auth-card .card-body { padding: 2.5rem; }
        .title-decoration { position: relative; padding-bottom: 14px; margin-bottom: 1.75rem; font-family: 'Playfair Display', serif; }
        .title-decoration::after {
            content: ''; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%);
            width: 40px; height: 3px; background: var(--teal); border-radius: 2px;
        }
        .form-label { font-weight: 500; color: var(--text-primary); font-size: .9rem; margin-bottom: .4rem; }
        .form-control {
            background: var(--surface-2); border: 1.5px solid var(--border);
            border-radius: var(--r-sm); color: var(--text-primary); padding: .65rem 1rem; transition: var(--t);
        }
        .form-control:focus { border-color: var(--teal); box-shadow: 0 0 0 3px rgba(0,168,150,.15); background: var(--surface); }
        .btn-primary {
            background: var(--teal); border: none; font-family: 'DM Sans', sans-serif;
            font-weight: 600; padding: .75rem 2rem; border-radius: var(--r-sm); letter-spacing: .02em; transition: var(--t);
        }
        .btn-primary:hover { background: var(--teal-light); transform: translateY(-1px); box-shadow: 0 4px 16px rgba(0,168,150,.3); }
        .alert { border-radius: var(--r-md); border: 0; border-left: 4px solid; padding: .9rem 1.1rem; font-size: .9rem; }
        .alert-danger { background: #fef0f0; border-color: var(--red); color: #8b1a1a; }
        a { color: var(--teal); text-decoration: none; }
        a:hover { color: var(--teal-light); }
        footer { background: var(--surface) !important; border-top: 1px solid var(--border); padding: 1.5rem 0; color: var(--text-muted); font-size: .88rem; }
    </style>
</head>
<body>
<div class="hero-section">
    <div class="container text-center position-relative">
        <div class="hero-logo"><i class="fas fa-lock-open"></i></div>
        <h1 class="display-4 fw-bold mb-2">Új jelszó beállítása</h1>
        <p class="lead mb-0">Adj meg egy új, biztonságos jelszót</p>
    </div>
</div>

<div class="container">
    <div class="row justify-content-center">
        <div class="col-lg-6">
            <div class="card auth-card">
                <div class="card-body">

                    <!-- Érvénytelen / lejárt token -->
                    <div th:if="${invalidToken}">
                        <div class="text-center mb-4">
                            <div style="font-size:3rem; color:var(--red); margin-bottom:1rem;">
                                <i class="fas fa-times-circle"></i>
                            </div>
                            <h2 class="title-decoration">Érvénytelen link</h2>
                            <p class="text-muted">Ez a visszaállítási link lejárt vagy már felhasználásra került.</p>
                        </div>
                        <div class="d-grid">
                            <a th:href="@{/forgot-password}" class="btn btn-primary btn-lg">
                                <i class="fas fa-redo me-2"></i>Új link kérése
                            </a>
                        </div>
                    </div>

                    <!-- Új jelszó form (érvényes token) -->
                    <div th:unless="${invalidToken}">
                        <h2 class="text-center title-decoration">Új jelszó</h2>

                        <div th:if="${passwordMismatch}" class="alert alert-danger mb-3" role="alert">
                            <i class="fas fa-exclamation-circle me-2"></i>A két jelszó nem egyezik!
                        </div>
                        <div th:if="${passwordTooShort}" class="alert alert-danger mb-3" role="alert">
                            <i class="fas fa-exclamation-circle me-2"></i>A jelszónak legalább 6 karakter hosszúnak kell lennie!
                        </div>

                        <form th:action="@{/reset-password}" method="post">
                            <input type="hidden" name="token" th:value="${token}">
                            <div class="mb-3">
                                <label for="newPassword" class="form-label">Új jelszó</label>
                                <input type="password" class="form-control" name="newPassword" id="newPassword"
                                       placeholder="Minimum 6 karakter" required>
                            </div>
                            <div class="mb-4">
                                <label for="confirmPassword" class="form-label">Jelszó megerősítése</label>
                                <input type="password" class="form-control" name="confirmPassword" id="confirmPassword"
                                       placeholder="Jelszó ismétlése" required>
                            </div>
                            <div class="d-grid">
                                <button type="submit" class="btn btn-primary btn-lg">
                                    <i class="fas fa-save me-2"></i>Jelszó mentése
                                </button>
                            </div>
                        </form>
                    </div>

                </div>
            </div>
        </div>
    </div>
</div>

<footer class="mt-5">
    <div class="container text-center">
        <p class="mb-0"><i class="fas fa-calendar-check me-2" style="color:var(--teal)"></i>Időpontfoglaló Rendszer &copy; <span th:text="${#dates.year(#dates.createNow())}">2025</span></p>
    </div>
</footer>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
```

---

## Task 9: `login.html` frissítése

**Fájlok:**
- Módosítás: `src/main/resources/templates/login.html`

Két változás: (1) "Elfelejtett jelszó?" link a form alá, (2) `?passwordReset=true` alert a sikeres reset után.

- [ ] **Step 1: `?passwordReset` alert hozzáadása**

A `login.html`-ben a meglévő `th:if="${param.logout}"` alert blokk (106-109. sorok) **után** add hozzá:

```html
    <div th:if="${param.passwordReset}" class="alert alert-success alert-dismissible fade show mb-4" role="alert">
        <i class="fas fa-check-circle me-2"></i>Jelszavad sikeresen megváltoztatva! Kérjük, jelentkezz be az új jelszóval.
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
```

- [ ] **Step 2: "Elfelejtett jelszó?" link hozzáadása**

A `login.html`-ben a meglévő regisztrációs bekezdés (131-133. sorok):

```html
                    <p class="text-center text-muted mt-4 mb-0" style="font-size:.9rem;">
                        Még nincs fiókja? <a th:href="@{/register}">Regisztráljon itt!</a>
                    </p>
```

Cseréld erre:

```html
                    <div class="text-center mt-4" style="font-size:.9rem;">
                        <p class="text-muted mb-1">
                            Még nincs fiókja? <a th:href="@{/register}">Regisztráljon itt!</a>
                        </p>
                        <p class="mb-0">
                            <a th:href="@{/forgot-password}" style="color:var(--text-muted);">
                                <i class="fas fa-key me-1"></i>Elfelejtett jelszó?
                            </a>
                        </p>
                    </div>
```

---

## Task 10: `DataInitializer` frissítése — pre-generált felhasználók emailjei

**Fájlok:**
- Módosítás: `src/main/java/com/BC/Idopontfoglalo/config/DataInitializer.java`

**Két részből áll:**

1. Email hozzáadása az újonnan létrehozott felhasználókhoz (a meglévő `if` blokkokban)
2. Idempotens email-frissítő blokk a metódus végén — hogy meglévő DB-k esetén is beállításra kerüljenek az emailek (H2 file DB, ahol már vannak felhasználók email nélkül)

- [ ] **Step 1: Email hozzáadása a superadmin és admin létrehozáshoz**

A `DataInitializer.java`-ban a `superAdmin` objektumhoz (53-58. sorok között) add hozzá a `.setEmail()` hívást:

```java
                User superAdmin = new User();
                superAdmin.setUsername("superadmin");
                superAdmin.setEmail("superadmin@idopontfoglalo.hu");
                superAdmin.setPassword(passwordEncoder.encode("password"));
                superAdmin.setEnabled(true);
                superAdmin.setRoles(Collections.singleton(superAdminRole));
                userRepository.save(superAdmin);

                Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN");
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@idopontfoglalo.hu");
                admin.setPassword(passwordEncoder.encode("password"));
                admin.setEnabled(true);
                admin.setRoles(Collections.singleton(adminRole));
                userRepository.save(admin);
```

- [ ] **Step 2: Email hozzáadása a department admin létrehozáshoz**

A `DataInitializer.java`-ban a három department admin objektumhoz add hozzá a `.setEmail()` hívásokat:

```java
                // Orvosi admin
                User medicalAdmin = new User();
                medicalAdmin.setUsername("orvosi_admin");
                medicalAdmin.setEmail("orvosi_admin@idopontfoglalo.hu");
                medicalAdmin.setPassword(passwordEncoder.encode("password"));
                medicalAdmin.setEnabled(true);
                medicalAdmin.setRoles(Collections.singleton(departmentAdminRole));
                medicalAdmin.setManagedDepartment(medicalDept);
                userRepository.save(medicalAdmin);

                // Fogorvosi admin
                User dentalAdmin = new User();
                dentalAdmin.setUsername("fogorvosi_admin");
                dentalAdmin.setEmail("fogorvosi_admin@idopontfoglalo.hu");
                dentalAdmin.setPassword(passwordEncoder.encode("password"));
                dentalAdmin.setEnabled(true);
                dentalAdmin.setRoles(Collections.singleton(departmentAdminRole));
                dentalAdmin.setManagedDepartment(dentalDept);
                userRepository.save(dentalAdmin);

                // Lab admin
                User labAdmin = new User();
                labAdmin.setUsername("lab_admin");
                labAdmin.setEmail("lab_admin@idopontfoglalo.hu");
                labAdmin.setPassword(passwordEncoder.encode("password"));
                labAdmin.setEnabled(true);
                labAdmin.setRoles(Collections.singleton(departmentAdminRole));
                labAdmin.setManagedDepartment(labDept);
                userRepository.save(labAdmin);
```

- [ ] **Step 3: Email hozzáadása a testuser létrehozáshoz**

A `DataInitializer.java`-ban a testuser objektumhoz (181-188. sorok):

```java
            if (!userRepository.findByUsername("testuser").isPresent()) {
                Role userRole = roleRepository.findByRoleName("ROLE_USER");
                User testUser = new User();
                testUser.setUsername("testuser");
                testUser.setEmail("testuser@idopontfoglalo.hu");
                testUser.setPassword(passwordEncoder.encode("password"));
                testUser.setEnabled(true);
                testUser.setRoles(Collections.singleton(userRole));
                userRepository.save(testUser);
            }
```

- [ ] **Step 4: Idempotens email-frissítő blokk hozzáadása**

A `return args -> {` lambda **végén** (a testuser blokk után, de még a `};` előtt) add hozzá ezt a blokkot. Ez gondoskodik arról, hogy a már létező H2 DB-ben lévő felhasználók is megkapják az emailjeiket:

```java
            // ========== EMAIL FRISSÍTÉS MEGLÉVŐ FELHASZNÁLÓKNAK ==========
            // Idempotens: csak akkor állít be emailt, ha még nincs megadva
            updateEmailIfMissing(userRepository, "superadmin", "superadmin@idopontfoglalo.hu");
            updateEmailIfMissing(userRepository, "admin", "admin@idopontfoglalo.hu");
            updateEmailIfMissing(userRepository, "orvosi_admin", "orvosi_admin@idopontfoglalo.hu");
            updateEmailIfMissing(userRepository, "fogorvosi_admin", "fogorvosi_admin@idopontfoglalo.hu");
            updateEmailIfMissing(userRepository, "lab_admin", "lab_admin@idopontfoglalo.hu");
            updateEmailIfMissing(userRepository, "testuser", "testuser@idopontfoglalo.hu");
```

- [ ] **Step 5: `updateEmailIfMissing` segédmetódus hozzáadása a `DataInitializer` osztályba**

Az osztály zárójele előtt (a `}` előtt, a `initData` bean metódus után) add hozzá:

```java
    private void updateEmailIfMissing(
            com.BC.Idopontfoglalo.repository.UserRepository userRepository,
            String username, String email) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                user.setEmail(email);
                userRepository.save(user);
            }
        });
    }
```

---

## Task 11: Végső ellenőrzés

- [ ] **Step 1: Alkalmazás indítása**

```bash
cd Szakdolgozat_Idopontfoglalo
./mvnw spring-boot:run
```

Várt output a konzolon: `Started IdopontfoglaloApplication` (BUILD errors nélkül).

- [ ] **Step 2: Login oldal ellenőrzése**

Nyisd meg: `http://localhost:8080/login`

Elvárt: az "Elfelejtett jelszó?" link megjelenik a form alatt a regisztrációs link mellett.

- [ ] **Step 3: Forgot password flow tesztelése**

1. Kattints az "Elfelejtett jelszó?" linkre → `http://localhost:8080/forgot-password`
2. Add meg: `testuser@idopontfoglalo.hu`
3. Kattints "Link küldése" → Elvárt: "Email elküldve" visszajelzés jelenik meg
4. Nyisd meg a Mailtrap inbox-ot (`https://mailtrap.io`) → Elvárt: reset email érkezett reset linkkel

- [ ] **Step 4: Reset link tesztelése**

1. Kattints a Mailtrap-ben kapott `/reset-password?token=...` linkre
2. Elvárt: új jelszó form jelenik meg
3. Add meg: `ujJelszó123` mindkét mezőbe
4. Kattints "Jelszó mentése" → Elvárt: redirect `/login?passwordReset=true` zöld alerttel

- [ ] **Step 5: Bejelentkezés az új jelszóval**

1. Felhasználónév: `testuser`, jelszó: `ujJelszó123`
2. Elvárt: sikeres bejelentkezés, redirect az appointment oldalra

- [ ] **Step 6: Lejárt token tesztelése**

1. Nyisd meg: `http://localhost:8080/reset-password?token=nemletezik`
2. Elvárt: "Érvénytelen link" hibaüzenet + "Új link kérése" gomb

- [ ] **Step 7: H2 console ellenőrzése**

Nyisd meg: `http://localhost:8080/h2-console`
- Futtasd: `SELECT * FROM APP_USERS WHERE EMAIL IS NOT NULL` → Elvárt: mind a 6 pre-generált felhasználó emailje látható
- Futtasd: `SELECT * FROM PASSWORD_RESET_TOKENS` → Elvárt: üres tábla (reset után a token törlődött)
