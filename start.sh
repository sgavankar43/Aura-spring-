#!/usr/bin/env bash
# Aura Platform - Unified Local Runner

set -e

# ANSI Color Codes
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
RESET='\033[0m'

echo -e "${CYAN}========================================================================${RESET}"
echo -e "${BOLD}${CYAN}      🚀 Aura Feature Flag & Configuration Platform - Local Runner      ${RESET}"
echo -e "${CYAN}========================================================================${RESET}"

# 1. Check prerequisites
if ! command -v java >/dev/null 2>&1; then
    echo -e "${YELLOW}❌ Error: Java 21+ is required but not found.${RESET}"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -n 1)
echo -e "${GREEN}✅ Java runtime:${RESET} $JAVA_VER"

if ! command -v node >/dev/null 2>&1; then
    echo -e "${YELLOW}❌ Error: Node.js is required but not found.${RESET}"
    exit 1
fi
echo -e "${GREEN}✅ Node.js runtime:${RESET} $(node -v) / npm $(npm -v)"

# 2. Check and start PostgreSQL
echo -e "${BLUE}🔍 Checking PostgreSQL database service...${RESET}"
if ! nc -z localhost 5432 2>/dev/null; then
    echo -e "${YELLOW}⚡ Starting local PostgreSQL service via Homebrew...${RESET}"
    brew services start postgresql@18 2>/dev/null || brew services start postgresql@15 2>/dev/null || true
    sleep 2
fi

if nc -z localhost 5432 2>/dev/null; then
    echo -e "${GREEN}✅ PostgreSQL is active on port 5432 (aura_db)${RESET}"
else
    echo -e "${YELLOW}⚠️ Warning: PostgreSQL not reachable on port 5432. Please ensure DB is running.${RESET}"
fi

# 3. Clean up any existing stale processes on ports 8080 and 5173
for port in 8080 5173; do
    stale_pids=$(lsof -ti :$port 2>/dev/null || true)
    if [ -n "$stale_pids" ]; then
        echo -e "${YELLOW}🧹 Clearing stale process on port $port (PID: $stale_pids)...${RESET}"
        echo "$stale_pids" | xargs kill -9 2>/dev/null || true
    fi
done

# 4. Ensure client dependencies are installed
if [ ! -d "client/node_modules" ]; then
    echo -e "${BLUE}📦 Installing client dependencies...${RESET}"
    (cd client && npm install)
fi

echo -e "${BLUE}------------------------------------------------------------------------${RESET}"
echo -e "${BOLD}Starting Backend (Spring Boot 3 / Port 8080) & Frontend (Vite / Port 5173)...${RESET}"
echo -e "${BLUE}------------------------------------------------------------------------${RESET}"

# Cleanup on exit or interrupt
cleanup() {
    echo ""
    echo -e "${YELLOW}🛑 Shutting down Aura Platform services...${RESET}"
    kill "$SERVER_PID" 2>/dev/null || true
    kill "$CLIENT_PID" 2>/dev/null || true
    for port in 8080 5173; do
        lsof -ti :$port 2>/dev/null | xargs kill -9 2>/dev/null || true
    done
    echo -e "${GREEN}✅ All services stopped successfully.${RESET}"
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# 5. Launch Spring Boot Server with prefix
(cd server && ./mvnw spring-boot:run) 2>&1 | while IFS= read -r line; do
    echo -e "${CYAN}[BACKEND]${RESET} $line"
done &
SERVER_PID=$!

# 6. Launch Vite Client with prefix
(cd client && npm run dev) 2>&1 | while IFS= read -r line; do
    echo -e "${MAGENTA}[FRONTEND]${RESET} $line"
done &
CLIENT_PID=$!

# 7. Wait for Backend Health Check
echo -e "${BLUE}⏳ Waiting for Spring Boot Backend to initialize...${RESET}"
backend_ready=false
for i in {1..40}; do
    if curl -s http://localhost:8080/health 2>/dev/null | grep -q "ok"; then
        backend_ready=true
        break
    fi
    sleep 1
done

if [ "$backend_ready" = true ]; then
    echo -e "${GREEN}✅ Spring Boot Backend is healthy on http://localhost:8080${RESET}"
else
    echo -e "${YELLOW}⚠️ Backend starting up (still warming up on port 8080)...${RESET}"
fi

# 8. Wait for Frontend
echo -e "${BLUE}⏳ Waiting for Vite Frontend to initialize...${RESET}"
frontend_ready=false
for i in {1..20}; do
    if curl -s -I http://localhost:5173 2>/dev/null | grep -q "200"; then
        frontend_ready=true
        break
    fi
    sleep 1
done

if [ "$frontend_ready" = true ]; then
    echo -e "${GREEN}✅ Vite Frontend is healthy on http://localhost:5173${RESET}"
fi

echo ""
echo -e "${GREEN}╔════════════════════════════════════════════════════════════════════════╗${RESET}"
echo -e "${GREEN}║${RESET}                ${BOLD}🌟 AURA PLATFORM IS RUNNING LOCALLY${RESET}                    ${GREEN}║${RESET}"
echo -e "${GREEN}╠════════════════════════════════════════════════════════════════════════╣${RESET}"
echo -e "${GREEN}║${RESET}  ${BOLD}Frontend UI:${RESET}       ${CYAN}http://localhost:5173${RESET}                                   ${GREEN}║${RESET}"
echo -e "${GREEN}║${RESET}  ${BOLD}Backend API:${RESET}       ${CYAN}http://localhost:8080${RESET}                                   ${GREEN}║${RESET}"
echo -e "${GREEN}║${RESET}  ${BOLD}Health Check:${RESET}      ${CYAN}http://localhost:8080/health${RESET}                            ${GREEN}║${RESET}"
echo -e "${GREEN}║${RESET}                                                                        ${GREEN}║${RESET}"
echo -e "${GREEN}║${RESET}  ${BOLD}🔑 SEED CREDENTIALS:${RESET}                                                  ${GREEN}║${RESET}"
echo -e "${GREEN}║${RESET}  • ${BOLD}Admin Account:${RESET}   ${YELLOW}admin@aura.dev${RESET}     / ${YELLOW}AdminPassword123!${RESET}         ${GREEN}║${RESET}"
echo -e "${GREEN}║${RESET}  • ${BOLD}Developer:${RESET}       ${YELLOW}developer@aura.dev${RESET} / ${YELLOW}DevPassword123!${RESET}           ${GREEN}║${RESET}"
echo -e "${GREEN}║${RESET}                                                                        ${GREEN}║${RESET}"
echo -e "${GREEN}║${RESET}  Press ${BOLD}Ctrl+C${RESET} in this terminal to stop all running services.          ${GREEN}║${RESET}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════════════╝${RESET}"
echo ""

# Keep process alive and stream output
wait "$SERVER_PID" "$CLIENT_PID"
