package com.BC.Idopontfoglalo.controller;

import com.BC.Idopontfoglalo.entity.User;
import com.BC.Idopontfoglalo.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PasswordResetController {

    @Autowired
    private PasswordResetService passwordResetService;

    // ---- Elfelejtett jelszó oldal ----

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(@RequestParam(required = false) String sent, Model model) {
        if ("true".equals(sent)) {
            model.addAttribute("sent", true);
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email) {
        passwordResetService.initiatePasswordReset(email);
        // Mindig ugyanoda irányítunk (biztonsági okokból)
        return "redirect:/forgot-password?sent=true";
    }

    // ---- Jelszó visszaállítás oldal ----

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        User user = passwordResetService.validateToken(token);
        if (user == null) {
            model.addAttribute("invalidToken", true);
            return "reset-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
                                       @RequestParam String newPassword,
                                       @RequestParam String confirmPassword,
                                       Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("passwordMismatch", true);
            return "reset-password";
        }
        if (newPassword.length() < 6) {
            model.addAttribute("token", token);
            model.addAttribute("passwordTooShort", true);
            return "reset-password";
        }
        boolean success = passwordResetService.resetPassword(token, newPassword);
        if (!success) {
            model.addAttribute("invalidToken", true);
            return "reset-password";
        }
        return "redirect:/login?passwordReset=true";
    }
}
