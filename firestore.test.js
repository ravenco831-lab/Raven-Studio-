const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_raven_123";
const BOB_UID = "bob_breeze_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("songs").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("lyrics").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("mediaAssets").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("jobs").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Unauthorized",
      email: "unauth@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Cross-user access is strictly blocked", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("projects")
      .doc("bob_duet_1")
      .set({
        userId: BOB_UID,
        projectType: "DUET_RAVEN_BREEZE",
        title: "Bob Private Duet",
        status: "DRAFT",
        aspectRatio: "9:16",
        resolution: "1080p",
        watermarkEnabled: true,
        aiGenerated: true,
        consentConfirmed: true,
        createdAt: created,
        updatedAt: created,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("projects")
      .doc("bob_duet_1")
      .get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("projects").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("projects")
      .doc("bob_duet_1")
      .update({
        title: "Hijacked",
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Owner can create, update, list, and delete SongRecord, LyricSheet, MediaAsset, StudioProject, and GenerationJob", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // 1. UserProfile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Raven Creator",
      email: "raven@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  // 2. SongRecord
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("songs")
      .doc("song_1")
      .set({
        userId: ALICE_UID,
        title: "Midnight Highway",
        prompt: "Southern rock anthem with soaring duet vocals",
        lyrics: "[Verse 1]\nNeon lights on the gravel road",
        genre: "Southern Rock",
        mood: "Anthemic",
        bpm: 112,
        vocalType: "DUET",
        mode: "LYRICS_TO_SONG",
        status: "SETUP_REQUIRED",
        aiGenerated: true,
        durationMs: 185000,
        versionNumber: 1,
        instruments: ["Acoustic Guitar", "Pedal Steel", "Drums"],
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("songs")
      .doc("song_1")
      .update({
        title: "Midnight Highway (Mastered)",
        updatedAt: updated,
      })
  );

  // 3. LyricSheet
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("lyrics")
      .doc("lyric_1")
      .set({
        userId: ALICE_UID,
        title: "Gold & Thunder",
        genre: "Country",
        duetMode: true,
        content: "[Verse 1 - Raven]\nHear the thunder roll",
        productionPrompt: "Warm country acoustic duet at 92 BPM",
        singer1Name: "Raven",
        singer2Name: "Breeze",
        aiGenerated: true,
        parts: [
          {
            id: "line_1",
            timestampMs: 0,
            durationMs: 4000,
            singer: "RAVEN",
            lyric: "Hear the thunder roll",
          },
        ],
        createdAt: created,
        updatedAt: created,
      })
  );

  // 4. MediaAsset (Audio with trim/fade/waveform)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("mediaAssets")
      .doc("audio_1")
      .set({
        userId: ALICE_UID,
        mediaType: "AUDIO",
        title: "studio_take_1.wav",
        mimeType: "audio/wav",
        durationMs: 120000,
        trimStartMs: 0,
        trimEndMs: 120000,
        splitPointMs: 60000,
        volume: 1.0,
        fadeInMs: 1500,
        fadeOutMs: 2000,
        syncOffsetMs: 0,
        faceDetected: false,
        faceCount: 0,
        aiGenerated: false,
        waveformSamples: [12, 44, 78, 95, 62, 30],
        createdAt: created,
        updatedAt: created,
      })
  );

  // 5. StudioProject (Raven & Breeze Duet Mode)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("duet_1")
      .set({
        userId: ALICE_UID,
        projectType: "DUET_RAVEN_BREEZE",
        title: "Raven & Breeze - Sunset Duet",
        status: "DRAFT",
        aspectRatio: "9:16",
        resolution: "1080p",
        watermarkEnabled: true,
        aiGenerated: true,
        consentConfirmed: true,
        singer1Name: "Raven",
        singer1VocalRole: "FEMALE",
        singer1FaceDetected: true,
        singer2Name: "Breeze",
        singer2VocalRole: "MALE",
        singer2FaceDetected: true,
        selectedSongTitle: "Midnight Highway",
        audioDurationMs: 185000,
        performanceStyle: "Live Concert",
        performanceIntensity: 0.8,
        useVocalSeparation: true,
        lipSyncOffsetMs: 0,
        duetLines: [
          {
            id: "l1",
            timestampMs: 0,
            durationMs: 5000,
            singer: "RAVEN",
            lyric: "Neon lights on the gravel road",
          },
          {
            id: "l2",
            timestampMs: 5000,
            durationMs: 5000,
            singer: "BOTH",
            lyric: "We ride till the morning glow",
          },
        ],
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("duet_1")
      .update({
        lipSyncOffsetMs: 120,
        performanceStyle: "Romantic Duet",
        updatedAt: updated,
      })
  );

  // 6. GenerationJob
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("jobs")
      .doc("job_1")
      .set({
        userId: ALICE_UID,
        jobType: "DUET_VIDEO",
        title: "Raven & Breeze Render",
        providerName: "LipSyncStudioBackend",
        status: "SETUP_REQUIRED",
        progressPercent: 0,
        estimatedCostUsd: 0.45,
        projectId: "duet_1",
        errorMessage: "STUDIO_BACKEND_BASE_URL not configured",
        createdAt: created,
        updatedAt: created,
      })
  );

  // List queries succeed for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );

  // Delete succeeds for owner
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("duet_1")
      .delete()
  );
});

test("Rejects @DocumentId id key, ghost fields, immutable createdAt mutation, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Rejects "id" key in body
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("lyrics")
      .doc("bad_lyric")
      .set({
        id: "bad_lyric",
        userId: ALICE_UID,
        title: "Bad",
        genre: "Pop",
        duetMode: false,
        content: "Test",
        productionPrompt: "Test",
        singer1Name: "Raven",
        singer2Name: "Breeze",
        aiGenerated: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Rejects future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("lyrics")
      .doc("future_lyric")
      .set({
        userId: ALICE_UID,
        title: "Future",
        genre: "Pop",
        duetMode: false,
        content: "Test",
        productionPrompt: "Test",
        singer1Name: "Raven",
        singer2Name: "Breeze",
        aiGenerated: true,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Create valid lyric, then try to mutate createdAt or add ghost field
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("lyrics")
      .doc("good_lyric")
      .set({
        userId: ALICE_UID,
        title: "Good",
        genre: "Pop",
        duetMode: false,
        content: "Line 1",
        productionPrompt: "Pop ballad",
        singer1Name: "Raven",
        singer2Name: "Breeze",
        aiGenerated: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("lyrics")
      .doc("good_lyric")
      .update({
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("lyrics")
      .doc("good_lyric")
      .update({
        isAdmin: true,
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});
