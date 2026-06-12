# Grocery Alert — Private Client-Server App Ecosystem

Mom sends a grocery alert → Dad's phone rings like an alarm (even on silent).

> ⚠️ **No third-party push services.** Everything runs over your own WebSocket server.

---

## 1. Server Setup

### Option A: Deploy on Render (Free — works from anywhere)

1. Push the project to a GitHub repo
2. Go to [render.com](https://render.com) → **New Web Service** → connect your repo
3. Render auto-detects `render.yaml` — just click **Apply**
4. Your server will be live at `wss://grocery-alert-server.onrender.com`

The apps are already configured to use that URL. To change it later, update `SERVER_URL` in all Kotlin files.

### Option B: Run locally

```bash
pip install websockets
python server.py
```

The server listens on `0.0.0.0:8765`.

---

## 3. Android Build Dependencies

Add these to your app-level `build.gradle` (both apps):

```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.core:core-ktx:1.12.0'
}
```

---

## 4. Dad App — Required Permissions (Runtime)

On first launch the user must grant:

1. **Do Not Disturb access** — Settings > Notifications > Do Not Disturb > Allowed apps > *Grocery Alert Dad*  
   (This allows the alarm sound to play even when the phone is on Silent / DND mode.)

2. **Notifications** — Allow alerts and full-screen intents.

No other runtime permissions are needed — all other permissions are declared in the manifest and auto-granted at install time.

---

## 5. File Overview

### `server.py`
Maintains a registry of connected MOM and DAD WebSocket clients. Routes MOM's grocery payload to DAD in real time.

### Dad App (`com.groceryalert.dad`)
| File | Purpose |
|---|---|
| `DadApplication.kt` | Creates notification channels (`grocery_alarm_channel`, `websocket_channel`) |
| `DadMainActivity.kt` | Launcher activity — starts the foreground service, then finishes |
| `AlarmWebSocketService.kt` | **Persistent foreground service.** OkHttp WebSocket with auto-reconnect (exponential backoff 1s → 60s). On receiving a MOM message, fires a full-screen intent to `AlarmActivity`. |
| `AlarmActivity.kt` | Full-screen takeover. Turns screen on, shows over lock screen, plays looping alarm sound via `MediaPlayer` (using the system default alarm ringtone). "Got It" button stops everything. |
| `activity_alarm.xml` | Red full-screen layout with item text and dismiss button |
| `activity_dad_main.xml` | Minimal launcher layout |

### Mom App (`com.groceryalert.mom`)
| File | Purpose |
|---|---|
| `MainActivity.kt` | UI with `EditText` + "Alert Dad" button |
| `MomWebSocketClient.kt` | OkHttp WebSocket client. Connects, sends `{"role":"MOM","item":"..."}`, closes on acknowledgment. 15-second timeout fallback. |
| `activity_main.xml` | Clean input layout |

---

## 6. How It Works (Data Flow)

```
[Mom's Phone]
  │  User types "milk, eggs" & taps "Alert Dad"
  ▼
MomWebSocketClient connects → sends JSON → waits for ack → closes
  │
  ▼
[Python Server]  (ws://your-server:8765)
  │  Receives from MOM
  │  Looks up DAD in client registry
  ▼
  └─ If DAD is online: forwards the JSON payload
  └─ If DAD is offline: sends error back to MOM
  │
  ▼
[Dad's Phone]  (AlarmWebSocketService — always connected)
  │  WebSocketListener.onMessage() fires
  ▼
AlarmWebSocketService:
  1. Creates a full-screen intent notification (bypasses DND)
  2. Directly starts AlarmActivity (fallback)
  3. Acquires a WAKE_LOCK to turn on the screen
  │
  ▼
AlarmActivity:
  1. setTurnScreenOn(true) + setShowWhenLocked(true)
  2. Window flags: TURN_SCREEN_ON, SHOW_WHEN_LOCKED, KEEP_SCREEN_ON, DISMISS_KEYGUARD
  3. MediaPlayer plays system alarm sound (looping)
  4. Red full-screen shows "Buy: milk, eggs"
  5. Dad taps "Got It" → sound stops, notification cleared, activity closes
```

---

## 7. Project Structure

```
grocery-alert/
├── server.py
├── dad-app/
│   └── app/src/main/
│       ├── java/com/groceryalert/dad/
│       │   ├── DadApplication.kt
│       │   ├── DadMainActivity.kt
│       │   ├── AlarmWebSocketService.kt
│       │   └── AlarmActivity.kt
│       ├── res/layout/
│       │   ├── activity_alarm.xml
│       │   └── activity_dad_main.xml
│       └── AndroidManifest.xml
├── mom-app/
│   └── app/src/main/
│       ├── java/com/groceryalert/mom/
│       │   ├── MainActivity.kt
│       │   └── MomWebSocketClient.kt
│       ├── res/layout/
│       │   └── activity_main.xml
│       └── AndroidManifest.xml
└── README.md
```

---

## 8. Testing

```bash
# Terminal 1 — Start server
python server.py

# Terminal 2 — Simulate Mom (or use Mom app on device)
python -c "
import asyncio, websockets, json
async def test():
    async with websockets.connect('ws://localhost:8765') as ws:
        await ws.send(json.dumps({'role':'MOM','item':'test'}))
        print(await ws.recv())
asyncio.run(test())
"
```

---

## 9. Troubleshooting

| Symptom | Fix |
|---|---|
| "DAD is offline" error in Mom app | Dad's app hasn't been launched (or service was killed). Open Dad's app at least once. Check server logs. |
| Alarm doesn't ring on silent / DND | Dad must grant **Do Not Disturb access** in system settings. See §4 above. |
| Screen doesn't turn on | On Android 13+ some OEMs restrict `setShowWhenLocked`. The `WAKE_LOCK` and window flags are fallbacks. |
| WebSocket disconnects frequently | Ensure both phones and server are on the same stable Wi-Fi network. The service auto-reconnects with backoff. |
