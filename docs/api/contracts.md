# Encore API Contracts

**Milestone:** 1 - Foundation
**Status:** Draft
**Last Updated:** 2026-03-26

## Base URL

```
Production: https://api.encore.app/v1
Development: http://localhost:8080/v1
```

## Authentication

All endpoints except `/auth/google` require Bearer token authentication:

```
Authorization: Bearer <jwt_token>
```

## Endpoints

### Authentication

#### POST /auth/google
Exchange Google identity token for application session.

**Request:**
```json
{
  "id_token": "string",
  "device_info": {
    "platform": "android",
    "app_version": "1.0.0-milestone1",
    "device_id": "uuid (optional, generated if not provided)"
  }
}
```

**Response (200):**
```json
{
  "token": "jwt_token_string",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "display_name": "User Name"
  },
  "device": {
    "id": "uuid",
    "active": true,
    "last_sync_at": "2026-03-26T12:00:00Z"
  },
  "sync_token": "base64_encoded_sync_state"
}
```

**Errors:**
- 401: Invalid Google token
- 409: Another device is active (single-device policy)

---

### Bootstrap

#### GET /bootstrap
Return user profile, active device state, and initial sync data.

**Request Headers:**
```
Authorization: Bearer <token>
```

**Response (200):**
```json
{
  "user": { ... },
  "device": { ... },
  "sync_token": "string",
  "library_snapshot": {
    "songs": [],
    "setlists": [],
    "sets": [],
    "set_entries": []
  }
}
```

---

### Songs

#### POST /songs/import
Import one or more songs from markdown.

**Request:**
```json
{
  "songs": [
    {
      "title": "Song Title",
      "artist": "Artist Name",
      "current_key": "G",
      "markdown_body": "full markdown content",
      "original_import_body": "same or preprocessed"
    }
  ]
}
```

**Response (200):**
```json
{
  "created": [
    {
      "id": "uuid",
      "title": "Song Title",
      "artist": "Artist Name",
      "version": 1,
      "created_at": "timestamp"
    }
  ],
  "duplicates": [
    {
      "title": "Duplicate Song",
      "artist": "Artist",
      "existing_id": "uuid",
      "action": "skipped"
    }
  ]
}
```

**Errors:**
- 400: Invalid markdown or missing required fields

---

#### GET /songs
List all songs for the authenticated user.

**Query Parameters:**
- `search` (optional): Filter by title or artist
- `limit` (optional): Max results (default 100)
- `offset` (optional): Pagination offset

**Response (200):**
```json
{
  "songs": [
    {
      "id": "uuid",
      "title": "string",
      "artist": "string",
      "current_key": "string",
      "version": 1,
      "updated_at": "timestamp"
    }
  ],
  "total": 150
}
```

---

#### GET /songs/:id
Get full song details including markdown body.

**Response (200):**
```json
{
  "id": "uuid",
  "title": "string",
  "artist": "string",
  "current_key": "string",
  "markdown_body": "full content",
  "original_import_body": "string",
  "version": 3,
  "created_at": "timestamp",
  "updated_at": "timestamp"
}
```

---

#### PUT /songs/:id
Update song metadata or markdown body.

**Request:**
```json
{
  "title": "Updated Title (optional)",
  "artist": "Updated Artist (optional)",
  "current_key": "Am (optional)",
  "markdown_body": "updated content (optional)",
  "version": 3
}
```

**Response (200):**
```json
{
  "id": "uuid",
  "version": 4,
  "updated_at": "timestamp"
}
```

**Errors:**
- 409: Version conflict (local version != server version)

---

### Setlists

#### POST /setlists
Create a new setlist.

**Request:**
```json
{
  "name": "Summer Tour 2026"
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "name": "Summer Tour 2026",
  "version": 1,
  "created_at": "timestamp"
}
```

---

#### GET /setlists
List all setlists for the authenticated user.

**Response (200):**
```json
{
  "setlists": [
    {
      "id": "uuid",
      "name": "string",
      "version": 2,
      "set_count": 3,
      "updated_at": "timestamp"
    }
  ]
}
```

---

#### PUT /setlists/:id
Rename or update setlist metadata.

**Request:**
```json
{
  "name": "Updated Name",
  "version": 2
}
```

**Response (200):**
```json
{
  "id": "uuid",
  "name": "Updated Name",
  "version": 3,
  "updated_at": "timestamp"
}
```

**Errors:**
- 409: Version conflict

---

### Sets

#### POST /setlists/:id/sets
Add a new set to a setlist.

**Request:**
```json
{
  "color_token": "blue"
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "setlist_id": "uuid",
  "number": 3,
  "color_token": "blue",
  "created_at": "timestamp"
}
```

---

#### DELETE /sets/:id
Delete a set and renumber subsequent sets.

**Response (204):** No content

---

### Set Entries

#### POST /sets/:id/entries
Add a song to a set.

**Request:**
```json
{
  "song_id": "uuid",
  "position": 0
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "set_id": "uuid",
  "song_id": "uuid",
  "position": 0,
  "created_at": "timestamp"
}
```

---

#### PUT /set-entries/:id
Update position or song reference.

**Request:**
```json
{
  "position": 3
}
```

**Response (200):**
```json
{
  "id": "uuid",
  "position": 3
}
```

---

### Sync

#### POST /sync
Manual sync endpoint: upload local changes, pull remote updates, detect conflicts.

**Request:**
```json
{
  "device_id": "uuid",
  "sync_token": "last_known_sync_token",
  "changes": {
    "songs": [
      {
        "id": "uuid",
        "action": "update",
        "version": 3,
        "data": { "markdown_body": "...", "updated_at": "..." }
      }
    ],
    "setlists": [],
    "sets": [],
    "set_entries": []
  }
}
```

**Response (200):**
```json
{
  "new_sync_token": "updated_sync_token",
  "accepted": {
    "songs": ["uuid1", "uuid2"],
    "setlists": []
  },
  "remote_updates": {
    "songs": [
      {
        "id": "uuid",
        "version": 4,
        "data": { ... }
      }
    ]
  },
  "conflicts": [
    {
      "id": "conflict_uuid",
      "entity_type": "song",
      "entity_id": "song_uuid",
      "local_version": 3,
      "remote_version": 5,
      "local_snapshot": { ... },
      "remote_snapshot": { ... }
    }
  ]
}
```

**Errors:**
- 409: Device no longer active (another device activated)
- 400: Invalid sync token

---

### Conflicts

#### POST /conflicts/:id/resolve
Apply user-selected resolution for a conflict.

**Request:**
```json
{
  "resolution": "local" | "remote"
}
```

**Response (200):**
```json
{
  "id": "conflict_uuid",
  "status": "resolved_local",
  "resolved_at": "timestamp",
  "new_entity_version": 6
}
```

---

## Error Response Format

All errors follow this structure:

```json
{
  "error": {
    "code": "CONFLICT",
    "message": "Version mismatch detected",
    "details": {
      "expected_version": 3,
      "current_version": 5
    }
  }
}
```

## Data Types

- **UUID:** Standard UUID v4 format
- **Timestamp:** ISO 8601 format (e.g., `2026-03-26T12:00:00Z`)
- **JWT Token:** Standard Bearer token (expires in 30 days)

---

**Next Steps:**
- Generate OpenAPI/Swagger spec
- Implement backend endpoints
- Create Retrofit interfaces for Android client
