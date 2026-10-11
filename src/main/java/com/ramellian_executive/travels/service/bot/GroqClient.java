package com.ramellian_executive.travels.service.bot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${app.groq.api-key}")
    private String apiKey;

    @Value("${app.groq.model:llama-3.3-70b-versatile}")
    private String model;

    public GroqClient(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public GroqChoice complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 1024);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        try {
            GroqResponse response = restClient.post()
                    .uri(GROQ_CHAT_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GroqResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new RuntimeException("Empty response from Groq");
            }
            return response.choices().get(0);
        } catch (Exception e) {
            log.error("Groq API error: {}", e.getMessage(), e);
            throw new RuntimeException("LLM unavailable: " + e.getMessage(), e);
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqResponse(List<GroqChoice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqChoice(
            @JsonProperty("finish_reason") String finishReason,
            GroqMessage message
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqMessage(
            String role,
            String content,
            @JsonProperty("tool_calls") List<GroqToolCall> toolCalls
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqToolCall(
            String id,
            String type,
            @JsonProperty("function") GroqFunction function
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroqFunction(String name, String arguments) {}

    // ── Message builders ─────────────────────────────────────────────────────

    public static Map<String, Object> systemMsg(String content) {
        return Map.of("role", "system", "content", content);
    }

    public static Map<String, Object> userMsg(String content) {
        return Map.of("role", "user", "content", content);
    }

    public static Map<String, Object> assistantMsg(String content) {
        return Map.of("role", "assistant", "content", content != null ? content : "");
    }

    public static Map<String, Object> assistantToolCallMsg(List<GroqToolCall> toolCalls) {
        var msg = new java.util.LinkedHashMap<String, Object>();
        msg.put("role", "assistant");
        msg.put("content", (Object) null);
        msg.put("tool_calls", toolCalls.stream().map(tc -> {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("id", tc.id());
            m.put("type", "function");
            m.put("function", Map.of("name", tc.function().name(), "arguments", tc.function().arguments()));
            return m;
        }).toList());
        return msg;
    }

    public static Map<String, Object> toolResultMsg(String toolCallId, String content) {
        return Map.of("role", "tool", "tool_call_id", toolCallId, "content", content);
    }

    // ── Tool definition builder ───────────────────────────────────────────────

    public static Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    public static Map<String, Object> params(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required
        );
    }

    public static Map<String, Object> strProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    public static Map<String, Object> enumProp(String description, List<String> values) {
        return Map.of("type", "string", "description", description, "enum", values);
    }
}
