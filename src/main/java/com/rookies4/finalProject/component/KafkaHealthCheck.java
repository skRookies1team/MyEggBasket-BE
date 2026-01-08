package com.rookies4.finalProject.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Kafka 토픽 초기화 및 헬스 체크 컴포넌트
 * 
 * 애플리케이션 시작 시 필요한 Kafka 토픽이 존재하는지 확인하고,
 * 없으면 생성합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaHealthCheck {

    private final KafkaAdmin kafkaAdmin;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * 애플리케이션 시작 시 Kafka 연결 상태 확인
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkKafkaConnection() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            // 기존 토픽 목록 조회
            Set<String> existingTopics = adminClient.listTopics().names().get();
            
            log.info("========================================");
            log.info("Kafka Health Check");
            log.info("Bootstrap Servers: {}", bootstrapServers);
            log.info("Connection Status: OK");
            log.info("Existing Topics: {}", existingTopics);
            
            // 필요한 토픽 확인 및 생성
            checkAndCreateRequiredTopics(adminClient, existingTopics);
            
            log.info("========================================");
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("========================================");
            log.error("Kafka Health Check FAILED");
            log.error("Bootstrap Servers: {}", bootstrapServers);
            log.error("Error: {}", e.getMessage());
            log.error("Please check if Kafka is running at {}", bootstrapServers);
            log.error("========================================");
            
            // 개발 환경에서는 경고만 하고 계속 진행
            // 프로덕션 환경에서는 throw new RuntimeException으로 변경 고려
            log.warn("Application will continue without Kafka connection");
        }
    }

    /**
     * 필요한 토픽이 존재하는지 확인하고, 없으면 생성
     */
    private void checkAndCreateRequiredTopics(AdminClient adminClient, Set<String> existingTopics) {
        String[] requiredTopics = {
                "stock-ticks",
                "subscription-events"
        };
        
        log.info("Checking required Kafka topics...");
        
        for (String topicName : requiredTopics) {
            if (existingTopics.contains(topicName)) {
                log.info("✓ Topic '{}' exists", topicName);
            } else {
                log.warn("✗ Topic '{}' does NOT exist - creating...", topicName);
                try {
                    // 토픽 생성: partitions=3, replication-factor=1
                    NewTopic newTopic = new NewTopic(topicName, 3, (short) 1);
                    adminClient.createTopics(Arrays.asList(newTopic)).all().get();
                    log.info("✓ Topic '{}' created successfully", topicName);
                } catch (Exception e) {
                    log.error("✗ Failed to create topic '{}': {}", topicName, e.getMessage());
                }
            }
        }
    }
}
