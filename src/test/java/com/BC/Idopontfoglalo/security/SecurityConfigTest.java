package com.BC.Idopontfoglalo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A Spring Security konfiguráció integrációs tesztjei.
 *
 * A @SpringBootTest és @AutoConfigureMockMvc annotációk együttes használatával
 * a teljes Spring kontextus betöltődik, így a tesztek valódi HTTP-kéréseket
 * szimulálnak – adatbázis-kapcsolat nélkül (H2 in-memory).
 *
 * Tesztelt biztonsági szabályok:
 *  1. Nem hitelesített felhasználót a védett oldalak a /login oldalra irányítják át.
 *  2. A /login és /register oldalak hitelesítés nélkül is elérhetők.
 *  3. Az /admin/** végpont csak ADMIN szerepkörrel érhető el.
 *  4. Egyszerű USER szerepkörrel az /admin/** tiltott (403 vagy átirányítás).
 *  5. ADMIN szerepkörrel az /admin/** elérhető.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // =========================================================
    // 1. Hitelesítés nélküli hozzáférés tesztjei
    // =========================================================

    @Test
    @DisplayName("Nem hitelesített felhasználó a /dashboard oldalra navigálva a /login oldalra kerül")
    void unauthenticatedUser_RedirectsToLogin_WhenAccessingDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Nem hitelesített felhasználó a /admin oldalra navigálva a /login oldalra kerül")
    void unauthenticatedUser_RedirectsToLogin_WhenAccessingAdmin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Nem hitelesített felhasználó a /appointments oldalra navigálva a /login oldalra kerül")
    void unauthenticatedUser_RedirectsToLogin_WhenAccessingAppointments() throws Exception {
        mockMvc.perform(get("/appointments"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // =========================================================
    // 2. Nyilvánosan elérhető oldalak tesztjei
    // =========================================================

    @Test
    @DisplayName("A /login oldal hitelesítés nélkül is elérhető (200 OK)")
    void loginPage_IsAccessible_WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A /register oldal hitelesítés nélkül is elérhető (200 OK)")
    void registerPage_IsAccessible_WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    // =========================================================
    // 3. Szerepköralapú hozzáférés – ADMIN végpontok
    // =========================================================

    @Test
    @DisplayName("ADMIN szerepkörrel az /admin/dashboard végpont elérhető (200 OK)")
    void adminDashboard_IsAccessible_WithAdminRole() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER szerepkörrel az /admin/dashboard végpont nem érhető el (403 Forbidden)")
    void adminDashboard_IsForbidden_WithUserRole() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("normaluser").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER szerepkörrel az /admin/appointments végpont nem érhető el (403 Forbidden)")
    void adminAppointments_IsForbidden_WithUserRole() throws Exception {
        mockMvc.perform(get("/admin/appointments")
                        .with(user("normaluser").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // 4. Hitelesített felhasználó hozzáférési tesztjei
    // =========================================================


    @Test
    @DisplayName("USER szerepkörrel az /appointments oldal elérhető")
    void appointments_IsAccessible_WithUserRole() throws Exception {
        mockMvc.perform(get("/appointments")
                        .with(user("normaluser").roles("USER")))
                .andExpect(status().isOk());
    }
}
