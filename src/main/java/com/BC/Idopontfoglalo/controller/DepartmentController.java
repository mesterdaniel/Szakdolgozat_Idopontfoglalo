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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
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
@RequestMapping("/admin/departments")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private AppointmentTypeService appointmentTypeService;

    @Autowired
    private NamedParameterJdbcOperations namedParameterJdbcOperations;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AvailableTimeSlotService availableTimeSlotService;

    @GetMapping
    public String listAllDepartments(Model model, Authentication authentication) {
        try {
            List<Department> departments = departmentService.getAllDepartments();
            model.addAttribute("departments", departments);
            model.addAttribute("username", authentication.getName());
            return "admin/departments";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt a részlegek betöltésekor: " + e.getMessage());
            return "admin/departments";
        }
    }

    @GetMapping("/new")
    public String showCreateDepartmentForm(Model model, Authentication authentication) {
        model.addAttribute("username", authentication.getName());
        return "admin/new-department";
    }

    @PostMapping("/create")
    public String createDepartment(@RequestParam String name,
                                   @RequestParam String description,
                                   RedirectAttributes redirectAttributes) {
        try {
            departmentService.createDepartment(name, description);
            redirectAttributes.addFlashAttribute("success", "Részleg sikeresen létrehozva!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @GetMapping("/{id}")
    public String viewDepartmentDetail(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Department department = departmentService.getDepartmentWithFullDetails(id);

            model.addAttribute("department", department);
            model.addAttribute("username", authentication.getName());
            model.addAttribute("pathvariable", id);

            return "admin/department-detail";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt: " + e.getMessage());
            return "redirect:/admin/departments";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditDepartmentForm(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Department department = departmentService.getDepartmentById(id);
            model.addAttribute("department", department);
            model.addAttribute("username", authentication.getName());
            return "admin/edit-department";
        } catch (Exception e) {
            model.addAttribute("error", "Hiba történt: " + e.getMessage());
            return "redirect:/admin/departments";
        }
    }

    @GetMapping("/{id}/new-type")
    public String createAppointmentType(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            Department department = departmentService.getDepartmentById(id);
            model.addAttribute("department", department);
            model.addAttribute("username", authentication.getName());
            return "admin/new-appointment-type";

        }catch (Exception e) {
            model.addAttribute("error", "Hiba történt: " + e.getMessage());
            return "redirect:/admin/departments/{id}";
        }
    }

    @GetMapping("/{departmentId}/edit-type/{typeId}")
    public String showEditAppointmentTypeForm(
            @PathVariable Long departmentId,
            @PathVariable Long typeId,
            Model model,
            Authentication authentication) {

        try {
            Department currentDepartment = departmentService.getDepartmentById(departmentId);


            AppointmentType appointmentType = appointmentTypeService.getAppointmentTypeById(typeId);
            if (!appointmentType.getDepartment().getId().equals(departmentId)) {
                return "redirect:/admin/departments/" + departmentId + "?error=Nem+található+az+időpont+típus";
            }

            model.addAttribute("department", currentDepartment);
            model.addAttribute("appointmentType", appointmentType);
            model.addAttribute("username", authentication.getName());

            return "admin/edit-appointment-type";

        } catch (Exception e) {
            return "redirect:/admin/departments/" + departmentId + "?error=" + e.getMessage();
        }
    }

    //Időpont típus törlése
    @PostMapping("/{departmentId}/delete-appointment-type/{typeId}")
    public String deleteAppointmentType(@PathVariable Long departmentId,
                                        @PathVariable Long typeId,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        try {
            Department currentDepartment = departmentService.getDepartmentById(departmentId);

            appointmentTypeService.deleteAppointmentType(typeId);

            redirectAttributes.addFlashAttribute("success", "Időpont típus sikeresen törölve!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }

        return "redirect:/admin/departments/" + departmentId;
    }

    @PostMapping("/{id}/update")
    public String updateDepartment(@PathVariable Long id,
                                   @RequestParam String name,
                                   @RequestParam String description,
                                   @RequestParam(required = false) boolean active,
                                   RedirectAttributes redirectAttributes) {
        try {
            departmentService.updateDepartment(id, name, description);
            redirectAttributes.addFlashAttribute("success", "Részleg sikeresen frissítve!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }
        return "redirect:/admin/departments/" + id;
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleDepartmentStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            departmentService.toggleDepartmentStatus(id);
            redirectAttributes.addFlashAttribute("success", "Részleg állapota sikeresen megváltoztatva!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }
        return "redirect:/admin/departments/" + id;
    }

    @PostMapping("/{id}/assign-admin")
    public String assignDepartmentAdmin(@PathVariable Long id,
                                        @RequestParam String username,
                                        @RequestParam(required = false) String password,
                                        @RequestParam(defaultValue = "false") boolean createNew,
                                        RedirectAttributes redirectAttributes) {
        try {
            User admin = departmentService.createOrAssignDepartmentAdmin(id, username, password, createNew);
            redirectAttributes.addFlashAttribute("success",
                    "Admin sikeresen " + (createNew ? "létrehozva és " : "") + "hozzárendelve!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }
        return "redirect:/admin/departments/" + id;
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

        return "redirect:/admin/departments/" + departmentId;
    }

    @PostMapping("/{id}/remove-admin/{adminId}")
    public String removeDepartmentAdmin(@PathVariable Long id,
                                        @PathVariable Long adminId,
                                        RedirectAttributes redirectAttributes) {
        try {
            departmentService.removeDepartmentAdmin(id, adminId);
            redirectAttributes.addFlashAttribute("success", "Admin sikeresen eltávolítva!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }
        return "redirect:/admin/departments/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteDepartment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            departmentService.deleteDepartment(id);
            redirectAttributes.addFlashAttribute("success", "Részleg sikeresen törölve!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt: " + e.getMessage());
        }
        return "redirect:/admin/departments";
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
            Department currentDepartment = departmentService.getDepartmentById(departmentId);



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
        return "redirect:/admin/departments/" + departmentId;
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
            // Ha nincs vég dátum, akkor csak egy napra
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

        return "redirect:/admin/departments/" + departmentId;
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