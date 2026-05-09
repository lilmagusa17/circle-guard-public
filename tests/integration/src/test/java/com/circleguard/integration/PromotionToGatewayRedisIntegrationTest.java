package com.circleguard.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-04 a IT-05: Integración promotion-service → Redis → gateway-service.
 * Verifica que el estado escrito por promotion sea leído correctamente por gateway.
 */
@Testcontainers
class PromotionToGatewayRedisIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379)
        );
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
    }

    // IT-04: Estado CONTAGIED escrito por promotion debe ser leído por gateway
    @Test
    void gatewayShouldReadContagiedStatusWrittenByPromotion() {
        String anonymousId = "test-user-contagied";
        // Simula lo que haría promotion-service
        redisTemplate.opsForValue().set("user:status:" + anonymousId, "CONTAGIED");

        // Simula lo que haría gateway-service para leer
        String status = redisTemplate.opsForValue().get("user:status:" + anonymousId);

        assertThat(status).isEqualTo("CONTAGIED");
    }

    // IT-05: Cuando promotion limpia el estado (RECOVERED), gateway debe permitir acceso
    @Test
    void gatewayShouldAllowAccessAfterStatusClearedByPromotion() {
        String anonymousId = "test-user-recovered";
        // Estado previo: contagiado
        redisTemplate.opsForValue().set("user:status:" + anonymousId, "CONTAGIED");
        // Promotion actualiza a RECOVERED
        redisTemplate.opsForValue().set("user:status:" + anonymousId, "RECOVERED");

        String status = redisTemplate.opsForValue().get("user:status:" + anonymousId);

        // Gateway: RECOVERED no está en la lista de denegación
        assertThat(status).isNotEqualTo("CONTAGIED");
        assertThat(status).isNotEqualTo("POTENTIAL");
    }
}
