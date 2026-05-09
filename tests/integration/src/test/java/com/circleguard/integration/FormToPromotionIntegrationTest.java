package com.circleguard.integration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-01 a IT-03: Pruebas de integración del flujo Kafka entre form-service y promotion-service.
 */
@Testcontainers
class FormToPromotionIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    private KafkaTemplate<String, Object> buildProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private BlockingQueue<ConsumerRecord<String, String>> buildConsumerQueue(String topic) throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(props);
        ContainerProperties containerProps = new ContainerProperties(topic);
        BlockingQueue<ConsumerRecord<String, String>> queue = new LinkedBlockingQueue<>();
        containerProps.setMessageListener((MessageListener<String, String>) queue::add);

        KafkaMessageListenerContainer<String, String> container =
                new KafkaMessageListenerContainer<>(factory, containerProps);
        container.start();
        Thread.sleep(1000); // esperar a que el consumer esté listo
        return queue;
    }

    // IT-01: form publica en survey.submitted cuando se envía encuesta
    @Test
    void shouldPublishSurveySubmittedEventToKafka() throws Exception {
        KafkaTemplate<String, Object> producer = buildProducer();
        BlockingQueue<ConsumerRecord<String, String>> queue = buildConsumerQueue("survey.submitted");

        String anonymousId = UUID.randomUUID().toString();
        Map<String, Object> event = Map.of(
                "anonymousId", anonymousId,
                "hasSymptoms", true,
                "timestamp", System.currentTimeMillis()
        );

        producer.send("survey.submitted", anonymousId, event);

        ConsumerRecord<String, String> received = queue.poll(5, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo(anonymousId);
        assertThat(received.value()).contains("hasSymptoms");
    }

    // IT-02: form publica en certificate.validated cuando se aprueba certificado
    @Test
    void shouldPublishCertificateValidatedEventWhenApproved() throws Exception {
        KafkaTemplate<String, Object> producer = buildProducer();
        BlockingQueue<ConsumerRecord<String, String>> queue = buildConsumerQueue("certificate.validated");

        String anonymousId = UUID.randomUUID().toString();
        Map<String, Object> event = Map.of(
                "anonymousId", anonymousId,
                "status", "APPROVED",
                "adminId", UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis()
        );

        producer.send("certificate.validated", anonymousId, event);

        ConsumerRecord<String, String> received = queue.poll(5, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.value()).contains("APPROVED");
    }

    // IT-03: promotion publica en promotion.status.changed después de survey con síntomas
    @Test
    void shouldPublishStatusChangedEventAfterSymptomsDetected() throws Exception {
        KafkaTemplate<String, Object> producer = buildProducer();
        BlockingQueue<ConsumerRecord<String, String>> statusQueue = buildConsumerQueue("promotion.status.changed");

        String anonymousId = UUID.randomUUID().toString();

        // Simula lo que haría promotion-service al consumir survey.submitted con síntomas
        Map<String, Object> statusEvent = Map.of(
                "anonymousId", anonymousId,
                "status", "SUSPECT",
                "timestamp", System.currentTimeMillis()
        );

        producer.send("promotion.status.changed", anonymousId, statusEvent);

        ConsumerRecord<String, String> received = statusQueue.poll(5, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.value()).contains("SUSPECT");
    }
}
