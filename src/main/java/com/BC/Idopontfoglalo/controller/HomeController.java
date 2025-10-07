package com.BC.Idopontfoglalo.controller;

import com.BC.Idopontfoglalo.entity.User;
import jakarta.servlet.http.HttpSession;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller

public class HomeController {
    @GetMapping("/")
    public String home() {

        return "redirect:/login";
    }

}
