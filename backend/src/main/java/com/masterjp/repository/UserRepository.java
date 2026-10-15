package com.masterjp.repository;

import com.masterjp.domain.Plan;
import com.masterjp.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** User PREMIUM da het han goi (job moi gio + check luc login). */
    List<User> findByPlanAndPlanExpiresAtLessThan(Plan plan, Date now);
}
