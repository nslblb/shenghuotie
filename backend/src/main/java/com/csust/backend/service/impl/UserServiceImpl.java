package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.XssUtil;
import com.csust.backend.dto.request.LoginRequest;
import com.csust.backend.dto.request.RegisterRequest;
import com.csust.backend.dto.request.UpdateUserRequest;
import com.csust.backend.dto.response.LoginVO;
import com.csust.backend.dto.response.UserProfileVO;
import com.csust.backend.dto.response.UserVO;
import com.csust.backend.entity.User;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.security.JwtUtil;
import com.csust.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String CACHE_USER = "shenghuotie:cache:user:";
    private static final String CACHE_USER_PROFILE = "shenghuotie:cache:user:profile:";
    private static final long USER_TTL = 1800;
    private static final long USER_PROFILE_TTL = 600;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FollowService followService;
    private final PostRepository postRepository;
    private final RedisCacheService redisCacheService;
    private final VerificationCodeService verificationCodeService;
    private final LoginProtectionService loginProtectionService;
    private final SensitiveWordService sensitiveWordService;

    @Override
    @Transactional
    public LoginVO register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        if (sensitiveWordService.containsSensitiveWord(request.getNickname())) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }

        User user = new User();
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getPhone(), user.getRole().name());
        return new LoginVO(token, UserVO.fromEntity(user));
    }

    @Override
    public LoginVO login(LoginRequest request) {
        // 检查账号是否被锁定
        loginProtectionService.checkLock(request.getPhone());

        User user = userRepository.findByPhone(request.getPhone()).orElse(null);
        if (user == null) {
            loginProtectionService.recordFail(request.getPhone());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginProtectionService.recordFail(request.getPhone());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (user.getStatus() == com.csust.backend.common.Status.DISABLED) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        if (user.getStatus() == com.csust.backend.common.Status.DELETED) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), "该账号已注销");
        }

        // 登录成功，清除失败计数
        loginProtectionService.clearFail(request.getPhone());

        String token = jwtUtil.generateToken(user.getId(), user.getPhone(), user.getRole().name());
        return new LoginVO(token, UserVO.fromEntity(user));
    }

    @Override
    public UserVO getUserById(Long id) {
        return redisCacheService.getWithMutex(CACHE_USER + id, USER_TTL, () -> {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            return UserVO.fromEntity(user);
        });
    }

    @Override
    @Transactional
    public UserVO updateUserInfo(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.getNickname() != null) {
            String cleanNickname = XssUtil.clean(request.getNickname());
            if (sensitiveWordService.containsSensitiveWord(cleanNickname)) {
                throw new BusinessException(ErrorCode.SENSITIVE_WORD);
            }
            user.setNickname(cleanNickname);
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getBio() != null) {
            String cleanBio = XssUtil.clean(request.getBio());
            if (sensitiveWordService.containsSensitiveWord(cleanBio)) {
                throw new BusinessException(ErrorCode.SENSITIVE_WORD);
            }
            user.setBio(cleanBio);
        }
        if (request.getMessagePrivacy() != null) {
            String privacy = request.getMessagePrivacy();
            if ("EVERYONE".equals(privacy) || "FOLLOWERS_ONLY".equals(privacy) || "NONE".equals(privacy)) {
                user.setMessagePrivacy(privacy);
            }
        }

        user = userRepository.save(user);

        redisCacheService.evict(CACHE_USER + userId);
        redisCacheService.evict(CACHE_USER_PROFILE + userId);

        return UserVO.fromEntity(user);
    }

    @Override
    public UserProfileVO getUserProfile(Long userId, Long currentUserId) {
        return redisCacheService.getWithMutex(CACHE_USER_PROFILE + userId, USER_PROFILE_TTL, () -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            UserProfileVO profile = new UserProfileVO();
            profile.setId(user.getId());
            profile.setPhone(maskPhone(user.getPhone()));
            profile.setNickname(user.getNickname());
            profile.setAvatarUrl(user.getAvatarUrl());
            profile.setBio(user.getBio());
            profile.setMessagePrivacy(user.getMessagePrivacy());
            profile.setCreatedAt(user.getCreatedAt());

            profile.setFollowerCount(followService.getFollowerCount(userId));
            profile.setFollowingCount(followService.getFollowingCount(userId));
            profile.setPostCount(postRepository.countByUserId(userId));

            if (currentUserId != null && !currentUserId.equals(userId)) {
                profile.setFollowed(followService.isFollowing(currentUserId, userId));
            }

            return profile;
        });
    }

    @Override
    public Page<UserVO> searchUsers(String keyword, Pageable pageable) {
        Page<User> userPage = userRepository.findByNicknameContaining(keyword, pageable);
        return userPage.map(UserVO::fromEntity);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED.getCode(), "原密码不正确");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    public void sendResetCode(String phone) {
        if (!userRepository.existsByPhone(phone)) {
            throw new BusinessException(ErrorCode.PHONE_NOT_REGISTERED);
        }
        verificationCodeService.sendCode(phone);
    }

    @Override
    public void resetPassword(String phone, String code, String newPassword) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONE_NOT_REGISTERED));

        if (!verificationCodeService.verifyCode(phone, code)) {
            throw new BusinessException(ErrorCode.CODE_INVALID);
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "密码长度不能少于6位");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        redisCacheService.evict(CACHE_USER + user.getId());
        redisCacheService.evict(CACHE_USER_PROFILE + user.getId());
    }

    @Override
    public void sendDeleteCode(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        verificationCodeService.sendCode(user.getPhone());
    }

    @Override
    @Transactional
    public void deleteAccount(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 验证短信验证码
        if (!verificationCodeService.verifyCode(user.getPhone(), code)) {
            throw new BusinessException(ErrorCode.CODE_INVALID);
        }

        // 标记为已注销
        user.setNickname("已注销用户_" + userId);
        user.setAvatarUrl(null);
        user.setBio(null);
        user.setPhone("deleted_" + userId);
        user.setPassword("");
        user.setStatus(com.csust.backend.common.Status.DELETED);
        userRepository.save(user);

        redisCacheService.evict(CACHE_USER + userId);
        redisCacheService.evict(CACHE_USER_PROFILE + userId);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
