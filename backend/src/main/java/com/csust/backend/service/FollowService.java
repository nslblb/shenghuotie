package com.csust.backend.service;

import com.csust.backend.dto.response.UserVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FollowService {

    void follow(Long followerId, Long followingId);

    void unfollow(Long followerId, Long followingId);

    boolean isFollowing(Long followerId, Long followingId);

    Page<UserVO> getFollowers(Long userId, Pageable pageable);

    Page<UserVO> getFollowings(Long userId, Pageable pageable);

    int getFollowerCount(Long userId);

    int getFollowingCount(Long userId);
}
