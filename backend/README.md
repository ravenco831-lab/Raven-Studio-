# Raven AI Music Studio — Media & AI Backend

This lightweight Node.js + Express + FFmpeg service powers the remote rendering endpoints called by the Android app (`MediaGenerationApi.kt`):

- `GET /health` — Unauthenticated server, auth-key, FFmpeg, Replicate, and Hugging Face readiness check
- `POST /v1/media/upload` — Authenticated upload bridge (`<= 25 MB`) for local Android phone portraits, audio, and video clips
- `POST /v1/music/generate` — AI music generation (Hugging Face free tier, Replicate, or custom music API)
- `POST /v1/video/lipsync` — Audio-driven photo-to-singing facial lip-sync (`REPLICATE_API_TOKEN`)
- `POST /v1/video/image-to-video` — Image-to-video motion generation (`REPLICATE_API_TOKEN`)
- `POST /v1/video/duet-performance` — **Raven & Breeze Duet Mode** multi-singer lip-sync + FFmpeg split-screen compositing (`REPLICATE_API_TOKEN` + FFmpeg)
- `POST /v1/media/ffmpeg-export` — **100% free** open-source FFmpeg trim, fade, volume, sync offset, watermark, and `9:16` / `16:9` / `1:1` MP4 export
- `GET /v1/jobs/:jobId` — Progress tracking & completion polling

---

## Render Free Tier Deployment

### Option A: Blueprint (Recommended — Docker + FFmpeg included)
A root `/render.yaml` Blueprint is included in the repository pointing to `/backend`:
1. Push your project to GitHub.
2. In Render (`dashboard.render.com`), tap **New +** → **Blueprint** and pick your GitHub repository.
3. Render automatically creates `raven-ai-music-studio-backend` on the **Free** plan with **Docker** (`ffmpeg` included) and auto-generates `STUDIO_BACKEND_API_KEY`.
4. Add `REPLICATE_API_TOKEN` (for Image-to-Video & Lip-Sync) and/or `HF_API_TOKEN` (free tier for MusicGen) in Render **Environment**.

### Option B: Manual Render Web Service
- **If using Docker (Recommended for FFmpeg + Duet compositing)**:
  - Root Directory: `backend`
  - Language / Runtime: `Docker`
  - Build Command / Start Command: handled automatically by `/backend/Dockerfile` (`node server.js`)
  - Health Check Path: `/health`
- **If using Node runtime (FFmpeg features unavailable on standard Node image)**:
  - Root Directory: `backend`
  - Language / Runtime: `Node`
  - Build Command: `npm install --omit=dev`
  - Start Command: `npm start`
  - Health Check Path: `/health`

---

## Environment Variables

| Variable | Required? | Cost / Tier | What It Does |
|---|---|---|---|
| `PORT` | Auto-set | Free | HTTP port (`8080`). |
| `STUDIO_BACKEND_API_KEY` | **Yes** | Free | Secret token matching `STUDIO_BACKEND_API_KEY` in AI Studio Secrets (`Authorization: Bearer ...`). |
| `REPLICATE_API_TOKEN` | For Video / Lip-Sync | Pay-as-you-go / starter credits | Required for Image-to-Video (`stability-ai/stable-video-diffusion`), Photo-to-Singing (`cjwbw/sadtalker`), and Duet video rendering. |
| `HF_API_TOKEN` | Optional | **Free tier** | Hugging Face Inference API token for free AI music (`facebook/musicgen-small`). |
| `MUSIC_GEN_API_URL` / `MUSIC_GEN_API_KEY` | Optional | Varies | Custom REST gateway URL & key for dedicated vocal song generation. |
| `PUBLIC_BASE_URL` | Optional | Free | Override public HTTPS URL if behind a custom domain. |
