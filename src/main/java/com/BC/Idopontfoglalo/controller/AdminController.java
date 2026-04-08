package com.BC.Idopontfoglalo.controller;

import com.BC.Idopontfoglalo.entity.Appointment;
import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.service.AppointmentService;
import com.BC.Idopontfoglalo.service.EmailService;
import com.BC.Idopontfoglalo.service.UserService;
import com.BC.Idopontfoglalo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")  // Csak admin férhet hozzá
public class AdminController {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    // ========== IDŐPONT KEZELÉS ==========

    /**
     * Admin időpont áttekintő oldal
     */
    @GetMapping("/appointments")
    public String listAllAppointments(Model model, Authentication authentication) {
        try {
            List<Appointment> allAppointments = appointmentService.getAllAppointments();
            List<Appointment> upcomingAppointments = appointmentService.getAllUpcomingAppointments();
            List<Appointment> pendingAppointments = appointmentService.getPendingAppointments();
            long pendingCount = appointmentService.getPendingAppointmentsCount();

            model.addAttribute("allAppointments", allAppointments);
            model.addAttribute("upcomingAppointments", upcomingAppointments);
            model.addAttribute("pendingAppointments", pendingAppointments);
            model.addAttribute("pendingCount", pendingCount);
            model.addAttribute("username", authentication.getName());

            return "admin/appointments";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt az időpontok betöltésekor: " + e.getMessage());
            return "admin/appointments";
        }
    }

    /**
     * Függőben lévő időpontok (jóváhagyásra várók)
     */
    @GetMapping("/appointments/pending")
    public String listPendingAppointments(Model model, Authentication authentication) {
        try {
            List<Appointment> pendingAppointments = appointmentService.getPendingAppointments();
            model.addAttribute("pendingAppointments", pendingAppointments);
            model.addAttribute("username", authentication.getName());
            return "admin/pending-appointments";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt: " + e.getMessage());
            return "admin/pending-appointments";
        }
    }

    /**
     * Időpont jóváhagyása
     */
    @PostMapping("/appointment/{id}/approve")
    public String approveAppointment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            appointmentService.approveAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Időpont jóváhagyva!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/admin/appointments";
    }

    /**
     * Időpont elutasítása
     */
    @PostMapping("/appointment/{id}/reject")
    public String rejectAppointment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            appointmentService.rejectAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Időpont elutasítva!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/admin/appointments/pending";
    }

    /**
     * Időpont lemondása
     */
    @PostMapping("/appointment/{id}/cancel")
    public String cancelAppointment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            appointmentService.cancelAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Időpont lemondva!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/admin/appointments";
    }


    /**
     * Időpont befejezése
     */
    @PostMapping("/appointment/{id}/complete")
    public String completeAppointment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            appointmentService.completeAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Időpont sikeresen lezárva!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/admin/appointments";
    }

    /**
     * Időpont részletes megtekintése (admin)
     */
    @GetMapping("/appointment/{id}")
    public String viewAppointmentDetail(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Appointment appointment = appointmentService.getAppointmentById(id);
            model.addAttribute("appointment", appointment);
            model.addAttribute("user", appointment.getUser());
            model.addAttribute("username", authentication.getName());
            return "admin/appointment-detail";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "redirect:/admin/appointments";
        }
    }

    // ========== FELHASZNÁLÓ KEZELÉS ==========

    /**
     * Összes felhasználó listázása
     */
    @GetMapping("/users")
    public String listAllUsers(Model model, Authentication authentication) {
        try {
            List<User> allUsers = userRepository.findAll();
            model.addAttribute("users", allUsers);
            model.addAttribute("username", authentication.getName());
            return "admin/users";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt a felhasználók betöltésekor: " + e.getMessage());
            return "admin/users";
        }
    }

    /**
     * Felhasználó részletes megtekintése
     */
    @GetMapping("/user/{id}")
    public String viewUserDetail(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Nem található felhasználó ezzel az ID-val: " + id));

            // Felhasználó időpontjainak lekérése
            List<Appointment> userAppointments = user.getAppointments();

            model.addAttribute("user", user);
            model.addAttribute("userAppointments", userAppointments);
            model.addAttribute("username", authentication.getName());

            return "admin/user-detail";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "redirect:/admin/users";
        }
    }

    /**
     * Felhasználó engedélyezése/letiltása
     */
    @PostMapping("/user/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Nem található felhasználó"));

            user.setEnabled(!user.isEnabled());
            userRepository.save(user);

            String status = user.isEnabled() ? "engedélyezve" : "letiltva";
            redirectAttributes.addFlashAttribute("success",
                    "Felhasználó (" + user.getUsername() + ") sikeresen " + status + "!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/admin/users";
    }



    // ========== FELHASZNÁLÓ SZERKESZTÉS / TÖRLÉS ==========

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

    // ========== HIBAKEZELÉS ==========

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        model.addAttribute("error", "Admin hiba történt: " + e.getMessage());
        return "error";
    }
}