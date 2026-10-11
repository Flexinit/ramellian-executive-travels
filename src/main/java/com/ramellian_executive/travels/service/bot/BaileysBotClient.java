package com.ramellian_executive.travels.service.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class BaileysBotClient {

    private static final Logger log = LoggerFactory.getLogger(BaileysBotClient.class);

    private final RestClient restClient;

    @Value("${app.bot.baileys-url:http://localhost:3001}")
    private String baileysUrl;

    public BaileysBotClient() {
        this.restClient = RestClient.create();
    }

    public void sendText(String phone, String message) {
        post("/send/text", Map.of("phone", phone, "message", message));
    }

    public void sendImage(String phone, String imageUrl, String caption) {
        post("/send/image", Map.of("phone", phone, "imageUrl", imageUrl, "caption", caption != null ? caption : ""));
    }

    public void sendDocument(String phone, String filePath, String fileName) {
        post("/send/document", Map.of(
                "phone", phone,
                "filePath", filePath,
                "fileName", fileName,
                "mimetype", "application/pdf"
        ));
    }

    public boolean isHealthy() {
        try {
            restClient.get().uri(baileysUrl + "/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void post(String path, Map<String, Object> body) {
        try {
            restClient.post()
                    .uri(baileysUrl + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Baileys {} failed for {}: {}", path, body.get("phone"), e.getMessage());
        }
    }
}
