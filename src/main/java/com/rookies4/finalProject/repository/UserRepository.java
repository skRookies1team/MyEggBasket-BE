package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // 추가
import org.springframework.data.repository.query.Param; // 추가
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 1. 이메일로 찾기 (토큰 포함)
    @EntityGraph(attributePaths = "kisAuthToken")
    @Query("SELECT u FROM User u WHERE u.email = :email") // JPQL 명시
    Optional<User> findWithTokenByEmail(@Param("email") String email);

    // 2. ID(PK)로 찾기
    @EntityGraph(attributePaths = "kisAuthToken")
    @Query("SELECT u FROM User u WHERE u.id = :userId") // User 엔티티의 필드명이 'userId'인지 확인 필수!
    Optional<User> findWithTokenByUserId(@Param("userId") Long userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // 이 외에도 JpaRepository를 상속받아 save(), findById(), findAll(), delete() 등의
    // 기본 메서드를 바로 사용할 수 있습니다.
}