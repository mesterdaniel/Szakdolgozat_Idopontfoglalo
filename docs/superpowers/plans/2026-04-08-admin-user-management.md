# Admin Felhasználókezelés — Implementációs Terv

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Az `/admin/users` oldal bővítése: felhasználó-szerkesztés, közvetlen jelszó-visszaállítás, törlés (cascade), és opcionális email értesítők.

**Architecture:** Új GET/POST végpontok az `AdminController`-ben; új `adminSetPassword()` metódus a `UserService`-ben; a meglévő `EmailService.sendSimpleMessage()` és `UserRepository.deleteById()` újrafelhasználása. Új `admin/edit-user.html` Thymeleaf sablon a `profile.html` mintájára.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Security, Thymeleaf 3, Bootstrap 5, JUnit 5 + Mockito, MockMvc + @SpringBootTest

---

## Érintett fájlok

| Művelet | Fájl |
|---------|------|
| Módosítás | `src/main/java/com/BC/Idopontfoglalo/service/UserService.java` |
| Módosítás | `src/main/java/com/BC/Idopontfoglalo/controller/AdminController.java` |
| Módosítás | `src/main/resources/templates/admin/users.html` |
| Létrehozás | `src/main/resources/templates/admin/edit-user.html` |
| Módosítás | `src/test/java/com/BC/Idopontfoglalo/service/UserServiceTest.java` |
| Létrehozás | `src/test/java/com/BC/Idopontfoglalo/controller/AdminControllerTest.java` |

---

## Task 1: adminSetPassword() metódus a UserService-ben

**Fájlok:**
- Módosítás: `src/main/java/com/BC/Idopontfoglalo/service/UserService.java`
- Teszt: `src/test/java/com/BC/Idopontfoglalo/service/UserServiceTest.java`

- [ ] **Lépés 1: Írjuk meg a hibázó tesztet**

Adjuk hozzá a következő tesztmetódust a `UserServiceTest` osztályhoz (a meglévő tesztek után, az osztály záró `}` kapcsos zárójele elé):

```java
// =========================================================
// 10. Admin jelszó-visszaállítás – jelszó titkosítva kerül mentésre
// =========================================================

@Test
@DisplayName("adminSetPassword: az új jelszó titkosított formában kerül az adatbázisba, jelenlegi jelszó ellenőrzése nélkül")
void adminSetPassword_SavesEncodedPassword_WithoutCurrentPasswordCheck() {
    // Arrange
    User user = new User("admin_target", "target@email.hu", "regi_hash", "Cél", "Felhasználó");
    when(passwordEncoder.encode("admin_uj_jelszo")).thenReturn("admin_uj_hash");
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    // Act
    userService.adminSetPassword(user, "admin_uj_jelszo");

    // Assert
    verify(userRepository).save(argThat(u ->
        "admin_uj_hash".equals(u.getPassword())
    ));
    // Ellenőrzés: passwordEncoder.matches() SOHA NEM HÍVÓDOTT (nincs currentPassword ellenőrzés)
    verify(passwordEncoder, never()).matches(anyString(), anyString());
}
```

- [ ] **Lépés 2: Futtassuk le, ellenőrizzük hogy hibázik**

```bash
./mvnw test -Dtest=UserServiceTest#adminSetPassword_SavesEncodedPassword_WithoutCurrentPasswordCheck
```

Várt eredmény: `FAILED` — `adminSetPassword` metódus még nem létezik.

- [ ] **Lépés 3: Implementáljuk a metódust**

A `UserService.java`-ban adjuk hozzá az alábbi metódust a `checkPassword()` után:

```java
public void adminSetPassword(User user, String newPassword) {
    user.setPassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
}
```

- [ ] **Lépés 4: Futtassuk le a tesztet, ellenőrizzük hogy átmegy**

```bash
./mvnw test -Dtest=UserServiceTest
```

Várt eredmény: minden teszt `PASS`.

