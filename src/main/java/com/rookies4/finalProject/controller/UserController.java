package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.TransactionDTO;
import com.rookies4.finalProject.dto.UserDTO;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.TransactionService;
import com.rookies4.finalProject.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TransactionService transactionService;

    // 1. 현재 로그인한 사용자 정보 조회
    @GetMapping("/me")
    public ResponseEntity<UserDTO.UserResponse> getCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.readUser(userId));
    }

    // 2. 회원 조회 (ID로 조회)
    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO.UserResponse> readUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.readUser(userId));
    }

    // 4. 회원 정보 수정
    @PutMapping("/{userId}")
    public ResponseEntity<UserDTO.UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserDTO.UpdateRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    // 5. 회원 삭제
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}