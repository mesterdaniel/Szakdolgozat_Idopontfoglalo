package com.BC.Idopontfoglalo.controller;

import com.BC.Idopontfoglalo.entity.*;
import com.BC.Idopontfoglalo.service.AppointmentService;
import com.BC.Idopontfoglalo.service.AppointmentTypeService;
import com.BC.Idopontfoglalo.service.AvailableTimeSlotService;
import com.BC.Idopontfoglalo.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/department-admin")
@PreAuthorize("hasRole('DEPARTMENT_ADMIN')")
public class DepartmentAdminController {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentTypeService appointmentTypeService;

    @Autowired
    private AvailableTimeSlotService availableTimeSlotService;

    @GetMapping("/edit")
    public String showEditDepartmentForm(Model model, Authentication authentication) {
        try {
            Department department = departmentService.getCurrentUserManagedDepartment();
            model.addAttribute("department", department);
            model.addAttribute("username", authentication.getName());
            return "department-admin/edit-department";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt: " + e.getMessage());
            return "redirect:/department-admin/dashboard";
        }
    }

    @GetMapping("/departments/{departmentId}/edit-type/{typeId}")
    public String showEditAppointmentTypeForm(
            @PathVariable Long departmentId,
            @PathVariable Long typeId,
            Model model,
            Authentication authentication) {

        try {
            Department currentDepartment = departmentService.getCurrentUserManagedDepartment();
            if (!currentDepartment.getId().equals(departmentId)) {
                return "redirect:/department-admin/dashboard?error=Nem+van+jogosultságod+ehhez+a+részleghez";
            }

            AppointmentType appointmentType = appointmentTypeService.getAppointmentTypeById(typeId);
            if (!appointmentType.getDepartment().getId().equals(departmentId)) {
                return "redirect:/department-admin/departments/" + departmentId + "?error=Nem+található+az+időpont+típus";
            }

            model.addAttribute("department", currentDepartment);
            model.addAttribute("appointmentType", appointmentType);
            model.addAttribute("username", authentication.getName());

            return "department-admin/edit-appointment-type";

        } catch (Exception e) {
            return "redirect:/department-admin/departments/" + departmentId + "?error=" + e.getMessage();
        }
    }

