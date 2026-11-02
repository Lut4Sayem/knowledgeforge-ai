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

### Comments
- Add comments to any document
- Delete your own comments

### Attachments
- Add link-based attachments (name + URL) to documents
- View and delete attachments

### Search
- Global search across all documents in teams you belong to
- Results show title, excerpt, tags, space, and team name

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
│   ├── java/com/knowledgeforge/
│   │   ├── controller/        # REST API controllers
│   │   │   ├── AuthController.java
│   │   │   ├── UserController.java
│   │   │   ├── TeamController.java
│   │   │   ├── SpaceController.java
│   │   │   ├── DocumentController.java
│   │   │   ├── CommentController.java
│   │   │   ├── AttachmentController.java
│   │   │   ├── SearchController.java
│   │   │   └── DashboardController.java
│   │   ├── model/             # MongoDB document models
│   │   │   ├── User.java
│   │   │   ├── Team.java
│   │   │   ├── TeamMembership.java
│   │   │   ├── Space.java
│   │   │   ├── Document.java
│   │   │   ├── Comment.java
│   │   │   ├── Attachment.java
│   │   │   ├── DocumentVersion.java
│   │   │   ├── JoinRequest.java
│   │   │   └── ActivityLog.java
│   │   ├── repository/        # Spring Data MongoDB repositories
│   │   └── config/            # Spring Security configuration
│   └── resources/
│       ├── static/            # Frontend HTML/CSS/JS files
│       │   ├── login.html
│       │   ├── register.html
│       │   ├── dashboard.html
│       │   ├── team.html
│       │   ├── space.html
│       │   ├── document.html
│       │   ├── search.html
│       │   ├── profile.html
│       │   ├── join.html
│       │   └── style.css
│       └── application.properties
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
| PUT | `/api/me` | Update name and profile picture |
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
| DELETE | `/api/attachments/{id}` | Delete an attachment |

### Search & Dashboard
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/search?q=keyword` | Search documents |
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

---

## Known Limitations

- No real-time collaboration (changes require a page refresh to see others' edits).
- Attachments are link-based only; actual file upload is not supported.
- Search is keyword-based with no advanced filters.

---

## Future Enhancements

- Real-time collaborative editing via WebSockets
- Rich text editor (TipTap or Quill)
- Email notifications for invite and approval events
- Actual file upload via cloud storage (AWS S3 or Cloudinary)
- Document-level permissions (not just team-level)
- Advanced search with filters (status, tag, date range)
- Two-factor authentication (2FA)
- PDF and Markdown export
- Integration AI agent and many more

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