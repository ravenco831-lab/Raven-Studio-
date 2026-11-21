const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');

const app = express();
app.set('trust proxy', 1);

const PORT = Number(process.env.PORT || 8080);
const API_KEY = (process.env.STUDIO_BACKEND_API_KEY || '').trim();
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || '').trim().replace(/\/+$/, '');

const HF_API_TOKEN = (process.env.HF_API_TOKEN || '').trim();
const HF_MUSIC_MODEL = (process.env.HF_MUSIC_MODEL || 'facebook/musicgen-small').trim();

const REPLICATE_API_TOKEN = (process.env.REPLICATE_API_TOKEN || '').trim();
const REPLICATE_MUSIC_MODEL = (process.env.REPLICATE_MUSIC_MODEL || 'meta/musicgen').trim();
const REPLICATE_LIPSYNC_MODEL = (process.env.REPLICATE_LIPSYNC_MODEL || 'cjwbw/sadtalker').trim();
const REPLICATE_I2V_MODEL = (process.env.REPLICATE_I2V_MODEL || 'stability-ai/stable-video-diffusion').trim();

const MUSIC_GEN_API_URL = (process.env.MUSIC_GEN_API_URL || '').trim();
const MUSIC_GEN_API_KEY = (process.env.MUSIC_GEN_API_KEY || '').trim();

const GENERATED_DIR = path.join(__dirname, 'generated');
const UPLOADS_DIR = path.join(__dirname, 'uploads');
fs.mkdirSync(GENERATED_DIR, { recursive: true });
fs.mkdirSync(UPLOADS_DIR, { recursive: true });

// In-memory job store (bounded to 500 recent jobs)
const jobs = new Map();
let ffmpegAvailable = false;

execFile('ffmpeg', ['-version'], (err) => {
  ffmpegAvailable = !err;
  console.log(`[startup] FFmpeg available: ${ffmpegAvailable}`);
});

// Periodically purge ephemeral files older than 6 hours on Render Free Tier
const MAX_EPHEMERAL_AGE_MS = 6 * 60 * 60 * 1000;
function cleanupOldFiles(dirPath) {
  try {
    const cutoff = Date.now() - MAX_EPHEMERAL_AGE_MS;
    for (const entry of fs.readdirSync(dirPath)) {
      if (entry.startsWith('.')) continue;
      const full = path.join(dirPath, entry);
      const stat = fs.statSync(full);
      if (stat.isFile() && stat.mtimeMs < cutoff) {
        fs.unlinkSync(full);
      }
    }
  } catch (err) {
    console.warn('[cleanup] Warning:', err.message);
  }
}
setInterval(() => {
  cleanupOldFiles(GENERATED_DIR);
  cleanupOldFiles(UPLOADS_DIR);
}, 30 * 60 * 1000).unref();

function setJob(job) {
  if (jobs.size >= 500) {
    const oldestKey = jobs.keys().next().value;
    if (oldestKey) jobs.delete(oldestKey);
  }
  jobs.set(job.jobId, job);
  return job;
}

function isAuthSecretConfigured() {
  return Boolean(API_KEY && API_KEY !== 'change-me-to-a-long-random-secret');
}

