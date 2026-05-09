package com.circleguard.e2e;

/**
 * URLs base de los servicios según el ambiente.
 * Inyectado desde el pipeline via -Denv=dev|stage|master
 */
public class E2EConfig {

    private static final String ENV = System.getProperty("env", "local");

    private static String base(String service) {
        return switch (ENV) {
            case "stage"  -> "http://" + service + ".circleguard-stage.svc.cluster.local:8080";
            case "master" -> "http://" + service + ".circleguard-master.svc.cluster.local:8080";
            case "dev"    -> "http://" + service + ".circleguard-dev.svc.cluster.local:8080";
            default       -> localUrl(service); // "local" → localhost con puertos reales
        };
    }

    private static String localUrl(String service) {
        int port = switch (service) {
            case "identity-service"     -> 8083;
            case "notification-service" -> 8082;
            case "file-service"         -> 8085;
            case "form-service"         -> 8086;
            case "gateway-service"      -> 8087;
            case "promotion-service"    -> 8088;
            default -> 8080;
        };
        return "http://localhost:" + port;
    }

    public static final String FORM_SERVICE      = base("form-service");
    public static final String GATEWAY_SERVICE   = base("gateway-service");
    public static final String IDENTITY_SERVICE  = base("identity-service");
    public static final String FILE_SERVICE      = base("file-service");
    public static final String PROMOTION_SERVICE = base("promotion-service");
}
