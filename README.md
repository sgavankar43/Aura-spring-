# Aura — Distributed Feature Flag Platform (Spring Boot & Supabase)

A high-performance feature flag and remote configuration management platform. Built with a modern **React + Vite + TypeScript** frontend and an enterprise-grade **Spring Boot 3 (Java 21)** backend backed by **PostgreSQL (Supabase)**.

---

## 🏗️ Architecture & Project Structure

```
MEGA/
├── client/                     # React 18 + Vite + TypeScript + Tailwind CSS Frontend
│   ├── src/                    # Pages, components, hooks, auth contexts
│   ├── package.json            # Client dependencies
│   └── vite.config.ts          # Vite configuration with API proxy to port 8080
│
├── server/                     # Spring Boot 3 Backend Application
│   ├── pom.xml                 # Maven configuration (Spring Boot 3.3.2, Java 21)
│   ├── mvnw / mvnw.cmd         # Self-contained Maven wrapper script
│   ├── src/main/java/com/aura/ # Java Source Code
│   │   ├── config/             # SecurityConfig, CorsConfig
│   │   ├── controller/         # Auth, Project, Feature, Environment, AuditLog, Health Controllers
│   │   ├── dto/                # Data Transfer Objects
│   │   ├── entity/             # JPA Entities (User, Project, Environment, Feature, FeatureState, AuditLog)
│   │   ├── exception/          # Global Exception Handler & ApiException
│   │   ├── repository/         # Spring Data JPA Repositories
│   │   ├── security/           # JWT Token Provider, UserPrincipal, CustomUserDetailsService
│   │   └── service/            # Core Business Logic Services
│   └── src/main/resources/
│       └── application.yml     # Spring Boot application configuration
│
├── supabase/
│   └── schema.sql              # Supabase PostgreSQL DDL database migration script
│
├── prisma/
│   └── schema.prisma           # Prisma schema for managing Supabase PostgreSQL
│
├── docker-compose.yml          # Optional local PostgreSQL container for offline development
├── start.sh                    # All-in-one execution script for Client & Server
└── README.md                   # Setup documentation
```

---

## ⚡ Quick Start Guide

### Prerequisites
- **Java 21** (or Java 17+)
- **Node.js 18+** & **npm**

---

### Step 1: Set up Supabase PostgreSQL Database

1. Go to [Supabase](https://supabase.com) and create a new project.
2. In the Supabase Dashboard, open the **SQL Editor**.
3. Copy the contents of [`supabase/schema.sql`](supabase/schema.sql) and click **Run**.
4. Navigate to **Project Settings -> Database** and copy your Connection String (URI or Host/Port/User/Password).

---

### Step 2: Configure Environment Variables

Set environment variables for the Spring Boot server (or update `server/src/main/resources/application.yml`):

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://<YOUR_SUPABASE_HOST>:5432/postgres?sslmode=require"
export SPRING_DATASOURCE_USERNAME="postgres.<YOUR_PROJECT_REF>"
export SPRING_DATASOURCE_PASSWORD="<YOUR_SUPABASE_PASSWORD>"
export JWT_SECRET="your_custom_secure_256bit_jwt_secret_key_here"
```

*Note: If testing locally without Supabase, you can run `docker-compose up -d` to launch a local PostgreSQL database on `localhost:5432`.*

---

### Step 3: Run the Application

#### Option A: One-Click Startup Script

```bash
./start.sh
```

#### Option B: Manual Execution

1. **Start Spring Boot Backend** (Port 8080):
   ```bash
   cd server
   ./mvnw spring-boot:run
   ```

2. **Start React Frontend** (Port 5173):
   ```bash
   cd client
   npm install
   npm run dev
   ```

3. Open `http://localhost:5173` in your browser!

---

## 🔌 API Endpoints Summary

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/health` | Server health check | No |
| `POST` | `/api/auth/register` | Register new admin user | No |
| `POST` | `/api/auth/login` | Login user & receive JWT token | No |
| `GET` | `/api/auth/me` | Fetch authenticated user profile | Yes |
| `GET` | `/api/projects` | List all active projects | Yes |
| `POST` | `/api/projects` | Create a new project (auto-seeds Development, Staging, Production envs) | Yes |
| `GET` | `/api/projects/{id}` | Get single project details & environments | Yes |
| `PATCH` | `/api/projects/{id}` | Update project name or description | Yes |
| `DELETE` | `/api/projects/{id}` | Archive project | Yes |
| `GET` | `/api/projects/{id}/features` | List feature flags & environment states | Yes |
| `POST` | `/api/projects/{id}/features` | Create new feature flag & seed env states | Yes |
| `PATCH` | `/api/projects/{id}/flags/{envSlug}/{key}` | Toggle feature flag status for environment | Yes |
| `POST` | `/api/projects/{id}/environments` | Create additional environment for project | Yes |
| `GET` | `/api/projects/{id}/audit-logs` | Retrieve paginated audit logs for project | Yes |

---

## 🛠️ Verification & Quality Checks

- **Backend compilation**: `cd server && ./mvnw compile`
- **Frontend compilation**: `cd client && npm run build`

---

## 📜 License
MIT License
