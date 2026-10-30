package com.hms.auth;

import com.hms.config.AppProperties;
import com.hms.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class PasswordMailService {
    private final JavaMailSender sender;
    private final AppProperties properties;
    public void sendReset(User user, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from()); message.setTo(user.getEmail()); message.setSubject("Reset your HMS password");
        message.setText("Hello " + user.getFirstName() + ",\n\nUse this link to reset your password within 30 minutes:\n" +
            properties.mail().resetUrl() + "?token=" + rawToken + "\n\nIf you did not request this, you can ignore this email.");
        sender.send(message);
    }
}
