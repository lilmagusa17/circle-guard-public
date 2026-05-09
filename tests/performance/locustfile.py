"""
Locust Performance Tests - CircleGuard
Simula carga real en form-service, gateway-service y promotion-service.

Ejecución:
  locust -f locustfile.py --headless -u 50 -r 5 -t 60s \
         --host=http://<service-url> --csv=results/circleguard

Variables de entorno:
  TARGET_SERVICE = form | gateway | promotion  (default: form)
"""

import os
import uuid
import json
from locust import HttpUser, task, between, events
from locust.runners import MasterRunner


TARGET = os.getenv("TARGET_SERVICE", "form")


# ─────────────────────────────────────────────
# form-service load test
# ─────────────────────────────────────────────
class FormServiceUser(HttpUser):
    """
    Simula usuarios enviando encuestas de salud.
    Caso de uso: oleada de encuestas al inicio de jornada.
    """
    wait_time = between(1, 3)
    host = os.getenv("FORM_SERVICE_URL", "http://form-service:8080")

    @task(3)
    def submit_survey_no_symptoms(self):
        """Encuesta típica sin síntomas (80% de los casos)."""
        payload = {
            "anonymousId": str(uuid.uuid4()),
            "hasFever": False,
            "hasCough": False,
            "otherSymptoms": None
        }
        with self.client.post(
            "/api/v1/surveys",
            json=payload,
            catch_response=True,
            name="POST /surveys [no-symptoms]"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(1)
    def submit_survey_with_symptoms(self):
        """Encuesta con síntomas (20% de los casos)."""
        payload = {
            "anonymousId": str(uuid.uuid4()),
            "hasFever": True,
            "hasCough": True,
            "otherSymptoms": "sore throat"
        }
        with self.client.post(
            "/api/v1/surveys",
            json=payload,
            catch_response=True,
            name="POST /surveys [with-symptoms]"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(1)
    def get_active_questionnaire(self):
        """Consulta del cuestionario activo antes de mostrar el form."""
        with self.client.get(
            "/api/v1/questionnaires/active",
            catch_response=True,
            name="GET /questionnaires/active"
        ) as response:
            if response.status_code in (200, 404):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")


# ─────────────────────────────────────────────
# gateway-service load test
# ─────────────────────────────────────────────
class GatewayServiceUser(HttpUser):
    """
    Simula validaciones QR simultáneas en torniquetes de acceso.
    Caso de uso: hora pico de entrada al campus (100s de validaciones/min).
    """
    wait_time = between(0.5, 1.5)
    host = os.getenv("GATEWAY_SERVICE_URL", "http://gateway-service:8080")

    # Token JWT de prueba (firmado con secreto de test)
    VALID_TOKEN = os.getenv(
        "TEST_QR_TOKEN",
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXIifQ.invalid"
    )

    @task(4)
    def validate_valid_token(self):
        """Valida token QR de usuario activo."""
        with self.client.post(
            "/api/v1/gate/validate",
            json={"token": self.VALID_TOKEN},
            catch_response=True,
            name="POST /gate/validate [valid-token]"
        ) as response:
            # El servicio siempre retorna 200 con valid=true|false
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(1)
    def validate_invalid_token(self):
        """Valida token malformado (simula intentos de acceso inválidos)."""
        with self.client.post(
            "/api/v1/gate/validate",
            json={"token": "invalid.token.here"},
            catch_response=True,
            name="POST /gate/validate [invalid-token]"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")


# ─────────────────────────────────────────────
# promotion-service load test
# ─────────────────────────────────────────────
class PromotionServiceUser(HttpUser):
    """
    Simula señales de ubicación en tiempo real desde dispositivos BLE.
    Caso de uso: múltiples access points enviando señales concurrentemente.
    """
    wait_time = between(0.2, 1.0)
    host = os.getenv("PROMOTION_SERVICE_URL", "http://promotion-service:8080")

    @task(5)
    def report_location_signal(self):
        """Señal WiFi desde un access point (apMac + deviceMac + rssi)."""
        # Genera MACs aleatorias simulando dispositivos reales
        ap_mac = ":".join([f"{uuid.uuid4().int >> i & 0xFF:02x}" for i in range(0, 48, 8)][:6])
        device_mac = ":".join([f"{uuid.uuid4().int >> i & 0xFF:02x}" for i in range(0, 48, 8)][:6])
        payload = {
            "apMac": ap_mac,
            "deviceMac": device_mac,
            "rssi": -70
        }
        with self.client.post(
            "/api/v1/location/signal",
            json=payload,
            catch_response=True,
            name="POST /location/signal"
        ) as response:
            if response.status_code in (200, 202, 204):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(1)
    def get_health_stats(self):
        """Consulta de estadísticas de salud (dashboard de admin)."""
        with self.client.get(
            "/api/v1/health-status/stats",
            catch_response=True,
            name="GET /health-status/stats"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")


# ─────────────────────────────────────────────
# Evento: resumen al finalizar
# ─────────────────────────────────────────────
@events.quitting.add_listener
def on_quitting(environment, **kwargs):
    stats = environment.stats
    total = stats.total
    print("\n========== PERFORMANCE SUMMARY ==========")
    print(f"Total requests  : {total.num_requests}")
    print(f"Failures        : {total.num_failures}")
    print(f"Error rate      : {total.fail_ratio:.2%}")
    print(f"Avg response    : {total.avg_response_time:.0f} ms")
    print(f"p50             : {total.get_response_time_percentile(0.50):.0f} ms")
    print(f"p95             : {total.get_response_time_percentile(0.95):.0f} ms")
    print(f"p99             : {total.get_response_time_percentile(0.99):.0f} ms")
    print(f"Throughput      : {total.current_rps:.1f} req/s")
    print("=========================================\n")

    # Falla el pipeline si error rate > 5%
    if total.fail_ratio > 0.05:
        print("ERROR: fail ratio exceeds 5% threshold!")
        environment.process_exit_code = 1
