package com.circleguard.file.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para FileStorageService.
 */
class FileStorageServiceTest {

    private FileStorageService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "root", tempDir);
    }

    // UT-FL-01: Archivo válido debe guardarse y retornar un nombre único
    @Test
    void shouldSaveFileAndReturnUniqueName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "PDF content".getBytes()
        );

        String savedName = service.saveFile(file);

        assertNotNull(savedName);
        assertTrue(savedName.endsWith("test.pdf"));
        assertTrue(Files.exists(tempDir.resolve(savedName)));
    }

    // UT-FL-02: Dos archivos con el mismo nombre original deben tener nombres distintos
    @Test
    void shouldGenerateUniqueNamesForSameOriginalFilename() {
        MockMultipartFile file1 = new MockMultipartFile("file", "doc.pdf", "application/pdf", "A".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "doc.pdf", "application/pdf", "B".getBytes());

        String name1 = service.saveFile(file1);
        String name2 = service.saveFile(file2);

        assertNotEquals(name1, name2);
    }

    // UT-FL-03: Archivo con contenido vacío debe guardarse sin error
    @Test
    void shouldSaveEmptyFileWithoutError() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertDoesNotThrow(() -> service.saveFile(file));
    }

    // UT-FL-04: El contenido del archivo guardado debe ser idéntico al original
    @Test
    void shouldPreserveFileContent() throws IOException {
        byte[] content = "Hello CircleGuard!".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", content);

        String savedName = service.saveFile(file);
        byte[] saved = Files.readAllBytes(tempDir.resolve(savedName));

        assertArrayEquals(content, saved);
    }

    // UT-FL-05: Archivo sin nombre original debe manejarse sin lanzar NullPointerException
    @Test
    void shouldHandleFileWithNullOriginalFilename() {
        MockMultipartFile file = new MockMultipartFile("file", null, "application/octet-stream", "data".getBytes());

        // No debe lanzar NPE; si el servicio lo maneja con "null" como suffix es aceptable
        assertDoesNotThrow(() -> service.saveFile(file));
    }
}
