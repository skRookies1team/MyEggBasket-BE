package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * email(이메일)을 통해 User 엔티티를 조회합니다.
     * email 필드에 unique 제약 조건이 있으므로 Optional<User>로 반환하여
     * 사용자가 존재하지 않을 경우를 안전하게 처리합니다.
     *
     * @param email 조회할 사용자 이메일
     * @return 해당 이메일을 가진 User 엔티티 (존재하지 않을 경우 Optional.empty())
     */
    Optional<User> findByEmail(String email);

    /**
     * email을 통해 사용자가 존재하는지 여부를 확인합니다.
     *
     * @param email 확인할 사용자 이메일
     * @return 사용자 존재 여부 (true/false)
     */
    boolean existsByEmail(String email);

    // 이 외에도 JpaRepository를 상속받아 save(), findById(), findAll(), delete() 등의
    // 기본 메서드를 바로 사용할 수 있습니다.
}