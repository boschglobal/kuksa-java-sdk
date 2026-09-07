FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
ENV DOCKER_BUILD=true

# Cache dependency downloads separately from source changes.
# Only re-downloads when build scripts or lockfiles change.
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY buildSrc/ buildSrc/
COPY mock-provider/build.gradle.kts mock-provider/build.gradle.kts
COPY mock-provider/gradle.lockfile mock-provider/gradle.lockfile
COPY kuksa-java-sdk/build.gradle.kts kuksa-java-sdk/build.gradle.kts
COPY vss-core/build.gradle.kts vss-core/build.gradle.kts

RUN ./gradlew :mock-provider:dependencies --no-daemon --no-configuration-cache -q 2>/dev/null || true

# Full source copy and build
COPY . .

RUN ./gradlew :mock-provider:installDist --no-daemon --no-configuration-cache -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/mock-provider/build/install/mock-provider .
RUN mkdir -p mock-environment
COPY mock-environment/behavior-rules.yaml mock-environment/behavior-rules.yaml
COPY vss/ vss/
EXPOSE 8080
ENTRYPOINT ["bin/mock-provider"]
CMD ["--host", "databroker", "--port", "55556", "--web-port", "8080"]
