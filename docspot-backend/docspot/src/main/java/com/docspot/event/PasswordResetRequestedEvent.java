package com.docspot.event;

public record PasswordResetRequestedEvent(String email, String name, String otp) {}