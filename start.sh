#!/usr/bin/env bash
# Aura Platform - Quick Startup Script

set -e

echo "======================================================="
echo "   🚀 Starting Aura Platform (Spring Boot & Supabase)   "
echo "======================================================="

# Check Java
if ! command -v java >/dev/null 2>&1; then
    echo "❌ Error: Java 17+ is required but not found."
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -n 1)
echo "✅ $JAVA_VER detected"

# Check Node
if ! command -v node >/dev/null 2>&1; then
    echo "❌ Error: Node.js is required for client but not found."
    exit 1
fi
echo "✅ Node $(node -v) detected"

# Ensure client dependencies are installed
if [ ! -d "client/node_modules" ]; then
    echo "📦 Installing frontend dependencies..."
    (cd client && npm install)
fi

echo "-------------------------------------------------------"
echo "Starting Spring Boot Backend (Port 8080)..."
echo "Starting Vite Frontend (Port 5173)..."
echo "-------------------------------------------------------"

(cd server && ./mvnw spring-boot:run) &
SERVER_PID=$!

(cd client && npm run dev) &
CLIENT_PID=$!

trap "kill $SERVER_PID $CLIENT_PID 2>/dev/null || true" EXIT

echo "Aura platform is starting up!"
echo "➡️  Frontend: http://localhost:5173"
echo "➡️  Backend:  http://localhost:8080/health"
echo "Press Ctrl+C to terminate both servers."

wait
