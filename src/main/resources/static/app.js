const $ = (id) => document.getElementById(id);
const form = $('form'), fileInput = $('video'), drop = $('drop'), goBtn = $('go');
const errorBox = $('error'), progress = $('progress'), result = $('result'), wave = $('wave');

// ---- waveform (decorative; animates while processing) ----
for (let i = 0; i < 56; i++) {
  const bar = document.createElement('i');
  const h = 18 + Math.abs(Math.sin(i * 0.55) * Math.cos(i * 0.21)) * 78;
  bar.style.setProperty('--h', h.toFixed(0) + '%');
  bar.style.setProperty('--d', (i % 9) * 0.09 + 's');
  wave.appendChild(bar);
}

// ---- file selection ----
function showFile(file) {
  const url = URL.createObjectURL(file);
  $('preview').src = url + '#t=0.5';
  $('fileName').textContent = file.name;
  $('fileSize').textContent = (file.size / 1048576).toFixed(1) + ' MB';
  $('dropEmpty').hidden = true;
  $('dropFile').hidden = false;
  errorBox.hidden = true;
}
fileInput.addEventListener('change', () => fileInput.files[0] && showFile(fileInput.files[0]));
['dragenter', 'dragover'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove('over'); }));
drop.addEventListener('drop', e => {
  const f = e.dataTransfer.files[0];
  if (f && f.type.startsWith('video/')) { fileInput.files = e.dataTransfer.files; showFile(f); }
  else showError('That file is not a video. Choose an MP4, MOV or WebM file.');
});

// ---- transcript tabs ----
document.querySelectorAll('.tab').forEach(tab => tab.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach(t => { t.classList.toggle('active', t === tab); t.setAttribute('aria-selected', t === tab); });
  $('translated').hidden = tab.dataset.tab !== 'translated';
  $('original').hidden = tab.dataset.tab !== 'original';
}));

// ---- progress: the server runs one request, so steps advance on an estimate ----
const steps = [...document.querySelectorAll('#steps li')];
let timer;
function setStep(n) {
  steps.forEach((li, i) => {
    li.classList.toggle('done', i < n);
    li.classList.toggle('active', i === n);
  });
}
function startProgress() {
  let n = 0;
  setStep(0);
  const delays = [4000, 25000, 8000, 15000]; // rough time spent before moving to the next step
  const advance = () => {
    if (n < delays.length) { timer = setTimeout(() => { setStep(++n); advance(); }, delays[n]); }
  };
  advance();
}
function stopProgress(ok) {
  clearTimeout(timer);
  if (ok) steps.forEach(li => { li.classList.add('done'); li.classList.remove('active'); });
}

// ---- submit ----
form.addEventListener('submit', async (e) => {
  e.preventDefault();
  if (!fileInput.files[0]) { showError('Choose a video first.'); return; }

  const data = new FormData();
  data.append('video', fileInput.files[0]);
  data.append('language', form.elements.language.value);

  errorBox.hidden = true;
  result.hidden = true;
  progress.hidden = false;
  wave.classList.add('live');
  goBtn.disabled = true;
  goBtn.textContent = 'Dubbing…';
  startProgress();
  progress.scrollIntoView({ behavior: 'smooth', block: 'center' });

  try {
    const res = await fetch('/api/convert', { method: 'POST', body: data });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(json.error || 'Dubbing failed. Check the server log for details.');

    stopProgress(true);
    $('player').src = json.videoUrl;
    $('download').href = json.videoUrl + '?download=true';
    $('original').textContent = json.originalText;
    $('translated').textContent = json.translatedText;
    result.hidden = false;
    setTimeout(() => result.scrollIntoView({ behavior: 'smooth', block: 'start' }), 300);
  } catch (err) {
    stopProgress(false);
    progress.hidden = true;
    showError(err.message);
  } finally {
    wave.classList.remove('live');
    goBtn.disabled = false;
    goBtn.textContent = 'Dub video';
  }
});

function showError(msg) {
  errorBox.textContent = msg;
  errorBox.hidden = false;
}
