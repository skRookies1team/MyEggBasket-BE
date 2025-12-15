package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.KisIndexDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisForeignIndexService;
import com.rookies4.finalProject.service.KisKoreaIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/kis/index")
@RequiredArgsConstructor
public class KisIndexController {

    private final KisForeignIndexService kisForeignIndexService;
    private final KisKoreaIndexService kisKoreaIndexService;

    @GetMapping("/oversea")
    public ResponseEntity<KisIndexDTO.IndexResponse> showForeignIndex(
            @RequestParam String indexCode){

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        return ResponseEntity.ok(kisForeignIndexService.getForeignIndex(indexCode, currentUserId));
    }

    @GetMapping("/domestic")
    public ResponseEntity<KisIndexDTO.IndexResponse> showKoreaIndex(
            @RequestParam String indexCode){

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        return ResponseEntity.ok(kisKoreaIndexService.getKoreaIndex(indexCode, currentUserId));
    }
}