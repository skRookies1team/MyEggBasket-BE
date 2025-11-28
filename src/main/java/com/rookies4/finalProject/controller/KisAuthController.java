package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes an endpoint that issues KIS Open API access tokens on demand.
 */
@RestController
@RequestMapping("/api/app/kis")
@RequiredArgsConstructor
public class KisAuthController {

	private final KisAuthService kisAuthService;
	private final UserRepository userRepository;

	/**
	 * KIS API 인증 토큰을 발급합니다.
	 * 현재 로그인한 사용자의 API 키를 사용하여 토큰을 발급합니다.
	 * 
	 * @param useVirtualServer 모의투자 서버 사용 여부 (기본값: false)
	 * @return KIS 토큰 응답
	 */
	@PostMapping("/token")
	public ResponseEntity<KisAuthTokenDTO.KisTokenResponse> issueToken(
			@RequestParam(name = "virtual", defaultValue = "false") boolean useVirtualServer) {
		
		// 현재 로그인한 사용자 확인
		Long currentUserId = SecurityUtil.getCurrentUserId();
		if (currentUserId == null) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
		}

		// 사용자 조회
		User user = userRepository.findById(currentUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, 
						"로그인한 사용자를 찾을 수 없습니다."));

		return ResponseEntity.ok(kisAuthService.issueToken(useVirtualServer, user));
	}
}


