package com.circleguard.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias extendidas para QrValidationService.
 * Cubren casos de borde no contemplados en QrValidationServiceTest existente.
 */
class QrValidationServiceExtendedTest {

    private QrValidationService service;
    private ValueOperations<String, String> valueOps;
    private final String secret = "my-super-secret-test-key-32-chars-long";

    @BeforeEach
    void setUp() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new QrValidationService(redisTemplate);
        ReflectionTestUtils.setField(service, "qrSecret", secret);
    }

    // UT-GW-01: Estado POTENTIAL en Redis debe denegar acceso
    @Test
    void shouldDenyAccessForPotentialStatus() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("POTENTIAL");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health Risk Detected", result.message());
    }

    // UT-GW-02: Sin entrada en Redis (null) debe permitir acceso (usuario sin riesgo conocido)
    @Test
    void shouldAllowAccessWhenRedisStatusIsNull() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn(null);

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    // UT-GW-03: Token vacío debe retornar resultado inválido
    @Test
    void shouldReturnInvalidForEmptyToken() {
        QrValidationService.ValidationResult result = service.validateToken("");

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    // UT-GW-04: Token con firma incorrecta debe ser rechazado
    @Test
    void shouldRejectTokenSignedWithWrongKey() {
        String anonymousId = UUID.randomUUID().toString();
        String wrongSecret = "another-secret-key-completely-wrong-32c";
        Key wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(wrongKey, SignatureAlgorithm.HS256)
                .compact();

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    // UT-GW-05: Estado RECOVERED debe permitir acceso (no es CONTAGIED ni POTENTIAL)
    @Test
    void shouldAllowAccessForRecoveredStatus() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("RECOVERED");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }
}
