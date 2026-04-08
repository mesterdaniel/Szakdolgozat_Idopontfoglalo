package com.BC.Idopontfoglalo.service;


import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.repository.RoleRepository;
import com.BC.Idopontfoglalo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;

    public void registerNewUser(String username, String email, String rawPassword, String firstName, String lastName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        user.setRoles(Collections.singleton(roleRepository.findByRoleName("ROLE_USER")));
        userRepository.save(user);

        // Send welcome email
        String text = String.format(
            "Kedves %s!\n\n" +
            "Köszönjük, hogy regisztrált az Időpontfoglaló rendszerünkbe!\n" +
            "Mostantól lehetősége van kényelmesen és gyorsan időpontot foglalni a különböző szolgáltatásainkra.\n\n" +
            "A fiókjába a megadott e-mail címmel (%s) vagy felhasználónevével tud bejelentkezni.\n\n" +
            "Üdvözlettel,\nAz Időpontfoglaló csapata",
            firstName != null ? firstName : username,
            email
        );
        emailService.sendSimpleMessage(email, "Sikeres regisztráció - Időpontfoglaló", text);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public void updateUser(User user) {
        userRepository.save(user);
    }

    public void changePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public void adminSetPassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