- [ ] **Lépés 5: Commit**

```bash
git add src/main/java/com/BC/Idopontfoglalo/service/UserService.java \
        src/test/java/com/BC/Idopontfoglalo/service/UserServiceTest.java
git commit -m "feat: add adminSetPassword() to UserService"
```

---

## Task 2: Új végpontok az AdminController-ben

**Fájlok:**
- Módosítás: `src/main/java/com/BC/Idopontfoglalo/controller/AdminController.java`
- Létrehozás: `src/test/java/com/BC/Idopontfoglalo/controller/AdminControllerTest.java`

- [ ] **Lépés 1: Írjuk meg a hibázó teszteket**

Hozzuk létre az alábbi fájlt:

`src/test/java/com/BC/Idopontfoglalo/controller/AdminControllerTest.java`

```java
package com.BC.Idopontfoglalo.controller;

import com.BC.Idopontfoglalo.entity.Role;
import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.repository.UserRepository;
import com.BC.Idopontfoglalo.service.AppointmentService;
import com.BC.Idopontfoglalo.service.EmailService;
import com.BC.Idopontfoglalo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Az AdminController felhasználókezelési végpontjainak integrációs tesztjei.
 *
 * Tesztelt funkciók:
 *  1. GET /admin/user/{id}/edit – szerkesztési form megjelenítése
 *  2. POST /admin/user/{id}/edit – adatok mentése (jelszóval és anélkül)
 *  3. POST /admin/user/{id}/edit – jelszó-eltérés esetén hibaüzenet
 *  4. POST /admin/user/{id}/delete – felhasználó törlése
 *  5. POST /admin/user/{id}/delete – saját fiók törlésének megakadályozása
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private AppointmentService appointmentService;

    private User testUser;

    @BeforeEach
    void setUp() {
        Role userRole = new Role();
        userRole.setRoleName("ROLE_USER");
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        testUser = new User();
        testUser.setId(99L);
        testUser.setUsername("teszt_user");
        testUser.setEmail("teszt@email.hu");
        testUser.setFirstName("Teszt");
        testUser.setLastName("Felhasználó");
        testUser.setEnabled(true);
        testUser.setRoles(roles);
    }

    // =========================================================
    // 1. GET /admin/user/{id}/edit – szerkesztési form
    // =========================================================

    @Test
    @DisplayName("GET /admin/user/{id}/edit: ADMIN-ként 200 OK és az edit-user template jelenik meg")
    void editUserForm_ReturnsOk_ForAdmin() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/admin/user/99/edit")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/edit-user"));
    }

    @Test
    @DisplayName("GET /admin/user/{id}/edit: nem létező ID esetén redirect /admin/users-re")
    void editUserForm_RedirectsToUsers_WhenUserNotFound() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/user/999/edit")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    // =========================================================
    // 2. POST /admin/user/{id}/edit – adatok mentése jelszó nélkül
    // =========================================================

    @Test
    @DisplayName("POST /admin/user/{id}/edit: sikeres mentés után redirect /admin/users-re")
    void updateUser_RedirectsToUsers_OnSuccess() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.of(testUser));
        doNothing().when(userService).updateUser(any(User.class));

        mockMvc.perform(post("/admin/user/99/edit")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "uj@email.hu")
                        .param("firstName", "Új")
                        .param("lastName", "Név"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userService).updateUser(any(User.class));
    }

    // =========================================================
    // 3. POST /admin/user/{id}/edit – jelszó-eltérés hibaüzenet
    // =========================================================

    @Test
    @DisplayName("POST /admin/user/{id}/edit: jelszó-eltérés esetén redirect vissza a szerkesztési formra")
    void updateUser_RedirectsBackWithError_WhenPasswordMismatch() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.of(testUser));

        mockMvc.perform(post("/admin/user/99/edit")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "teszt@email.hu")
                        .param("newPassword", "jelszo1")
                        .param("confirmPassword", "jelszo2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/user/99/edit"));

        verify(userService, never()).adminSetPassword(any(), anyString());
    }

    // =========================================================
    // 4. POST /admin/user/{id}/delete – törlés
    // =========================================================

    @Test
    @DisplayName("POST /admin/user/{id}/delete: sikeres törlés után redirect /admin/users-re")
    void deleteUser_RedirectsToUsers_OnSuccess() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.of(testUser));
        doNothing().when(userRepository).deleteById(99L);

        mockMvc.perform(post("/admin/user/99/delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userRepository).deleteById(99L);
    }

    // =========================================================
    // 5. POST /admin/user/{id}/delete – saját fiók védelme
    // =========================================================

    @Test
    @DisplayName("POST /admin/user/{id}/delete: admin nem törölheti saját fiókját – redirect /admin/users-re")
    void deleteUser_RedirectsToUsers_WhenSelfDeletion() throws Exception {
        testUser.setUsername("admin"); // ugyanaz mint a bejelentkezett user
        when(userRepository.findById(99L)).thenReturn(Optional.of(testUser));

        mockMvc.perform(post("/admin/user/99/delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userRepository, never()).deleteById(anyLong());
    }
}
```

