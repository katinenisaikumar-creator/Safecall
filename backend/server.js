require("dotenv").config();
const express = require("express");
const cors = require("cors");
const fs = require("fs");
const path = require("path");
const twilio = require("twilio");
const admin = require("firebase-admin");

const app = express();
const PORT = process.env.PORT || 3000;
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || "").replace(/\/+$/, "");
const STATE_FILE = path.join(__dirname, "safecall_state.json");

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// ---------------------------------------------------------------------------
// Firebase Admin Initialization (for FCM Push Notifications)
// ---------------------------------------------------------------------------
let fcmEnabled = false;
try {
  const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || "./serviceAccountKey.json";
  if (fs.existsSync(serviceAccountPath)) {
    const serviceAccount = require(path.resolve(serviceAccountPath));
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
    fcmEnabled = true;
    console.log("[FCM] Firebase Admin initialized.");
  } else {
    console.warn("[FCM] serviceAccountKey.json not found. Running without FCM push (HTTP polling still works).");
  }
} catch (err) {
  console.error("[FCM] Failed to initialize Firebase Admin:", err.message);
}

// ---------------------------------------------------------------------------
// Language & Voice Catalog (Twilio <Say> Google/Polly Neural Voices)
// ---------------------------------------------------------------------------
const LANGUAGE_PROFILES = {
  te: {
    ttsLang: "te-IN",
    voice: "Google.te-IN-Standard-A",
    greeting: (status) =>
      `నమస్కారం, వారు ఇప్పుడు ${status}. ప్రస్తుతం బిజీగా ఉన్నారు. దయచేసి బీప్ తర్వాత మీ మెసేజ్ చెప్పండి, నేను నోట్ చేసి వారికి పంపుతాను.`,
    closing: "ధన్యవాదాలు, మీ మెసేజ్ నోట్ చేసుకున్నాను. వారు ఫ్రీ అయిన తర్వాత మీకు కాల్ చేస్తారు.",
  },
  ta: {
    ttsLang: "ta-IN",
    voice: "Google.ta-IN-Standard-A",
    greeting: (status) =>
      `வணக்கம், அவர்கள் தற்போது ${status} இல் பிஸியாக உள்ளார்கள். பீப் சத்தத்திற்குப் பிறகு உங்கள் செய்தியைச் சொல்லுங்கள், நான் குறித்து அவர்களுக்கு அனுப்புகிறேன்.`,
    closing: "நன்றி, உங்கள் செய்தி குறிக்கப்பட்டது. அவர்கள் ஓய்வாக இருக்கும்போது மீண்டும் அழைப்பார்கள்.",
  },
  hi: {
    ttsLang: "hi-IN",
    voice: "Google.hi-IN-Wavenet-A",
    greeting: (status) =>
      `नमस्ते, वह अभी ${status} में व्यस्त हैं। कृपया बीप के बाद अपना संदेश बोलें, मैं इसे नोट करके उन्हें भेज दूँगा।`,
    closing: "धन्यवाद, आपका संदेश नोट कर लिया गया है। खाली होते ही वे आपको वापस कॉल करेंगे।",
  },
  kn: {
    ttsLang: "kn-IN",
    voice: "Google.kn-IN-Standard-A",
    greeting: (status) =>
      `ನಮಸ್ಕಾರ, ಅವರು ಈಗ ${status} ನಲ್ಲಿ ಬ್ಯುಸಿಯಾಗಿದ್ದಾರೆ. ದಯವಿಟ್ಟು ಬೀಪ್ ಶಬ್ದದ ನಂತರ ನಿಮ್ಮ ಸಂದೇಶವನ್ನು ಹೇಳಿ, ನಾನು ಅದನ್ನು ನೋಟ್ ಮಾಡಿ ಅವರಿಗೆ ಕಳುಹಿಸುತ್ತೇನೆ.`,
    closing: "ಧನ್ಯವಾದಗಳು, ನಿಮ್ಮ ಸಂದೇಶವನ್ನು ನೋಟ್ ಮಾಡಲಾಗಿದೆ. ಅವರು ಫ್ರೀ ಆದ ನಂತರ ನಿಮಗೆ ವಾಪಸ್ ಕರೆ ಮಾಡುತ್ತಾರೆ.",
  },
  en: {
    ttsLang: "en-IN",
    voice: "Polly.Kajal-Neural",
    greeting: (status) =>
      `Hello, they are currently in ${status} and busy right now. Please tell your message after the beep, I will note and send it to them.`,
    closing: "Thank you, your message is noted. They will call you back when free.",
  },
};

