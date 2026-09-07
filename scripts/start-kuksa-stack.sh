#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=== 1. Starting Kuksa Databroker container ==="
if command -v docker &> /dev/null && docker info &> /dev/null; then
    docker compose -f mock-environment/docker-compose.yml up --build -d
    echo "Kuksa Databroker container started."
else
    echo "Note: Docker daemon not reachable. Ensure Databroker is running on port 55556 if testing against real broker."
fi

# Function to clean up background processes on script exit
cleanup() {
    echo ""
    echo "=== Shutting down Mock Provider ==="
    if [ -n "$MOCK_PROVIDER_PID" ]; then
        kill "$MOCK_PROVIDER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

echo ""
echo "=== 2. Starting Mock Provider in background ==="
./gradlew :mock-provider:run --quiet &
MOCK_PROVIDER_PID=$!
echo "Mock Provider started (PID: $MOCK_PROVIDER_PID)."

sleep 2

echo ""
echo "=== 3. Launching Kuksa Java TestApp ==="
./gradlew :kuksa-java-testapp:run
