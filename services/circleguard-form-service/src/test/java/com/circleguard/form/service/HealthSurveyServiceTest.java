package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para HealthSurveyService.
 */
class HealthSurveyServiceTest {

    private HealthSurveyService service;
    private HealthSurveyRepository repository;
    private QuestionnaireService questionnaireService;
    private SymptomMapper symptomMapper;
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        repository = mock(HealthSurveyRepository.class);
        questionnaireService = mock(QuestionnaireService.class);
        symptomMapper = mock(SymptomMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new HealthSurveyService(repository, questionnaireService, symptomMapper, kafkaTemplate);
    }

    // UT-FS-01: Sin cuestionario activo, hasSymptoms debe ser false
    @Test
    void shouldSetHasSymptomsToFalseWhenNoActiveQuestionnaire() {
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(UUID.randomUUID())
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = service.submitSurvey(survey);

        // hasSymptoms=false → hasFever y hasCough deben ser false
        assertFalse(result.getHasFever());
        assertFalse(result.getHasCough());
    }

    // UT-FS-02: Encuesta con adjunto debe tener ValidationStatus.PENDING
    @Test
    void shouldSetPendingValidationWhenAttachmentPresent() {
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(UUID.randomUUID())
                .attachmentPath("/uploads/certificate.pdf")
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = service.submitSurvey(survey);

        assertEquals(ValidationStatus.PENDING, result.getValidationStatus());
    }

    // UT-FS-03: submitSurvey debe publicar evento en Kafka topic survey.submitted
    @Test
    void shouldPublishKafkaEventOnSurveySubmit() {
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submitSurvey(survey);

        verify(kafkaTemplate, times(1)).send(eq("survey.submitted"), eq(anonymousId.toString()), any());
    }

    // UT-FS-04: validateSurvey con APPROVED debe publicar en certificate.validated
    @Test
    void shouldPublishCertificateValidatedEventOnApproval() {
        UUID surveyId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(anonymousId)
                .validationStatus(ValidationStatus.PENDING)
                .build();

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        verify(kafkaTemplate, times(1)).send(eq("certificate.validated"), eq(anonymousId.toString()), any());
    }

    // UT-FS-05: validateSurvey con REJECTED NO debe publicar en certificate.validated
    @Test
    void shouldNotPublishEventOnRejection() {
        UUID surveyId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(anonymousId)
                .validationStatus(ValidationStatus.PENDING)
                .build();

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.validateSurvey(surveyId, ValidationStatus.REJECTED, adminId);

        verify(kafkaTemplate, never()).send(eq("certificate.validated"), any(), any());
    }
}
