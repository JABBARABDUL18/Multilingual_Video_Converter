package com.videoconverter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs the whole pipeline: video -> audio -> timed phrases -> translated phrases -> voice per phrase
 * -> every phrase placed back at its original time in the video.
 */
@Service
public class ConversionService {

    /** Languages offered in the UI: code -> text-to-speech locale. */
    private static final Map<String, String> LANGUAGES = Map.of(
            "te", "te-IN",
            "hi", "hi-IN",
            "en", "en-US",
            "ta", "ta-IN",
            "kn", "kn-IN",
            "ml", "ml-IN");

    private final FfmpegService ffmpeg;
    private final SpeechToTextService stt;
    private final TranslationService translator;
    private final TextToSpeechService tts;
    private final Path workspace;

    public ConversionService(FfmpegService ffmpeg, SpeechToTextService stt, TranslationService translator,
                             TextToSpeechService tts, @Value("${app.workspace}") String workspace) {
        this.ffmpeg = ffmpeg;
        this.stt = stt;
        this.translator = translator;
        this.tts = tts;
        this.workspace = Path.of(workspace).toAbsolutePath();
    }

    public record Result(String id, String originalText, String translatedText) {}

    public Result convert(MultipartFile file, String targetLang) throws IOException, InterruptedException {
        String voice = LANGUAGES.get(targetLang);
        if (voice == null) throw new IllegalArgumentException("Unsupported language: " + targetLang);

        String id = UUID.randomUUID().toString();
        Path dir = Files.createDirectories(workspace.resolve(id));
        Path input = dir.resolve("input.mp4");
        Path audio = dir.resolve("speech.mp3");
        Path output = dir.resolve("output.mp4");

        file.transferTo(input);                                          // 1. save the upload
        ffmpeg.extractAudio(input, audio);                               // 2. extract speech
        SpeechToTextService.Transcript t = stt.transcribe(audio);        // 3. speech -> timed phrases
        if (t.segments().isEmpty()) throw new IllegalStateException("No speech was found in this video.");
        double videoLength = ffmpeg.duration(input);

        // 4. translate + voice every phrase in parallel, each fitted to its own time slot
        var phrases = t.segments();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<String>> translations = new ArrayList<>();
        List<Future<FfmpegService.Placed>> voices = new ArrayList<>();
        try {
            for (int i = 0; i < phrases.size(); i++) {
                final int n = i;
                var seg = phrases.get(i);
                double nextStart = i + 1 < phrases.size() ? phrases.get(i + 1).start() : videoLength;
                double slot = Math.max(0.5, nextStart - seg.start() - 0.05);
                Future<String> tr = pool.submit(() -> translator.translate(seg.text(), t.language(), targetLang));
                translations.add(tr);
                voices.add(pool.submit(() -> {
                    Path raw = dir.resolve("raw" + n + ".mp3");
                    tts.synthesize(tr.get(), voice, raw);
                    Path fit = ffmpeg.fitToSlot(raw, dir.resolve("seg" + n + ".mp3"), slot);
                    return new FfmpegService.Placed(fit, seg.start());
                }));
            }
            List<FfmpegService.Placed> placed = new ArrayList<>();
            StringBuilder original = new StringBuilder();
            StringBuilder translated = new StringBuilder();
            for (int i = 0; i < phrases.size(); i++) {
                placed.add(get(voices.get(i)));
                original.append(phrases.get(i).text()).append(' ');
                translated.append(get(translations.get(i))).append(' ');
            }
            ffmpeg.mixIntoVideo(input, placed, output);                  // 5. place phrases at original times
            return new Result(id, original.toString().trim(), translated.toString().trim());
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> T get(Future<T> f) throws IOException, InterruptedException {
        try {
            return f.get();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof IOException io) throw io;
            throw new IOException(c.getMessage() == null ? c.toString() : c.getMessage(), c);
        }
    }

    public Path outputFile(String id) {
        if (!id.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Bad id");
        return workspace.resolve(id).resolve("output.mp4");
    }
}
