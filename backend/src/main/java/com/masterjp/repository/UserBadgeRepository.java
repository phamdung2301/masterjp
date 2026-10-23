package com.masterjp.repository;

import com.masterjp.domain.UserBadge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserBadgeRepository extends MongoRepository<UserBadge, String> {

    List<UserBadge> findByUserId(String userId);

    boolean existsByUserIdAndBadgeCode(String userId, String badgeCode);

    long countByUserId(String userId);
}
