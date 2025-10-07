package com.BC.Idopontfoglalo.service;

import com.BC.Idopontfoglalo.entity.AvailableTimeSlot;
import com.BC.Idopontfoglalo.entity.TimeSlotStatus;
import com.BC.Idopontfoglalo.repository.AvailableTimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AvailableTimeSlotService {

    @Autowired
    private AvailableTimeSlotRepository availableTimeSlotRepository;

    /**
     * TimeSlot lekérése ID alapján
     */
    public AvailableTimeSlot getTimeSlotById(Long id) {
        return availableTimeSlotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nem található időpont slot: " + id));
    }

    /**
     * TimeSlot törlése
     * Csak akkor törölhető, ha nincs hozzá foglalás (currentAttendees == 0)
     */
    @Transactional
    public void deleteTimeSlot(Long timeSlotId) {
        AvailableTimeSlot timeSlot = getTimeSlotById(timeSlotId);

        // Ellenőrizzük, hogy nincs-e foglalás
        if (timeSlot.getCurrentAttendees() > 0) {
            throw new IllegalArgumentException("Nem törölhető olyan időpont, amelyhez már van foglalás!");
        }


        availableTimeSlotRepository.delete(timeSlot);
    }

    /**
     * TimeSlot státusz frissítése
     */
    public void updateTimeSlotStatus(Long timeSlotId, TimeSlotStatus newStatus) {
        AvailableTimeSlot timeSlot = getTimeSlotById(timeSlotId);
        timeSlot.setStatus(newStatus);
        availableTimeSlotRepository.save(timeSlot);
    }
}