- [ ] **Lépés 2: Futtassuk le, ellenőrizzük hogy hibáznak**

```bash
./mvnw test -Dtest=AdminControllerTest
```

Várt eredmény: több teszt `FAILED` — a végpontok még nem léteznek.

- [ ] **Lépés 3: Adjuk hozzá a szükséges dependency-ket az AdminController-hez**

Az `AdminController.java` importjai közé adjuk hozzá (a meglévők mellé):

```java
import com.BC.Idopontfoglalo.service.EmailService;
import com.BC.Idopontfoglalo.service.UserService;
```

A class mezőkhez adjuk hozzá az `AppointmentService` mellé:

```java
@Autowired
private UserService userService;

@Autowired
private EmailService emailService;
```

- [ ] **Lépés 4: Adjuk hozzá a GET /user/{id}/edit végpontot**

Az `AdminController`-ben a `// ========== HIBAKEZELÉS ==========` rész elé szúrjuk be:

```java
/**
 * Felhasználó szerkesztési form megjelenítése
 */
@GetMapping("/user/{id}/edit")
public String showEditUserForm(@PathVariable Long id, Model model, Authentication authentication) {
    try {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nem található felhasználó ezzel az ID-val: " + id));
        model.addAttribute("user", user);
        model.addAttribute("username", authentication.getName());
        return "admin/edit-user";
    } catch (IllegalArgumentException e) {
        return "redirect:/admin/users";
    }
}
```

- [ ] **Lépés 5: Adjuk hozzá a POST /user/{id}/edit végpontot**

A GET végpont után szúrjuk be:

```java
/**
 * Felhasználó adatainak mentése (admin általi szerkesztés)
 */
@PostMapping("/user/{id}/edit")
public String updateUser(@PathVariable Long id,
                         @RequestParam String email,
                         @RequestParam(required = false) String firstName,
                         @RequestParam(required = false) String lastName,
                         @RequestParam(required = false) String newPassword,
                         @RequestParam(required = false) String confirmPassword,
                         @RequestParam(defaultValue = "false") boolean sendEmail,
                         RedirectAttributes redirectAttributes) {
    try {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nem található felhasználó"));

        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);

        if (newPassword != null && !newPassword.isEmpty()) {
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Az új jelszavak nem egyeznek!");
                return "redirect:/admin/user/" + id + "/edit";
            }
            userService.adminSetPassword(user, newPassword);
        } else {
            userService.updateUser(user);
        }

        if (sendEmail && user.getEmail() != null && !user.getEmail().isEmpty()) {
            String text = String.format(
                "Kedves %s!\n\nTájékoztatjuk, hogy az Ön fiókjának adatait egy rendszeradminisztrátor módosította.\n\nÜdvözlettel,\nAz Időpontfoglaló csapata",
                user.getFirstName() != null ? user.getFirstName() : user.getUsername()
            );
            emailService.sendSimpleMessage(user.getEmail(), "Fiókadatok módosítva - Időpontfoglaló", text);
        }

        redirectAttributes.addFlashAttribute("success",
                "Felhasználó (" + user.getUsername() + ") sikeresen frissítve!");

    } catch (IllegalArgumentException e) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
    }

    return "redirect:/admin/users";
}
```

