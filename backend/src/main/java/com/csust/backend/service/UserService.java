package com.csust.backend.service;

import com.csust.backend.dto.request.LoginRequest;
import com.csust.backend.dto.request.RegisterRequest;
import com.csust.backend.dto.request.UpdateUserRequest;
import com.csust.backend.dto.response.LoginVO;
import com.csust.backend.dto.response.UserProfileVO;
import com.csust.backend.dto.response.UserVO;
import org.springframework.data.domain.Page;

public interface UserService {

    LoginVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    UserVO getUserById(Long id);

    UserVO updateUserInfo(Long userId, UpdateUserRequest request);

    UserProfileVO getUserProfile(Long userId, Long currentUserId);

    org.springframework.data.domain.Page<UserVO> searchUsers(String keyword, org.springframework.data.domain.Pageable pageable);

    void changePassword(Long userId, String oldPassword, String newPassword);

    void sendResetCode(String phone);

    void resetPassword(String phone, String code, String newPassword);

    void sendDeleteCode(Long userId);

    void deleteAccount(Long userId, String code);
}
