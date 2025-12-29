package com.rookies4.finalProject.config;

import com.rookies4.finalProject.dto.kafka.PriceAlertEventDTO;
import com.rookies4.finalProject.dto.kafka.SubscriptionEventDTO;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer 설정
 * 
 * Producer: price-alert-events, subscription-events 토픽으로 이벤트 발행
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ==================== Producer Configuration ====================

    /**
     * 공통 Producer 설정
     */
    private Map<String, Object> producerConfigs() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1"); // 리더 파티션 확인만 (성능 최적화)
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10); // 10ms 배칭
        return config;
    }

    /**
     * PriceAlertEventDTO Producer
     * price-alert-events 토픽으로 목표가 알림 이벤트를 발행합니다.
     */
    @Bean
    public ProducerFactory<String, PriceAlertEventDTO> priceAlertEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, PriceAlertEventDTO> priceAlertEventKafkaTemplate() {
        return new KafkaTemplate<>(priceAlertEventProducerFactory());
    }

    /**
     * SubscriptionEventDTO Producer
     * subscription-events 토픽으로 구독/해지 이벤트를 발행합니다.
     */
    @Bean
    public ProducerFactory<String, SubscriptionEventDTO> subscriptionEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, SubscriptionEventDTO> subscriptionEventKafkaTemplate() {
        return new KafkaTemplate<>(subscriptionEventProducerFactory());
    }
}