- [ ] **Lépés 6: Adjuk hozzá a POST /user/{id}/delete végpontot**

A POST /user/{id}/edit végpont után szúrjuk be:

```java
/**
 * Felhasználó törlése (cascade: időpontok is törlődnek)
 */
@PostMapping("/user/{id}/delete")
public String deleteUser(@PathVariable Long id,
                         @RequestParam(defaultValue = "false") boolean sendEmail,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
    try {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nem található felhasználó"));

        if (user.getUsername().equals(authentication.getName())) {
            redirectAttributes.addFlashAttribute("error", "Nem törölheti saját fiókját!");
            return "redirect:/admin/users";
        }

        if (sendEmail && user.getEmail() != null && !user.getEmail().isEmpty()) {
            String text = String.format(
                "Kedves %s!\n\nTájékoztatjuk, hogy az Ön fiókja törlésre került a rendszerből.\n\nÜdvözlettel,\nAz Időpontfoglaló csapata",
                user.getFirstName() != null ? user.getFirstName() : user.getUsername()
            );
            emailService.sendSimpleMessage(user.getEmail(), "Fiók törölve - Időpontfoglaló", text);
        }

        String deletedUsername = user.getUsername();
        userRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success",
                "Felhasználó (" + deletedUsername + ") sikeresen törölve!");

    } catch (IllegalArgumentException e) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
    }

    return "redirect:/admin/users";
}
```

- [ ] **Lépés 7: Futtassuk le a teszteket, ellenőrizzük hogy átmennek**

```bash
./mvnw test -Dtest=AdminControllerTest
```

Várt eredmény: minden 5 teszt `PASS`.

- [ ] **Lépés 8: Futtassuk le az összes tesztet**

```bash
./mvnw test
```

Várt eredmény: minden meglévő teszt `PASS`.

- [ ] **Lépés 9: Commit**

```bash
git add src/main/java/com/BC/Idopontfoglalo/controller/AdminController.java \
        src/test/java/com/BC/Idopontfoglalo/controller/AdminControllerTest.java
git commit -m "feat: add user edit and delete endpoints to AdminController"
```

---

## Task 3: admin/users.html frissítése — műveletek oszlop

**Fájlok:**
- Módosítás: `src/main/resources/templates/admin/users.html`

- [ ] **Lépés 1: Adjunk hozzá flash üzenet megjelenítést**

A `<div class="card-custom">` elem elé (az `<div class="container-fluid px-4 py-4">` belül, a `hero-section` div után) szúrjuk be:

```html
<div th:if="${success}" class="alert alert-success alert-dismissible fade show mb-3" role="alert">
    <i class="fas fa-check-circle me-2"></i><span th:text="${success}"></span>
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
</div>
<div th:if="${error}" class="alert alert-danger alert-dismissible fade show mb-3" role="alert">
    <i class="fas fa-exclamation-circle me-2"></i><span th:text="${error}"></span>
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
</div>
```

- [ ] **Lépés 2: Bővítsük a táblázat fejlécét**

A `<thead><tr>` sorban cseréljük le a teljes sort:

```html
<thead><tr>
    <th>Felhasználónév</th><th>Email</th><th>Név</th><th>Szerepkörök</th><th>Osztály admin</th><th>Státusz</th><th>Időpontok</th><th>Műveletek</th>
</tr></thead>
```

- [ ] **Lépés 3: Adjuk hozzá a műveletek cellát minden sorhoz**

