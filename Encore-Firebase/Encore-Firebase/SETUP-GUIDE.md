# Encore Desktop Manager - Setup Guide

This guide is written for a technical product person, not a software engineer.

## What you need

You need just two things:

1. **Node.js** installed on your computer
2. A **Chromium-based browser** like Chrome or Edge

## Step 1: Install Node.js

Go to the official Node.js website and install the **LTS** version.

Once installed, open Terminal and run:

```bash
node -v
npm -v
```

If both show version numbers, you are ready.

## Step 2: Open the project folder

Unzip the project package.

Open Terminal and move into the project folder:

```bash
cd /path/to/encore-desktop-manager
```

## Step 3: Install the app packages

Run:

```bash
npm install
```

This loads the pieces the app needs.

## Step 4: Start the app

Run:

```bash
npm run dev
```

Terminal will show a local address that looks something like this:

```bash
http://localhost:5173/
```

Open that address in Chrome or Edge.

## Step 5: Load your song library

Inside the app:

1. Click **Select Library Folder**
2. Choose the main folder that contains your markdown song files
3. The app will scan that folder and all folders inside it

## Step 6: Save changes back to your files

If you edit a song in the Harmony Editor and click **Save Song**, the app will write the updated markdown back to the original file.

That only works if:

- you originally loaded the folder through the app
- you are using a supported browser

## Optional: Build a cleaner release version

If you want a production build instead of the live development version, run:

```bash
npm run build
```

That creates a `dist` folder.

## If something does not work

### The folder button does nothing
Use Chrome or Edge. Safari and Firefox are the most likely cause here.

### The app opens but songs do not show up
Make sure your song files end in `.md`.

### Saving does not write to disk
Make sure you selected the folder through the app first, then edited the file.

### Terminal says a command is not found
Node.js is probably not installed correctly.

## Best first-use workflow

1. Duplicate a small test song library
2. Open that test copy in the app
3. Make sure scan, edit, and save all behave the way you want
4. Then move to your full real library


## Android set export

Use **Setlist Architect → Export active set to /sets**. This writes one `.encore.json` file into the selected library folder's `/sets` folder using the strict Encore set format: top-level `version`, `name`, and ordered `songs`; each song includes `title`, `artist`, optional `displayKey`, and `markdownBody`.
