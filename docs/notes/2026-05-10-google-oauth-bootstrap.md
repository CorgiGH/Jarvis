# Google OAuth Bootstrap — User Runbook

**Purpose:** one-time setup to activate calendar/drive/gmail tools in the Jarvis tutor.
**Who runs this:** you, on your Windows PC (needs a browser for OAuth consent).
**Time required:** ~10 minutes.

---

## Step 1 — Open Google Cloud Console

Go to: https://console.cloud.google.com/apis/credentials

---

## Step 2 — Create (or select) a project

Click the project dropdown at the top. Create a new project named **"Jarvis Personal Tutor"** (or reuse an existing project you own).

---

## Step 3 — Enable the three APIs

In the left menu go to **APIs & Services → Library** and enable each of:
- **Google Calendar API**
- **Google Drive API**
- **Gmail API**

Search by name and click **Enable**.

---

## Step 4 — Create OAuth Client Credentials

Go to **APIs & Services → Credentials** → **+ Create Credentials** → **OAuth client ID**.
- Application type: **Desktop app**
- Name: `jarvis-personal`
- Click **Create**

Download the JSON file that appears. It is named `client_secrets_<id>.json` by default.

---

## Step 5 — Configure OAuth Consent Screen

Go to **APIs & Services → OAuth consent screen**:
- User type: **External** (so you can add yourself as a test user)
- App name: `Jarvis Personal Tutor`
- Publishing status: leave as **Testing** (you are the only user)
- Under **Test users** → **+ Add users** → enter your Gmail address: `amoalexandru5@gmail.com`
- Save

---

## Step 6 — Copy credentials JSON to your PC

Rename the downloaded file to `client_secrets.json` and place it in the `jarvis-kotlin/` project root on your PC.

---

## Step 7 — Run the bootstrap command

Open PowerShell in the `jarvis-kotlin/` directory and run:

```powershell
.\gradlew run --args="google-auth-bootstrap"
```

Or, if you have a built jar:

```powershell
java -jar build/libs/jarvis.jar google-auth-bootstrap
```

The command will:
1. Open your browser to Google's OAuth consent page
2. You grant the three permissions (calendar, drive read-only, gmail compose)
3. Google redirects to `http://localhost:9999/callback`
4. The command prints the token JSON and saves it as `google-token.json`

---

## Step 8 — SCP the token to the VPS

```powershell
scp google-token.json root@46.247.109.91:/opt/jarvis/data/google-token.json
```

Also copy `client_secrets.json` if not already there:

```powershell
scp client_secrets.json root@46.247.109.91:/opt/jarvis/data/client_secrets.json
```

---

## Step 9 — Activate on the VPS

SSH into the VPS and edit `/opt/jarvis/.env`:

```
GWS_ENABLED=1
GOOGLE_TOKEN_PATH=/opt/jarvis/data/google-token.json
GOOGLE_CREDS_PATH=/opt/jarvis/data/client_secrets.json
```

Restart the service:

```bash
systemctl restart jarvis
```

Verify in the Tutor UI: Settings → Google status should show `tokenPresent: true`.

---

## Verification

Check the status endpoint:

```bash
curl -s -b "jarvis_session=<your-sid>" https://your-vps/api/v1/google/status
```

Expected response:

```json
{
  "enabled": true,
  "tokenPresent": true,
  "tokenExpiresAt": "2026-05-10T15:00:00Z",
  "tokenRefreshable": true
}
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `client_secrets.json not found` | Check GOOGLE_CREDS_PATH or place file in project root |
| Browser doesn't open | Copy the URL from terminal manually |
| Callback timeout | Complete consent within 120 seconds of the URL opening |
| `refresh_token missing` | Re-run bootstrap — ensure `prompt=consent` forces re-consent |
| `401 Invalid Credentials` after deploy | Re-run bootstrap and scp a fresh token |
| Token file deleted by ops | Re-run bootstrap and scp again |

---

## Token lifecycle

- Access tokens expire in ~1 hour. The server auto-refreshes using the stored `refresh_token`.
- Refresh tokens do **not** expire unless you revoke access in Google's security settings or the token file is deleted.
- You will only need to re-run bootstrap if: (a) you revoke access in Google's My Account page, or (b) the token file is lost.
