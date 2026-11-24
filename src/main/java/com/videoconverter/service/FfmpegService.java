package com.videoconverter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Video / audio processing using the FFmpeg command line tool. */
@Service
public class FfmpegService {

    /** A dubbed phrase and where it starts in the video. */
    public record Placed(Path audio, double startSeconds) {}

    private static final Pattern DURATION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+\\.\\d+)");

    @Value("${ffmpeg.path}")
    private String ffmpeg;

    /** Step 1: pull the speech out of the video as a small mono MP3. */
    public void extractAudio(Path video, Path audioOut) throws IOException, InterruptedException {
        run(List.of(ffmpeg, "-y", "-i", video.toString(),
                "-vn", "-ac", "1", "-ar", "16000", "-b:a", "48k", audioOut.toString()));
    }

    /** Length of a media file in seconds. */
    public double duration(Path media) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(ffmpeg, "-i", media.toString()).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        Matcher m = DURATION.matcher(log);
        if (!m.find()) throw new IOException("Could not read duration of " + media);
        return Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3));
    }

    /**
     * Speeds a voice clip up (never slows it down) so it fits its time slot, up to 2x;
     * anything still longer than the slot is trimmed so it cannot run over the next phrase.
     */
    public Path fitToSlot(Path in, Path out, double slotSeconds) throws IOException, InterruptedException {
        double len = duration(in);
        double tempo = Math.min(2.0, Math.max(1.0, len / slotSeconds));
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-y", "-i", in.toString()));
        if (tempo > 1.01) cmd.addAll(List.of("-filter:a", String.format(Locale.ROOT, "atempo=%.3f", tempo)));
        cmd.addAll(List.of("-t", String.format(Locale.ROOT, "%.3f", slotSeconds), out.toString()));
        run(cmd);
        return out;
    }

    /** Final step: put every dubbed phrase at its original timestamp and replace the video's audio. */
    public void mixIntoVideo(Path video, List<Placed> parts, Path out) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-y", "-i", video.toString()));
        StringBuilder graph = new StringBuilder();
        StringBuilder labels = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            cmd.addAll(List.of("-i", parts.get(i).audio().toString()));
            long ms = Math.round(parts.get(i).startSeconds() * 1000);
            graph.append(String.format("[%d:a]adelay=%d|%d[a%d];", i + 1, ms, ms, i));
            labels.append("[a").append(i).append(']');
        }
        // phrases never overlap, so no volume normalisation; pad with silence, then -shortest cuts at the video end
        graph.append(labels).append(String.format("amix=inputs=%d:normalize=0:dropout_transition=0,apad[aout]", parts.size()));

        cmd.addAll(List.of("-filter_complex", graph.toString(),
                "-map", "0:v:0", "-map", "[aout]",
                "-c:v", "copy", "-c:a", "aac", "-shortest", out.toString()));
        run(cmd);
    }

    private void run(List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IOException("FFmpeg failed. Is it installed? Details:\n" + log);
        }
    }
}
