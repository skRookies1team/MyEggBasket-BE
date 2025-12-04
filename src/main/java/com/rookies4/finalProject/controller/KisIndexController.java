package com.rookies4.finalProject.controller;


import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisForeignIndexDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisForeignIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/kis/index")
@RequiredArgsConstructor
public class KisIndexController {

    private final KisForeignIndexService kisForeignIndexService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<KisForeignIndexDTO.KisForeignIndexResponse> showForeignIndex(
            @RequestParam KisForeignIndexDTO.RequestQueryParam indexCode){

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        // 사용자 조회
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "로그인한 사용자를 찾을 수 없습니다."));
        return ResponseEntity.ok(kisForeignIndexService.showForeignIndex(user, indexCode));
    }
}
