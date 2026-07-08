# Admin felhasználókezelés — Design Spec

**Dátum:** 2026-04-08  
**Érintett oldal:** `/admin/users`

## Összefoglalás

Az `/admin/users` oldal bővítése szerkesztési és törlési funkcióval. Az admin szerkesztheti a felhasználók alapadatait, közvetlenül beállíthat új jelszót, törölheti a felhasználót (időpontjaival együtt), és opcionálisan email értesítőt küldhet a változásokról.

---

## Backend

### AdminController — új végpontok

**`GET /admin/user/{id}/edit`**
- Betölti a felhasználót ID alapján (404 → redirect `/admin/users`)
- Átadja a `user` objektumot az `admin/edit-user` nézetnek

**`POST /admin/user/{id}/edit`**
- Paraméterek: `email`, `firstName`, `lastName`, `newPassword` (opcionális), `confirmPassword` (opcionális), `sendEmail` (checkbox, boolean)
- Validáció: ha `newPassword` ki van töltve, `newPassword == confirmPassword` — ha nem, redirect vissza hibával
- Ha `newPassword` ki van töltve: `userService.adminSetPassword(user, newPassword)`
- Alapadatok mentése: `userService.updateUser(user)`
- Ha `sendEmail == true`: email küldése a felhasználónak a változásokról
- Redirect: `/admin/users` flash sikerüzenettel

**`POST /admin/user/{id}/delete`**
- Paraméter: opcionális `sendEmail` checkbox
- Ha `sendEmail == true`: értesítő email küldése törlés előtt
- `userRepository.deleteById(id)` — cascade törli az időpontokat is
- Redirect: `/admin/users` flash sikerüzenettel

### UserService — új metódus

```java
public void adminSetPassword(User user, String newPassword) {
    user.setPassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
}
```

A meglévő `changePassword()` mintájára, de jelenlegi jelszó validáció nélkül.

### EmailService

Változatlan — a meglévő `sendSimpleMessage()` metódust használjuk.

**Profil módosítás email tartalma:**
> Tájékoztatjuk, hogy az Ön fiókjának adatait egy rendszeradminisztrátor módosította.

**Törlési email tartalma:**
> Tájékoztatjuk, hogy az Ön fiókja törlésre került a rendszerből.

---

## Frontend

### `admin/users.html` — módosítás

A táblázatba új **"Műveletek"** oszlop kerül az utolsó helyre, minden sorban:
- **Szerkesztés gomb** → `GET /admin/user/{id}/edit`
- **Törlés gomb** → `POST /admin/user/{id}/delete` form, JS `confirm()` megerősítéssel

### `admin/edit-user.html` — új oldal

A `profile.html` stílusát és struktúráját követi. Mezők:

| Mező | Típus | Megjegyzés |
|------|-------|-----------|
| Felhasználónév | text, disabled | Nem módosítható |
| Email | email, required | Szerkeszthető |
| Vezetéknév | text | Szerkeszthető |
| Keresztnév | text | Szerkeszthető |
| Új jelszó | password, opcionális | Admin közvetlenül beállítja |
| Jelszó megerősítése | password, opcionális | Egyeznie kell az új jelszóval |
| Email értesítő | checkbox | „Értesítő email küldése a változásokról" |

Gombok: **Mentés**, **Vissza** (`/admin/users`)

---

## Scope

- Szerepkörök módosítása **nem** része ennek a funkciónak (az részlegkezelésnél már elérhető)
- Elfelejtett jelszó (önkiszolgáló reset) **nem** része — külön spec és implementáció
- Admin nem törölheti saját magát (védelmi szabály a controllerben)
