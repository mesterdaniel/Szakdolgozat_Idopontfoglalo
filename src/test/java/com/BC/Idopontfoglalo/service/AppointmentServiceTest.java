package com.BC.Idopontfoglalo.service;

import com.BC.Idopontfoglalo.entity.*;
import com.BC.Idopontfoglalo.repository.AppointmentRepository;
import com.BC.Idopontfoglalo.repository.AvailableTimeSlotRepository;
import com.BC.Idopontfoglalo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Az AppointmentService osztály egységtesztjei.
 *
 * A tesztek a Mockito keretrendszert használják, így az adatbázis
 * és a Spring kontextus teljes egészében helyettesített (mock) objektumokkal
 * kerül kiváltásra. Ez gyors, izolált tesztelést tesz lehetővé.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    // =========================================================
    // Mock objektumok – ezek helyettesítik a valódi függőségeket
    // =========================================================
    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AvailableTimeSlotRepository availableTimeSlotRepository;

    @Mock
    private AppointmentTypeService appointmentTypeService;

    @Mock
    private DepartmentService departmentService;

    @Mock
    private EmailService emailService;

    // A tesztelendő osztály – a mockokat a Mockito automatikusan injektálja
    @InjectMocks
    private AppointmentService appointmentService;

    // =========================================================
    // Teszteléshez használt segédobjektumok
    // =========================================================
    private User testUser;
    private Department testDepartment;
    private AppointmentType testAppointmentType;
    private AvailableTimeSlot testTimeSlot;

    /**
     * Minden egyes teszt futása előtt friss tesztobjektumokat hozunk létre,
     * és szimulálunk egy bejelentkezett felhasználót a SecurityContext-ben.
     */
    @BeforeEach
    void setUp() {
        // Tesztfelhasználó létrehozása
        testUser = new User("testuser", "test@example.com", "encodedPassword", "Teszt", "Felhasználó");
        testUser.setId(1L);
        Set<Role> roles = new HashSet<>();
        Role userRole = new Role();
        userRole.setRoleName("ROLE_USER");
        roles.add(userRole);
        testUser.setRoles(roles);

        // Tesztrészleg
        testDepartment = new Department("Orvosi rendelő", "Általános orvosi rendelő");
        testDepartment.setId(1L);
        testDepartment.setActive(true);

        // Időponttípus (nem igényel jóváhagyást -> CONFIRMED státusz)
        testAppointmentType = new AppointmentType(
                "Általános vizsgálat", "Általános orvosi vizsgálat", 30, 1, testDepartment
        );
        testAppointmentType.setId(1L);
        testAppointmentType.setActive(true);
        testAppointmentType.setRequiresApproval(false);

        // Szabad időpont slot – holnap 10:00-kor
        testTimeSlot = new AvailableTimeSlot();
        testTimeSlot.setId(1L);
        testTimeSlot.setAppointmentType(testAppointmentType);
        testTimeSlot.setStartTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        testTimeSlot.setDurationMinutes(30);
        testTimeSlot.setMaxAttendees(1);
        testTimeSlot.setCurrentAttendees(0);
        testTimeSlot.setStatus(TimeSlotStatus.AVAILABLE);

        // SecurityContext szimulálása: "testuser" van bejelentkezve
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("testuser");
        SecurityContextHolder.setContext(securityContext);
    }

    // =========================================================
    // 1. bookAppointment – sikeres foglalás
    // =========================================================

    @Test
    @DisplayName("Sikeres időpontfoglalás: a slot AVAILABLE státuszú, nincs ütközés")
    void bookAppointment_Success() {
        // Arrange – mit adjon vissza a mock, amikor hívják
        when(availableTimeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(appointmentRepository.existsByUserAndAppointmentDateBetweenAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(availableTimeSlotRepository.save(any(AvailableTimeSlot.class))).thenReturn(testTimeSlot);

        // Act – a tesztelendő metódus meghívása
        assertDoesNotThrow(() -> appointmentService.bookAppointment(1L, "testuser", "Megjegyzés"));

        // Assert – ellenőrzések
        verify(appointmentRepository, times(1)).save(any(Appointment.class));
        verify(availableTimeSlotRepository, times(1)).save(argThat(slot ->
                slot.getCurrentAttendees() == 1
        ));
    }

    // =========================================================
    // 2. bookAppointment – betelt időpont elutasítása
    // =========================================================

    @Test
    @DisplayName("Foglalás elutasítása: az időpont betelt (FULL státuszú slot)")
    void bookAppointment_ThrowsException_WhenSlotIsFull() {
        // Arrange – FULL státuszra állítjuk a slotot
        testTimeSlot.setStatus(TimeSlotStatus.FULL);
        when(availableTimeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act & Assert – kivételt várunk
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> appointmentService.bookAppointment(1L, "testuser", null)
        );
        assertEquals("Ez az időpont már nem elérhető!", exception.getMessage());

        // A mentés soha nem hívódhat meg
        verify(appointmentRepository, never()).save(any());
    }

    // =========================================================
    // 3. bookAppointment – múltbeli időpontra foglalás
    // =========================================================

    @Test
    @DisplayName("Foglalás elutasítása: a kiválasztott időpont a múltban van")
    void bookAppointment_ThrowsException_WhenSlotIsInThePast() {
        // Arrange – tegnapi időpontra állítjuk
        testTimeSlot.setStartTime(LocalDateTime.now().minusDays(1));
        when(availableTimeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> appointmentService.bookAppointment(1L, "testuser", null)
        );
        assertEquals("Múltbeli időpontra nem lehet foglalni!", exception.getMessage());
    }

    // =========================================================
    // 4. bookAppointment – dupla foglalás megakadályozása
    // =========================================================

    @Test
    @DisplayName("Dupla foglalás elutasítása: a felhasználónak már van foglalása ebben az időpontban")
    void bookAppointment_ThrowsException_WhenUserAlreadyHasAppointment() {
        // Arrange – a repository azt jelzi, hogy van már ütköző foglalás
        when(availableTimeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(appointmentRepository.existsByUserAndAppointmentDateBetweenAndStatusNot(
                any(), any(), any(), any())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> appointmentService.bookAppointment(1L, "testuser", null)
        );
        assertEquals("Már van foglalásod ebben az időszakban!", exception.getMessage());
    }

    // =========================================================
    // 5. bookAppointment – inaktív részleg
    // =========================================================

    @Test
    @DisplayName("Foglalás elutasítása: a részleg inaktív")
    void bookAppointment_ThrowsException_WhenDepartmentIsInactive() {
        // Arrange – inaktívra állítjuk a részleget
        testDepartment.setActive(false);
        when(availableTimeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> appointmentService.bookAppointment(1L, "testuser", null)
        );
        assertEquals("Ez a részleg jelenleg nem aktív!", exception.getMessage());
    }

    // =========================================================
    // 6. bookAppointment – jóváhagyást igénylő típus -> PENDING státusz
    // =========================================================

    @Test
    @DisplayName("Jóváhagyást igénylő időponttípus: a foglalás PENDING státuszba kerül")
    void bookAppointment_CreatesPendingAppointment_WhenApprovalRequired() {
        // Arrange – jóváhagyás szükséges
        testAppointmentType.setRequiresApproval(true);
        when(availableTimeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(appointmentRepository.existsByUserAndAppointmentDateBetweenAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(availableTimeSlotRepository.save(any())).thenReturn(testTimeSlot);

        // Act
        appointmentService.bookAppointment(1L, "testuser", null);

        // Assert – a mentett Appointment státusza PENDING kell legyen
        verify(appointmentRepository).save(argThat(appointment ->
                appointment.getStatus() == AppointmentStatus.PENDING
        ));
    }

    // =========================================================
    // 7. bookAppointment – az utolsó hely foglalásakor FULL lesz a slot
    // =========================================================

    @Test
    @DisplayName("Az utolsó szabad hely foglalásakor a slot FULL státuszra vált")
    void bookAppointment_SetsSlotToFull_WhenLastSeatTaken() {
        // Arrange – 1 férőhely, 0 foglalt -> az első foglalás FULL-ra állítja
        testTimeSlot.setMaxAttendees(1);
        testTimeSlot.setCurrentAttendees(0);
        when(availableTimeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(appointmentRepository.existsByUserAndAppointmentDateBetweenAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(availableTimeSlotRepository.save(any(AvailableTimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        appointmentService.bookAppointment(1L, "testuser", null);

        // Assert – a slot FULL státuszra vált
        verify(availableTimeSlotRepository).save(argThat(slot ->
                slot.getStatus() == TimeSlotStatus.FULL
        ));
    }

    // =========================================================
    // 8. cancelAppointment – sikeres lemondás
    // =========================================================

    @Test
    @DisplayName("Sikeres lemondás: a saját jövőbeli foglalás lemondható")
    void cancelAppointment_Success_WhenOwnerCancels() {
        // Arrange – 48 órával ezutáni foglalás
        Appointment appointment = new Appointment("Vizsgálat", null,
                LocalDateTime.now().plusDays(2), 30, testUser, testAppointmentType);
        appointment.setId(10L);
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(appointmentRepository.save(any())).thenReturn(appointment);
        when(availableTimeSlotRepository.findByAppointmentTypeAndStartTime(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        assertDoesNotThrow(() -> appointmentService.cancelAppointment(10L));

        // Assert – CANCELLED státusszal mentve
        verify(appointmentRepository).save(argThat(a ->
                a.getStatus() == AppointmentStatus.CANCELLED
        ));
    }

    // =========================================================
    // 9. cancelAppointment – 24 órán belüli lemondás tiltása
    // =========================================================

    @Test
    @DisplayName("Lemondás elutasítása: az időpont 24 órán belül van")
    void cancelAppointment_ThrowsException_WhenWithin24Hours() {
        // Arrange – 2 óra múlva kezdődő foglalás
        Appointment appointment = new Appointment("Vizsgálat", null,
                LocalDateTime.now().plusHours(2), 30, testUser, testAppointmentType);
        appointment.setId(10L);
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> appointmentService.cancelAppointment(10L)
        );
        assertTrue(exception.getMessage().contains("24 órával"));
    }

    // =========================================================
    // 10. approveAppointment – admin jóváhagyás
    // =========================================================

    @Test
    @DisplayName("Admin jóváhagyás: a PENDING foglalás CONFIRMED státuszra vált")
    void approveAppointment_SetsStatusToConfirmed() {
        // Arrange
        Appointment appointment = new Appointment("Vizsgálat", null,
                LocalDateTime.now().plusDays(1), 30, testUser, testAppointmentType);
        appointment.setId(5L);
        appointment.setStatus(AppointmentStatus.PENDING);

        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        appointmentService.approveAppointment(5L);

        // Assert
        verify(appointmentRepository).save(argThat(a ->
                a.getStatus() == AppointmentStatus.CONFIRMED
        ));
    }
}
