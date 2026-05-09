package com.circleguard.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E-04 a E2E-05: Flujos de acceso por QR y registro de identidad.
 */
class GatewayAndIdentityFlowE2ETest {

    // E2E-04: Token inválido en gateway debe ser rechazado con 200 y valid=false
    @Test
    void shouldRejectInvalidQrToken() {
        given()
            .baseUri(E2EConfig.GATEWAY_SERVICE)
            .contentType(ContentType.JSON)
            .body("""
                {"token": "this.is.not.a.valid.jwt.token"}
                """)
        .when()
            .post("/api/v1/gate/validate")
        .then()
            .statusCode(200)
            .body("valid", equalTo(false))
            .body("status", equalTo("RED"));
    }

    // E2E-05: Registro de visitante en identity-service debe retornar anonymousId único
    @Test
    void shouldRegisterVisitorAndReturnAnonymousId() {
        String visitorName = "Visitor-" + UUID.randomUUID();

        String anonymousId1 = given()
            .baseUri(E2EConfig.IDENTITY_SERVICE)
            .contentType(ContentType.JSON)
            .body("""
                {"realIdentity": "%s"}
                """.formatted(visitorName))
        .when()
            .post("/api/v1/identities/visitor")
        .then()
            .statusCode(200)
            .body("anonymousId", notNullValue())
            .extract().path("anonymousId");

        // Segunda llamada con la misma identidad debe devolver el mismo ID
        String anonymousId2 = given()
            .baseUri(E2EConfig.IDENTITY_SERVICE)
            .contentType(ContentType.JSON)
            .body("""
                {"realIdentity": "%s"}
                """.formatted(visitorName))
        .when()
            .post("/api/v1/identities/visitor")
        .then()
            .statusCode(200)
            .extract().path("anonymousId");

        org.junit.jupiter.api.Assertions.assertEquals(anonymousId1, anonymousId2);
    }
}
