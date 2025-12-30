package com.rookies4.finalProject.config;

import com.rookies4.finalProject.dto.kafka.PriceAlertEventDTO;
import com.rookies4.finalProject.dto.kafka.StockTickDTO; // [추가] StockTickDTO 임포트
import com.rookies4.finalProject.dto.kafka.SubscriptionEventDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig; // [추가]
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer; // [추가]
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory; // [추가]
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer; // [추가]
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer & Consumer 설정
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:stock-group}") // 기본값 stock-group
    private String defaultGroupId;

    // ==================== Producer Configuration ====================

    private Map<String, Object> producerConfigs() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        return config;
    }

    @Bean
    public ProducerFactory<String, PriceAlertEventDTO> priceAlertEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, PriceAlertEventDTO> priceAlertEventKafkaTemplate() {
        return new KafkaTemplate<>(priceAlertEventProducerFactory());
    }

    @Bean
    public ProducerFactory<String, SubscriptionEventDTO> subscriptionEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, SubscriptionEventDTO> subscriptionEventKafkaTemplate() {
        return new KafkaTemplate<>(subscriptionEventProducerFactory());
    }

    // ==================== Consumer Configuration (추가됨) ====================

    /**
     * StockTickDTO를 수신하기 위한 Consumer Factory
     */
    @Bean
    public ConsumerFactory<String, Map<String, Object>> stockTickConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, defaultGroupId);

        // Value Deserializer를 Map.class로 설정
        JsonDeserializer<Map<String, Object>> deserializer = new JsonDeserializer<>(Map.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                deserializer
        );
    }

    // [수정] ContainerFactory
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> stockTickKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stockTickConsumerFactory());
        return factory;
    }
}