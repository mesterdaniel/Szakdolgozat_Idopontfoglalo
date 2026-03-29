package com.BC.Idopontfoglalo.security;

import com.BC.Idopontfoglalo.entity.Role;
import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.repository.UserRepository;
import com.BC.Idopontfoglalo.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A CustomUserDetailsService osztály egységtesztjei.
 *
 * Ez a service felelős azért, hogy a Spring Security megtalálja és betöltse
 * a felhasználói adatokat bejelentkezéskor. Az implementáció felhasználónév
 * és e-mail-cím alapján is képes azonosítani a felhasználót.
 *
 * Tesztelt esetek:
 *  1. Sikeres betöltés felhasználónév alapján.
 *  2. Sikeres betöltés e-mail-cím alapján (fallback logika).
 *  3. Kivétel dobása, ha a felhasználó nem létezik.
 *  4. A visszaadott UserDetails tartalmazza a helyes szerepköröket.
 *  5. Letiltott fiók esetén az isEnabled() false értékű.
 *  6. A jelszó titkosított formában kerül átadásra a Security kontextusnak.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User activeUser;
    private User disabledUser;

    @BeforeEach
    void setUp() {
        Role userRole = new Role();
        userRole.setRoleName("ROLE_USER");

        Role adminRole = new Role();
        adminRole.setRoleName("ROLE_ADMIN");

        Set<Role> userRoles = new HashSet<>();
        userRoles.add(userRole);

        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(adminRole);

        // Aktív, normál felhasználó
        activeUser = new User("teszt_user", "teszt@example.com",
                              "$2a$10$encodedHash", "Teszt", "Felhasználó");
        activeUser.setId(1L);
        activeUser.setEnabled(true);
        activeUser.setRoles(userRoles);

        // Letiltott felhasználó
        disabledUser = new User("letiltott_user", "letiltott@example.com",
                                "$2a$10$disabledHash", "Letiltott", "User");
        disabledUser.setId(2L);
        disabledUser.setEnabled(false);
        disabledUser.setRoles(userRoles);
    }

    // =========================================================
    // 1. Betöltés felhasználónév alapján
    // =========================================================

    @Test
    @DisplayName("loadUserByUsername: felhasználónév alapján visszaadja a helyes UserDetails objektumot")
    void loadUserByUsername_ReturnsUserDetails_WhenUsernameFound() {
        // Arrange
        when(userRepository.findByUsername("teszt_user"))
            .thenReturn(Optional.of(activeUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserByUsername("teszt_user");

        // Assert
        assertNotNull(result);
        assertEquals("teszt_user", result.getUsername());
        assertTrue(result.isEnabled());
        // Az e-mail-alapú keresés nem fut le, ha a felhasználónév megtalálható
        verify(userRepository, never()).findByEmail(anyString());
    }

    // =========================================================
    // 2. Betöltés e-mail-cím alapján (fallback logika)
    // =========================================================

    @Test
    @DisplayName("loadUserByUsername: ha felhasználónévvel nem találja, e-mail alapján próbálja betölteni")
    void loadUserByUsername_FallsBackToEmail_WhenUsernameNotFound() {
        // Arrange – felhasználónévvel nem találja, de e-mail-lel igen
        when(userRepository.findByUsername("teszt@example.com"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("teszt@example.com"))
            .thenReturn(Optional.of(activeUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserByUsername("teszt@example.com");

        // Assert
        assertNotNull(result);
        assertEquals("teszt_user", result.getUsername());
        verify(userRepository, times(1)).findByEmail("teszt@example.com");
    }

    // =========================================================
    // 3. Kivétel dobása – nem létező felhasználó
    // =========================================================

    @Test
    @DisplayName("loadUserByUsername: UsernameNotFoundException-t dob, ha a felhasználó nem létezik")
    void loadUserByUsername_ThrowsException_WhenUserNotFound() {
        // Arrange – sem felhasználónévvel, sem e-mail-lel nem találja
        when(userRepository.findByUsername("nem_letezo"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("nem_letezo"))
            .thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(
            UsernameNotFoundException.class,
            () -> customUserDetailsService.loadUserByUsername("nem_letezo")
        );
        assertTrue(exception.getMessage().contains("nem_letezo"));
    }

    // =========================================================
    // 4. Szerepkörök helyes betöltése
    // =========================================================

    @Test
    @DisplayName("loadUserByUsername: a visszaadott UserDetails tartalmazza a felhasználó szerepköreit")
    void loadUserByUsername_ReturnsCorrectAuthorities() {
        // Arrange
        when(userRepository.findByUsername("teszt_user"))
            .thenReturn(Optional.of(activeUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserByUsername("teszt_user");

        // Assert – a ROLE_USER szerepkör megtalálható a hatáskörök között
        assertNotNull(result.getAuthorities());
        assertFalse(result.getAuthorities().isEmpty());
        assertTrue(result.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(auth -> auth.equals("ROLE_USER"))
        );
    }

    // =========================================================
    // 5. Letiltott fiók – isEnabled() false
    // =========================================================

    @Test
    @DisplayName("loadUserByUsername: letiltott fiók esetén az isEnabled() értéke false")
    void loadUserByUsername_ReturnsDisabledUser_WhenAccountIsDisabled() {
        // Arrange
        when(userRepository.findByUsername("letiltott_user"))
            .thenReturn(Optional.of(disabledUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserByUsername("letiltott_user");

        // Assert
        assertNotNull(result);
        assertFalse(result.isEnabled());
    }

    // =========================================================
    // 6. A jelszó titkosított formában kerül átadásra
    // =========================================================

    @Test
    @DisplayName("loadUserByUsername: a visszaadott UserDetails a tárolt (titkosított) jelszót tartalmazza")
    void loadUserByUsername_ReturnsEncodedPassword() {
        // Arrange
        when(userRepository.findByUsername("teszt_user"))
            .thenReturn(Optional.of(activeUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserByUsername("teszt_user");

        // Assert – a hash kerül átadásra, nem a nyílt szöveges jelszó
        assertEquals("$2a$10$encodedHash", result.getPassword());
        assertNotEquals("nyers_jelszo", result.getPassword());
    }

    // =========================================================
    // 7. A felhasználónév-keresés elsőbbséget élvez az e-mail-lel szemben
    // =========================================================

    @Test
    @DisplayName("Ha a felhasználónév megtalálható, az e-mail-alapú keresés nem fut le")
    void loadUserByUsername_DoesNotSearchByEmail_WhenUsernameFound() {
        // Arrange
        when(userRepository.findByUsername("teszt_user"))
            .thenReturn(Optional.of(activeUser));

        // Act
        customUserDetailsService.loadUserByUsername("teszt_user");

        // Assert – a findByEmail egyszer sem lett meghívva
        verify(userRepository, never()).findByEmail(anyString());
    }
}
