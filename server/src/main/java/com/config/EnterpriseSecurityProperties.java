package com.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public class EnterpriseSecurityProperties {
    @Valid
    private final Web web = new Web();
    @Valid
    private final PublicEndpoints publicEndpoints = new PublicEndpoints();

    public Web getWeb() { return web; }
    public PublicEndpoints getPublicEndpoints() { return publicEndpoints; }

    public static class Web {
        @NotEmpty
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:8080", "http://127.0.0.1:8080"));

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class PublicEndpoints {
        @Min(1) @Max(1000)
        private int uploadRequestsPerHour = 10;
        @Min(1) @Max(1000)
        private int applyRequestsPerHour = 5;

        public int getUploadRequestsPerHour() { return uploadRequestsPerHour; }
        public void setUploadRequestsPerHour(int value) { this.uploadRequestsPerHour = value; }
        public int getApplyRequestsPerHour() { return applyRequestsPerHour; }
        public void setApplyRequestsPerHour(int value) { this.applyRequestsPerHour = value; }
    }
}