// ---------------------------------------------------------------------------
// Lightweight Persistent Store (survives server restarts)
// ---------------------------------------------------------------------------
function loadState() {
  try {
    if (fs.existsSync(STATE_FILE)) {
      return JSON.parse(fs.readFileSync(STATE_FILE, "utf8"));
    }
  } catch (err) {
    console.warn("[State] Could not read state file:", err.message);
  }
  return {
    student: {
      studentId: "default_student",
      collegeModeOn: true,
      status: "Lab Session lo unna",
      languageId: "te",
      localeTag: "te-IN",
      customGreeting: "",
      customClosing: "",
      fcmToken: "",
      updatedAt: Date.now(),
    },
    messages: [],
  };
}

function saveState(state) {
  try {
    fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
  } catch (err) {
    console.error("[State] Failed to write state file:", err.message);
  }
}

const state = loadState();

function resolveBaseUrl(req) {
  if (PUBLIC_BASE_URL) return PUBLIC_BASE_URL;
  const proto = req.headers["x-forwarded-proto"] || req.protocol || "http";
  return `${proto}://${req.get("host")}`;
}

function getProfileForStudent() {
  const s = state.student;
  if (s.languageId === "custom" && s.customGreeting) {
    const fallback = LANGUAGE_PROFILES.en;
    return {
      ttsLang: s.localeTag || "en-IN",
      voice: LANGUAGE_PROFILES[s.localeTag?.split("-")[0]]?.voice || fallback.voice,
      greeting: (status) => s.customGreeting.replace(/\{status\}/g, status),
      closing: s.customClosing || fallback.closing,
    };
  }
  return LANGUAGE_PROFILES[s.languageId] || LANGUAGE_PROFILES.te;
}

async function pushMessageToAndroid(msg) {
  const token = state.student.fcmToken;
  if (!fcmEnabled || !token) return;

  try {
    await admin.messaging().send({
      token,
      notification: {
        title: `SafeCall: Message from ${msg.callerDisplay}`,
        body: msg.transcript || "New recorded voice message",
      },
      data: {
        type: "parent_voice_message",
        id: String(msg.id),
        callSid: String(msg.callSid || ""),
        callerDisplay: String(msg.callerDisplay),
        callerNumber: String(msg.callerNumber),
        transcript: String(msg.transcript || ""),
        recordingUrl: String(msg.recordingUrl || ""),
        durationSeconds: String(msg.durationSeconds || 0),
        studentStatusAtCall: String(msg.studentStatusAtCall || ""),
        timestampMillis: String(msg.timestampMillis),
      },
      android: {
        priority: "high",
      },
    });
    console.log(`[FCM] Pushed message ${msg.id} to student device.`);
  } catch (err) {
    console.error("[FCM] Push error:", err.message);
  }
}

// ---------------------------------------------------------------------------
// REST Endpoints for Android App
// ---------------------------------------------------------------------------
app.get("/health", (_req, res) => {
  res.json({ ok: true, fcmEnabled, studentStatus: state.student.status });
});

app.post("/api/status", (req, res) => {
  const {
    studentId = "default_student",
    collegeModeOn = true,
    status = "Lab Session lo unna",
    languageId = "te",
    localeTag = "te-IN",
    customGreeting = "",
    customClosing = "",
    fcmToken = "",
  } = req.body || {};

  state.student = {
    studentId,
    collegeModeOn: Boolean(collegeModeOn),
    status: String(status).trim() || "College Class",
    languageId: String(languageId),
    localeTag: String(localeTag),
    customGreeting: String(customGreeting || ""),
    customClosing: String(customClosing || ""),
    fcmToken: fcmToken ? String(fcmToken) : state.student.fcmToken,
    updatedAt: Date.now(),
  };

  saveState(state);
  console.log("[API] Synced student status:", state.student.status, "lang:", state.student.languageId);
  res.json({ success: true, student: state.student });
});

app.get("/api/messages", (_req, res) => {
  res.json({ messages: state.messages });
});

