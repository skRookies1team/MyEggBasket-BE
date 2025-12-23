package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.VolumeRankResponseDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisVolumeRankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/kis/rank")
@RequiredArgsConstructor
@Tag(name = "KIS 거래량 순위", description = "한국투자증권 거래량 순위 조회 API")
public class KisVolumeRankController {

    private final KisVolumeRankService kisVolumeRankService;
    private final UserRepository userRepository;

    /**
     * 거래량 순위 TOP 10 조회
     * @return 거래량 순위 응답
     */
    @GetMapping("/volume-top10")
    @Operation(summary = "거래량 순위 TOP 10 조회", description = "당일 거래량 기준 상위 10개 종목을 조회합니다. (로그인 필요)")
    public ResponseEntity<List<VolumeRankResponseDTO>> getVolumeRankTop10() {

        // 현재 로그인한 사용자 확인
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        // 사용자 조회
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "로그인한 사용자를 찾을 수 없습니다."
                ));

        // 거래량 순위 조회
        List<VolumeRankResponseDTO> response =
                kisVolumeRankService.getVolumeRank(user);

        return ResponseEntity.ok(response);
    }
}