function constantTimeEquals(a, b) {
  const bufA = Buffer.from(a, 'utf8');
  const bufB = Buffer.from(b, 'utf8');
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function requireBearerAuth(req, res, next) {
  if (!isAuthSecretConfigured()) {
    return res.status(503).json({
      error: 'STUDIO_BACKEND_API_KEY is not configured on the backend server.'
    });
  }
  const auth = req.headers.authorization || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
  if (!token || !constantTimeEquals(token, API_KEY)) {
    return res.status(401).json({ error: 'Unauthorized: invalid STUDIO_BACKEND_API_KEY' });
  }
  next();
}

function resolvePublicAssetUrl(req, folder, fileName) {
  const safeName = path.basename(fileName);
  if (PUBLIC_BASE_URL) {
    return `${PUBLIC_BASE_URL}/${folder}/${safeName}`;
  }
  const protoHeader = (req.headers['x-forwarded-proto'] || req.protocol || 'https').toString();
  const proto = protoHeader.split(',')[0].trim();
  const hostHeader = (req.headers['x-forwarded-host'] || req.get('host') || '').toString();
  const host = hostHeader.split(',')[0].trim();
  return `${proto}://${host}/${folder}/${safeName}`;
}

function resolvePublicUrl(req, fileName) {
  return resolvePublicAssetUrl(req, 'generated', fileName);
}

function isAllowedRemoteMediaUrl(rawUrl) {
  if (!rawUrl || typeof rawUrl !== 'string') return false;
  try {
    const parsed = new URL(rawUrl);
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') return false;
    const host = parsed.hostname.toLowerCase();
    if (
      host === 'localhost' ||
      host === '127.0.0.1' ||
      host === '::1' ||
      host.startsWith('10.') ||
      host.startsWith('192.168.') ||
      /^172\.(1[6-9]|2\d|3[0-1])\./.test(host) ||
      host === '169.254.169.254'
    ) {
      return false;
    }
    return true;
  } catch {
    return false;
  }
}

function resolutionDimensions(aspectRatio, resolution) {
  const is1080 = resolution === '1080p';
  if (aspectRatio === '16:9') return is1080 ? '1920x1080' : '1280x720';
  if (aspectRatio === '1:1') return is1080 ? '1080x1080' : '720x720';
  return is1080 ? '1080x1920' : '720x1280'; // 9:16 default
}

const MIME_TO_EXT = {
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'audio/mpeg': 'mp3',
  'audio/mp3': 'mp3',
  'audio/wav': 'wav',
  'audio/x-wav': 'wav',
  'audio/wave': 'wav',
  'audio/mp4': 'm4a',
  'audio/x-m4a': 'm4a',
  'audio/m4a': 'm4a',
  'audio/aac': 'aac',
  'audio/flac': 'flac',
  'audio/ogg': 'ogg',
  'video/mp4': 'mp4',
  'video/quicktime': 'mov',
  'video/webm': 'webm'
};

function resolveUploadExtension(rawMime, rawName) {
  const normalizedMime = (rawMime || '').split(';')[0].trim().toLowerCase();
  if (MIME_TO_EXT[normalizedMime]) {
    return { ext: MIME_TO_EXT[normalizedMime], mimeType: normalizedMime };
  }
  const nameExt = path.extname((rawName || '').toString()).replace('.', '').toLowerCase();
  const matchedMime = Object.entries(MIME_TO_EXT).find(([, ext]) => ext === nameExt)?.[0];
  if (matchedMime) {
    return { ext: nameExt, mimeType: matchedMime };
  }
  return null;
}

async function createReplicatePrediction(modelSlug, input) {
  const [owner, name] = modelSlug.split('/');
  const endpoint =
    owner && name
      ? `https://api.replicate.com/v1/models/${encodeURIComponent(owner)}/${encodeURIComponent(name)}/predictions`
      : 'https://api.replicate.com/v1/predictions';

  const resp = await fetch(endpoint, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${REPLICATE_API_TOKEN}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ input })
  });

  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(`Replicate API error (${resp.status}): ${text.slice(0, 300)}`);
  }
  return resp.json();
}

async function fetchReplicatePrediction(predictionId) {
  const resp = await fetch(`https://api.replicate.com/v1/predictions/${encodeURIComponent(predictionId)}`, {
    headers: { Authorization: `Bearer ${REPLICATE_API_TOKEN}` }
  });
  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(`Replicate poll error (${resp.status}): ${text.slice(0, 300)}`);
  }
  return resp.json();
}

// Root landing status
app.get('/', (_req, res) => {
  res.json({
    ok: true,
    service: 'Raven AI Music Studio Backend',
    health: '/health'
  });
});

