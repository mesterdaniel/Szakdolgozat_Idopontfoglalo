package com.BC.Idopontfoglalo.service;

import com.BC.Idopontfoglalo.entity.Role;
import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.repository.RoleRepository;
import com.BC.Idopontfoglalo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A UserService osztály egységtesztjei.
 *
 * Tesztelt funkciók:
 *  1. Sikeres felhasználóregisztráció – mentés és jelszó-hash ellenőrzése.
 *  2. Jelszóellenőrzés – helyes és helytelen jelszó esete.
 *  3. Jelszócsere – az új jelszó titkosítva kerül mentésre.
 *  4. Felhasználó keresése felhasználónév alapján (létező és nem létező).
 *  5. A regisztrációkor alapértelmezett ROLE_USER szerepkör rendelődik a fiókhoz.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = new Role();
        userRole.setRoleName("ROLE_USER");
    }

    // =========================================================
    // 1. Sikeres regisztráció
    // =========================================================

    @Test
    @DisplayName("Sikeres regisztráció: a felhasználó mentésre kerül, a jelszó titkosított formában tárolódik")
    void registerNewUser_Success() {
        // Arrange
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(userRole);
        when(passwordEncoder.encode("jelszo123")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendSimpleMessage(anyString(), anyString(), anyString());

        // Act
        assertDoesNotThrow(() ->
            userService.registerNewUser("teszt_user", "teszt@example.com",
                                        "jelszo123", "Teszt", "Felhasználó")
        );

        // Assert – a repository save metódusa egyszer hívódott meg
        verify(userRepository, times(1)).save(argThat(user ->
            "teszt_user".equals(user.getUsername()) &&
            "teszt@example.com".equals(user.getEmail()) &&
            "$2a$10$hashedpassword".equals(user.getPassword()) &&
            user.isEnabled()
        ));
    }

    // =========================================================
    // 2. Regisztráció – ROLE_USER szerepkör hozzárendelése
    // =========================================================

    @Test
    @DisplayName("Regisztrációkor a felhasználóhoz automatikusan ROLE_USER szerepkör rendelődik")
    void registerNewUser_AssignsUserRole() {
        // Arrange
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(userRole);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendSimpleMessage(anyString(), anyString(), anyString());

        // Act
        userService.registerNewUser("valaki", "valaki@email.hu", "pw", "Valaki", "Teszt");

        // Assert – a mentett user rendelkezik ROLE_USER szerepkörrel
        verify(userRepository).save(argThat(user ->
            user.getRoles() != null &&
            user.getRoles().stream()
                .anyMatch(r -> "ROLE_USER".equals(r.getRoleName()))
        ));
    }

    // =========================================================
    // 3. Regisztráció – üdvözlő e-mail kiküldése
    // =========================================================

    @Test
    @DisplayName("Sikeres regisztrációkor üdvözlő e-mail kerül kiküldésre a megadott e-mail-címre")
    void registerNewUser_SendsWelcomeEmail() {
        // Arrange
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(userRole);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        userService.registerNewUser("user1", "user1@email.hu", "pw", "Elek", "Teszt");

        // Assert – az EmailService a megadott e-mail-címre hívódott
        verify(emailService, times(1)).sendSimpleMessage(
            eq("user1@email.hu"),
            contains("regisztráció"),
            anyString()
        );
    }

    // =========================================================
    // 4. Regisztráció – a jelszót soha nem tárolja nyílt szövegként
    // =========================================================

    @Test
    @DisplayName("A nyílt szöveges jelszó soha nem kerül mentésre – csak a titkosított változat")
    void registerNewUser_NeverStoresPlainTextPassword() {
        // Arrange
        String rawPassword = "titokjelszo";
        String encodedPassword = "$2a$10$encodedHash";

        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(userRole);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendSimpleMessage(anyString(), anyString(), anyString());

        // Act
        userService.registerNewUser("user2", "user2@email.hu", rawPassword, "Anna", "Kiss");

        // Assert – a tárolt jelszó nem egyezik a nyílt szöveggel
        verify(userRepository).save(argThat(user ->
            !rawPassword.equals(user.getPassword()) &&
            encodedPassword.equals(user.getPassword())
        ));
    }

    // =========================================================
    // 5. Jelszóellenőrzés – helyes jelszó
    // =========================================================

    @Test
    @DisplayName("Helyes jelszó megadásakor a checkPassword true értékkel tér vissza")
    void checkPassword_ReturnsTrue_WhenPasswordMatches() {
        // Arrange
        User user = new User("user3", "user3@email.hu", "$2a$10$hash", "Béla", "Nagy");
        when(passwordEncoder.matches("helyes_jelszo", "$2a$10$hash")).thenReturn(true);

        // Act
        boolean result = userService.checkPassword(user, "helyes_jelszo");

        // Assert
        assertTrue(result);
    }

    // =========================================================
    // 6. Jelszóellenőrzés – helytelen jelszó
    // =========================================================

    @Test
    @DisplayName("Helytelen jelszó megadásakor a checkPassword false értékkel tér vissza")
    void checkPassword_ReturnsFalse_WhenPasswordDoesNotMatch() {
        // Arrange
        User user = new User("user4", "user4@email.hu", "$2a$10$hash", "Péter", "Kovács");
        when(passwordEncoder.matches("rossz_jelszo", "$2a$10$hash")).thenReturn(false);

        // Act
        boolean result = userService.checkPassword(user, "rossz_jelszo");

        // Assert
        assertFalse(result);
    }

    // =========================================================
    // 7. Jelszócsere – az új jelszó titkosítva kerül mentésre
    // =========================================================

    @Test
    @DisplayName("Jelszócserekor az új jelszó titkosított formában kerül az adatbázisba")
    void changePassword_SavesEncodedNewPassword() {
        // Arrange
        User user = new User("user5", "user5@email.hu", "regi_hash", "Kata", "Szabó");
        when(passwordEncoder.encode("uj_jelszo")).thenReturn("uj_hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        userService.changePassword(user, "uj_jelszo");

        // Assert – a felhasználó jelszava az új hash lett
        verify(userRepository).save(argThat(u ->
            "uj_hash".equals(u.getPassword())
        ));
    }

    // =========================================================
    // 8. Felhasználó keresése – létező felhasználónév
    // =========================================================

    @Test
    @DisplayName("findByUsername visszaadja a felhasználót, ha létezik a megadott felhasználónévvel")
    void findByUsername_ReturnsUser_WhenExists() {
        // Arrange
        User user = new User("keresett_user", "k@email.hu", "hash", "Kati", "Molnár");
        when(userRepository.findByUsername("keresett_user")).thenReturn(Optional.of(user));

        // Act
        User result = userService.findByUsername("keresett_user");

        // Assert
        assertNotNull(result);
        assertEquals("keresett_user", result.getUsername());
    }

    // =========================================================
    // 9. Felhasználó keresése – nem létező felhasználónév
    // =========================================================

    @Test
    @DisplayName("findByUsername null értékkel tér vissza, ha nem létezik a felhasználó")
    void findByUsername_ReturnsNull_WhenNotFound() {
        // Arrange
        when(userRepository.findByUsername("nem_letezo")).thenReturn(Optional.empty());

        // Act
        User result = userService.findByUsername("nem_letezo");

        // Assert
        assertNull(result);
    }
}
