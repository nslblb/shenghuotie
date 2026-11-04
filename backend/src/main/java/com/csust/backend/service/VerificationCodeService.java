package com.csust.backend.service;

public interface VerificationCodeService {

    void sendCode(String phone);

    boolean verifyCode(String phone, String code);
}
