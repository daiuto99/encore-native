# Encore Desktop Manager — Firebase Cloud Companion

This version of Encore Desktop Manager is wired for Firebase Hosting, Firebase Authentication, and Firebase Storage while still preserving a Local Mode fallback.

## What changed

- Cloud Mode uses Google Sign-In.
- Cloud Mode reads and writes markdown files from the `encore-cloud-leo-2026-songs` bucket.
- Set exports write strict `.encore.json` files into `/sets` in that same bucket.
- Local Mode still uses the browser's local file picker and writes directly to files on disk.

## Required setup

1. Copy `.env.example` to `.env`.
2. Verify the Firebase values are correct.
3. Run:

```bash
npm install
npm run dev
```

## Deploy

```bash
npm install -g firebase-tools
firebase login
firebase use --add encore-cloud-leo-2026-8a467
firebase init hosting
npm run build
firebase deploy --only hosting
```

When `firebase init hosting` prompts you:
- use existing project: `encore-cloud-leo-2026-8a467`
- public directory: `dist`
- single-page app: `Yes`
- GitHub automatic deploys: `No`
- overwrite `index.html`: `No`


This corrected package fixes earlier App.tsx and CloudLibraryService syntax issues.