// Health check (unauthenticated)
app.get('/health', (_req, res) => {
  res.json({
    ok: true,
    service: 'Raven AI Music Studio Backend',
    authConfigured: isAuthSecretConfigured(),
    ffmpegAvailable,
    providers: {
      huggingFaceMusic: Boolean(HF_API_TOKEN),
      replicate: Boolean(REPLICATE_API_TOKEN),
      customMusicApi: Boolean(MUSIC_GEN_API_URL && MUSIC_GEN_API_KEY)
    },
    capabilities: {
      mediaUpload: isAuthSecretConfigured(),
      aiMusic: Boolean(HF_API_TOKEN || REPLICATE_API_TOKEN || (MUSIC_GEN_API_URL && MUSIC_GEN_API_KEY)),
      lipSync: Boolean(REPLICATE_API_TOKEN),
      imageToVideo: Boolean(REPLICATE_API_TOKEN),
      duetPerformance: Boolean(REPLICATE_API_TOKEN && ffmpegAvailable),
      ffmpegExport: ffmpegAvailable
    }
  });
});

// Secure Upload Endpoint for local Android device portraits, audio, and video clips (up to 25 MB)
app.post(
  '/v1/media/upload',
  requireBearerAuth,
  express.raw({ type: '*/*', limit: '25mb' }),
  (req, res) => {
    if (!Buffer.isBuffer(req.body) || req.body.length === 0) {
      return res.status(400).json({ error: 'Empty upload body.' });
    }
    const rawName = req.headers['x-file-name'] || '';
    const resolved = resolveUploadExtension(req.headers['content-type'], rawName);
    if (!resolved) {
      return res.status(415).json({
        error: 'Unsupported media type. Allowed: JPG, PNG, WEBP, MP3, WAV, M4A, AAC, FLAC, OGG, MP4, MOV, WEBM.'
      });
    }

    const fileName = `upload_${crypto.randomUUID()}.${resolved.ext}`;
    const destPath = path.join(UPLOADS_DIR, fileName);
    fs.writeFileSync(destPath, req.body);

    return res.json({
      url: resolvePublicAssetUrl(req, 'uploads', fileName),
      fileName,
      mimeType: resolved.mimeType,
      sizeBytes: req.body.length
    });
  }
);

app.use(express.json({ limit: '2mb' }));

// Serve uploaded user media safely for remote AI providers & preview
app.get('/uploads/:filename', (req, res) => {
  const fileName = path.basename(req.params.filename || '');
  if (!/^[a-zA-Z0-9._-]+$/.test(fileName)) {
    return res.status(400).json({ error: 'Invalid file name' });
  }
  const fullPath = path.join(UPLOADS_DIR, fileName);
  if (!fs.existsSync(fullPath)) {
    return res.status(404).json({ error: 'File not found' });
  }
  res.sendFile(fullPath);
});

// Serve generated assets safely
app.get('/generated/:filename', (req, res) => {
  const fileName = path.basename(req.params.filename || '');
  if (!/^[a-zA-Z0-9._-]+$/.test(fileName)) {
    return res.status(400).json({ error: 'Invalid file name' });
  }
  const fullPath = path.join(GENERATED_DIR, fileName);
  if (!fs.existsSync(fullPath)) {
    return res.status(404).json({ error: 'File not found' });
  }
  res.sendFile(fullPath);
});

