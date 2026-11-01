package com.hms.appointment;

public interface SmsNotificationService {
    boolean sendSms(String recipientPhoneNumber, String message);
}

