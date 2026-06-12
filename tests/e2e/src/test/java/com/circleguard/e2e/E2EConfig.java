package com.circleguard.e2e;

/**
 * URLs base de los servicios según el ambiente.
 * Inyectado desde el pipeline via -Denv=dev|stage|master
 */
public class E2EConfig {

    private static final String ENV = System.getProperty("env", "local");

    // Services listen on their real application ports in every environment
    // (the Kubernetes Service port == targetPort == app port). Only the host
    // differs between local port-forwards and in-cluster DNS.
    private static int port(String service) {
        return switch (service) {
            case "identity-service"     -> 8083;
            case "notification-service" -> 8082;
            case "file-service"         -> 8085;
            case "form-service"         -> 8086;
            case "gateway-service"      -> 8087;
            case "promotion-service"    -> 8088;
            default -> 8080;
        };
    }

    private static String base(String service) {
        int port = port(service);
        return switch (ENV) {
            // In-cluster DNS (use when running E2E from a pod inside the mesh).
            case "stage"      -> "http://" + service + ".circleguard-stage.svc.cluster.local:" + port;
            case "master",
                 "production" -> "http://" + service + ".circleguard-production.svc.cluster.local:" + port;
            case "dev"        -> "http://" + service + ".circleguard-dev.svc.cluster.local:" + port;
            // "local" → localhost with real ports (kubectl port-forward or local stack).
            default           -> "http://localhost:" + port;
        };
    }

    public static final String FORM_SERVICE      = base("form-service");
    public static final String GATEWAY_SERVICE   = base("gateway-service");
    public static final String IDENTITY_SERVICE  = base("identity-service");
    public static final String FILE_SERVICE      = base("file-service");
    public static final String PROMOTION_SERVICE = base("promotion-service");
}