A `<tr th:each="user : ${users}">` soron belül, az `Időpontok` `<td>` cella után adjuk hozzá:

```html
<td>
    <div class="d-flex gap-1">
        <a th:href="@{'/admin/user/' + ${user.id} + '/edit'}" class="btn btn-sm btn-outline-primary">
            <i class="fas fa-edit me-1"></i>Szerkesztés
        </a>
        <form th:action="@{'/admin/user/' + ${user.id} + '/delete'}" method="post" style="display:inline;">
            <input type="hidden" name="sendEmail" value="false">
            <button type="submit" class="btn btn-sm btn-outline-danger"
                    onclick="return confirm('Biztosan törli ezt a felhasználót? Az összes időpontja is törlődik.')">
                <i class="fas fa-trash me-1"></i>Törlés
            </button>
        </form>
    </div>
</td>
```

- [ ] **Lépés 4: Ellenőrizzük az alkalmazást böngészőben**

```bash
./mvnw spring-boot:run
```

Navigáljunk a `http://localhost:8080/admin/users` oldalra admin-ként. Ellenőrizzük, hogy minden sorban megjelenik a "Szerkesztés" és "Törlés" gomb.

- [ ] **Lépés 5: Commit**

```bash
git add src/main/resources/templates/admin/users.html
git commit -m "feat: add edit and delete action buttons to admin users table"
```

---

## Task 4: admin/edit-user.html létrehozása

**Fájlok:**
- Létrehozás: `src/main/resources/templates/admin/edit-user.html`

- [ ] **Lépés 1: Hozzuk létre a sablont**

Hozzuk létre a `src/main/resources/templates/admin/edit-user.html` fájlt az alábbi tartalommal:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/header :: head('Felhasználó szerkesztése | Admin')}"></head>
<body>
<header th:replace="~{fragments/header :: header}"></header>

<div class="hero-section">
    <div class="container text-center position-relative">
        <h1 class="display-5 fw-bold mb-2">Felhasználó szerkesztése</h1>
        <p class="lead mb-0" th:text="'Fiók: ' + ${user.username}"></p>
    </div>
</div>

