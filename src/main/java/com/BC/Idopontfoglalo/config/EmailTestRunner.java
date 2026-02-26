package com.BC.Idopontfoglalo.config;

import com.BC.Idopontfoglalo.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class EmailTestRunner implements CommandLineRunner {

    @Autowired
    private EmailService emailService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- TESZT EMAIL KULDES INDITASA ---");
     /*   emailService.sendSimpleMessage(
                "teszt@cimzett.hu",
                "Rendszer Indulás Teszt",
                "Ez egy automatikus teszt üzenet az alkalmazás indulásakor. Ha ezt látod a Mailtrap-en, a rendszer működik!"
        );*/
    }
}