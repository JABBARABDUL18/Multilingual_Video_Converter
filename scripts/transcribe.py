"""Transcribe an audio file with local Whisper. Prints JSON: {language, segments:[{start,end,text}]}."""
import json
import subprocess
import sys

import numpy as np
from faster_whisper import WhisperModel

audio_path, model_name = sys.argv[1], sys.argv[2]

# Decode with ffmpeg to 16 kHz mono float32 (avoids PyAV version issues).
raw = subprocess.run(
    ["ffmpeg", "-nostdin", "-loglevel", "error", "-i", audio_path,
     "-f", "f32le", "-ac", "1", "-ar", "16000", "-"],
    check=True, capture_output=True).stdout
samples = np.frombuffer(raw, dtype=np.float32)

model = WhisperModel(model_name, device="cpu", compute_type="int8", cpu_threads=0)
# beam_size=1 + VAD: noticeably faster, and skips silence so timings stay tight.
segments, info = model.transcribe(samples, beam_size=1, vad_filter=True)
out = {
    "language": info.language,
    "segments": [{"start": round(s.start, 2), "end": round(s.end, 2), "text": s.text.strip()}
                 for s in segments if s.text.strip()],
}
sys.stdout.buffer.write(json.dumps(out, ensure_ascii=False).encode("utf-8"))
