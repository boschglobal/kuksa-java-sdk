# Mock Environment for Kuksa Java SDK

This folder contains the Docker Compose environment and default behavior rules for testing the Kuksa Java SDK.

## Startup Procedure

```bash
# 1. Start databroker container
docker compose -f mock-environment/docker-compose.yml up -d

# 2. Start mock provider (in separate terminal)
./gradlew :mock-provider:run

# 3. Launch test app
./gradlew :kuksa-java-testapp:run
```

Or execute the all-in-one runner:
```bash
./scripts/start-kuksa-stack.sh
```
