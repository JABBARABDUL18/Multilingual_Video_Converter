package com.videoconverter.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Step 4a: text -> speech using Google Translate's free TTS endpoint (no API key). */
@Service
public class TextToSpeechService {

    private final HttpClient http = HttpClient.newHttpClient();

    /** voiceLocale like "te-IN"; the free endpoint only needs the language part ("te"). */
    public void synthesize(String text, String voiceLocale, Path mp3Out) throws IOException, InterruptedException {
        String lang = voiceLocale.split("-")[0];
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        // The free endpoint accepts at most ~200 characters per request.
        for (String chunk : TextUtil.split(text, 180)) {
            String url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=" + lang
                    + "&q=" + URLEncoder.encode(chunk, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) throw new IOException("Text-to-Speech error: HTTP " + res.statusCode());
            audio.write(res.body());
        }
        Files.write(mp3Out, audio.toByteArray());
    }
}
