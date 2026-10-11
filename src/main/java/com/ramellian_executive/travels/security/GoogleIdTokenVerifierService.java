package com.ramellian_executive.travels.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

@Component
public class GoogleIdTokenVerifierService {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifierService.class);

    private final GoogleIdTokenVerifier verifier;
    private final String clientId;

    public GoogleIdTokenVerifierService(@Value("${app.google.client-id}") String clientId) {
        this.clientId = clientId;
        log.info("GoogleIdTokenVerifierService initialised with client-id: {}", clientId);
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleIdToken.Payload verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                log.warn("Google token verification returned null — audience mismatch or expired token. Expected client-id: {}", clientId);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
            }
            return token.getPayload();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token verification threw an exception (possible network issue reaching googleapis.com): {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token verification failed: " + e.getMessage());
        }
    }
}
