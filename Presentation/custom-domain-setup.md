# Custom Domain Setup: encore.sonicink.space → Firebase Hosting

## Goal
Point `encore.sonicink.space` to the Encore Firebase web app currently live at:
`https://encore-cloud-leo-2026-8a467.web.app`

---

## Step 1 — Add the Custom Domain in Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select project **encore-cloud-leo-2026**
3. In the left sidebar: **Build → Hosting**
4. Click **Add custom domain**
5. Enter: `encore.sonicink.space`
6. Click **Continue**

Firebase will display two sets of DNS records to add:
- A **TXT record** — used to verify you own the domain
- An **A record** (or CNAME) — used to point traffic to Firebase servers

**Leave this browser tab open** — you'll copy the values into your DNS provider next.

---

## Step 2 — Add DNS Records at Your DNS Provider

Log in to wherever `sonicink.space` DNS is managed (Cloudflare, Namecheap, etc.) and add the records Firebase gave you.

### TXT record (ownership verification)
| Type | Name / Host | Value |
|------|-------------|-------|
| TXT  | `encore` (or `encore.sonicink.space`) | _(value from Firebase)_ |

### A records (traffic routing)
Firebase typically provides two A record IPs:
| Type | Name / Host | Value |
|------|-------------|-------|
| A    | `encore`    | _(IP 1 from Firebase)_ |
| A    | `encore`    | _(IP 2 from Firebase)_ |

> **Cloudflare users:** Set the A records to **DNS only (grey cloud)**, not proxied, at least initially. Firebase manages its own SSL and the proxied mode can interfere with certificate provisioning.

---

## Step 3 — Verify Ownership in Firebase

1. Back in the Firebase Console tab, click **Verify**
2. Firebase checks for the TXT record — this usually passes within a few minutes
3. If it fails, wait 5–10 minutes and try again (DNS can be slow to propagate)

---

## Step 4 — Wait for SSL Certificate

Once ownership is verified, Firebase automatically provisions a free SSL certificate via Let's Encrypt.

- Status will show **Certificate provisioning** in the Hosting dashboard
- This typically completes within **5–15 minutes**
- When complete, status changes to **Connected**

---

## Step 5 — Test It

Open a browser and navigate to:
```
https://encore.sonicink.space
```

You should see the Encore web manager. Both the old URL and the new custom domain will work — Firebase keeps both active.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| TXT verification keeps failing | DNS hasn't propagated yet — wait 10–30 min and retry |
| Cloudflare showing SSL errors | Switch A records to DNS-only (grey cloud) mode |
| Site loads but shows SSL warning | Certificate still provisioning — wait and reload |
| `encore.sonicink.space` resolves but shows wrong site | Check A record values match exactly what Firebase provided |

---

## Notes

- No code changes or redeployment needed
- Firebase Hosting supports multiple custom domains on the same project if needed later
- The `.web.app` URL remains active — nothing breaks for existing links
