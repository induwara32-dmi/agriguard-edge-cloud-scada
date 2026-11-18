# AgriGuard RT-SCADA — Multi-Zone Plantation Climate & Irrigation Controller

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Architecture](https://img.shields.io/badge/Architecture-Rate--Monotonic%20RMS-green.svg)](docs/ARCHITECTURE.md)
[![Safety](https://img.shields.io/badge/Fault%20Tolerance-TMR%20%2B%20Watchdog-orange.svg)](docs/ARCHITECTURE.md)
[![Audit](https://img.shields.io/badge/Integrity-SHA--256%20Hash%20Chain-purple.svg)](docs/ARCHITECTURE.md)
[![Docker](https://img.shields.io/badge/Docker-Alpine%20Multi--Stage-blue.svg)](Dockerfile)
[![CI](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-brightgreen.svg)](.github/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Proprietary%20%2F%20Commercial%20Edge-lightgrey.svg)]()

**AgriGuard RT-SCADA** is an industrial-grade, fault-tolerant real-time edge Supervisory Control and Data Acquisition (SCADA) platform and telemetry gateway engineered for protected agriculture, automated commercial greenhouses, tea nurseries, and precision plantation climate regulation.

Developed purely in standard Java 17+ with **zero third-party runtime dependencies**, AgriGuard deploys directly onto industrial edge IPCs, embedded Linux gateways, Raspberry Pi compute modules, and Docker containers with deterministic timing, hardware abstraction, cryptographic audit logging, and fail-safe safety interlocks.

---

## 1. Key Architectural & Industrial Features

- **Zero External Dependencies:** Built 100% on standard Java 17+ APIs (`java.util.concurrent.*`, `com.sun.net.httpserver.*`, `java.time.*`, `javax.swing.*`), eliminating third-party supply-chain vulnerabilities, licensing issues, and native library incompatibilities.
- **Embedded Edge REST, SSE & Prometheus Telemetry Gateway (`EdgeTelemetryServer.java`):** Non-blocking HTTP server on port `8085` providing:
  - `GET /api/v1/status`: Comprehensive JSON state snapshot.
  - `GET /api/v1/telemetry/stream`: Server-Sent Events (SSE) live push stream.
  - `POST /api/v1/control/profile`: Remote agronomic profile switching.
  - `POST /api/v1/control/fault`: Remote diagnostic fault injection.
  - `GET /metrics`: Standard Prometheus metrics exposition (`text/plain; version=0.0.4`).
- **Cryptographic Tamper-Evident SHA-256 Audit Trail (`TelemetryLogger.java`):** Blockchain-style hash chaining where each telemetry row includes `PrevHash` and `RowHash = SHA256(PrevHash + "|" + Payload)` anchored to a genesis seed (`0000...0000`). Supports standalone cryptographic verification via `--verify-audit`.
- **Industrial Modbus/TCP Sensor Bridge (`ModbusTcpSensorBridge.java`):** Transducer protocol adapter on TCP port `5020` decoding 10-byte binary frames (`[Preamble][ChannelID][Float32][Modbus CRC-16]`) with automated loopback self-testing.
- **Deterministic Headless CI/CD Verification Suite (`HeadlessVerificationSuite.java`):** 7 deterministic real-time tests executed via `--headless-test` with standard exit codes (`0` for pass, `1` for fail) for automated CI/CD and container healthchecks.
- **Triple Modular Redundancy (TMR) Inexact Majority Voting:** Triple-redundant canopy temperature channels ($T_1, T_2, T_3$) are evaluated with an inexact consensus window ($\pm 1.0^\circ\text{C}$), automatically isolating outliers and degrading gracefully to dual-channel operation without interrupting climate control.
- **Dynamic Agronomic Crop Profiles:** Runtime switching across pre-tuned presets:
  - **Capsicum (Bell Pepper):** $21.0 - 27.0^\circ\text{C}$, $65 - 80\%$ RH, $40 - 55\%$ Soil, Critical High: $34^\circ\text{C}$
  - **Commercial Strawberry:** $18.0 - 24.0^\circ\text{C}$, $60 - 75\%$ RH, $45 - 60\%$ Soil, Critical High: $30^\circ\text{C}$
  - **Tea Nursery (*C. Sinensis*):** $20.0 - 26.0^\circ\text{C}$, $75 - 88\%$ RH, $50 - 65\%$ Soil, Critical High: $32^\circ\text{C}$
  - **Tropical Orchid House:** $22.0 - 29.0^\circ\text{C}$, $70 - 85\%$ RH, $35 - 50\%$ Soil, Critical High: $35^\circ\text{C}$
- **Vapor Pressure Deficit (VPD) Engine:** Computes real-time transpiration pressure in kilopascals ($VPD = VP_{\text{sat}} - VP_{\text{air}}$) to prevent moisture stress and fungal disease.
- **Deterministic Fail-Safe Interlock:** Actuators support individual `AUTO (Closed-Loop)` and `MANUAL OVERRIDE` control. If sensor consensus is lost or thermal limits are breached, the safety interlock unconditionally trips all actuators into fail-safe mode (**Heater OFF, Fans ON, Alarm ON, Pump OFF, Fogger OFF**).
- **Rate-Monotonic Schedulability Audit:** High-resolution `System.nanoTime()` WCET monitoring verifying compliance with the Liu & Layland (1973) Rate-Monotonic least upper bound.

---

## 2. Quick Start & Execution

### Prerequisites
- Java Development Kit (JDK) 17 or later (Temurin, OpenJDK, Oracle). Verify via `java -version`.
- Node.js 18+ and npm for the Next.js Cloud Command Center.
- Optional: Docker & Docker Compose for containerized deployments.

### 1. Launching the Java Edge Controller
**On Windows:**
```cmd
run.bat
```
Or in headless mode:
```cmd
java -cp bin Main --headless --port=8085 --modbus-port=5020 --zone-id=ZONE-01 --zone-name="Highland Strawberry Canopy A" --profile=STRAWBERRY
```

**On Linux / Raspberry Pi / Edge IPC:**
```bash
chmod +x run.sh
./run.sh
```

**Via Docker Compose (2-Zone Multi-Container Deployment):**
```bash
docker-compose up -d
# Starts Zone 01 on port 8085 (Strawberry) and Zone 02 on port 8086 (Tea Nursery)
```

### 2. Launching the Next.js Cloud Command Center
Inside a separate terminal:
```bash
cd cloud-dashboard
npm install
npm run dev
```
Open **[http://localhost:3000](http://localhost:3000)** in your browser.  
The Cloud Command Center will connect to the live SSE stream (`http://localhost:8085/api/v1/telemetry/stream`), display the 60-second real-time SVG trend chart, render the TMR voter consensus matrix, provide remote actuator controls with safety interlock enforcement, and verify the cryptographic SHA-256 audit ledger. (If the edge node is offline, it automatically falls back to simulated preview mode).

### Command-Line Arguments & Flags (Edge Controller)
```bash
# Launch standard SCADA controller (UI + REST + Modbus) with default Zone 1
java -cp bin Main

# Launch with custom Multi-Zone identity and agronomic crop preset
java -cp bin Main --headless --port=8085 --modbus-port=5020 --zone-id=ZONE-01 --zone-name="Highland Strawberry Canopy A" --profile=STRAWBERRY

# Launch Zone 2 on separate ports and dedicated audit log
java -cp bin Main --headless --port=8086 --modbus-port=5021 --zone-id=ZONE-02 --zone-name="Highland Tea Nursery House B" --profile=TEA_NURSERY --log-file=logs/zone02_tea_telemetry.csv

# Run automated CI/CD verification suite (7 deterministic tests, exits 0 on pass)
java -cp bin Main --headless-test

# Verify cryptographic SHA-256 audit chain on telemetry log
java -cp bin Main --verify-audit

# Verify custom audit log path
java -cp bin Main --verify-audit=logs/plantation_telemetry.csv
```

---

## 3. Distributed Telemetry & REST API Reference

The embedded HTTP server runs on default port `8085` (configurable via `--port=<num>`):

### Endpoints Overview
| Method | Endpoint | Description |
| :---: | :--- | :--- |
| `GET` | `/api/v1/status` | Current complete JSON system snapshot (with Zone Identity & Agronomy) |
| `GET` | `/api/v1/telemetry/stream` | Server-Sent Events (SSE) live push stream (1 Hz) |
| `GET` | `/api/v1/telemetry/backfill` | Offline-first store-and-forward ledger catch-up stream (`sinceBlock`, `limit`) |
| `POST` | `/api/v1/control/profile` | Switch active agronomic crop profile (Requires `AGRONOMIST` role) |
| `POST` | `/api/v1/control/fault` | Remote diagnostic fault injector (Requires `OPERATOR` role) |
| `POST` | `/api/v1/control/actuator` | Remote actuator controller with safety interlock enforcement (Requires `OPERATOR` role) |
| `GET` | `/api/v1/audit/status` | Live cryptographic SHA-256 audit chain verification status |
| `GET` | `/metrics` | Prometheus metrics exposition endpoint (with Dew Point, GDD, ET₀) |

### 1. Query Current System Status
```bash
curl -s http://localhost:8085/api/v1/status | jq .
```
Response:
```json
{
  "zoneId": "ZONE-01",
  "zoneName": "Highland Strawberry Canopy A",
  "timestamp": "11:39:36.776",
  "isoDateTime": "2026-11-18T06:09:36.776708400Z",
  "cropProfile": "STRAWBERRY",
  "cropDisplayName": "Commercial Strawberry",
  "votedTemperature": 24.55,
  "voteStatus": "OK",
  "voteDetail": "All 3 channels agree: T1=24.52, T2=24.54, T3=24.59",
  "humidityPercent": 68.4,
  "soilMoisturePercent": 47.5,
  "vpdKpa": 0.976,
  "actuators": {
    "heater": false,
    "fan": false,
    "pump": false,
    "fogger": false,
    "alarm": false,
    "controlMode": "AUTO",
    "safeStateInterlock": false
  },
  "diagnostics": {
    "tmrMaskedTotal": 0,
    "watchdogFailuresTotal": 0,
    "safeStateActive": false,
    "bufferOccupancy": 9,
    "bufferCapacity": 10
  }
}
```

### 2. Live Telemetry Push Stream (SSE)
```bash
curl -N -H "Accept: text/event-stream" http://localhost:8085/api/v1/telemetry/stream
```

### 3. Remote Actuator Control (`POST /api/v1/control/actuator`)
Allows SCADA operators or automation controllers to toggle actuator modes (`AUTO` vs `MANUAL`) and states (`true`/`false`).

```bash
# Set Exhaust Fan to MANUAL mode and turn ON
curl -X POST http://localhost:8085/api/v1/control/actuator \
     -H "Content-Type: application/json" \
     -d '{"actuator":"FAN", "mode":"MANUAL", "state":true}'

# Return Heater back to AUTO closed-loop control
curl -X POST http://localhost:8085/api/v1/control/actuator \
     -H "Content-Type: application/json" \
     -d '{"actuator":"HEATER", "mode":"AUTO"}'
```

**Critical Safety Interlock Enforcement:**  
If a safety condition is active (thermal spike $> T_{\text{critical}}$, TMR consensus loss, or watchdog dropout), manual commands attempting to activate heat sources or disable cooling fans are **strictly overridden**:
```json
{
  "status": "INTERLOCKED",
  "zoneId": "ZONE-01",
  "actuator": "HEATER",
  "mode": "MANUAL_OVERRIDE",
  "requestedState": true,
  "actualState": false,
  "safeStateInterlock": true,
  "message": "Command registered, but CRITICAL SAFETY INTERLOCK IS ACTIVE. Physical outputs remain strictly locked in safe state (Heater OFF, Fan ON, Alarm ON). Actual 'HEATER' state is OFF."
}
```

### 4. Cryptographic Audit Status (`GET /api/v1/audit/status`)
```bash
curl -s http://localhost:8085/api/v1/audit/status | jq .
```
Response:
```json
{
  "zoneId": "ZONE-01",
  "zoneName": "Highland Strawberry Canopy A",
  "auditLedgerFile": "logs/plantation_telemetry.csv",
  "totalRecords": 83,
  "genesisHash": "0000000000000000000000000000000000000000000000000000000000000000",
  "latestHash": "eed91f18940f66b68999d966bf43583e51dd6f71e8b373be9d1b254ee1574796",
  "chainVerified": true
}
```

### 5. Switch Agronomic Profile Remotely
```bash
# Via JSON Body
curl -X POST http://localhost:8085/api/v1/control/profile \
     -H "Content-Type: application/json" \
     -d '{"profile":"STRAWBERRY"}'

# Or via Query Parameter
curl -X POST "http://localhost:8085/api/v1/control/profile?profile=TEA_NURSERY"
```

### 6. Inject Remote Diagnostic Faults
```bash
# Injects +15°C bias on T2 (TMR Outlier Masking test)
curl -X POST http://localhost:8085/api/v1/control/fault \
     -H "Content-Type: application/json" \
     -d '{"action":"BIAS"}'

# Actions: BIAS, DISCONNECT, DUAL, SURGE, FREEZE, FAULT, RESET
```

### 7. Prometheus Metrics Scraping
```bash
curl -s http://localhost:8085/metrics
```
Sample Prometheus Configuration (`prometheus.yml`):
```yaml
scrape_configs:
  - job_name: 'agriguard_scada'
    scrape_interval: 2s
    static_configs:
      - targets: ['localhost:8085', 'localhost:8086']
```

### 8. Offline-First Store-and-Forward Backfill Sync (`GET /api/v1/telemetry/backfill`)
Enables cloud SCADA ingestion pipelines to stream missed hash-chained ledger blocks after field network disconnects:
```bash
curl -s "http://localhost:8085/api/v1/telemetry/backfill?sinceBlock=50&limit=100" | jq .
```
Response:
```json
{
  "zoneId": "ZONE-01",
  "totalRecords": 154,
  "sinceBlock": 50,
  "returnedCount": 100,
  "hasMore": true,
  "nextBlock": 151,
  "records": [
    {
      "block": 51,
      "timestamp": "14:22:10.512",
      "isoDateTime": "2026-11-18T08:52:10.512Z",
      "cropProfile": "STRAWBERRY",
      "votedTemp": 24.20,
      "voteStatus": "OK",
      "humidity": 68.0,
      "soilMoisture": 48.0,
      "vpd": 1.050,
      "heater": false,
      "fan": false,
      "pump": false,
      "fogger": false,
      "alarm": false,
      "controlMode": "AUTO",
      "safeState": false,
      "eventNotes": "Nominal cycle",
      "prevHash": "f0b099e457...",
      "rowHash": "2f13fd8562..."
    }
  ]
}
```

---

## 4. IEC 62443 Industrial Cybersecurity, RBAC & Replay Defense

All mutating edge control endpoints (`POST /api/v1/control/*`) require zero-trust cryptographic signature validation and Role-Based Access Control:

1. **Authentication Headers:**
   - `X-AgriGuard-Role`: `VIEWER` (Read-only), `OPERATOR` (Actuators & Diagnostics), `AGRONOMIST` (Crop setpoints & overrides).
   - `X-AgriGuard-Timestamp`: Epoch timestamp in milliseconds.
   - `X-AgriGuard-Signature`: `HMAC_SHA256(secret, role + ":" + timestamp + ":" + method + ":" + path + ":" + body)`.

2. **Replay-Attack Protection:**  
   The controller strictly verifies that $|T_{\text{server}} - T_{\text{client}}| \le 30,000\text{ ms}$. Stale or replayed requests are rejected with `401 Unauthorized`.

3. **Per-IP Token-Bucket Rate Limiter:**  
   Every client IP is throttled to a **30 req/sec** burst bucket. Excess traffic triggers `429 Too Many Requests`.

4. **Configurable Security Flags:**
   ```bash
   # Run with custom authentication secret and strict IEC 62443 enforcement
   java -cp bin Main --auth-mode=strict --auth-secret=my-estate-master-key-2026
   ```

---

## 5. Predictive Agronomy & Condensation Defense

AgriGuard integrates real-time thermodynamic microclimate modeling:

1. **Magnus-Tetens Dew Point ($T_d$):**
   $$\gamma(T, RH) = \frac{17.27 \cdot T}{237.7 + T} + \ln\left(\frac{RH}{100}\right), \quad T_d = \frac{237.7 \cdot \gamma}{17.27 - \gamma}$$

2. **Dew Point Spread ($\Delta T = T_{\text{canopy}} - T_d$):**  
   Measures proximity to surface condensation. When $\Delta T < 2.0^\circ\text{C}$ and $RH > 82\%$, the controller elevates **Botrytis Condensation Risk** to `HIGH` or `CRITICAL`, triggering preventive ventilation warnings.

3. **Growing Degree Days (GDD) & Reference Evapotranspiration ($ET_0$):**  
   Tracks physiological crop heat accumulation ($T_{\text{base}} = 10^\circ\text{C}$) and calculates FAO-56 Penman-Monteith daily evapotranspiration demand ($ET_0\text{ mm/day}$) to generate precision pulse irrigation advisories.

---

## 6. Cryptographic Tamper-Evident SHA-256 Audit Chain

All historical points recorded to `logs/plantation_telemetry.csv` are cryptographically linked using a blockchain-style SHA-256 hash chain:

```
Row 1: [Payload_1] | PrevHash: 0000...0000 | RowHash_1: d870cf24c2...
Row 2: [Payload_2] | PrevHash: d870cf24c2... | RowHash_2: 4352ab8887...
Row 3: [Payload_3] | PrevHash: 4352ab8887... | RowHash_3: deb8f51543...
```

### Verify Ledger Cryptographic Integrity
```bash
java -cp bin Main --verify-audit
```
Sample output:
```
================================================================================
          AGRIGUARD RT-SCADA: CRYPTOGRAPHIC AUDIT TRAIL VERIFICATION           
================================================================================
Target Audit Trail : logs/plantation_telemetry.csv
Genesis Hash Seed  : 0000000000000000000000000000000000000000000000000000000000000000
Hashing Algorithm  : SHA-256 (FIPS 180-4 Standard)
--------------------------------------------------------------------------------
  [VERIFIED] Block #0001 | PrevHash: 0000000000... | RowHash: d870cf24c2...
  [VERIFIED] Block #0002 | PrevHash: d870cf24c2... | RowHash: 4352ab8887...
--------------------------------------------------------------------------------
Audit Trail Verification Summary:
  Total Records Checked : 22
  Chain Genesis Anchor  : 0000000000000000000000000000000000000000000000000000000000000000
  Latest Ledger Hash    : b9a2bf9103b79b6a1b463731b22fc50fe8bfc7c32265d27007b2eb68e445af1b
  Tamper Detection      : ZERO INTEGRITY ANOMALIES DETECTED
  Ledger Status         : CRYPTOGRAPHICALLY VALID & VERIFIED (PASSED)
================================================================================
```

---

## 7. Industrial Modbus/TCP Binary Bridge

External industrial PLCs and transducers communicate with the controller over TCP port `5020` using a compact 10-byte binary protocol:

```
[0xAA 0x55] [Channel ID: 2B ASCII] [Float32: 4B Big-Endian] [Modbus CRC-16: 2B Little-Endian]
```

- **Preamble:** `0xAA 0x55` (2 bytes)
- **Channel ID:** e.g., `"T1"`, `"H1"`, `"S1"` (2 bytes)
- **Measurement:** IEEE-754 32-bit float in Big-Endian order (4 bytes)
- **Error Checking:** Modbus CRC-16 polynomial `0xA001`, initial `0xFFFF` (2 bytes)

---

## 8. Multi-Zone Orchestration, Observability & Containerization

### Multi-Stage Alpine Docker Build
```bash
# Build the optimized minimal production container
docker build -t agriguard-rt-scada:latest .

# Run with persistent telemetry volume and exposed ports
docker run -d \
  --name agriguard-edge \
  -p 8085:8085 \
  -p 5020:5020 \
  -v $(pwd)/logs:/opt/agriguard/logs \
  agriguard-rt-scada:latest
```

### Docker Compose (Multi-Zone Estate & Observability Stack)
The included [docker-compose.yml](docker-compose.yml) launches a realistic multi-zone estate orchestration with full observability:
- **`agriguard-zone-01`**: Port `8085` (Modbus `5020`), Zone ID `ZONE-01` (*Highland Strawberry Canopy A*), running `STRAWBERRY`.
- **`agriguard-zone-02`**: Port `8086` (Modbus `5021`), Zone ID `ZONE-02` (*Highland Tea Nursery House B*), running `TEA_NURSERY`.
- **`prometheus`**: Port `9090`, continuously scraping metrics every 2s across both zones.
- **`grafana`**: Port `3001` (login: `admin` / `admin`), auto-provisioned with the industrial SCADA dashboard.
- **`cloud-dashboard`**: Port `3000`, the Next.js 14 operator command center.

```bash
# Launch entire estate & observability cluster
docker-compose up -d

# Verify all service healthchecks
docker-compose ps

# Access Cloud Command Center: http://localhost:3000
# Access Grafana SCADA Dashboard: http://localhost:3001
# Access Prometheus Metrics: http://localhost:9090
```

### Continuous Integration (GitHub Actions)
The repository includes a comprehensive GitHub Actions matrix workflow (`.github/workflows/ci.yml`) that automatically executes:
- Source code compilation on Linux and Windows runners under JDK 21.
- Deterministic Headless Verification Suite (`--headless-test`).
- Telemetry generation and SHA-256 cryptographic audit chain verification (`--verify-audit`).
- Multi-stage Docker image build and healthcheck validation.

---

## 9. Real-Time Schedulability & Timing Analysis

The system runs 4 periodic task categories scheduled strictly by **Rate-Monotonic Priority Assignment (RMS)**:

| Task Class | Period ($T_i$) | Deadline ($D_i$) | RMS Priority | Description |
| :--- | :---: | :---: | :---: | :--- |
| **Sensor Acquisition** | 500 ms | 500 ms | **Highest** | Samples 5 field channels through HAL, pushes to `BoundedBuffer`, pulses watchdog heartbeat. |
| **SCADA Control Cycle** | 1000 ms | 1000 ms | **High** | Drains buffer, runs TMR voter, computes VPD, executes crop hysteresis rules, steps plant dynamics. |
| **Watchdog Supervisor** | 1000 ms | 1000 ms | **High** | Scans heartbeats; detects channel omission within 2.5 seconds (1500 ms timeout window). |
| **Telemetry & Status** | 2000 ms | 2000 ms | **Lowest** | Formats operator status line and dispatches telemetry points to the Historian. |

### Rate-Monotonic Schedulability Verification
By the **Liu & Layland (1973)** theorem:

$$U = \sum_{i=1}^{n} \frac{C_i}{T_i} \le n \left(2^{1/n} - 1\right) = 4 \left(2^{0.25} - 1\right) \approx 75.68\% \quad (0.75683)$$

With measured processor utilization $U \approx 0.05\% - 0.55\% \ll 75.68\%$, AgriGuard RT-SCADA is **provably schedulable** with zero probability of deadline misses.

---

## 10. Source Code Structure

```
.
├── src/
│   ├── AgronomyIntelligence.java    # Magnus-Tetens dew point, Botrytis risk, GDD & ET₀ engine
│   ├── EdgeSecurityManager.java     # IEC 62443 HMAC-SHA256 authentication, RBAC & token bucket
│   ├── CropProfile.java             # Agronomic crop presets, VPD equations, transpiration metrics
│   ├── SensorDriver.java            # Hardware Abstraction Layer (HAL) interface
│   ├── SimulatedFieldBus.java       # Fieldbus simulator & microclimate thermodynamic physics
│   ├── SensorReading.java           # Immutable telemetry packet record
│   ├── SensorTask.java              # 500 ms periodic field acquisition task
│   ├── TMRVoter.java                # Triple Modular Redundancy inexact majority voter (±1.0°C)
│   ├── WatchdogMonitor.java         # 1000 ms heartbeat supervisor & fail-safe coordinator
│   ├── BoundedBuffer.java           # Custom Java Monitor circular queue (Mesa semantics)
│   ├── RaceConditionDemo.java       # Multithreaded concurrency integrity verification benchmark
│   ├── ActuatorManager.java         # Actuator state machine (AUTO vs MANUAL) & safety interlocks
│   ├── TelemetryLogger.java         # SHA-256 cryptographic audit historian & backfill stream engine
│   ├── ModbusTcpSensorBridge.java   # Industrial 10-byte binary Modbus/TCP transducer adapter
│   ├── EdgeTelemetryServer.java     # Embedded Edge REST, SSE, Backfill & Prometheus telemetry gateway
│   ├── HeadlessVerificationSuite.java# 9-test deterministic real-time CI/CD verification harness
│   ├── ControlTask.java             # 1000 ms closed-loop SCADA control cycle
│   ├── TimingMonitor.java           # High-resolution System.nanoTime() WCET & RM schedulability audit
│   ├── PlantationDashboardUI.java   # Industrial dark-mode Swing SCADA dashboard & live trend chart
│   └── Main.java                    # Runtime bootstrap, thread pool executor, and CLI harness
├── cloud-dashboard/                 # AgriGuard Cloud Command Center (Next.js 14 + TS + Tailwind)
│   ├── app/                         # Next.js App Router (layout.tsx, page.tsx, globals.css)
│   ├── components/                  # Enterprise SCADA telemetry widgets
│   │   ├── useTelemetry.ts          # Resilient SSE streaming, polling fallback & zero-trust signing
│   │   ├── CommandBar.tsx           # Brand bar, live SSE pill, Zone/Role switcher & crop selector
│   │   ├── SafetyBanner.tsx         # Real-time fail-safe interlock alert banner
│   │   ├── MicroclimateCards.tsx    # TMR Temp, VPD kPa, RH %, and Soil Moisture telemetry cards
│   │   ├── AgronomyCard.tsx         # Dew point, Botrytis risk badge, GDD & ET₀ advisory
│   │   ├── TrendChart.tsx           # 60-second real-time multi-series SVG line chart
│   │   ├── TmrHealthMatrix.tsx      # T1/T2/T3 consensus matrix & watchdog monitor status
│   │   ├── ActuatorMatrix.tsx       # 5-actuator remote control switches with RBAC/interlock lockouts
│   │   └── AuditCommissioningPanel.tsx # Remote diagnostic fault triggers, backfill sync & SHA-256 ledger
│   ├── lib/                         # Client security, localization and unit conversion
│   │   ├── security.ts              # Web Crypto API (crypto.subtle) HMAC-SHA256 signature generator
│   │   ├── i18n.ts                  # Multi-language dictionary (English, Sinhala, Tamil)
│   │   └── units.ts                 # Metric (°C / kPa) vs Imperial (°F / psi) converters
│   ├── types/
│   │   └── telemetry.ts             # Strict TypeScript telemetry and SCADA interfaces
│   ├── package.json                 # Next.js 14, Lucide React, Tailwind CSS dependencies
│   ├── next.config.mjs              # Multi-zone edge rewrites proxy configuration
│   └── tailwind.config.ts           # Industrial SCADA dark theme palette
├── observability/                   # Full-Stack Estate Observability Configuration
│   ├── prometheus.yml               # Multi-zone Prometheus metric scraping configuration
│   └── grafana/provisioning/        # Pre-provisioned Grafana datasources and SCADA dashboards
│       ├── datasources/prometheus.yml
│       └── dashboards/
│           ├── dashboard-provider.yml
│           └── agriguard-scada-dashboard.json # Real-time Grafana SCADA dashboard
├── docs/
│   └── ARCHITECTURE.md              # Detailed technical architecture, timing budgets, and API specs
├── logs/
│   └── plantation_telemetry.csv     # Rolling SHA-256 chained audit ledger
├── .github/
│   └── workflows/
│       └── ci.yml                   # Automated GitHub Actions CI/CD matrix pipeline
├── Dockerfile                       # Multi-stage Alpine container build
├── docker-compose.yml               # Service orchestration with healthchecks and volume mounts
├── run.bat                          # One-click Windows runner supporting CLI arguments
└── README.md                        # Product overview and reference manual
│   └── ARCHITECTURE.md              # Detailed technical architecture, timing budgets, and API specs
├── logs/
│   └── plantation_telemetry.csv     # Rolling SHA-256 chained audit ledger
├── .github/
│   └── workflows/
│       └── ci.yml                   # Automated GitHub Actions CI/CD matrix pipeline
├── Dockerfile                       # Multi-stage Alpine container build
├── docker-compose.yml               # Service orchestration with healthchecks and volume mounts
├── run.bat                          # One-click Windows runner supporting CLI arguments
└── README.md                        # Product overview and reference manual
```

---

## 11. Demonstration Media

A comprehensive video walkthrough demonstrating the application build, live 60-second trend graph, crop switching, TMR outlier isolation, watchdog timeout detection, fail-safe safety interlocks, and Rate-Monotonic timing analysis is documented:

- **Local Video Backup File:** `demo/Demo.mp4` *(place recording here for offline evaluation)*
- **Demo Media Guide:** [demo/README.txt](demo/README.txt)
