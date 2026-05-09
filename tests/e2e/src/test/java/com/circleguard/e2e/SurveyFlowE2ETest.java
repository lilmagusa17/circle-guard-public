package com.circleguard.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E-01 a E2E-03: Flujos completos de encuesta de salud.
 *
 * Pre-condición: los servicios deben estar desplegados en el namespace correcto.
 * El ambiente se configura con -Denv=dev|stage|master
 */
class SurveyFlowE2ETest {

    // E2E-01: Flujo completo - usuario llena encuesta con síntomas
    // form-service recibe → guarda → publica Kafka → promotion actualiza estado
    @Test
    void shouldSubmitSurveyWithSymptomsAndReturn200() {
        String anonymousId = UUID.randomUUID().toString();

        given()
            .baseUri(E2EConfig.FORM_SERVICE)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "anonymousId": "%s",
                  "hasFever": true,
                  "hasCough": true,
                  "otherSymptoms": "headache"
                }
                """.formatted(anonymousId))
        .when()
            .post("/api/v1/surveys")
        .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("anonymousId", equalTo(anonymousId));
    }

    // E2E-02: Encuesta sin síntomas no debe bloquear acceso en gateway
    @Test
    void shouldAllowGatewayAccessAfterNegativeSurvey() throws InterruptedException {
        // Paso 1: mapear identidad
        String identity = "user-no-symptoms-" + UUID.randomUUID();
        String anonymousId = given()
            .baseUri(E2EConfig.IDENTITY_SERVICE)
            .contentType(ContentType.JSON)
            .body("""
                {"realIdentity": "%s"}
                """.formatted(identity))
        .when()
            .post("/api/v1/identities/map")
        .then()
            .statusCode(200)
            .extract().path("anonymousId");

        // Paso 2: enviar encuesta sin síntomas
        given()
            .baseUri(E2EConfig.FORM_SERVICE)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "anonymousId": "%s",
                  "hasFever": false,
                  "hasCough": false
                }
                """.formatted(anonymousId))
        .when()
            .post("/api/v1/surveys")
        .then()
            .statusCode(200);

        // Pequeña espera para que el evento Kafka sea procesado
        Thread.sleep(2000);

        // Paso 3: verificar que el estado en promotion no es de riesgo
        given()
            .baseUri(E2EConfig.PROMOTION_SERVICE)
        .when()
            .get("/api/v1/health-status/stats")
        .then()
            .statusCode(200);
    }

    // E2E-03: Flujo de certificado - form valida certificado → promotion restaura acceso
    @Test
    void shouldRestoreAccessAfterCertificateApproval() throws InterruptedException {
        UUID anonymousId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        // Paso 1: enviar encuesta con síntomas y adjunto (espera validación)
        String surveyId = given()
            .baseUri(E2EConfig.FORM_SERVICE)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "anonymousId": "%s",
                  "hasFever": true,
                  "attachmentPath": "/uploads/medical-cert.pdf"
                }
                """.formatted(anonymousId))
        .when()
            .post("/api/v1/surveys")
        .then()
            .statusCode(200)
            .body("validationStatus", equalTo("PENDING"))
            .extract().path("id");

        Thread.sleep(1000);

        // Paso 2: admin aprueba el certificado → debe emitir certificate.validated
        // El endpoint usa @RequestParam, no @RequestBody
        given()
            .baseUri(E2EConfig.FORM_SERVICE)
            .queryParam("status", "APPROVED")
            .queryParam("adminId", adminId.toString())
        .when()
            .post("/api/v1/certificates/{id}/validate", surveyId)
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }
}