    //Időpont típus törlése
    @PostMapping("/{departmentId}/delete-appointment-type/{typeId}")
    public String deleteAppointmentType(@PathVariable Long departmentId,
                                        @PathVariable Long typeId,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        try {
            Department currentDepartment = departmentService.getCurrentUserManagedDepartment();
            if (!currentDepartment.getId().equals(departmentId)) {
                redirectAttributes.addFlashAttribute("error", "Nincs jogosultságod ehhez a részleghez");
                return "redirect:/department-admin/dashboard";
            }
            appointmentTypeService.deleteAppointmentType(typeId);

              redirectAttributes.addFlashAttribute("success", "Időpont típus sikeresen törölve!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/department-admin/departments/" + departmentId;
    }

    @PostMapping("/{departmentId}/update-appointment-type/{typeId}")
    public String updateAppointmentType(
            @PathVariable Long departmentId,
            @PathVariable Long typeId,
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam Integer defaultDurationMinutes,
            @RequestParam Integer maxParticipants,
            @RequestParam(required = false) Integer bufferMinutes,
            @RequestParam(defaultValue = "true") boolean requiresApproval,
            @RequestParam(defaultValue = "true") boolean active,
            RedirectAttributes redirectAttributes) {

        try {
            Department currentDepartment = departmentService.getCurrentUserManagedDepartment();
            if (!currentDepartment.getId().equals(departmentId)) {
                redirectAttributes.addFlashAttribute("error", "Nem van jogosultságod ehhez a részleghez");
                return "redirect:/department-admin/dashboard";
            }

            appointmentTypeService.updateAppointmentType(
                    typeId,
                    name,
                    description,
                    defaultDurationMinutes,
                    maxParticipants,
                    bufferMinutes != null ? bufferMinutes : 0,
                    requiresApproval,
                    active
            );

            redirectAttributes.addFlashAttribute("success", "Időpont típus sikeresen frissítve!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/department-admin/departments/" + departmentId;
    }

    @PostMapping("/update")
    public String updateDepartment(@RequestParam String description,
                                   RedirectAttributes redirectAttributes,
                                   Authentication authentication) {
        try {
            Department department = departmentService.getCurrentUserManagedDepartment();
            departmentService.updateDepartment(department.getId(), department.getName(), description);
            redirectAttributes.addFlashAttribute("success", "Részleg sikeresen frissítve!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }
        return "redirect:/department-admin/dashboard";
    }

    @GetMapping("/departments/{id}")
    public String viewDepartmentDetail(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Department department = departmentService.getDepartmentWithFullDetails(id);
            Department currentDepartment = departmentService.getCurrentUserManagedDepartment();

            // Ellenőrizzük, hogy a felhasználónak joga van-e ehhez a részleghez
            if (!currentDepartment.getId().equals(id)) {
                return "redirect:/department-admin/dashboard?error=Nem+van+jogosultságod+ehhez+a+részleghez";
            }

            model.addAttribute("department", department);
            model.addAttribute("username", authentication.getName());
            model.addAttribute("pathvariable", id);

            return "department-admin/department-detail";
        } catch (Exception e) {
            return "redirect:/department-admin/dashboard?error=" + e.getMessage();
        }
    }

    /**
     * Időpont részletes megtekintése
     */
    @GetMapping("/appointment/{id}")
    public String viewAppointmentDetail(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Appointment appointment = appointmentService.getAppointmentById(id);
            model.addAttribute("appointment", appointment);
            model.addAttribute("user", appointment.getUser());
            model.addAttribute("username", authentication.getName());
            return "department-admin/appointment-detail";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "redirect:/department-admin/appointments";
        }
    }

    @GetMapping("/departments/{id}/new-type")
    public String showCreateAppointmentTypeForm(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Department department = departmentService.getDepartmentById(id);
            Department currentDepartment = departmentService.getCurrentUserManagedDepartment();

            // Ellenőrizzük, hogy a felhasználónak joga van-e ehhez a részleghez
            if (!currentDepartment.getId().equals(id)) {
                return "redirect:/department-admin/dashboard?error=Nem+van+jogosultságod+ehhez+a+részleghez";
            }

            model.addAttribute("department", department);
            model.addAttribute("username", authentication.getName());
            return "department-admin/new-appointment-type";

        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt: " + e.getMessage());
            return "redirect:/department-admin/departments/" + id;
        }
    }

    @PostMapping("/{departmentId}/create-appointment-type")
    public String createAppointmentType(
            @PathVariable("departmentId") Long departmentId,
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam Integer defaultDurationMinutes,
            @RequestParam Integer maxParticipants,
            @RequestParam(required = false) Integer bufferMinutes,
            @RequestParam(defaultValue = "true") boolean requiresApproval,
            RedirectAttributes redirectAttributes) {

        try {
            Department currentDepartment = departmentService.getCurrentUserManagedDepartment();

            // Ellenőrizzük, hogy a felhasználónak joga van-e ehhez a részleghez
            if (!currentDepartment.getId().equals(departmentId)) {
                redirectAttributes.addFlashAttribute("error", "Nem van jogosultságod ehhez a részleghez");
                return "redirect:/department-admin/dashboard";
            }

            appointmentTypeService.createAppointmentType(
                    name,
                    description,
                    defaultDurationMinutes,
                    maxParticipants,
                    bufferMinutes != null ? bufferMinutes : 0,
                    requiresApproval,
                    departmentId
            );
            redirectAttributes.addFlashAttribute("success", "Időpont típus sikeresen létrehozva!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }
        return "redirect:/department-admin/departments/" + departmentId;
    }

    @PostMapping("/{departmentId}/create-appointments")
    public String createBulkAppointments(
            @PathVariable Long departmentId,
            @RequestParam Long appointmentTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam Integer duration,
            @RequestParam(required = false) List<DayOfWeek> selectedDays,
            RedirectAttributes redirectAttributes) {

        try {
            Department currentDepartment = departmentService.getCurrentUserManagedDepartment();

            if (!currentDepartment.getId().equals(departmentId)) {
                redirectAttributes.addFlashAttribute("error", "Nem van jogosultságod ehhez a részleghez");
                return "redirect:/department-admin/dashboard";
            }

            if (endDate == null) {
                endDate = startDate;
            }

            // Ha nincs kiválasztva nap, akkor alapértelmezetten minden hétköznap
            if (selectedDays == null || selectedDays.isEmpty()) {
                selectedDays = List.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                );
            }

            appointmentService.createBulkAppointments(
                    appointmentTypeId,
                    startDate,
                    endDate,
                    startTime,
                    endTime,
                    duration,
                    selectedDays
            );

            redirectAttributes.addFlashAttribute("success", "Időpontok sikeresen létrehozva!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/department-admin/departments/" + departmentId;
    }

    @GetMapping("/{departmentId}/appointments/weekly")
    @ResponseBody
    public Map<String, List<AppointmentSlotDTO>> getWeeklyAppointments(
            @PathVariable Long departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) Long appointmentTypeId) {

        try {
            return appointmentService.getWeeklyAppointmentSlots(departmentId, startDate, appointmentTypeId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Hiba történt: " + e.getMessage());
        }
    }

    // ========== IDŐPONT KEZELÉS ==========

    /**
     * Admin időpont áttekintő oldal
     */
    @GetMapping("/appointments")
    public String listAllAppointments(Model model, Authentication authentication) {
        try {
            Department department = departmentService.getCurrentUserManagedDepartment();
            List<Appointment> allAppointments = appointmentService.getAllAppointmentsbyDepartment(department.getId());
            List<Appointment> upcomingAppointments = appointmentService.getAllUpcomingAppointmentsbyDepartment(department.getId());
            List<Appointment> pendingAppointments = appointmentService.getPendingAppointmentsbyDepartment(department.getId());
            long pendingCount = appointmentService.getPendingAppointmentsForDepartment(department.getId());

            model.addAttribute("allAppointments", allAppointments);
            model.addAttribute("upcomingAppointments", upcomingAppointments);
            model.addAttribute("pendingAppointments", pendingAppointments);
            model.addAttribute("pendingCount", pendingCount);
            model.addAttribute("username", authentication.getName());

            return "department-admin/appointments";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt az időpontok betöltésekor: " + e.getMessage());
            return "department-admin/appointments";
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
            return "department-admin/pending-appointments";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt: " + e.getMessage());
            return "department-admin/pending-appointments";
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

        return "redirect:/department-admin/appointments";
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

        return "redirect:/department-admin/appointments";
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

        return "redirect:/department-admin/appointments";
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

        return "redirect:/department-admin/appointments";
    }

    /**
     * TimeSlot törlése - CSRF token nélkül
     */
    @DeleteMapping("/{departmentId}/delete-timeslot/{timeSlotId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteTimeSlot(
            @PathVariable Long departmentId,
            @PathVariable Long timeSlotId) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Jogosultság ellenőrzése
            Department currentDepartment = departmentService.getCurrentUserManagedDepartment();
            if (!currentDepartment.getId().equals(departmentId)) {
                response.put("success", false);
                response.put("message", "Nincs jogosultságod ehhez a részleghez");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // TimeSlot lekérése és jogosultság ellenőrzése
            AvailableTimeSlot timeSlot = availableTimeSlotService.getTimeSlotById(timeSlotId);
            if (!timeSlot.getAppointmentType().getDepartment().getId().equals(departmentId)) {
                response.put("success", false);
                response.put("message", "Ez az időpont nem ehhez a részleghez tartozik");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Törlés végrehajtása
            availableTimeSlotService.deleteTimeSlot(timeSlotId);

            response.put("success", true);
            response.put("message", "Időpont sikeresen törölve!");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Hiba történt: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


}