// Authenticated proxy stream so Android MediaPlayer can play Twilio recordings seamlessly
app.get("/api/recordings/:recordingSid", async (req, res) => {
  const { recordingSid } = req.params;
  const accountSid = process.env.TWILIO_ACCOUNT_SID;
  const authToken = process.env.TWILIO_AUTH_TOKEN;

  if (!accountSid || !authToken) {
    return res.status(500).send("Twilio credentials missing on server");
  }

  const twilioUrl = `https://api.twilio.com/2010-04-01/Accounts/${accountSid}/Recordings/${recordingSid}.mp3`;
  const authHeader = "Basic " + Buffer.from(`${accountSid}:${authToken}`).toString("base64");

  try {
    const response = await fetch(twilioUrl, {
      headers: { Authorization: authHeader },
    });
    if (!response.ok) {
      return res.status(response.status).send("Could not fetch recording");
    }
    res.setHeader("Content-Type", "audio/mpeg");
    const buffer = Buffer.from(await response.arrayBuffer());
    res.send(buffer);
  } catch (err) {
    console.error("[Proxy] Error fetching Twilio recording:", err.message);
    res.status(500).send("Recording stream error");
  }
});

// ---------------------------------------------------------------------------
// Twilio Voice Webhooks (TwiML)
// ---------------------------------------------------------------------------
app.post("/voice/incoming", (req, res) => {
  const twiml = new twilio.twiml.VoiceResponse();
  const baseUrl = resolveBaseUrl(req);
  const profile = getProfileForStudent();
  const busyStatus = state.student.status || "College Class";

  console.log(`[Twilio] Incoming forwarded call from ${req.body.From || "Unknown"} -> Speaking (${profile.ttsLang})`);

  // 1. Speak localized greeting directly on the live cellular voice channel
  twiml.say(
    {
      voice: profile.voice,
      language: profile.ttsLang,
    },
    profile.greeting(busyStatus)
  );

  // 2. Record parent voice message with beep + automatic transcription
  twiml.record({
    action: `${baseUrl}/voice/recording-complete`,
    method: "POST",
    playBeep: true,
    maxLength: 15,
    timeout: 4,
    finishOnKey: "#",
    transcribe: true,
    transcribeCallback: `${baseUrl}/voice/transcription-complete`,
  });

  // Fallback if caller doesn't say anything
  twiml.say(
    {
      voice: profile.voice,
      language: profile.ttsLang,
    },
    profile.closing
  );
  twiml.hangup();

  res.type("text/xml").send(twiml.toString());
});

app.post("/voice/recording-complete", async (req, res) => {
  const twiml = new twilio.twiml.VoiceResponse();
  const profile = getProfileForStudent();
  const baseUrl = resolveBaseUrl(req);

  const callSid = req.body.CallSid || `call_${Date.now()}`;
  const fromNumber = req.body.From || "Parent";
  const recordingSid = req.body.RecordingSid || "";
  const rawRecordingUrl = req.body.RecordingUrl || "";
  const durationSeconds = parseInt(req.body.RecordingDuration || "0", 10) || 0;

  // Prefer server proxy URL if RecordingSid exists (avoids Twilio Basic Auth issues in MediaPlayer)
  const playableUrl = recordingSid
    ? `${baseUrl}/api/recordings/${recordingSid}`
    : rawRecordingUrl
    ? `${rawRecordingUrl}.mp3`
    : "";

  const messageRecord = {
    id: callSid,
    callSid,
    callerDisplay: fromNumber,
    callerNumber: fromNumber,
    transcript: "Voice recording received (transcribing...)",
    recordingUrl: playableUrl,
    durationSeconds,
    studentStatusAtCall: state.student.status,
    timestampMillis: Date.now(),
  };

  state.messages = [messageRecord, ...state.messages.filter((m) => m.callSid !== callSid)].slice(0, 50);
  saveState(state);

  await pushMessageToAndroid(messageRecord);

  // Speak polite closing confirmation to parent and hang up
  twiml.say(
    {
      voice: profile.voice,
      language: profile.ttsLang,
    },
    profile.closing
  );
  twiml.hangup();

  res.type("text/xml").send(twiml.toString());
});

app.post("/voice/transcription-complete", async (req, res) => {
  const callSid = req.body.CallSid || "";
  const transcriptionText = (req.body.TranscriptionText || "").trim();
  const transcriptionStatus = req.body.TranscriptionStatus || "";

  console.log(`[Twilio] Transcription callback for ${callSid}: status=${transcriptionStatus}`);

  if (callSid && transcriptionText) {
    const existing = state.messages.find((m) => m.callSid === callSid);
    if (existing) {
      existing.transcript = transcriptionText;
      saveState(state);
      await pushMessageToAndroid(existing);
    }
  }

  res.sendStatus(200);
});

app.listen(PORT, () => {
  console.log(`SafeCall backend listening on port ${PORT}`);
});