<div class="container">
    <div th:if="${success}" class="alert alert-success mb-4" th:text="${success}"></div>
    <div th:if="${error}" class="alert alert-danger mb-4" th:text="${error}"></div>

    <div class="row justify-content-center">
        <div class="col-lg-7">
            <div class="card" style="border-radius:16px;border:1px solid var(--border);box-shadow:var(--shadow-md);">
                <div class="card-body p-4">
                    <h5 class="fw-bold mb-4 d-flex align-items-center">
                        <i class="fas fa-user-edit me-2" style="color:var(--teal)"></i>Személyes adatok szerkesztése
                    </h5>

                    <form th:action="@{'/admin/user/' + ${user.id} + '/edit'}" method="post">

                        <div class="mb-3">
                            <label class="form-label">Felhasználónév</label>
                            <input type="text" class="form-control" th:value="${user.username}" disabled
                                   style="background:var(--surface-2);opacity:.7;">
                            <div class="form-text">A felhasználónév nem módosítható.</div>
                        </div>

                        <div class="mb-3">
                            <label for="email" class="form-label">Email cím <span style="color:var(--red)">*</span></label>
                            <input type="email" id="email" name="email" class="form-control"
                                   th:value="${user.email}" required>
                        </div>

                        <div class="row mb-3">
                            <div class="col-md-6">
                                <label for="firstName" class="form-label">Vezetéknév</label>
                                <input type="text" id="firstName" name="firstName" class="form-control"
                                       th:value="${user.firstName}">
                            </div>
                            <div class="col-md-6">
                                <label for="lastName" class="form-label">Keresztnév</label>
                                <input type="text" id="lastName" name="lastName" class="form-control"
                                       th:value="${user.lastName}">
                            </div>
                        </div>

                        <hr style="border-color:var(--border);margin:1.5rem 0;">
                        <h6 class="fw-bold mb-3" style="color:var(--text-muted);font-size:.85rem;text-transform:uppercase;letter-spacing:.05em;">
                            Jelszó visszaállítása (opcionális)
                        </h6>
                        <p class="text-muted" style="font-size:.85rem;">Ha nem kívánja módosítani a jelszót, hagyja üresen az alábbi mezőket.</p>

                        <div class="mb-3">
                            <label for="newPassword" class="form-label">Új jelszó</label>
                            <input type="password" id="newPassword" name="newPassword" class="form-control">
                        </div>
                        <div class="mb-4">
                            <label for="confirmPassword" class="form-label">Új jelszó megerősítése</label>
                            <input type="password" id="confirmPassword" name="confirmPassword" class="form-control">
                        </div>

                        <hr style="border-color:var(--border);margin:1.5rem 0;">

                        <div class="mb-4 form-check">
                            <input type="checkbox" class="form-check-input" id="sendEmail" name="sendEmail" value="true">
                            <label class="form-check-label" for="sendEmail">
                                Értesítő email küldése a felhasználónak a változásokról
                            </label>
                        </div>

                        <div class="d-flex gap-2">
                            <button type="submit" class="btn btn-primary">
                                <i class="fas fa-save me-2"></i>Mentés
                            </button>
                            <a th:href="@{/admin/users}" class="btn btn-outline-secondary">
                                <i class="fas fa-arrow-left me-2"></i>Vissza
                            </a>
                        </div>
                    </form>
                </div>
            </div>

            <!-- Veszélyzóna: törlés -->
            <div class="card mt-4" style="border:1px solid var(--red);border-radius:16px;">
                <div class="card-body p-4">
                    <h5 class="fw-bold mb-3 d-flex align-items-center" style="color:var(--red);">
                        <i class="fas fa-exclamation-triangle me-2"></i>Veszélyzóna
                    </h5>
                    <p class="text-muted mb-3" style="font-size:.9rem;">
                        A felhasználó törlése visszafordíthatatlan. Az összes időpontja is törlődik.
                    </p>
                    <div class="d-flex align-items-center gap-3">
                        <form th:action="@{'/admin/user/' + ${user.id} + '/delete'}" method="post">
                            <div class="form-check mb-2">
                                <input type="checkbox" class="form-check-input" id="sendDeleteEmail"
                                       name="sendEmail" value="true">
                                <label class="form-check-label" for="sendDeleteEmail" style="font-size:.9rem;">
                                    Értesítő email küldése a törlésről
                                </label>
                            </div>
                            <button type="submit" class="btn btn-danger btn-sm"
                                    onclick="return confirm('Biztosan törli ezt a felhasználót? Az összes időpontja is törlődik.')">
                                <i class="fas fa-trash me-2"></i>Felhasználó törlése
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<footer th:replace="~{fragments/footer :: footer}"></footer>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
```

- [ ] **Lépés 2: Ellenőrizzük az alkalmazást böngészőben**

```bash
./mvnw spring-boot:run
```

1. Navigáljunk `http://localhost:8080/admin/users`-re admin-ként
2. Kattintsunk egy "Szerkesztés" gombra — ellenőrizzük, hogy az edit-user oldal betölt
3. Módosítsunk egy email-t (jelszó nélkül) → Mentés → ellenőrizzük a sikeres visszajelzést
4. Próbáljuk módosítani jelszóval (egyező és eltérő jelszóval)
5. Próbáljuk törölni a Veszélyzónából

- [ ] **Lépés 3: Futtassuk le az összes tesztet**

```bash
./mvnw test
```

Várt eredmény: minden teszt `PASS`.

- [ ] **Lépés 4: Commit**

```bash
git add src/main/resources/templates/admin/edit-user.html
git commit -m "feat: create admin/edit-user.html template"
```
