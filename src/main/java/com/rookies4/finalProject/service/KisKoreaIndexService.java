package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.repository.KisAuthRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class KisKoreaIndexService {

    private static final Logger log = LoggerFactory.getLogger(KisKoreaIndexService.class);

    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;
    private final ObjectMapper objectMapper;

    public
}
