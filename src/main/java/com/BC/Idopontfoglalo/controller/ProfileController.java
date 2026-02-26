package com.BC.Idopontfoglalo.controller;

import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    @Autowired
    private UserService userService;

    @GetMapping("/profile")
    public String showProfile(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        
        if (user == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String email,
                                @RequestParam(required = false) String firstName,
                                @RequestParam(required = false) String lastName,
                                @RequestParam(required = false) String currentPassword,
                                @RequestParam(required = false) String newPassword,
                                @RequestParam(required = false) String confirmPassword,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username);
        
        if (user == null) {
            return "redirect:/login";
        }

        try {
            // Update basic info
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            
            // Handle password change if requested
            if (newPassword != null && !newPassword.isEmpty()) {
                if (currentPassword == null || currentPassword.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "A jelszó módosításához adja meg a jelenlegi jelszavát!");
                    return "redirect:/profile";
                }

                if (!userService.checkPassword(user, currentPassword)) {
                    redirectAttributes.addFlashAttribute("error", "A jelenlegi jelszó helytelen!");
                    return "redirect:/profile";
                }

                if (!newPassword.equals(confirmPassword)) {
                    redirectAttributes.addFlashAttribute("error", "Az új jelszavak nem egyeznek!");
                    return "redirect:/profile";
                }

                userService.changePassword(user, newPassword);
                redirectAttributes.addFlashAttribute("success", "Profil és jelszó sikeresen frissítve!");
            } else {
                userService.updateUser(user);
                redirectAttributes.addFlashAttribute("success", "Profil sikeresen frissítve!");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Hiba történt a mentés során: " + e.getMessage());
        }

        return "redirect:/profile";
    }
}
