package com.csust.backend.service;

import com.csust.backend.dto.response.UserDetailVO;
import com.csust.backend.dto.response.UserVO;
import com.csust.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminUserService {

    Page<UserVO> getUsers(String keyword, String role, String status, String startTime, String endTime, Pageable pageable);

    UserDetailVO getUserDetail(Long id);

    void updateUserStatus(Long id, String status, String banReason);

    void resetUserPassword(Long id);

    void updateUserRole(Long id, String role);

    void batchUpdateUserStatus(List<Long> ids, String status, String banReason);
}
