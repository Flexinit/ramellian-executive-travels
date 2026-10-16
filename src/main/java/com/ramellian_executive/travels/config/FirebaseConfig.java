package com.ramellian_executive.travels.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${app.firebase.service-account-path:}")
    private String serviceAccountPath;

    // Firebase phone verification is disabled until FIREBASE_SERVICE_ACCOUNT_PATH is configured.
    @Bean
    @ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true")
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials;
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            try (InputStream is = new FileInputStream(serviceAccountPath)) {
                credentials = GoogleCredentials.fromStream(is);
            }
            log.info("Firebase initialised from service account: {}", serviceAccountPath);
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
            log.info("Firebase initialised from application default credentials");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
        return FirebaseApp.initializeApp(options);
    }
}