// 1. AI Music Generation
app.post('/v1/music/generate', requireBearerAuth, async (req, res) => {
  const jobId = `music_${crypto.randomUUID()}`;
  const { mode, title, prompt, lyrics, genre, mood, bpm, vocalType, vocalStyle, instruments } = req.body || {};

  const fullPrompt = [
    `${genre || 'Country'} ${mood || 'Soulful'} track`,
    `${bpm || 100} BPM`,
    mode === 'INSTRUMENTAL' ? 'instrumental only' : `${vocalType || 'DUET'} vocals (${vocalStyle || 'expressive'})`,
    Array.isArray(instruments) && instruments.length ? `instruments: ${instruments.join(', ')}` : '',
    prompt || title || ''
  ]
    .filter(Boolean)
    .join(', ');

  // Option A: Custom Music API (e.g. Suno/Udio gateway)
  if (MUSIC_GEN_API_URL && MUSIC_GEN_API_KEY) {
    try {
      const upstream = await fetch(MUSIC_GEN_API_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${MUSIC_GEN_API_KEY}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ mode, title, prompt: fullPrompt, lyrics, genre, mood, bpm, vocalType })
      });
      const data = await upstream.json();
      if (!upstream.ok) {
        return res.json(setJob({
          jobId,
          providerName: 'CustomMusicAPI',
          status: 'FAILED',
          progressPercent: 0,
          estimatedCostUsd: 0,
          errorMessage: data?.error || `Custom Music API returned ${upstream.status}`
        }));
      }
      return res.json(setJob({
        jobId,
        providerName: 'CustomMusicAPI',
        status: data.audioUrl ? 'COMPLETED' : 'PROCESSING',
        progressPercent: data.audioUrl ? 100 : 25,
        estimatedCostUsd: Number(data.estimatedCostUsd || 0.1),
        resultAudioUrl: data.audioUrl || null,
        resultWavUrl: data.wavUrl || null,
        durationMs: Number(data.durationMs || 0)
      }));
    } catch (err) {
      return res.json(setJob({
        jobId,
        providerName: 'CustomMusicAPI',
        status: 'FAILED',
        progressPercent: 0,
        estimatedCostUsd: 0,
        errorMessage: err.message
      }));
    }
  }

  // Option B: Replicate API
  if (REPLICATE_API_TOKEN) {
    try {
      const pred = await createReplicatePrediction(REPLICATE_MUSIC_MODEL, {
        prompt: fullPrompt,
        duration: 20,
        model_version: 'stereo-large',
        output_format: 'mp3'
      });
      return res.json(setJob({
        jobId,
        providerName: `Replicate (${REPLICATE_MUSIC_MODEL})`,
        status: 'QUEUED',
        progressPercent: 15,
        estimatedCostUsd: 0.08,
        externalType: 'replicate',
        replicateId: pred.id,
        mediaKind: 'audio'
      }));
    } catch (err) {
      return res.json(setJob({
        jobId,
        providerName: 'Replicate',
        status: 'FAILED',
        progressPercent: 0,
        estimatedCostUsd: 0,
        errorMessage: err.message
      }));
    }
  }

  // Option C: Free-tier Hugging Face Inference API (MusicGen)
  if (HF_API_TOKEN) {
    const job = setJob({
      jobId,
      providerName: `HuggingFace (${HF_MUSIC_MODEL})`,
      status: 'PROCESSING',
      progressPercent: 20,
      estimatedCostUsd: 0.0
    });

    const publicUrl = resolvePublicUrl(req, `${jobId}.flac`);

    (async () => {
      try {
        const hfResp = await fetch(`https://router.huggingface.co/hf-inference/models/${HF_MUSIC_MODEL}`, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${HF_API_TOKEN}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ inputs: fullPrompt })
        });
        if (!hfResp.ok) {
          const errText = await hfResp.text();
          setJob({
            ...job,
            status: 'FAILED',
            progressPercent: 0,
            errorMessage: `Hugging Face inference error (${hfResp.status}): ${errText.slice(0, 240)}`
          });
          return;
        }
        const arrBuf = await hfResp.arrayBuffer();
        const fileName = `${jobId}.flac`;
        fs.writeFileSync(path.join(GENERATED_DIR, fileName), Buffer.from(arrBuf));
        setJob({
          ...job,
          status: 'COMPLETED',
          progressPercent: 100,
          resultAudioUrl: publicUrl,
          durationMs: 15000
        });
      } catch (err) {
        setJob({
          ...job,
          status: 'FAILED',
          progressPercent: 0,
          errorMessage: err.message
        });
      }
    })();

    return res.json(job);
  }

  return res.json(setJob({
    jobId,
    providerName: 'RavenStudioBackend',
    status: 'FAILED',
    progressPercent: 0,
    estimatedCostUsd: 0,
    errorMessage:
      'No music generation API token is configured on Render. Add HF_API_TOKEN (free Hugging Face tier) or REPLICATE_API_TOKEN in Render Environment Variables.'
  }));
});

