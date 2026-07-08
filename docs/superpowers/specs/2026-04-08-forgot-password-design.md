# Elfelejtett jelszó & pre-generált felhasználók emailjei — Design Spec

**Dátum:** 2026-04-08
**Projekt:** Időpontfoglaló (Spring Boot + Thymeleaf)

---

## Kontextus

A bejelentkezési oldalon jelenleg nincs lehetőség elfelejtett jelszó visszaállítására. A felhasználóknak szükségük van egy emailes reset folyamatra. Emellett a `DataInitializer` által létrehozott előre generált felhasználók (admin, dept adminok, testuser) nem rendelkeznek email címmel, ami megakadályozza, hogy a reset funkciót használhassák.

---

## Scope

1. Token-alapú jelszó-visszaállítás emailen keresztül
2. `@idopontfoglalo.hu` végű email hozzárendelése az összes pre-generált felhasználóhoz

---

## Adatbázis

### Új entitás: `PasswordResetToken`

| Mező | Típus | Leírás |
|------|-------|--------|
| `id` | Long (PK, auto) | Azonosító |
| `token` | String (unique) | UUID alapú véletlenszerű token |
| `user` | ManyToOne → User | A token tulajdonosa |
| `expiryDate` | LocalDateTime | Létrehozástól 1 óra múlva jár le |

- `PasswordResetTokenRepository` Spring Data JPA repository az entitáshoz
- Új metódusok: `findByToken(String)`, `deleteByUser(User)`

---

## Flow

```
GET /forgot-password
  → forgot-password.html (email bekérő form)

POST /forgot-password (email paraméter)
  → Ha az email létezik a DB-ben:
      - Korábbi tokenek törlése az adott user-hez
      - UUID token generálás
      - PasswordResetToken mentés (lejárat: now + 1 óra)
      - Email küldés a reset linkkel
  → Ha az email NEM létezik:
      - Semmi nem történik (biztonság miatt)
  → Mindkét esetben: redirect GET /forgot-password?sent=true
      → "Ha az email regisztrált, küldtünk egy levelet" üzenet

GET /reset-password?token=<UUID>
  → Token keresés DB-ben
  → Ha nem létezik vagy lejárt: hibaüzenet, link /forgot-password-hoz
  → Ha érvényes: reset-password.html (új jelszó + megerősítés form)

POST /reset-password (token + newPassword + confirmPassword paraméterek)
  → Token validálás
  → Jelszavak egyezésének ellenőrzése (min. 6 karakter)
  → BCrypt-tel kódolt jelszó mentés
  → Token törlése DB-ből
  → Redirect /login?passwordReset=true
```

---

## Új komponensek

### Entitás & Repository
- `entity/PasswordResetToken.java`
- `repository/PasswordResetTokenRepository.java`

### Service metódusok (PasswordResetService vagy UserService bővítése)
- `createPasswordResetToken(User user)` → token generálás + mentés
- `validateToken(String token)` → érvényes-e, lejárt-e
- `resetPassword(String token, String newPassword)` → jelszó frissítés + token törlés

### Controller
- `PasswordResetController.java`
  - `GET /forgot-password`
  - `POST /forgot-password`
  - `GET /reset-password`
  - `POST /reset-password`

### Thymeleaf oldalak
- `forgot-password.html` — email bekérő form (a login.html stílusát követi)
- `reset-password.html` — új jelszó + megerősítés form (a login.html stílusát követi)

### Login oldal módosítás
- `login.html`: új "Elfelejtett jelszó?" link a form alá (a regisztrációs link mellé/alá)
- Új alert: `?passwordReset=true` paraméter esetén "Jelszó sikeresen megváltoztatva" üzenet

### SecurityConfig módosítás
- `/forgot-password` és `/reset-password` elérési utak hozzáadása a publikus route-okhoz

---

## Email tartalma

A meglévő `EmailService.sendSimpleMessage()` metódust használja.

- **Tárgy:** `Jelszó visszaállítás - Időpontfoglaló`
- **Tartalom:**
  - Reset link: `http://localhost:8080/reset-password?token=<UUID>`
  - Figyelmeztetés: "Ez a link 1 óráig érvényes."
  - Ha nem ők kérték: "Ha nem Ön kérte a visszaállítást, hagyja figyelmen kívül ezt az emailt."

---

## Pre-generált felhasználók email frissítése

`DataInitializer.java` módosítás — email mezők hozzáadása a felhasználó-létrehozáshoz:

| Felhasználó | Email |
|-------------|-------|
| superadmin | superadmin@idopontfoglalo.hu |
| admin | admin@idopontfoglalo.hu |
| orvosi_admin | orvosi_admin@idopontfoglalo.hu |
| fogorvosi_admin | fogorvosi_admin@idopontfoglalo.hu |
| lab_admin | lab_admin@idopontfoglalo.hu |
| testuser | testuser@idopontfoglalo.hu |

---

## Biztonsági szempontok

- A `/forgot-password` POST mindig ugyanazt az üzenetet jeleníti meg, függetlenül attól, hogy létezik-e az email (felhasználónév enumeration elleni védelem)
- A token UUID alapú (kriptográfiailag erős véletlenszám)
- A token felhasználás után azonnal törlődik
- A token 1 óra után lejár
- Lejárt token esetén a reset form nem érhető el, hibaüzenet jelenik meg linkkel az új kérés küldéséhez

---

## Érintett fájlok

### Új fájlok
- `src/main/java/com/BC/Idopontfoglalo/entity/PasswordResetToken.java`
- `src/main/java/com/BC/Idopontfoglalo/repository/PasswordResetTokenRepository.java`
- `src/main/java/com/BC/Idopontfoglalo/service/PasswordResetService.java`
- `src/main/java/com/BC/Idopontfoglalo/controller/PasswordResetController.java`
- `src/main/resources/templates/forgot-password.html`
- `src/main/resources/templates/reset-password.html`

### Módosított fájlok
- `src/main/java/com/BC/Idopontfoglalo/security/SecurityConfig.java` — publikus route-ok bővítése
- `src/main/java/com/BC/Idopontfoglalo/config/DataInitializer.java` — email mezők hozzáadása
- `src/main/resources/templates/login.html` — "Elfelejtett jelszó?" link + új alert

---

## Verifikáció

1. App indítás után: pre-generált felhasználók email mezői be vannak töltve (H2 console-ban ellenőrizhető)
2. Login oldalon megjelenik az "Elfelejtett jelszó?" link
3. `testuser@idopontfoglalo.hu` email megadásával: Mailtrap inbox-ban megjelenik a reset email
4. A linkre kattintva elérhető az új jelszó form
5. 1 óránál régebbi token esetén: hibaüzenet jelenik meg
6. Sikeres reset után: `/login?passwordReset=true` oldalon megjelenik a sikeres üzenet
7. Bejelentkezés az új jelszóval: sikeres
