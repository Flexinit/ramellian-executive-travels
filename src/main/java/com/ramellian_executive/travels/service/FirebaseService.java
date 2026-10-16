package com.ramellian_executive.travels.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FirebaseService {

    public String verifyPhoneToken(String idToken) {
        if (FirebaseApp.getApps().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Phone number verification via SMS is not enabled");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firebase ID token is required");
        }
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            Object phone = decoded.getClaims().get("phone_number");
            if (phone == null || phone.toString().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Firebase token does not contain a verified phone number");
            }
            return phone.toString();
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired Firebase phone token");
        }
    }
}
