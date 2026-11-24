package com.videoconverter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Step 2: speech -> timed text using free, local Whisper (faster-whisper via scripts/transcribe.py). */
@Service
public class SpeechToTextService {

    /** One spoken phrase with its position in the video (seconds). */
    public record Segment(double start, double end, String text) {}

    public record Transcript(String language, List<Segment> segments) {}

    @Value("${python.path:python}")
    private String python;

    @Value("${whisper.model:base}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();

    public Transcript transcribe(Path audio) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(python, "scripts/transcribe.py", audio.toString(), model)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (p.waitFor() != 0) {
            throw new IOException("Local Whisper failed. Is 'pip install faster-whisper' done? See server log.");
        }
        JsonNode root = mapper.readTree(out);
        List<Segment> segments = new ArrayList<>();
        for (JsonNode s : root.get("segments")) {
            segments.add(new Segment(s.get("start").asDouble(), s.get("end").asDouble(), s.get("text").asText()));
        }
        return new Transcript(root.get("language").asText(), segments);
    }
}
