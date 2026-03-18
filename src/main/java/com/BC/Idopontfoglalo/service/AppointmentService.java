package com.BC.Idopontfoglalo.service;

import com.BC.Idopontfoglalo.entity.*;
import com.BC.Idopontfoglalo.repository.AppointmentRepository;
import com.BC.Idopontfoglalo.repository.AvailableTimeSlotRepository;
import com.BC.Idopontfoglalo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentTypeService appointmentTypeService;

    @Autowired
    private AvailableTimeSlotRepository availableTimeSlotRepository;
    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private EmailService emailService;

    // ========== FELHASZNÁLÓI MŰVELETEK ==========

    /**
     * Új időpont létrehozása
     */
    public Appointment createAppointment(String title, String description,
                                         LocalDateTime appointmentDate, Integer durationMinutes, AppointmentType appointmentType) {
        // Aktuális felhasználó lekérése
        User currentUser = getCurrentUser();

        // Validációk
        validateAppointmentData(title, appointmentDate, durationMinutes);

        // Ütközés ellenőrzése
        LocalDateTime endTime = appointmentDate.plusMinutes(durationMinutes);
        if (hasConflictingAppointment(appointmentDate, endTime)) {
            throw new IllegalArgumentException("Az adott időpontban már van foglalás!");
        }

        // Új időpont létrehozása
        Appointment appointment = new Appointment(title, description, appointmentDate, durationMinutes, currentUser, appointmentType);
        Appointment savedAppointment = appointmentRepository.save(appointment);

        // Send confirmation email
        sendAppointmentConfirmationEmail(savedAppointment);

        return savedAppointment;
    }

    /**
     * Felhasználó saját időpontjainak lekérése
     */
    public List<Appointment> getUserAppointments() {
        User currentUser = getCurrentUser();
        return appointmentRepository.findByUserOrderByAppointmentDateAsc(currentUser);
    }

    /**
     * Felhasználó jövőbeli időpontjai
     */
    public List<Appointment> getUserUpcomingAppointments() {
        User currentUser = getCurrentUser();
        return appointmentRepository.findUpcomingAppointmentsByUser(currentUser, LocalDateTime.now());
    }

    /**
     * Felhasználó múltbeli időpontjai
     */
    public List<Appointment> getUserPastAppointments() {
        User currentUser = getCurrentUser();
        return appointmentRepository.findPastAppointmentsByUser(currentUser, LocalDateTime.now());
    }


    public void deleteAppointment(Long appointmentId) {
        Appointment appointment = getAppointmentById(appointmentId);
        User currentUser = getCurrentUser();
        // Ellenőrizzük a jogosultságot
        if (!currentUser.isDepartmentAdmin() && !currentUser.hasAdminRole()) {
            throw new IllegalArgumentException("Nincs jogosultságod törölni!");
        }
        appointmentRepository.delete(appointment);
    }

    /**
     * Időpont módosítása (csak saját időpont)
     */


    public Appointment updateAppointment(Long appointmentId, String title, String description,
                                         LocalDateTime appointmentDate, Integer durationMinutes) {
        Appointment appointment = getAppointmentById(appointmentId);

        User currentUser = getCurrentUser();

        // Ellenőrizzük a jogosultságot
        if (!appointment.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Csak saját időpontot lehet módosítani!");
        }

        // Validációk
        validateAppointmentData(title, appointmentDate, durationMinutes);

        // Ütközés ellenőrzése (kivéve saját időpont)
        LocalDateTime endTime = appointmentDate.plusMinutes(durationMinutes);
        if (!appointment.getAppointmentDate().equals(appointmentDate) ||
                !appointment.getDurationMinutes().equals(durationMinutes)) {

            if (hasConflictingAppointment(appointmentDate, endTime, appointmentId)) {
                throw new IllegalArgumentException("Az adott időpontban már van foglalás!");
            }
        }

        // Módosítások alkalmazása
        appointment.setTitle(title);
        appointment.setDescription(description);
        appointment.setAppointmentDate(appointmentDate);
        appointment.setDurationMinutes(durationMinutes);
        appointment.setUpdatedAt(LocalDateTime.now());

        Appointment updatedAppointment = appointmentRepository.save(appointment);
        
        // Send update email
        sendAppointmentUpdateEmail(updatedAppointment);
        
        return updatedAppointment;
    }

    // ========== ADMIN MŰVELETEK ==========

    /**
     * Összes időpont lekérése (admin)
     */
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    /**
     * Összes jövőbeli időpont (admin)
     */
    public List<Appointment> getAllUpcomingAppointments() {
        return appointmentRepository.findAllUpcomingAppointments(LocalDateTime.now());
    }

    /**
     * Függőben lévő időpontok (admin jóváhagyáshoz)
     */
    public List<Appointment> getPendingAppointments() {
        return appointmentRepository.findByStatusOrderByAppointmentDateAsc(AppointmentStatus.PENDING);
    }

    /**
     * Időpont jóváhagyása (admin)
     */
    public void approveAppointment(Long appointmentId) {
        Appointment appointment = getAppointmentById(appointmentId);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appointment);
        
        // Send approval email
        sendAppointmentStatusEmail(appointment, "elfogadva", "Az időpontja jóváhagyásra került és bekerült a naptárba.");
    }

    /**
     * Időpont elutasítása (admin)
     */
    public void rejectAppointment(Long appointmentId) {
        Appointment appointment = getAppointmentById(appointmentId);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        
        // Send rejection email
        sendAppointmentStatusEmail(appointment, "elutasítva", "Sajnáljuk, de a kért időpontot elutasították. Kérjük, foglaljon egy másik időpontot.");
    }

    /**
     * Függőben lévő időpontok száma (dashboard-hoz)
     */
    public long getPendingAppointmentsCount() {
        return appointmentRepository.countByStatus(AppointmentStatus.PENDING);
    }

    // ========== SEGÉD METÓDUSOK ==========

    /**
     * Időpont lekérése ID alapján
     */
    public Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nem található időpont ezzel az ID-val: " + id));
    }

    /**
     * Aktuális bejelentkezett felhasználó lekérése
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Nem található felhasználó: " + username));
    }

    /**
     * Ütközés ellenőrzése - van-e már időpont ebben az időszakban
     */
    private boolean hasConflictingAppointment(LocalDateTime startTime, LocalDateTime endTime) {
        List<Appointment> conflicting = appointmentRepository.findConflictingAppointments(startTime, endTime);

        // Részletes ellenőrzés: valóban ütközik-e az időpont
        for (Appointment existing : conflicting) {
            LocalDateTime existingEnd = existing.getAppointmentDate().plusMinutes(existing.getDurationMinutes());

            // Ha az időpontok átfednek
            if (startTime.isBefore(existingEnd) && endTime.isAfter(existing.getAppointmentDate())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ütközés ellenőrzése módosításkor (saját időpontot kizárva)
     */
    private boolean hasConflictingAppointment(LocalDateTime startTime, LocalDateTime endTime, Long excludeId) {
        List<Appointment> conflicting = appointmentRepository.findConflictingAppointments(startTime, endTime);

        for (Appointment existing : conflicting) {
            // Saját időpontot kihagyjuk
            if (existing.getId().equals(excludeId)) {
                continue;
            }

            LocalDateTime existingEnd = existing.getAppointmentDate().plusMinutes(existing.getDurationMinutes());

            if (startTime.isBefore(existingEnd) && endTime.isAfter(existing.getAppointmentDate())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Időpont adatok validálása
     */
    private void validateAppointmentData(String title, LocalDateTime appointmentDate, Integer durationMinutes) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Az időpont címe nem lehet üres!");
        }

        if (appointmentDate == null) {
            throw new IllegalArgumentException("Az időpont dátuma kötelező!");
        }

        if (appointmentDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Nem lehet múltbeli időpontra foglalni!");
        }

        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalArgumentException("Az időtartam legalább 1 perc kell legyen!");
        }

        if (durationMinutes > 480) { // 8 óra
            throw new IllegalArgumentException("Az időtartam maximum 8 óra lehet!");
        }
    }

    /**
     * Count all appointments for a specific department
     */
    public long countByDepartment(Department department) {
        return appointmentRepository.countByAppointmentType_Department(department);
    }

    /**
     * Count pending appointments for a specific department
     */
    public long countPendingByDepartment(Department department) {
        return appointmentRepository.countByAppointmentType_DepartmentAndStatus(
                department,
                AppointmentStatus.PENDING
        );
    }

    /**
     * Count upcoming appointments for a specific department
     */
    public long countUpcomingByDepartment(Department department) {
        return appointmentRepository.countUpcomingByDepartment(
                department,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void createBulkAppointments(Long appointmentTypeId, LocalDate startDate, LocalDate endDate,
                                       LocalTime startTime, LocalTime endTime, Integer durationMinutes,
                                       List<DayOfWeek> selectedDays) {

        AppointmentType appointmentType = appointmentTypeService.getAppointmentTypeById(appointmentTypeId);

        // Ellenőrizzük, hogy a részleg aktív-e
        if (!appointmentType.getDepartment().isActive()) {
            throw new IllegalArgumentException("Inaktív részleghez nem lehet időpontot létrehozni!");
        }

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Ellenőrizzük, hogy ez a nap szerepel-e a kiválasztott napok között
            if (selectedDays.contains(currentDate.getDayOfWeek())) {
                createAppointmentsForDay(appointmentType, currentDate, startTime, endTime, durationMinutes);
            }
            currentDate = currentDate.plusDays(1);
        }
    }

    private void createAppointmentsForDay(AppointmentType appointmentType, LocalDate date,
                                          LocalTime startTime, LocalTime endTime, Integer durationMinutes) {

        LocalTime currentTime = startTime;

        while (currentTime.plusMinutes(durationMinutes).isBefore(endTime) ||
                currentTime.plusMinutes(durationMinutes).equals(endTime)) {

            LocalDateTime appointmentDateTime = LocalDateTime.of(date, currentTime);

            // Ellenőrizzük, hogy nincs-e már időpont ebben az időszakban
            if (!hasConflictingAppointment(appointmentDateTime,
                    appointmentDateTime.plusMinutes(durationMinutes))) {

                // Üres időpont létrehozása (nincs hozzárendelve felhasználó)
                AvailableTimeSlot timeSlot = new AvailableTimeSlot();
                timeSlot.setAppointmentType(appointmentType);
                timeSlot.setStartTime(appointmentDateTime);
                timeSlot.setDurationMinutes(durationMinutes);
                timeSlot.setMaxAttendees(appointmentType.getMaxParticipants());
                timeSlot.setCurrentAttendees(0);
                timeSlot.setStatus(TimeSlotStatus.AVAILABLE);

                availableTimeSlotRepository.save(timeSlot);
            }

            currentTime = currentTime.plusMinutes(durationMinutes);
        }
    }

    public Map<String, List<AppointmentSlotDTO>> getWeeklyAppointmentSlots(Long departmentId, LocalDate startDate, Long appointmentTypeId) {
        LocalDateTime weekStart = startDate.atStartOfDay();
        LocalDateTime weekEnd = startDate.plusDays(6).atTime(23, 59, 59);

        List<AvailableTimeSlot> timeSlots;

        // VÁLTOZÁS: Itt dől el, melyik lekérdezést használjuk
        if (appointmentTypeId != null) {
            // Ha a felhasználó szűrt, az új, típus-specifikus metódust hívjuk.
            timeSlots = availableTimeSlotRepository.findAvailableSlotsByDepartmentAndTypeAndTimeRange(
                    departmentId,
                    appointmentTypeId,
                    weekStart,
                    weekEnd
            );
        } else {
            // Ha nincs szűrés, a meglévő, összes időpontot lekérő metódust használjuk.
            timeSlots = availableTimeSlotRepository.findByAppointmentType_Department_IdAndStartTimeBetween(
                    departmentId,
                    weekStart,
                    weekEnd
            );
        }


        Map<String, List<AppointmentSlotDTO>> result = new HashMap<>();

        // Napok inicializálása
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
        for (String day : days) {
            result.put(day, new ArrayList<>());
        }

        // Csoportosítás napok szerint
        for (AvailableTimeSlot slot : timeSlots) {
            DayOfWeek dayOfWeek = slot.getStartTime().getDayOfWeek();
            String dayKey = dayOfWeek.name().toLowerCase();

            AppointmentSlotDTO dto = new AppointmentSlotDTO();
            dto.setId(slot.getId());
            dto.setStartTime(slot.getStartTime());
            dto.setEndTime(slot.getStartTime().plusMinutes(slot.getDurationMinutes()));
            dto.setTypeName(slot.getAppointmentType().getName());
            dto.setMaxAttendees(slot.getMaxAttendees());
            dto.setCurrentAttendees(slot.getCurrentAttendees());
            dto.setStatus(slot.getStatus().name());

            result.get(dayKey).add(dto);
        }

        // Időpontok rendezése naponként
        for (List<AppointmentSlotDTO> daySlots : result.values()) {
            daySlots.sort(Comparator.comparing(AppointmentSlotDTO::getStartTime));
        }

        return result;
    }



    /**
     * Időpont foglalása felhasználó által
     * @param timeSlotId - Az AvailableTimeSlot ID-ja
     * @param username - A foglaló felhasználó neve
     * @param notes - Opcionális megjegyzések
     */
    @Transactional
    public void bookAppointment(Long timeSlotId, String username, String notes) {
        // TimeSlot lekérése
        AvailableTimeSlot timeSlot = availableTimeSlotRepository.findById(timeSlotId)
                .orElseThrow(() -> new IllegalArgumentException("Nem található időpont slot: " + timeSlotId));

        // Felhasználó lekérése
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Nem található felhasználó: " + username));

        // Validációk
        validateBookingRequest(timeSlot, user);

        // Appointment létrehozása
        Appointment appointment = new Appointment();
        appointment.setTitle(timeSlot.getAppointmentType().getName());
        appointment.setDescription(notes);
        appointment.setAppointmentDate(timeSlot.getStartTime());
        appointment.setDurationMinutes(timeSlot.getDurationMinutes());
        appointment.setUser(user);
        appointment.setAppointmentType(timeSlot.getAppointmentType());

        // Status beállítása a típus alapján
        if (timeSlot.getAppointmentType().isRequiresApproval()) {
            appointment.setStatus(AppointmentStatus.PENDING);
        } else {
            appointment.setStatus(AppointmentStatus.CONFIRMED);
        }

        Appointment savedAppointment = appointmentRepository.save(appointment);

        // TimeSlot frissítése
        timeSlot.setCurrentAttendees(timeSlot.getCurrentAttendees() + 1);

        // Ha elérte a max létszámot, akkor FULL státuszra állítjuk
        if (timeSlot.getCurrentAttendees() >= timeSlot.getMaxAttendees()) {
            timeSlot.setStatus(TimeSlotStatus.FULL);
        }

        availableTimeSlotRepository.save(timeSlot);
        
        // Send booking confirmation email
        sendAppointmentConfirmationEmail(savedAppointment);
    }

    /**
     * Foglalási kérelem validálása
     */
    private void validateBookingRequest(AvailableTimeSlot timeSlot, User user) {
        // TimeSlot elérhetőség ellenőrzése
        if (timeSlot.getStatus() != TimeSlotStatus.AVAILABLE) {
            throw new IllegalArgumentException("Ez az időpont már nem elérhető!");
        }

        // Múltbeli időpont ellenőrzése
        if (timeSlot.getStartTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Múltbeli időpontra nem lehet foglalni!");
        }

        // Kapacitás ellenőrzése
        if (timeSlot.getCurrentAttendees() >= timeSlot.getMaxAttendees()) {
            throw new IllegalArgumentException("Ez az időpont már betelt!");
        }

        // Részleg aktivitás ellenőrzése
        if (!timeSlot.getAppointmentType().getDepartment().isActive()) {
            throw new IllegalArgumentException("Ez a részleg jelenleg nem aktív!");
        }

        // Időpont típus aktivitás ellenőrzése
        if (!timeSlot.getAppointmentType().isActive()) {
            throw new IllegalArgumentException("Ez az időpont típus jelenleg nem aktív!");
        }

        // Dupla foglalás ellenőrzése - ugyanaz a felhasználó, ugyanabban az időpontban
        boolean hasConflict = appointmentRepository.existsByUserAndAppointmentDateBetweenAndStatusNot(
                user,
                timeSlot.getStartTime(),
                timeSlot.getStartTime().plusMinutes(timeSlot.getDurationMinutes()),
                AppointmentStatus.CANCELLED
        );

        if (hasConflict) {
            throw new IllegalArgumentException("Már van foglalásod ebben az időszakban!");
        }
    }

    /**
     * Időpont lemondása - frissített verzió TimeSlot-okkal
     */

    @Transactional
    public void cancelAppointment(Long appointmentId) {
        Appointment appointment = getAppointmentById(appointmentId);
        User currentUser = getCurrentUser();

        boolean isOwner = appointment.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.hasAdminRole() || currentUser.isDepartmentAdmin() || currentUser.isSuperAdmin();

        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Csak saját időpontot vagy admin jogosultsággal lehet lemondani!");
        }


        // Státusz ellenőrzése
        if (appointment.getStatus() == AppointmentStatus.CANCELLED ||
                appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Ez az időpont már nem mondható le!");
        }

        if (!isAdmin && appointment.getAppointmentDate().isBefore(LocalDateTime.now().plusHours(24))) {
            throw new IllegalArgumentException("Az időpont lemondása csak 24 órával a kezdés előtt lehetséges!");
        }

        // Appointment lemondása
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);

        // Kapcsolódó TimeSlot frissítése
        updateTimeSlotAfterCancellation(appointment);
        
        // Send cancellation email
        sendAppointmentStatusEmail(appointment, "lemondva", "Sajnálattal vettük tudomásul, hogy lemondta az időpontját.");
    }

    public void completeAppointment(Long appointmentId) {
        Appointment appointment = getAppointmentById(appointmentId);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);
    }

    /**
     * TimeSlot frissítése lemondás után
     */
    private void updateTimeSlotAfterCancellation(Appointment appointment) {
        // Keresés a TimeSlot-ra
        List<AvailableTimeSlot> timeSlots = availableTimeSlotRepository
                .findByAppointmentTypeAndStartTime(
                        appointment.getAppointmentType(),
                        appointment.getAppointmentDate()
                );

        if (!timeSlots.isEmpty()) {
            AvailableTimeSlot timeSlot = timeSlots.get(0);

            // Résztvevők számának csökkentése
            timeSlot.setCurrentAttendees(Math.max(0, timeSlot.getCurrentAttendees() - 1));

            // Ha volt FULL, most AVAILABLE lehet
            if (timeSlot.getStatus() == TimeSlotStatus.FULL) {
                timeSlot.setStatus(TimeSlotStatus.AVAILABLE);
            }

            availableTimeSlotRepository.save(timeSlot);
        }
    }

    /**
     * Felhasználó jövőbeli időpontjai TimeSlot információkkal
     */
    public List<Appointment> getUserUpcomingAppointmentsWithDetails() {
        User currentUser = getCurrentUser();
        return appointmentRepository.findUpcomingAppointmentsByUserWithType(
                currentUser,
                LocalDateTime.now()
        );
    }

    // AppointmentService.java-ba hozzáadandó segédmetódus

    /**
     * TimeSlot lekérése ID alapján
     */
    public AvailableTimeSlot getTimeSlotById(Long timeSlotId) {
        return availableTimeSlotRepository.findById(timeSlotId)
                .orElseThrow(() -> new IllegalArgumentException("Nem található időpont slot: " + timeSlotId));
    }

    /**
     * Felhasználó mai időpontjai
     */
    public List<Appointment> getUserTodayAppointments() {
        User currentUser = getCurrentUser();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);

        return appointmentRepository.findByUserAndAppointmentDateBetweenAndStatusNotOrderByAppointmentDateAsc(
                currentUser, startOfDay, endOfDay, AppointmentStatus.CANCELLED
        );
    }

    /**
     * Dashboard statisztikák felhasználónak
     */
    public Map<String, Object> getUserDashboardStats() {
        User currentUser = getCurrentUser();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAppointments", appointmentRepository.countByUser(currentUser));
        stats.put("upcomingAppointments", appointmentRepository.countUpcomingByUser(currentUser, LocalDateTime.now()));
        stats.put("todayAppointments", getUserTodayAppointments().size());
        stats.put("pendingAppointments", appointmentRepository.countByUserAndStatus(currentUser, AppointmentStatus.PENDING));

        return stats;
    }

    // AppointmentService.java-ba hozzáadandó metódusok

    /**
     * Összes időpont lekérése részleghez (régi névkonvenció kompatibilitás)
     */
    public long getTotalAppointmentsForDepartment(Long departmentId) {
        Department department = new Department();
        department.setId(departmentId);
        return countByDepartment(department);
    }

    /**
     * Függőben lévő időpontok lekérése részleghez (régi névkonvenció kompatibilitás)
     */
    public long getPendingAppointmentsForDepartment(Long departmentId) {
        Department department = new Department();
        department.setId(departmentId);
        return countPendingByDepartment(department);
    }

    /**
     * Jövőbeli időpontok lekérése részleghez (régi névkonvenció kompatibilitás)
     */
    public long getUpcomingAppointmentsForDepartment(Long departmentId) {
        Department department = new Department();
        department.setId(departmentId);
        return countUpcomingByDepartment(department);
    }


    // ========== ADMIN DEPARTMENT MŰVELETEK ==========

    /**
     * Összes időpont lekérése
     */
    public List<Appointment> getAllAppointmentsbyDepartment(Long departmentId) {
        Department department = departmentService.getDepartmentById(departmentId);
        return appointmentRepository.findByAppointmentType_DepartmentOrderByAppointmentDateAsc(department);
    }

    /**
     * Összes jövőbeli időpont
     */
    public List<Appointment> getAllUpcomingAppointmentsbyDepartment(Long departmentId) {
        Department department = departmentService.getDepartmentById(departmentId);
        return appointmentRepository.findAllUpcomingAppointmentsByDepartment(department, LocalDateTime.now());
    }

    /**
     * Függőben lévő időpontok (admin jóváhagyáshoz)
     */
    public List<Appointment> getPendingAppointmentsbyDepartment(Long departmentId) {
        Department department = departmentService.getDepartmentById(departmentId);
        return appointmentRepository.findByStatusOrderByAppointmentDateAscByDepartment(department, AppointmentStatus.PENDING);
    }
    
    // ========== EMAIL HELPER METHODS ==========
    
    private void sendAppointmentConfirmationEmail(Appointment appointment) {
        try {
            String to = appointment.getUser().getEmail(); // Use email instead of username
            if (to == null || to.isEmpty()) {
                    to = appointment.getUser().getUsername(); // Fallback to username if email is missing
            }
            String emailName;
            if (appointment.getUser().getFirstName() != null && appointment.getUser().getLastName() != null){
                emailName= appointment.getUser().getFirstName() + " " + appointment.getUser().getLastName();
            }else {emailName= appointment.getUser().getUsername();}
            
            String subject = "Időpontfoglalás megerősítése: " + appointment.getTitle();
            String text = String.format(
                "Kedves %s!\n\n" +
                "Az időpont foglalása sikeresen megtörtént. Az alábbiakban olvashatóak a részletek:\n\n" +
                "Időpont neve: %s\n" +
                "Dátum és idő: %s\n" +
                "Jelenlegi státusz: %s\n\n" +
                "Köszönjük, hogy a mi rendszerünket választotta!\n",
                emailName,
                appointment.getTitle(),
                appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("yyyy. MM. dd. HH:mm")),
                appointment.getStatus().getDisplayName()
            );
            emailService.sendSimpleMessage(to, subject, text);
        } catch (Exception e) {
            // Log error but don't fail the transaction
            System.err.println("Failed to send confirmation email: " + e.getMessage());
        }
    }

    private void sendAppointmentUpdateEmail(Appointment appointment) {
        try {
            String to = appointment.getUser().getEmail();
            if (to == null || to.isEmpty()) {
                to = appointment.getUser().getUsername();
            }
            String emailName;
            if (appointment.getUser().getFirstName() != null && appointment.getUser().getLastName() != null){
                emailName= appointment.getUser().getFirstName() + " " + appointment.getUser().getLastName();
            }else {emailName= appointment.getUser().getUsername();}
            
            String subject = "Időpont módosítva: " + appointment.getTitle();
            String text = String.format(
                "Kedves %s!\n\n" +
                "A foglalása módosításra került. Az új adatok a következők:\n\n" +
                "Időpont neve: %s\n" +
                "Dátum és idő: %s\n" +
                "Jelenlegi státusz: %s\n\n" +
                "Amennyiben további kérdése van, forduljon hozzánk bizalommal!\n",
                emailName,
                appointment.getTitle(),
                appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("yyyy. MM. dd. HH:mm")),
                appointment.getStatus().getDisplayName()
            );
            emailService.sendSimpleMessage(to, subject, text);
        } catch (Exception e) {
            System.err.println("Failed to send update email: " + e.getMessage());
        }
    }

    private void sendAppointmentStatusEmail(Appointment appointment, String statusAction, String customMessage) {
        try {
            String to = appointment.getUser().getEmail();
            if (to == null || to.isEmpty()) {
                to = appointment.getUser().getUsername();
            }
            String emailName;
            if (appointment.getUser().getFirstName() != null && appointment.getUser().getLastName() != null){
                emailName= appointment.getUser().getFirstName() + " " + appointment.getUser().getLastName();
            }else {emailName= appointment.getUser().getUsername();}
            
            String subject = "Időpont státusza változott (" + statusAction + "): " + appointment.getTitle();
            String text = String.format(
                "Kedves %s!\n\n" +
                "A foglalásának státusza megváltozott (%s).\n\n" +
                "%s\n\n" +
                "Részletek:\n" +
                "Időpont neve: %s\n" +
                "Dátum és idő: %s\n" +
                "Új státusz: %s\n\n" +
                "Üdvözlettel,\nAz Időpontfoglaló csapata",
                emailName,
                statusAction.toLowerCase(),
                customMessage,
                appointment.getTitle(),
                appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("yyyy. MM. dd. HH:mm")),
                appointment.getStatus().getDisplayName()
            );
            emailService.sendSimpleMessage(to, subject, text);
        } catch (Exception e) {
            System.err.println("Failed to send status email: " + e.getMessage());
        }
    }
}
