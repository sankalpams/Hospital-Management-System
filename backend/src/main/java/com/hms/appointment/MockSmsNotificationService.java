package com.hms.appointment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MockSmsNotificationService implements SmsNotificationService {

    @Override
    public boolean sendSms(String recipientPhoneNumber, String message) {
        // Log without exposing full sensitive recipient phone (masking middle digits for Sri Lankan PII compliance)
        String maskedPhone = maskPhone(recipientPhoneNumber);
        log.info("[SMS GATEWAY MOCK] Dispatched SMS to {}: {}", maskedPhone, message);
        return true;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 3);
    }
}

