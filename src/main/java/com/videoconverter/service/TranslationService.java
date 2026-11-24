package com.videoconverter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Step 3: translate text using the free MyMemory API (no key; ~5000 chars/day anonymous). */
@Service
public class TranslationService {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String translate(String text, String sourceLang, String targetLang) throws IOException, InterruptedException {
        if (sourceLang.equalsIgnoreCase(targetLang)) return text;
        StringBuilder result = new StringBuilder();
        // MyMemory accepts at most 500 characters per request.
        for (String chunk : TextUtil.split(text, 450)) {
            String url = "https://api.mymemory.translated.net/get?langpair=" + sourceLang + "%7C" + targetLang
                    + "&q=" + URLEncoder.encode(chunk, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 429) { Thread.sleep(1500); res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
            if (res.statusCode() != 200) throw new IOException("Translation error: HTTP " + res.statusCode());

            JsonNode root = mapper.readTree(res.body());
            if (root.path("responseStatus").asInt() != 200) {
                throw new IOException("Translation error: " + root.path("responseDetails").asText());
            }
            result.append(root.at("/responseData/translatedText").asText()).append(' ');
        }
        return result.toString().trim();
    }
}
