package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para IdentityVaultService.
 */
class IdentityVaultServiceTest {

    private IdentityVaultService service;
    private IdentityMappingRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityMappingRepository.class);
        service = new IdentityVaultService(repository);
        ReflectionTestUtils.setField(service, "hashSalt", "test-salt-value");
    }

    // UT-ID-01: La misma identidad real debe retornar siempre el mismo anonymousId
    @Test
    void shouldReturnSameAnonymousIdForSameIdentity() {
        UUID expectedId = UUID.randomUUID();
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(expectedId)
                .build();

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(mapping));

        UUID result = service.getOrCreateAnonymousId("user@example.com");

        assertEquals(expectedId, result);
        verify(repository, never()).save(any());
    }

    // UT-ID-02: Identidad nueva debe crear un mapping y persistirlo
    @Test
    void shouldCreateNewMappingForNewIdentity() {
        UUID newId = UUID.randomUUID();
        IdentityMapping savedMapping = IdentityMapping.builder()
                .anonymousId(newId)
                .build();

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(savedMapping);

        UUID result = service.getOrCreateAnonymousId("newuser@example.com");

        assertEquals(newId, result);
        verify(repository, times(1)).save(any());
    }

    // UT-ID-03: resolveRealIdentity con ID desconocido debe lanzar 404
    @Test
    void shouldThrow404WhenAnonymousIdNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolveRealIdentity(unknownId));

        assertEquals(404, ex.getStatusCode().value());
    }

    // UT-ID-04: resolveRealIdentity debe retornar la identidad correcta
    @Test
    void shouldResolveRealIdentityFromAnonymousId() {
        UUID anonymousId = UUID.randomUUID();
        String realIdentity = "john.doe@company.com";
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(anonymousId)
                .realIdentity(realIdentity)
                .build();

        when(repository.findById(anonymousId)).thenReturn(Optional.of(mapping));

        String result = service.resolveRealIdentity(anonymousId);

        assertEquals(realIdentity, result);
    }

    // UT-ID-05: Dos identidades distintas deben generar anonymousIds distintos
    @Test
    void shouldGenerateDifferentIdsForDifferentIdentities() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any()))
                .thenReturn(IdentityMapping.builder().anonymousId(id1).build())
                .thenReturn(IdentityMapping.builder().anonymousId(id2).build());

        UUID result1 = service.getOrCreateAnonymousId("user1@company.com");
        UUID result2 = service.getOrCreateAnonymousId("user2@company.com");

        assertNotEquals(result1, result2);
    }
}
