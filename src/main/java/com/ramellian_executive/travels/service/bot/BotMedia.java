package com.ramellian_executive.travels.service.bot;

public record BotMedia(
        String type,      // IMAGE, DOCUMENT
        String url,       // for IMAGE: full http URL
        String caption,   // for IMAGE
        String filePath,  // for DOCUMENT: absolute local path
        String fileName   // for DOCUMENT: display name
) {
    public static BotMedia image(String url, String caption) {
        return new BotMedia("IMAGE", url, caption, null, null);
    }

    public static BotMedia document(String filePath, String fileName) {
        return new BotMedia("DOCUMENT", null, null, filePath, fileName);
    }
}
