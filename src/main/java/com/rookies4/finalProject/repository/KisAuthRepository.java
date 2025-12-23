package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KisAuthRepository extends JpaRepository<KisAuthToken,Long> {
    Optional<KisAuthToken> findByUser(User user);
}
