package com.rookies4.finalProject.config;

import com.rookies4.finalProject.dto.kafka.PriceAlertEventDTO;
import com.rookies4.finalProject.dto.kafka.StockTickDTO;
import com.rookies4.finalProject.dto.kafka.SubscriptionEventDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer/Producer 설정
 * 
 * Consumer: stock-ticks 토픽에서 실시간 체결 데이터 수신
 * Producer: price-alert-events, subscription-events 토픽으로 이벤트 발행
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ==================== Consumer Configuration ====================

    /**
     * StockTickDTO Consumer 설정
     * stock-ticks 토픽을 구독합니다.
     */
    @Bean
    public ConsumerFactory<String, StockTickDTO> stockTickConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, StockTickDTO.class.getName());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(StockTickDTO.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockTickDTO> stockTickKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StockTickDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stockTickConsumerFactory());
        return factory;
    }

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
