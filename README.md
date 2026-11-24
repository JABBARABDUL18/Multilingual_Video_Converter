# Multilingual Video Language Conversion System (Java)

Upload a video → the speech is converted to text → translated into Telugu / Hindi / English (and more) → turned back into speech → merged into a new video you can play and download.

## Pipeline

```
Video --FFmpeg--> Audio --Whisper (local)--> Timed phrases
                                                  | per phrase, in parallel
                                                  v
                          Translate (MyMemory) -> Voice (Google TTS) -> fit to the phrase's time slot
                                                                              |
Output video <--FFmpeg: place every phrase at its original timestamp----------+
```

Each spoken phrase is dubbed on its own and put back at the time it was originally spoken, so the new voice stays in sync with the picture. A voice clip that is too long is sped up (up to 2x) to fit before the next phrase.

## Tech stack

| Part | Technology |
|---|---|
| Language / Backend | Java 17, Spring Boot 3 |
| Video & audio | FFmpeg |
| Speech-to-Text | Local Whisper (faster-whisper, free) |
| Translation | MyMemory (free, no key) |
| Text-to-Speech | Google Translate TTS (free, no key) |
| Frontend | HTML, CSS, JavaScript |

## Project structure

```
src/main/java/com/videoconverter/
  VideoConverterApplication.java      # entry point
  controller/VideoController.java     # /api/convert and /api/video/{id}
  service/ConversionService.java      # runs the full pipeline
  service/FfmpegService.java          # extract audio, merge audio+video
  service/SpeechToTextService.java    # local Whisper (scripts/transcribe.py)
  service/TranslationService.java     # MyMemory
  service/TextToSpeechService.java    # Google Translate TTS
scripts/transcribe.py                  # local Whisper runner
src/main/resources/
  application.properties              # settings
  static/                             # index.html, style.css, app.js
```

## Setup

No API keys are needed.

1. Install **JDK 17+**, **Maven**, **Python 3** and **FFmpeg** (`ffmpeg -version` must work in a terminal).
2. Install the free local speech recognizer:
   ```
   pip install faster-whisper
   ```
   (The first run downloads the Whisper model, about 150 MB.)
3. Run:
   ```
   mvn spring-boot:run
   ```
4. Open <http://localhost:8080>, upload a video, choose a language, click **Dub video**.

Optional environment variables: `WHISPER_MODEL` (`tiny`, `base`, `small`, `medium`; default `base`), `PYTHON_PATH`, `FFMPEG_PATH`.

## Notes

- Supported target languages: Telugu, Hindi, English, Tamil, Kannada, Malayalam (add more in `ConversionService.LANGUAGES` and `index.html`).
- Original speech language is auto-detected by Whisper.
- MyMemory's free tier allows about 5,000 characters of translation per day without a key.
- The new voice replaces the original audio and is cut at the end of the video.