// 2. Photo-to-Singing Audio-Driven Lip-Sync
app.post('/v1/video/lipsync', requireBearerAuth, async (req, res) => {
  const jobId = `lipsync_${crypto.randomUUID()}`;
  const { portraitUrl, audioUrl, expressionIntensity, consentConfirmed } = req.body || {};

  if (!consentConfirmed) {
    return res.status(400).json({ error: 'Explicit portrait and audio animation consent is required.' });
  }
  if (!isAllowedRemoteMediaUrl(portraitUrl) || !isAllowedRemoteMediaUrl(audioUrl)) {
    return res.json(setJob({
      jobId,
      providerName: 'RavenStudioBackend',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: 'Portrait and audio must be reachable HTTPS URLs or uploaded via POST /v1/media/upload before lip-sync rendering.'
    }));
  }
  if (!REPLICATE_API_TOKEN) {
    return res.json(setJob({
      jobId,
      providerName: 'RavenStudioBackend',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: 'Lip-sync video generation requires REPLICATE_API_TOKEN configured in Render Environment Variables (default model: cjwbw/sadtalker).'
    }));
  }

  try {
    const pred = await createReplicatePrediction(REPLICATE_LIPSYNC_MODEL, {
      source_image: portraitUrl,
      driven_audio: audioUrl,
      expression_scale: Number(expressionIntensity || 1.0),
      still: false,
      preprocess: 'crop'
    });
    return res.json(setJob({
      jobId,
      providerName: `Replicate (${REPLICATE_LIPSYNC_MODEL})`,
      status: 'QUEUED',
      progressPercent: 15,
      estimatedCostUsd: 0.15,
      externalType: 'replicate',
      replicateId: pred.id,
      mediaKind: 'video'
    }));
  } catch (err) {
    return res.json(setJob({
      jobId,
      providerName: 'Replicate',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: err.message
    }));
  }
});

// 3. Image-to-Video Generator
app.post('/v1/video/image-to-video', requireBearerAuth, async (req, res) => {
  const jobId = `i2v_${crypto.randomUUID()}`;
  const { imageUrl, prompt, cameraMovement, animationStyle, audioUrl, audioTrimStartMs, audioTrimEndMs } = req.body || {};

  if (!isAllowedRemoteMediaUrl(imageUrl)) {
    return res.json(setJob({
      jobId,
      providerName: 'RavenStudioBackend',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: 'Source image must be a reachable HTTPS URL or uploaded via POST /v1/media/upload before remote video generation.'
    }));
  }
  if (!REPLICATE_API_TOKEN) {
    return res.json(setJob({
      jobId,
      providerName: 'RavenStudioBackend',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: 'Image-to-Video generation requires REPLICATE_API_TOKEN configured in Render Environment Variables (default model: stability-ai/stable-video-diffusion).'
    }));
  }

  try {
    const pred = await createReplicatePrediction(REPLICATE_I2V_MODEL, {
      input_image: imageUrl,
      sizing_strategy: 'maintain_aspect_ratio',
      motion_bucket_id: cameraMovement === 'Static' ? 40 : 127,
      fps: animationStyle === 'Cinematic' ? 24 : 16,
      prompt: prompt || ''
    });
    return res.json(setJob({
      jobId,
      providerName: `Replicate (${REPLICATE_I2V_MODEL})`,
      status: 'QUEUED',
      progressPercent: 15,
      estimatedCostUsd: 0.18,
      externalType: 'replicate',
      replicateId: pred.id,
      mediaKind: 'video',
      attachAudioUrl: isAllowedRemoteMediaUrl(audioUrl) ? audioUrl : null,
      audioTrimStartMs: Number(audioTrimStartMs || 0),
      audioTrimEndMs: Number(audioTrimEndMs || 0),
      publicUrlBuilder: (fileName) => resolvePublicUrl(req, fileName)
    }));
  } catch (err) {
    return res.json(setJob({
      jobId,
      providerName: 'Replicate',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: err.message
    }));
  }
});

