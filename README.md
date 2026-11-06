# KnowledgeForge

A collaborative knowledge management platform built with Java Spring Boot, MongoDB Atlas, and a plain HTML/CSS/JS frontend.

KnowledgeForge allows teams to create shared workspaces, write and version documents, manage members, and track activity — all in one place.

---

## Live Demo

Deployed on Render:  
**https://knowledgeforge-ai-1.onrender.com**

---

## Screenshots

| Login                                | Dashboard                                    | Team                               |
|--------------------------------------|----------------------------------------------|------------------------------------|
| ![Login](Docs/screenshots/login.png) | ![Dashboard](Docs/screenshots/dashboard.png) | ![Team](Docs/screenshots/team.png) |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1 |
| Security | Spring Security (session-based auth) |
| Database | MongoDB Atlas |
| Frontend | Plain HTML + CSS + JavaScript (no framework) |
| Markdown | marked.js (CDN) |
| AI | Groq API (OpenAI-compatible), model `openai/gpt-oss-20b` |
| Deployment | Render |

---

## Features

### Authentication
- Register with full name, email, and password
- Passwords hashed with BCrypt
- Session-based login and logout
- All API endpoints protected; returns 401 if not authenticated

### Teams
- Create teams with name and description
- Invite members via a unique invite link
- Join request workflow: user requests → admin approves or rejects
- Role-based membership: ADMIN, EDITOR, VIEWER
- Admin can update roles and remove members

### Spaces
- Create spaces inside a team
- Each space belongs to exactly one team

