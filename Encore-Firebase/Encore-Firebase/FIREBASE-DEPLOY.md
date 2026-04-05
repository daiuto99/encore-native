# Firebase deploy steps

## 1. Create your environment file

Duplicate `.env.example` to `.env`.

## 2. Install dependencies

```bash
npm install
```

## 3. Run locally

```bash
npm run dev
```

## 4. Firebase Hosting setup

```bash
firebase login
firebase use --add encore-cloud-leo-2026-8a467
firebase init hosting
```

Use these answers:
- existing project: `encore-cloud-leo-2026-8a467`
- public directory: `dist`
- configure as a single-page app: `Yes`
- set up automatic builds and deploys with GitHub: `No`
- overwrite `index.html`: `No`

## 5. Build and deploy

```bash
npm run build
firebase deploy --only hosting
```

## Hosting URL

After deploy, the site should be available at:

`https://encore-cloud-leo-2026.web.app`