// 4. Raven & Breeze Duet Multi-Scene Lip-Sync
app.post('/v1/video/duet-performance', requireBearerAuth, async (req, res) => {
  const jobId = `duet_${crypto.randomUUID()}`;
  const {
    singer1PortraitUrl,
    singer2PortraitUrl,
    audioUrl,
    performanceIntensity,
    consentConfirmed
  } = req.body || {};

  if (!consentConfirmed) {
    return res.status(400).json({ error: 'Explicit consent is required for both portraits and audio.' });
  }
  if (!REPLICATE_API_TOKEN) {
    return res.json(setJob({
      jobId,
      providerName: 'RavenStudioBackend',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: 'Duet lip-sync rendering requires REPLICATE_API_TOKEN configured in Render Environment Variables.'
    }));
  }
  if (!isAllowedRemoteMediaUrl(singer1PortraitUrl) || !isAllowedRemoteMediaUrl(singer2PortraitUrl) || !isAllowedRemoteMediaUrl(audioUrl)) {
    return res.json(setJob({
      jobId,
      providerName: 'RavenStudioBackend',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: 'Singer portraits and duet audio must be reachable HTTPS URLs or uploaded via POST /v1/media/upload.'
    }));
  }

  try {
    const [p1, p2] = await Promise.all([
      createReplicatePrediction(REPLICATE_LIPSYNC_MODEL, {
        source_image: singer1PortraitUrl,
        driven_audio: audioUrl,
        expression_scale: Number(performanceIntensity || 1.0)
      }),
      createReplicatePrediction(REPLICATE_LIPSYNC_MODEL, {
        source_image: singer2PortraitUrl,
        driven_audio: audioUrl,
        expression_scale: Number(performanceIntensity || 1.0)
      })
    ]);

    return res.json(setJob({
      jobId,
      providerName: `Replicate + FFmpeg (${REPLICATE_LIPSYNC_MODEL})`,
      status: 'PROCESSING',
      progressPercent: 20,
      estimatedCostUsd: 0.30,
      externalType: 'duet_replicate',
      replicateIds: [p1.id, p2.id],
      duetConfig: req.body,
      publicUrlBuilder: (fileName) => resolvePublicUrl(req, fileName)
    }));
  } catch (err) {
    return res.json(setJob({
      jobId,
      providerName: 'RavenStudioBackend',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: err.message
    }));
  }
});

