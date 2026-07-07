# Real-Time Collaborative Project Tracker

A full-stack project tracker built with Spring Boot, React, PostgreSQL, Redis, WebSockets, and JWT-based role access control.

## Stack

### Backend
- Spring Boot 3
- Spring Security + JWT
- Spring WebSocket (STOMP)
- Spring Data JPA
- PostgreSQL
- Redis pub/sub hook

### Frontend
- React 18
- Redux Toolkit
- Tailwind CSS
- shadcn-style local UI components
- SockJS + STOMP client

### DevOps / automation
- Docker Compose for PostgreSQL + Redis
- GitHub issue webhook endpoint
- GitHub Actions starter CI workflow

## Features
- Register and login with JWT auth
- Role-based permissions for `ADMIN`, `MEMBER`, and `VIEWER`
- Create, move, update, and delete tasks
- Real-time board sync across browser tabs using WebSockets
- GitHub issue webhook creates TODO tasks automatically
- Seeded admin user for instant demo access

## Default demo login
- Username: `admin`
- Password: `admin123`

## Project structure

```text
rt-project-tracker/
├── backend/
│   ├── docker-compose.yml
│   ├── pom.xml
│   └── src/main/java/com/example/tracker
├── frontend/
│   ├── package.json
│   └── src/
└── .github/workflows/ci.yml
```

## Prerequisites
- Java 17 or newer
- Maven 3.9+ or use the included Maven wrapper `./mvnw`
- Node.js 18+ and npm
- Docker Desktop or Docker Engine

## How to run locally

### 1) Start PostgreSQL and Redis
Open a terminal:

```bash
cd backend
docker compose up -d
```

This starts:
- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`

You can verify with:

```bash
docker compose ps
```

### 2) Start the Spring Boot backend
Open a second terminal:

```bash
cd backend
chmod +x mvnw
./mvnw spring-boot:run
```

Backend runs at:

```text
http://localhost:8080
```

Useful backend endpoints:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/tasks`
- `POST /api/tasks`
- `PUT /api/tasks/{id}`
- `PUT /api/tasks/{id}/move`
- `DELETE /api/tasks/{id}`
- `POST /api/webhooks/github`
- WebSocket endpoint: `/ws`
- Health check: `GET /actuator/health`

> Note: the backend root URL is not the UI. Open the frontend on port `5173`, not `8080`.

### 3) Start the React frontend
Open a third terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at:

```text
http://localhost:5173
```

### 4) Login and test live updates
- Open `http://localhost:5173`
- Login with `admin / admin123`
- Create a task
- Open another browser tab and log in again to verify live task updates

## Quick troubleshooting

### Backend starts but `localhost:8080` says access denied
That is expected. The backend is an API server protected by Spring Security. Use:
- `http://localhost:5173` for the app UI
- `http://localhost:8080/actuator/health` for a backend health check

### Login returns 404
That usually means you started a different backend project, not this one. This project expects:
- frontend base URL: `http://localhost:8080/api`
- login endpoint: `POST /api/auth/login`

Quick check:

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Docker is running but backend cannot connect to DB
Make sure the containers are up:

```bash
cd backend
docker compose ps
```

If needed, inspect logs:

```bash
docker compose logs postgres
docker compose logs redis
```

## Example GitHub webhook payload
Send this to create a task from a GitHub issue event:

```bash
curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -d '{
    "action": "opened",
    "issue": {
      "title": "Add drag and drop for Kanban cards",
      "body": "Implement drag and drop movement across columns.",
      "html_url": "https://github.com/example/repo/issues/12",
      "user": {
        "login": "octocat"
      }
    }
  }'
```

## Build for production

### Backend jar
```bash
cd backend
./mvnw clean package
```

### Frontend static build
```bash
cd frontend
npm install
npm run build
```

## Notes
- This is a strong MVP matching your resume project story and interview demo use case.
- The frontend uses shadcn-inspired local components for a clean dashboard look without requiring the shadcn CLI setup.
- A GitHub Actions starter workflow is included at `.github/workflows/ci.yml`.
# Real-Time-Project-Tracker