### Documents
- Create, read, update, and delete documents inside a space
- Markdown content with live preview in the editor
- Document status: DRAFT or PUBLISHED
- Tagging system
- Full version history: every save creates a snapshot
- Restore any previous version
- Export a document as Markdown (.md) or PDF (via the browser's print dialog)

### Comments
- Add comments to any document
- Delete your own comments

### Attachments
- Attach files (PDF, images, Office docs, text; up to 5 MB, stored in MongoDB GridFS) or links to documents
- View and delete attachments

### AI Assistant
- **Summarize** a document into a few bullet points (cached until the document is edited)
- **Ask this space**: ask a question and get an answer based only on that space's documents, with clickable `[n]` sources
- Answers are cached per space and question until the space's documents change, so repeats cost nothing
- Available to every team member, including viewers
- Limits: 10 AI requests per user per day and a daily token budget for the whole site (both configurable)
- **Privacy:** document text is sent to an AI provider (Groq) when you use these features

### Search
- Global search across the documents, spaces, and teams you belong to
- Filter by type, tag, and document status; results show the matched excerpt, tags, and team name

### Dashboard
- Summary counts: total teams, spaces, and documents
- Recent documents table with status badges
- Recent activity feed
- Sidebar showing all your teams

### Profile
- View and update your name and profile picture
- View your team affiliations and roles

### Activity Logging
- Every document create, update, delete, and restore is logged
- Displayed in the dashboard activity feed

---

## Project Structure

```
src/
├── main/
│   ├── java/com/knowledgeforge/knowledgeforge/
│   │   │   # one package per feature: Entity, Repository, Service, Controller, DTOs
│   │   ├── auth/              # register, login, session user
│   │   ├── user/              # profile, profile picture
│   │   ├── team/              # teams, members, invites, join requests
│   │   ├── space/
│   │   ├── documentPkg/       # documents
│   │   ├── documentVersion/   # version history and restore
│   │   ├── comment/
│   │   ├── attachment/        # links and file uploads (GridFS)
│   │   ├── search/
│   │   ├── dashboard/
│   │   ├── activity/          # activity log
│   │   ├── ai/                # summaries and "Ask this space" (Groq)
│   │   ├── SecurityConfig.java
│   │   └── GlobalExceptionHandler.java
│   └── resources/
│       ├── static/            # Frontend: login, register, dashboard, team, space,
│       │                      # document, search, profile, join (.html), app.js, style.css
│       └── application.properties
└── test/                      # JUnit + Mockito unit tests (no database needed)
```

---

## API Endpoints

### Auth
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Log in |
| POST | `/logout` | Log out |

### User
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/me` | Get current user profile |
| PUT | `/api/me` | Update your name |
| POST | `/api/me/picture` | Upload a profile picture (png, jpg, gif, webp, max 2 MB) |
| DELETE | `/api/me/picture` | Remove your profile picture |
| GET | `/api/users/{id}/picture` | Get a user's profile picture |
| GET | `/api/me/teams` | Get current user's team memberships with roles |

### Teams
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/teams` | List all teams the user belongs to |
| POST | `/api/teams` | Create a new team |
| GET | `/api/teams/{id}` | Get team details + members |
| POST | `/api/teams/{id}/invite` | Generate an invite link |
| GET | `/api/teams/{id}/join-requests` | List pending join requests (admin) |
| POST | `/api/teams/{id}/join-requests/{rid}/accept` | Accept a join request |
| POST | `/api/teams/{id}/join-requests/{rid}/reject` | Reject a join request |
| PUT | `/api/teams/{id}/members/{uid}/role` | Update a member's role |
| DELETE | `/api/teams/{id}/members/{uid}` | Remove a member |

### Spaces
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/teams/{id}/spaces` | List spaces in a team |
| POST | `/api/teams/{id}/spaces` | Create a space |
| GET | `/api/spaces/{id}` | Get space details |

### Documents
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/spaces/{id}/documents` | List documents in a space |
| POST | `/api/spaces/{id}/documents` | Create a document |
| GET | `/api/documents/{id}` | Get a document |
| PUT | `/api/documents/{id}` | Update a document |
| DELETE | `/api/documents/{id}` | Delete a document |
| GET | `/api/documents/{id}/versions` | List version history |
| POST | `/api/documents/{id}/versions/{vid}/restore` | Restore a version |

### Comments
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/documents/{id}/comments` | List comments on a document |
| POST | `/api/documents/{id}/comments` | Add a comment |
| DELETE | `/api/comments/{id}` | Delete a comment |

### Attachments
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/documents/{id}/attachments` | List attachments |
| POST | `/api/documents/{id}/attachments` | Add an attachment link |
| POST | `/api/documents/{id}/attachments/upload` | Upload a file (multipart `file`, editors only) |
| GET | `/api/attachments/{id}/download` | Download a file |
| DELETE | `/api/attachments/{id}` | Delete an attachment (and its stored file) |

### AI
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/documents/{id}/ai/summary` | Summarize a document (team members) |
| POST | `/api/spaces/{id}/ai/ask` | Ask a question about a space, body `{"question": "..."}` (team members) |

### Search & Dashboard
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/search?q=&type=&tag=&status=` | Search documents, spaces and teams (`type`: all, documents, spaces, teams) |
| GET | `/api/dashboard` | Get dashboard summary |

---

## How to Run Locally

### Prerequisites
- Java 17+
- Maven
- MongoDB Atlas account (free tier is fine)

### Steps

1. **Clone the repository**
```bash
git clone https://github.com/Lut4Sayem/knowledgeforge-ai.git
cd knowledgeforge-ai
```

2. **Configure MongoDB**  
   Set the `SPRING_DATA_MONGODB_URI` environment variable to your Atlas connection string (the app reads it in `application.properties`):
```bash
export SPRING_DATA_MONGODB_URI="mongodb+srv://username:password@your-cluster.mongodb.net/knowledgeforge"
```

   *(Optional)* To enable the AI assistant, also set a [Groq](https://console.groq.com) API key. Without it the app works normally and the AI buttons are hidden:
```bash
export AI_BASE_URL="https://api.groq.com/openai/v1"
export AI_MODEL="openai/gpt-oss-20b"
export AI_API_KEY="your-groq-key"
# optional: AI_USER_DAILY_LIMIT (default 10), AI_SITE_DAILY_TOKENS (default 180000)
```
Values must have no quotes, spaces or trailing characters.

3. **Run the application**
```bash
./mvnw spring-boot:run
```

4. **Open in browser**
```
http://localhost:8080/login.html
```

---

## Deployment (Render)

1. Push your code to GitHub.
2. Create a new **Web Service** on [Render](https://render.com).
3. Set the build command: `./mvnw clean package -DskipTests`
4. Set the start command: `java -jar target/*.jar`
5. Add environment variable: `SPRING_DATA_MONGODB_URI` = Atlas connection string.
6. *(Optional)* For the AI assistant add `AI_BASE_URL`, `AI_MODEL` and `AI_API_KEY` (see above).

---

## Known Limitations

- No real-time collaboration (changes require a page refresh to see others' edits).
- Uploaded files are not scanned for malware (no scanning service on the free hosting tier). Uploads are limited to an allowlist of file types, 5 MB each, and are always served as downloads.
- Search is a plain keyword (substring) match with no ranking.
- The AI assistant finds documents by keywords, not meaning, so a question worded very differently from the documents may get "I don't know". It relies on a free API tier, so heavy use can hit the provider's rate limits.

---

## Future Enhancements

- Real-time collaborative editing via WebSockets
- Rich text editor (TipTap or Quill)
- Email notifications for invite and approval events
- Move file storage from MongoDB GridFS to S3 or Cloudflare R2
- Document-level permissions (not just team-level)
- Date-range filter and ranking for search
- Two-factor authentication (2FA)
- Semantic (embedding) search for the AI assistant
- Streaming AI answers

---

## Author

**MD. LUTFUR RAHMAN**
*BRAC UNIVERSITY*

---

## AI Usage

AI tools were used during development as a learning and debugging assistant.  
See [`Docs/AI_DECLARATION.txt`](Docs/AI_DECLARATION.txt) for the full declaration.

---

## License

This project was created for academic and learning purposes as part of CSE391 course.