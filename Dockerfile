# ==============================================================================
# AgriGuard RT-SCADA: Multi-Zone Plantation Climate & Irrigation Controller
# High-Assurance Real-Time Edge Computing Platform for Protected Agriculture
# Multi-Stage Alpine Container Image (Pure Java Standard Library - Zero External JARs)
# ==============================================================================

# --- Stage 1: Compilation ---
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Java source tree
COPY src/ ./src/

# Compile all source files into bin directory
RUN mkdir -p bin && \
    javac -encoding UTF-8 -d bin src/*.java

# Run deterministic CI verification suite during image build
RUN java -cp bin Main --headless-test

# --- Stage 2: Minimal Production Runtime ---
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="AgriGuard RT-SCADA Edge Controller" \
      org.opencontainers.image.description="Real-Time Plantation Climate & Irrigation SCADA Controller" \
      org.opencontainers.image.version="2.1.0" \
      org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /opt/agriguard

# Install curl for container healthchecking
RUN apk add --no-cache curl

# Copy compiled bytecode from builder stage
COPY --from=builder /build/bin ./bin

# Create mount points for rolling cryptographic audit logs
RUN mkdir -p logs && chmod -R 755 /opt/agriguard

# Expose HTTP REST / SSE / Prometheus and Modbus/TCP Binary Bridge
EXPOSE 8085 8086 5020 5021

# Volume mount for persistent telemetry historian
VOLUME ["/opt/agriguard/logs"]

# Healthcheck probing embedded edge HTTP status endpoint
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -fs http://localhost:8085/api/v1/status || exit 1

# Launch headless SCADA edge controller
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-cp", "bin", "Main"]
CMD ["--headless", "--port=8085", "--modbus-port=5020", "--zone-id=ZONE-01", "--zone-name=Main Production Zone"]
