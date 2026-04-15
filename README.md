# Időpontfoglaló Rendszer

Webes időpontfoglaló alkalmazás, amely lehetővé teszi a felhasználók számára időpontok online foglalását különböző ügyintézési területekhez (osztályokhoz). A rendszer szakdolgozati projektként készült, Spring Boot alapon.

## Funkciók

- **Regisztráció és bejelentkezés** – saját felhasználói fiók létrehozása e-mail címmel és jelszóval.
- **Időpontfoglalás** – osztály és ügytípus kiválasztása után szabad időpontok közül való választás.
- **Foglalások kezelése** – a felhasználó megtekintheti, módosíthatja vagy lemondhatja saját időpontjait.
- **E-mail értesítések** – foglalás megerősítése és emlékeztetők küldése.
- **Három felhasználói szerepkör:**
  - **Felhasználó (USER)** – időpontot foglal és kezeli saját foglalásait.
  - **Osztály adminisztrátor (DEPARTMENT_ADMIN)** – egy adott osztály ügytípusait, szabad időpontjait és foglalásait kezeli.
  - **Rendszergazda (ADMIN)** – teljes rendszerszintű adminisztráció: felhasználók, osztályok, szerepkörök kezelése.

## Technológiák

- **Backend:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA
- **Frontend:** Thymeleaf, Bootstrap 5, JavaScript
- **Adatbázis:** H2 (fejlesztési módban) / PostgreSQL 15 (Docker módban)
- **Build eszköz:** Maven (mellékelt Maven Wrapper)
- **E-mail:** Mailtrap (teszt környezet)

---

## Telepítés és indítás

### Előfeltételek

A telepítés előtt győződj meg róla, hogy a gépeden telepítve van:

- **Java 21** vagy újabb
- *(Opcionális, Docker-es indításhoz)* **Docker Desktop** – [letöltés](https://www.docker.com/products/docker-desktop/)

A Maven telepítése nem szükséges, mert a projekt tartalmazza a Maven Wrappert (`mvnw`).

### 1. A projekt letöltése

```bash
git clone https://github.com/<felhasznalonev>/<repo-nev>.git
cd <repo-nev>/Szakdolgozat_Idopontfoglalo
```

### 2/A. Indítás fejlesztői módban (H2 adatbázissal)

Ez a legegyszerűbb módszer, nem igényel külső adatbázist. Az adatok egy helyi fájl alapú H2 adatbázisban tárolódnak.

**Windows (PowerShell / CMD):**
```bash
mvnw.cmd spring-boot:run
```

**Linux / macOS:**
```bash
./mvnw spring-boot:run
```

Az alkalmazás elindulása után a böngészőben nyisd meg:

```
http://localhost:8080
```

### 2/B. Indítás Dockerrel (PostgreSQL adatbázissal) (Ajánlott!)

Ha Docker Desktop telepítve van, egyetlen paranccsal elindítható a teljes környezet (alkalmazás + PostgreSQL + Adminer).

```bash
docker-compose up --build
```

Elérhetőségek:

- Alkalmazás: [http://localhost:8080](http://localhost:8080)
- Adminer (adatbázis-kezelő): [http://localhost:8082](http://localhost:8082)

Leállítás: `Ctrl + C`, majd:

```bash
docker-compose down
```

---

## Első használat

Az alkalmazás első indításakor egy alapértelmezett rendszergazda fiók automatikusan létrejön. A bejelentkezési adatok az `src/main/resources/application.properties` fájlban találhatók (`spring.security.user.name` és `spring.security.user.password`).

Átlag felhasználóként a főoldalon a **Regisztráció** gombra kattintva lehet új fiókot létrehozni.

### Hasznos elérhetőségek fejlesztőknek

- H2 Konzol (csak fejlesztői módban): [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

---

## Konfiguráció

A főbb beállítások az `src/main/resources/application.properties` fájlban módosíthatók:

- Adatbázis kapcsolat
- E-mail küldés (SMTP / Mailtrap)
- Munkamenet időtúllépés

> ⚠️ **Biztonsági figyelmeztetés:** A repository alapértelmezett jelszavakat tartalmaz (pl. `titok123`), amelyek csak fejlesztési célra alkalmasak. Éles környezetben ezeket mindenképp cseréld le!

---

## Projektstruktúra (rövid áttekintés)

```
Szakdolgozat_Idopontfoglalo/
├── src/main/java/com/BC/Idopontfoglalo/
│   ├── controller/   – HTTP végpontok
│   ├── service/      – üzleti logika
│   ├── repository/   – adatbázis-hozzáférés
│   ├── entity/       – adatmodell (User, Department, Appointment, …)
│   └── security/     – bejelentkezés, jogosultságkezelés
├── src/main/resources/
│   ├── templates/    – Thymeleaf HTML sablonok
│   ├── static/       – CSS, JS, képek
│   └── application.properties
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## Tesztek futtatása

```bash
./mvnw test
```

---

## Licenc

Ez a projekt szakdolgozati célra készült, oktatási felhasználásra.
