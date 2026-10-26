package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.service.PlatformSettingsService;
import com.ramellian_executive.travels.service.bot.BaileysBotClient;
import com.ramellian_executive.travels.service.bot.BotMedia;
import com.ramellian_executive.travels.service.bot.BotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Bot", description = "Conversational booking assistant — WhatsApp and in-app")
public class BotController {

    private final BotService botService;
    private final BaileysBotClient baileysClient;
    private final PlatformSettingsService platformSettingsService;

    // ── Public status check (frontend uses this to show/hide widget) ──────────

    @GetMapping("/api/v1/bot/status")
    @Operation(summary = "Check whether the in-app bot assistant is enabled")
    public ResponseEntity<BotStatusResponse> status() {
        return ResponseEntity.ok(new BotStatusResponse(platformSettingsService.isBotAssistantEnabled()));
    }

    // ── WhatsApp inbound (called by Baileys microservice, no JWT) ──────────────

    @PostMapping("/api/v1/bot/whatsapp/inbound")
    @Operation(summary = "Receive inbound WhatsApp message from Baileys and reply")
    public ResponseEntity<Void> whatsappInbound(@Valid @RequestBody WhatsAppInbound req) {
        BotService.BotResponse response = botService.processMessage(req.phone(), "WHATSAPP", req.message());

        for (BotMedia media : response.media()) {
            if ("IMAGE".equals(media.type())) {
                baileysClient.sendImage(req.phone(), media.url(), media.caption());
            } else if ("DOCUMENT".equals(media.type())) {
                baileysClient.sendDocument(req.phone(), media.filePath(), media.fileName());
            }
        }

        if (response.text() != null && !response.text().isBlank()) {
            baileysClient.sendText(req.phone(), response.text());
        }

        return ResponseEntity.ok().build();
    }

    // ── In-app bot (JWT authenticated) ────────────────────────────────────────

    @PostMapping("/api/v1/bot/inapp")
    @Operation(summary = "Send a message to the Ramellian in-app bot")
    public ResponseEntity<InAppBotResponse> inapp(
            @Valid @RequestBody InAppBotRequest req,
            Authentication authentication) {
        String channelId = authentication.getName();
        BotService.BotResponse response = botService.processMessage(channelId, "INAPP", req.message());
        return ResponseEntity.ok(new InAppBotResponse(response.text(), response.media()));
    }

    // ── Request / Response records ────────────────────────────────────────────

    public record WhatsAppInbound(@NotBlank String phone, @NotBlank String message) {}

    public record InAppBotRequest(@NotBlank String message) {}

    public record InAppBotResponse(String text, List<BotMedia> media) {}

    public record BotStatusResponse(boolean enabled) {}
}
