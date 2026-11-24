package com.videoconverter.controller;

import com.videoconverter.service.ConversionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final ConversionService service;

    public VideoController(ConversionService service) {
        this.service = service;
    }

    @PostMapping("/convert")
    public ResponseEntity<?> convert(@RequestParam("video") MultipartFile video,
                                     @RequestParam("language") String language) {
        if (video.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please choose a video file"));
        }
        try {
            ConversionService.Result r = service.convert(video, language);
            return ResponseEntity.ok(Map.of(
                    "id", r.id(),
                    "originalText", r.originalText(),
                    "translatedText", r.translatedText(),
                    "videoUrl", "/api/video/" + r.id()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    /** Streams the finished video (used by the player and the download button). */
    @GetMapping("/video/{id}")
    public ResponseEntity<Resource> video(@PathVariable String id,
                                          @RequestParam(defaultValue = "false") boolean download) {
        Path file = service.outputFile(id);
        if (!Files.exists(file)) return ResponseEntity.notFound().build();

        ResponseEntity.BodyBuilder res = ResponseEntity.ok().contentType(MediaType.valueOf("video/mp4"));
        if (download) {
            res.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"converted-video.mp4\"");
        }
        return res.body(new FileSystemResource(file));
    }
}