// 5. Free Local/Docker FFmpeg Audio & Video Export
app.post('/v1/media/ffmpeg-export', requireBearerAuth, async (req, res) => {
  const jobId = `export_${crypto.randomUUID()}`;
  const {
    aspectRatio = '9:16',
    resolution = '1080p',
    watermarkBrand,
    audioUrl,
    trimStartMs = 0,
    trimEndMs = 0,
    fadeInMs = 0,
    fadeOutMs = 0,
    volume = 1.0,
    textOverlay
  } = req.body || {};

  if (!ffmpegAvailable) {
    return res.json(setJob({
      jobId,
      providerName: 'FFmpeg',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: 'FFmpeg binary is not installed on this server. Deploy on Render using the Docker runtime.'
    }));
  }

  if (!isAllowedRemoteMediaUrl(audioUrl)) {
    return res.json(setJob({
      jobId,
      providerName: 'FFmpeg',
      status: 'FAILED',
      progressPercent: 0,
      estimatedCostUsd: 0,
      errorMessage: 'Audio must be a reachable HTTPS URL or uploaded via POST /v1/media/upload for server-side FFmpeg export.'
    }));
  }

  const job = setJob({
    jobId,
    providerName: 'FFmpeg (Open Source)',
    status: 'PROCESSING',
    progressPercent: 25,
    estimatedCostUsd: 0.0
  });

  const outFile = `${jobId}.mp4`;
  const outPath = path.join(GENERATED_DIR, outFile);
  const publicVideoUrl = resolvePublicUrl(req, outFile);
  const dims = resolutionDimensions(aspectRatio, resolution);
  const startSec = Math.max(0, Number(trimStartMs) / 1000);
  const endSec = Number(trimEndMs) > Number(trimStartMs) ? Number(trimEndMs) / 1000 : 0;
  const safeVol = Math.min(2.0, Math.max(0.0, Number(volume) || 1.0));
  const fadeInSec = Math.min(30, Math.max(0, Number(fadeInMs) / 1000));
  const fadeOutSec = Math.min(30, Math.max(0, Number(fadeOutMs) / 1000));

  const aFilters = [`volume=${safeVol.toFixed(2)}`];
  if (fadeInSec > 0) aFilters.push(`afade=t=in:st=0:d=${fadeInSec.toFixed(2)}`);
  if (fadeOutSec > 0 && endSec > startSec) {
    const dur = endSec - startSec;
    const outStart = Math.max(0, dur - fadeOutSec);
    aFilters.push(`afade=t=out:st=${outStart.toFixed(2)}:d=${fadeOutSec.toFixed(2)}`);
  }

  const args = ['-y'];
  if (startSec > 0) args.push('-ss', startSec.toFixed(2));
  if (endSec > startSec) args.push('-to', endSec.toFixed(2));
  args.push('-i', audioUrl);
  args.push('-f', 'lavfi', '-i', `color=c=#0E0F12:s=${dims}:r=24`);

  const safeOverlay = [textOverlay, watermarkBrand].filter(Boolean).join(' • ')
    .replace(/[^a-zA-Z0-9 .,&'_-]/g, '')
    .slice(0, 80);

  if (safeOverlay) {
    args.push(
      '-vf',
      `drawtext=text='${safeOverlay}':fontcolor=#C6A15B:fontsize=36:x=(w-text_w)/2:y=h-120`
    );
  }
  args.push(
    '-af', aFilters.join(','),
    '-map', '1:v:0',
    '-map', '0:a:0',
    '-c:v', 'libx264',
    '-preset', 'veryfast',
    '-c:a', 'aac',
    '-b:a', '192k',
    '-shortest',
    outPath
  );

  execFile('ffmpeg', args, { timeout: 120000 }, (err) => {
    if (err) {
      setJob({
        ...job,
        status: 'FAILED',
        progressPercent: 0,
        errorMessage: `FFmpeg export failed: ${err.message}`
      });
      return;
    }
    setJob({
      ...job,
      status: 'COMPLETED',
      progressPercent: 100,
      resultVideoUrl: publicVideoUrl
    });
  });

  return res.json(job);
});

// 6. Job Polling Endpoint
app.get('/v1/jobs/:jobId', requireBearerAuth, async (req, res) => {
  const job = jobs.get(req.params.jobId);
  if (!job) {
    return res.status(404).json({ error: 'Job not found' });
  }

  if (job.externalType === 'replicate' && job.replicateId && (job.status === 'QUEUED' || job.status === 'PROCESSING')) {
    try {
      const pred = await fetchReplicatePrediction(job.replicateId);
      const output = Array.isArray(pred.output) ? pred.output[0] : pred.output;
      if (pred.status === 'succeeded') {
        if (job.mediaKind === 'video' && job.attachAudioUrl && ffmpegAvailable && typeof output === 'string') {
          if (!job.muxingStarted) {
            job.muxingStarted = true;
            const outFile = `${job.jobId}.mp4`;
            const outPath = path.join(GENERATED_DIR, outFile);
            const startSec = Math.max(0, Number(job.audioTrimStartMs || 0) / 1000);
            const endSec = Number(job.audioTrimEndMs) > Number(job.audioTrimStartMs)
              ? Number(job.audioTrimEndMs) / 1000
              : 0;
            const ffArgs = ['-y', '-i', output];
            if (startSec > 0) ffArgs.push('-ss', startSec.toFixed(2));
            if (endSec > startSec) ffArgs.push('-to', endSec.toFixed(2));
            ffArgs.push(
              '-i', job.attachAudioUrl,
              '-map', '0:v:0',
              '-map', '1:a:0',
              '-c:v', 'copy',
              '-c:a', 'aac',
              '-shortest',
              outPath
            );
            execFile('ffmpeg', ffArgs, { timeout: 120000 }, (err) => {
              setJob({
                ...job,
                status: 'COMPLETED',
                progressPercent: 100,
                resultVideoUrl: err ? output : job.publicUrlBuilder(outFile)
              });
            });
          }
          return res.json({ ...job, status: 'PROCESSING', progressPercent: 90 });
        }

        const updated = setJob({
          ...job,
          status: 'COMPLETED',
          progressPercent: 100,
          resultAudioUrl: job.mediaKind === 'audio' ? output : null,
          resultVideoUrl: job.mediaKind === 'video' ? output : null
        });
        return res.json(updated);
      }
      if (pred.status === 'failed' || pred.status === 'canceled') {
        return res.json(setJob({
          ...job,
          status: 'FAILED',
          progressPercent: 0,
          errorMessage: pred.error || `Replicate prediction ${pred.status}`
        }));
      }
      return res.json(setJob({
        ...job,
        status: 'PROCESSING',
        progressPercent: pred.status === 'starting' ? 35 : 65
      }));
    } catch (err) {
      return res.json(setJob({ ...job, status: 'FAILED', errorMessage: err.message }));
    }
  }

  if (job.externalType === 'duet_replicate' && Array.isArray(job.replicateIds) && job.status === 'PROCESSING') {
    try {
      const [p1, p2] = await Promise.all(job.replicateIds.map(fetchReplicatePrediction));
      if (p1.status === 'failed' || p2.status === 'failed' || p1.status === 'canceled' || p2.status === 'canceled') {
        return res.json(setJob({
          ...job,
          status: 'FAILED',
          progressPercent: 0,
          errorMessage: p1.error || p2.error || 'One of the duet lip-sync renders failed.'
        }));
      }
      if (p1.status === 'succeeded' && p2.status === 'succeeded') {
        const v1 = Array.isArray(p1.output) ? p1.output[0] : p1.output;
        const v2 = Array.isArray(p2.output) ? p2.output[0] : p2.output;
        const outFile = `${job.jobId}.mp4`;
        const outPath = path.join(GENERATED_DIR, outFile);
        const cfg = job.duetConfig || {};
        const dims = resolutionDimensions(cfg.aspectRatio || '9:16', cfg.resolution || '1080p');
        const [w, h] = dims.split('x').map(Number);
        const halfW = Math.floor(w / 2);

        const filter = `[0:v]scale=${halfW}:${h}:force_original_aspect_ratio=increase,crop=${halfW}:${h}[left];` +
          `[1:v]scale=${halfW}:${h}:force_original_aspect_ratio=increase,crop=${halfW}:${h}[right];` +
          `[left][right]hstack=inputs=2[v]`;

        if (!job.compositingStarted) {
          job.compositingStarted = true;
          execFile('ffmpeg', [
            '-y', '-i', v1, '-i', v2,
            '-filter_complex', filter,
            '-map', '[v]', '-map', '0:a?',
            '-c:v', 'libx264', '-preset', 'veryfast', '-c:a', 'aac',
            '-shortest', outPath
          ], { timeout: 120000 }, (err) => {
            if (err) {
              setJob({ ...job, status: 'FAILED', progressPercent: 0, errorMessage: err.message });
            } else {
              setJob({
                ...job,
                status: 'COMPLETED',
                progressPercent: 100,
                resultVideoUrl: job.publicUrlBuilder(outFile)
              });
            }
          });
        }
        return res.json({ ...job, progressPercent: 85 });
      }
      return res.json({ ...job, progressPercent: 50 });
    } catch (err) {
      return res.json(setJob({ ...job, status: 'FAILED', errorMessage: err.message }));
    }
  }

  res.json(job);
});

app.listen(PORT, () => {
  console.log(`Raven AI Music Studio Backend listening on port ${PORT}`);
});
