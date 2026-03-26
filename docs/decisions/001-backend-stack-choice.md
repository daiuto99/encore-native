# ADR 001: Backend Stack Choice - Kotlin Ktor

**Status:** Accepted
**Date:** 2026-03-26
**Decision Maker:** Project Team
**Milestone:** 1 - Foundation

## Context

We need to select a backend technology stack for the Encore API server. The backend will provide:
- User authentication (Google OAuth)
- Song and setlist CRUD operations
- Manual sync orchestration
- Conflict detection and resolution
- PostgreSQL database access

The Technical Specification (Section 2) recommends:
> Backend API: Kotlin Ktor, Node/TypeScript, or equivalent clean REST/JSON API. Choose based on developer skill; keep contracts explicit.

The project stakeholder expressed a preference for **Kotlin** to keep the entire project in the same language family as the Android client.

## Decision

We will use **Kotlin with Ktor framework** for the backend API.

## Rationale

### Advantages of Kotlin Ktor

1. **Language Consistency**
   - Same language as Android client (Kotlin)
   - Shared data models possible (future opportunity for Kotlin Multiplatform)
   - Single language reduces context switching
   - Easier for developers proficient in Kotlin

2. **Type Safety**
   - End-to-end type safety from client to server
   - Shared serialization models via kotlinx.serialization
   - Compile-time contract validation

3. **Lightweight and Modern**
   - Ktor is lightweight, async-first framework
   - Built on Kotlin coroutines for efficient concurrency
   - Clean, minimal DSL for routing and API definition
   - Good performance characteristics

4. **PostgreSQL Integration**
   - Excellent database libraries: Exposed (ORM) or Ktorm
   - Type-safe SQL queries with Kotlin DSL
   - Strong ecosystem support

5. **Deployment Flexibility**
   - Compiles to JVM bytecode
   - Can run on any JVM-compatible host
   - Good Docker support
   - Compatible with modern cloud platforms (Fly.io, Google Cloud Run, AWS ECS)

### Disadvantages (Acknowledged)

1. **Smaller Ecosystem**
   - Ktor is less mature than Express.js or Spring Boot
   - Fewer third-party plugins and middleware
   - Smaller community and fewer Stack Overflow answers

2. **Learning Curve**
   - Team may be more familiar with Node.js or Spring Boot
   - Less boilerplate means more custom implementation

3. **Debugging Tools**
   - Fewer mature APM and monitoring integrations compared to Node.js

### Alternatives Considered

#### Option A: Node.js with TypeScript + Express
**Pros:**
- Extremely mature ecosystem
- Abundant libraries and middleware
- Large community and extensive documentation
- Fast development for simple REST APIs

**Cons:**
- Different language from Android (context switching)
- Less type safety (even with TypeScript)
- No shared models with Android client
- Async error handling can be tricky

**Verdict:** Strong option, but loses language consistency benefit.

---

#### Option B: Spring Boot (Kotlin)
**Pros:**
- Extremely mature and battle-tested
- Rich ecosystem of Spring libraries
- Enterprise-grade features
- Strong PostgreSQL support

**Cons:**
- Heavier framework (longer startup times, larger memory footprint)
- More boilerplate than Ktor
- Overkill for a simple REST API in V1
- Deployment complexity

**Verdict:** Too heavy for our needs. Ktor is sufficient.

---

#### Option C: Go (Gin or Echo framework)
**Pros:**
- Excellent performance
- Simple deployment (single binary)
- Strong concurrency model
- Growing ecosystem

**Cons:**
- Different language from Android
- No shared models
- Less familiar to Kotlin developers

**Verdict:** Performance not a bottleneck; language consistency more valuable.

---

## Implementation Plan

### Milestone 1 (Foundation)
- Set up basic Ktor project structure
- Configure PostgreSQL connection with Exposed ORM
- Implement health check endpoint (`/health`)
- Document project setup in backend README

### Milestone 2 (Core Library)
- Implement authentication endpoints (`POST /auth/google`)
- Implement song CRUD endpoints
- Implement import endpoint (`POST /songs/import`)

### Milestone 4 (Sync)
- Implement sync endpoint (`POST /sync`)
- Implement conflict detection logic
- Implement conflict resolution endpoint (`POST /conflicts/:id/resolve`)

### Technology Stack Details

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 1.9+ |
| Framework | Ktor | 2.3+ |
| Database | PostgreSQL | 15+ |
| ORM | Exposed | 0.46+ |
| Serialization | kotlinx.serialization | 1.6+ |
| Auth | OAuth via Google SDK | Latest |
| HTTP Client (for Google OAuth) | Ktor Client | 2.3+ |
| Testing | JUnit 5 + Ktor Test | Latest |

### Project Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/encore/api/
│   │   │       ├── Application.kt
│   │   │       ├── auth/
│   │   │       │   ├── AuthRoutes.kt
│   │   │       │   └── AuthService.kt
│   │   │       ├── songs/
│   │   │       │   ├── SongRoutes.kt
│   │   │       │   └── SongService.kt
│   │   │       ├── setlists/
│   │   │       │   ├── SetlistRoutes.kt
│   │   │       │   └── SetlistService.kt
│   │   │       ├── sync/
│   │   │       │   ├── SyncRoutes.kt
│   │   │       │   └── SyncService.kt
│   │   │       ├── conflicts/
│   │   │       │   ├── ConflictRoutes.kt
│   │   │       │   └── ConflictService.kt
│   │   │       ├── common/
│   │   │       │   ├── Database.kt
│   │   │       │   └── Serialization.kt
│   │   │       └── models/
│   │   │           ├── User.kt
│   │   │           ├── Song.kt
│   │   │           ├── Setlist.kt
│   │   │           └── SyncPayload.kt
│   │   └── resources/
│   │       └── application.conf
│   └── test/
│       └── kotlin/
│           └── com/encore/api/
│               └── [test files]
├── build.gradle.kts
└── README.md
```

## Consequences

### Positive
- Consistent Kotlin codebase across Android and backend
- Type-safe API contracts
- Modern, async-first architecture
- Lightweight and efficient
- Future potential for Kotlin Multiplatform shared code

### Negative
- Smaller community than Node.js/Express
- Less middleware available out-of-the-box
- Team may need to implement more custom solutions
- Fewer mature monitoring/APM integrations

### Mitigation for Negatives
- Document all custom implementations clearly
- Keep backend simple and focused (avoid over-engineering)
- Use well-established libraries (Exposed, kotlinx.serialization)
- Plan for migration path if Ktor becomes a blocker (unlikely)

## Review and Revision

This decision will be reviewed at the end of Milestone 4 (Sync implementation). If Ktor proves to be a significant obstacle, we will document the issues and consider alternatives.

**Review Date:** End of Milestone 4
**Revision History:** None yet

---

**References:**
- Ktor Documentation: https://ktor.io/
- Exposed ORM: https://github.com/JetBrains/Exposed
- Technical Specification: `docs/03_Technical_Specification.md` (Section 2)
