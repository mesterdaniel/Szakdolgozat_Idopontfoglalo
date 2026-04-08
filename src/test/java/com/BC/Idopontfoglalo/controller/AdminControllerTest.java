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
 *  2. GET /admin/user/{id}/edit – nem létező ID esetén redirect
 *  3. POST /admin/user/{id}/edit – sikeres mentés redirect
 *  4. POST /admin/user/{id}/edit – jelszó-eltérés esetén redirect vissza
 *  5. POST /admin/user/{id}/delete – sikeres törlés redirect
 *  6. POST /admin/user/{id}/delete – saját fiók törlésének megakadályozása
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
    @DisplayName("POST /admin/user/{id}/delete: admin nem törölheti saját fiókját – törlés nem fut le")
    void deleteUser_PreventsSelfDeletion() throws Exception {
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
