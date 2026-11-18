# AgriGuard RT-SCADA — System Architecture & Technical Specifications

**Product:** AgriGuard RT-SCADA  
**Subsystem:** Multi-Zone Plantation Climate & Irrigation Edge Controller & Telemetry Gateway  
**Runtime:** Java SE 17+ Standard Library (Zero External Dependencies)  
**Target Environments:** Industrial Edge IPCs, Raspberry Pi 4/5 Compute Modules, Embedded Linux, Docker Containers, Windows Workstations  

---

## 1. Executive Summary & Design Philosophy

**AgriGuard RT-SCADA** is an industrial-grade, hard/soft real-time edge Supervisory Control and Data Acquisition (SCADA) platform engineered for high-value protected agriculture, precision greenhouses, tea nurseries, and tropical orchid facilities.

The system is designed with a **safety-first, high-assurance architecture**:
- **Zero Third-Party Dependency Footprint:** Relies purely on the Java 17+ Standard Library (`java.util.concurrent.*`, `java.time.*`, `com.sun.net.httpserver.*`, `javax.swing.*`, `java.awt.*`), eliminating dependency supply-chain risks, licensing constraints, and third-party runtime vulnerabilities on embedded hardware.
- **Hardware Abstraction Layer (HAL) & Modbus TCP Bridge:** Isolates real-time control algorithms from physical transducers via `SensorDriver` and `ModbusTcpSensorBridge`, supporting 10-byte binary PLC frames with Modbus CRC-16 validation.
- **Fault-Tolerant Triple Modular Redundancy (TMR):** Triple-redundant canopy temperature channels are processed through an inexact majority voter ($\pm 1.0^\circ\text{C}$ tolerance) to mask transient transducer drift and sensor degradation.
- **Autonomous Watchdog Heartbeat Supervision:** Continuously scans field channels with a 1500 ms timeout window, guaranteeing channel dropout identification within 2.5 seconds.
- **Deterministic Fail-Safe Interlock:** Hardwired software interlock overriding manual commands to transition all actuators into a safe state (Heater OFF, Fans ON, Alarm ON, Pump OFF, Fogger OFF) during sensor consensus loss or extreme thermal spikes.
- **Agronomic Setpoint Profiles & VPD Modeling:** Real-time calculation of Vapor Pressure Deficit (VPD in kPa) using saturated and actual vapor pressure equations, driving closed-loop hysteresis control tailored to crop-specific physiological thresholds.
- **Cryptographic Tamper-Evident SHA-256 Audit Trail:** Blockchain-style hash chaining where every telemetry record links to the previous row's digest, providing mathematical proof against log tampering, line deletion, or history manipulation.
- **Embedded Edge REST, SSE & Prometheus Telemetry Gateway:** Non-blocking embedded HTTP server exposing JSON state snapshots, Server-Sent Events (SSE) live push streams, remote control/fault endpoints, and standard Prometheus metrics.
- **Deterministic Headless CI/CD Verification Suite:** Self-contained verification suite executing 7 real-time and fault-tolerance tests in headless environments (GitHub Actions, Docker) with standard exit codes.

---

## 2. System Architecture & Component Interactions

```mermaid
flowchart TD
    subgraph Physical Plant & Fieldbus Layer
        FB[Field Sensors / Modbus Bus: T1, T2, T3, H1, S1]
        MB[ModbusTcpSensorBridge - Port 5020]
        HAL[SensorDriver HAL Interface]
        FB --> HAL
        MB -- Decoded 10B Frames --> HAL
    end

    subgraph Real-Time Ingestion Layer
        ST[SensorTask Threads - 500 ms Periodic]
        HAL --> ST
        BB[BoundedBuffer - Custom Mesa Monitor Queue]
        WD[Watchdog Supervisor - 1000 ms Periodic]
        ST -- SensorReadings --> BB
        ST -- Heartbeat Pulse --> WD
    end

    subgraph Real-Time SCADA Core
        CT[ControlTask - 1000 ms Periodic]
        TMR[TMR Inexact Majority Voter]
        CP[CropProfile Agronomic Engine]
        VPD[VPD Calculation Engine]
        
        BB --> CT
        WD -- Channel Liveness & Interlock State --> CT
        CT --> TMR
        CT --> CP
        CT --> VPD
    end

    subgraph Actuator & Safety Interlock Layer
        AM[ActuatorManager - AUTO vs MANUAL Mode]
        SAFE[Fail-Safe Interlock Override]
        OUT[Heater | Exhaust Fans | Irrigation Pump | Fogger | Siren]
        
        CT -- Setpoints --> AM
        CT -- Safety Trip --> SAFE
        SAFE --> AM
        AM --> OUT
    end

    subgraph Distributed Gateway & Historian Layer
        HIST[TelemetryLogger - SHA-256 Chained Historian]
        HTTP[EdgeTelemetryServer - Port 8085]
        PROM[Prometheus Endpoint /metrics]
        SSE[SSE Live Stream /telemetry/stream]
        REST[REST API /status, /profile, /fault]
        SCADA[PlantationDashboardUI - Swing SCADA]
        TM[TimingMonitor - Rate-Monotonic WCET Audit]
        
        CT --> HIST
        CT --> HTTP
        HTTP --> REST
        HTTP --> SSE
        HTTP --> PROM
        CT --> SCADA
        CT --> TM
    end
```

---

## 3. Real-Time Task Allocation & Timing Budgets

Tasks are prioritized according to **Rate-Monotonic Priority Assignment (RMS)**, where shorter period tasks receive strictly higher execution priority:

| Task Class | Period ($T_i$) | Deadline ($D_i$) | Priority | Typical Execution ($C_{avg}$) | Measured WCET ($C_{max}$) | Functional Ownership |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| **Sensor Acquisition** | 500 ms | 500 ms | **Highest** | $\approx 0.02\text{ ms}$ | $\approx 0.15\text{ ms}$ | Polls HAL channels (`T1, T2, T3, H1, S1`), puts telemetry packets into bounded monitor buffer, pulses watchdog heartbeat. |
| **SCADA Control Cycle** | 1000 ms | 1000 ms | **High** | $\approx 0.05\text{ ms}$ | $\approx 0.30\text{ ms}$ | Drains buffer, runs TMR voter, computes VPD, evaluates agronomic hysteresis rules, steps plant dynamics. |
| **Watchdog Supervisor** | 1000 ms | 1000 ms | **High** | $\approx 0.01\text{ ms}$ | $\approx 0.08\text{ ms}$ | Scans heartbeat timestamps; flags channel omission when silence $> 1500\text{ ms}$. |
| **Telemetry & Status** | 2000 ms | 2000 ms | **Lowest** | $\approx 0.08\text{ ms}$ | $\approx 0.35\text{ ms}$ | Formats operator telemetry line and commits periodic status snapshots. |

### Rate-Monotonic Schedulability Proof
By the classical **Liu & Layland (1973)** utilization bound theorem:

$$U = \sum_{i=1}^{n} \frac{C_i}{T_i} \le U_{\text{bound}}(n) = n \left(2^{1/n} - 1\right)$$

For $n = 4$ periodic task categories:

$$U_{\text{bound}}(4) = 4 \left(2^{1/4} - 1\right) = 4 \left(1.189207 - 1\right) \approx 0.756828 \quad (75.68\%)$$

Measured total processor utilization:

$$U_{\text{actual}} = \frac{0.15}{500} + \frac{0.30}{1000} + \frac{0.08}{1000} + \frac{0.35}{2000} = 0.00030 + 0.00030 + 0.00008 + 0.000175 \approx 0.000855 \quad (0.0855\%)$$

Because $U_{\text{actual}} \ll U_{\text{bound}}$ ($0.0855\% \ll 75.68\%$), the system is **provably schedulable**, guaranteeing that no task will ever miss its hard real-time deadline under worst-case phasing.

---

## 4. Subsystem Specifications

### 4.1 Agronomic Crop Profiles (`CropProfile.java`)
The edge controller provides pre-tuned agronomic presets switchable at runtime without system restart:

| Preset | Target Temp (°C) | Target RH (%) | Target Soil (%) | Critical Temp (°C) | Target VPD (kPa) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Capsicum (Bell Pepper)** | 21.0 – 27.0 | 65.0 – 80.0 | 40.0 – 55.0 | 34.0 | 0.80 – 1.20 |
| **Commercial Strawberry** | 18.0 – 24.0 | 60.0 – 75.0 | 45.0 – 60.0 | 30.0 | 0.70 – 1.10 |
| **Tea Nursery (*C. Sinensis*)** | 20.0 – 26.0 | 75.0 – 88.0 | 50.0 – 65.0 | 32.0 | 0.40 – 0.80 |
| **Tropical Orchid House** | 22.0 – 29.0 | 70.0 – 85.0 | 35.0 – 50.0 | 35.0 | 0.60 – 1.00 |

#### Vapor Pressure Deficit (VPD) Formulation
Transpiration and stomatal conductance are governed by VPD:

$$VP_{\text{sat}} = 0.61078 \times \exp\left(\frac{17.27 \times T}{T + 237.3}\right) \quad [\text{kPa}]$$

$$VP_{\text{air}} = VP_{\text{sat}} \times \left(\frac{RH}{100}\right) \quad [\text{kPa}]$$

$$VPD = VP_{\text{sat}} - VP_{\text{air}} = VP_{\text{sat}} \times \left(1 - \frac{RH}{100}\right) \quad [\text{kPa}]$$

### 4.2 Triple Modular Redundancy Voter (`TMRVoter.java`)
Canopy temperature is sampled via triple-redundant sensors ($T_1, T_2, T_3$). The inexact majority voter applies a tolerance threshold $\epsilon = 1.0^\circ\text{C}$:
- **`CONSENSUS OK`**: $|T_1 - T_2| \le \epsilon \land |T_1 - T_3| \le \epsilon \land |T_2 - T_3| \le \epsilon$. Consensus is the arithmetic mean of all 3 channels.
- **`OUTLIER MASKED`**: Exactly one channel diverges by $>\epsilon$. The outlier is isolated and discarded; consensus is the mean of the two agreeing channels.
- **`DEGRADED (2-CH)`**: Exactly one channel has suffered hardware loss or omission. The voter operates on the 2 surviving channels if $|T_a - T_b| \le \epsilon$.
- **`CONSENSUS LOSS`**: All channel pairs diverge or $< 2$ channels remain operational. Emits `FAILED` status, triggering the immediate Fail-Safe Interlock.

### 4.3 Deterministic Fail-Safe Interlocks (`ActuatorManager.java`)
Each actuator maintains an independent operational mode:
- **`AUTO` (Closed-Loop):** Regulated by `ControlTask` hysteresis algorithms.
- **`MANUAL OVERRIDE`:** Controlled directly by authorized operators.

**Safety Interlock Rule:** Whenever the Fail-Safe State is triggered (due to TMR consensus loss, controller software exception, or canopy temperature $> T_{\text{critical}}$), the safety interlock unconditionally overrides all manual settings:
- **Thermal Curtain / Heater:** `FORCED OFF` (prevents crop burning and thermal runaway)
- **Exhaust Ventilation Fans:** `FORCED ON` (maximizes heat extraction and airflow)
- **Drip Irrigation Pump:** `FORCED OFF` (prevents substrate waterlogging and electrical hazard)
- **Misting Fogger:** `FORCED OFF` (prevents electrical short circuits)
- **Audio/Visual Siren:** `FORCED ON` (alerts personnel on site)

### 4.4 Custom Monitor Circular Buffer (`BoundedBuffer.java`)
Inter-task telemetry communication employs a custom Java Monitor avoiding `java.util.concurrent.BlockingQueue`:
- Uses intrinsic synchronization (`synchronized` methods).
- Implements **Mesa semantics** where thread awakenings are checked in `while` condition loops:
  ```java
  while (count == items.length) { wait(); }
  ```
- Non-blocking `poll()` allows the periodic control task to drain all pending telemetry packets in $O(k)$ without blocking on empty buffers.

---

## 5. Distributed Telemetry & Edge Gateway (`EdgeTelemetryServer.java`)

AgriGuard integrates an embedded HTTP server via `com.sun.net.httpserver.HttpServer` on port `8085` (configurable via `--port=<num>`) with daemon thread execution and global CORS (`*`):

### 5.1 Endpoints Reference

#### `GET /api/v1/status`
Returns a comprehensive JSON snapshot of the controller's runtime state:
```json
{
  "timestamp": "10:59:26.071",
  "isoDateTime": "2026-11-18T05:29:26.071353700Z",
  "cropProfile": "STRAWBERRY",
  "cropDisplayName": "Commercial Strawberry",
  "votedTemperature": 24.01,
  "voteStatus": "MASKED",
  "voteDetail": "Outlier T2=39.01 masked; T1=23.99 & T3=24.03 in consensus (diff=0.04°C)",
  "humidityPercent": 67.4,
  "soilMoisturePercent": 46.5,
  "vpdKpa": 0.974,
  "actuators": {
    "heater": false,
    "fan": true,
    "pump": false,
    "fogger": false,
    "alarm": false,
    "controlMode": "AUTO",
    "safeStateInterlock": false
  },
  "diagnostics": {
    "tmrMaskedTotal": 2,
    "watchdogFailuresTotal": 0,
    "safeStateActive": false,
    "bufferOccupancy": 4,
    "bufferCapacity": 10
  }
}
```

#### `GET /api/v1/telemetry/stream`
Server-Sent Events (`text/event-stream`) pushing compact real-time telemetry frames every `1000 ms`.
```bash
curl -N -H "Accept: text/event-stream" http://localhost:8085/api/v1/telemetry/stream
```

#### `POST /api/v1/control/profile`
Switches the active agronomic profile dynamically.
- Query Parameter: `?profile=STRAWBERRY`
- JSON Payload: `{"profile":"STRAWBERRY"}`
- Allowed: `BELL_PEPPER`, `STRAWBERRY`, `TEA_NURSERY`, `ORCHID_HOUSE`.

#### `POST /api/v1/control/fault`
Remotely injects field diagnostics for automated integration testing:
- JSON Payload: `{"action":"BIAS|DISCONNECT|DUAL|SURGE|FREEZE|FAULT|RESET"}`

#### `POST /api/v1/control/actuator`
Remotely sets actuator operational mode (`AUTO` vs `MANUAL`) and state (`true`/`false`).
- JSON Payload: `{"actuator":"HEATER|FAN|PUMP|FOGGER|ALARM", "mode":"AUTO|MANUAL", "state":true|false}`
- **Safety Interlock Enforcement:** If a safety excursion (thermal spike $> T_{\text{critical}}$, TMR consensus loss, or watchdog failure) is active, physical outputs remain strictly locked in the safe state (Heater OFF, Fan ON, Alarm ON, Pump OFF, Fogger OFF). Manual override requests attempting to turn on heating or silence alarms are overridden with `status: "INTERLOCKED"`.

#### `GET /api/v1/audit/status`
Queries the live integrity status of the local SHA-256 cryptographic audit ledger:
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

#### `GET /metrics`
Prometheus-compliant metrics exposition (`text/plain; version=0.0.4`):
```text
# HELP agriguard_voted_temperature_celsius Current consensus temperature from TMR voting in degrees Celsius.
# TYPE agriguard_voted_temperature_celsius gauge
agriguard_voted_temperature_celsius 24.81

# HELP agriguard_vpd_kpa Current Vapor Pressure Deficit in kilopascals.
# TYPE agriguard_vpd_kpa gauge
agriguard_vpd_kpa 0.987

# HELP agriguard_safe_state_active Indicates whether safety interlock fail-safe state is active (1=active, 0=normal).
# TYPE agriguard_safe_state_active gauge
agriguard_safe_state_active 0

# HELP agriguard_tmr_masked_total Cumulative count of transient sensor faults masked by TMR majority voting.
# TYPE agriguard_tmr_masked_total counter
agriguard_tmr_masked_total 2

# HELP agriguard_task_wcet_ms Worst-case execution time observed per real-time task in milliseconds.
# TYPE agriguard_task_wcet_ms gauge
agriguard_task_wcet_ms{task="sensor_sampling"} 0.150
agriguard_task_wcet_ms{task="control_cycle"} 0.300
agriguard_task_wcet_ms{task="watchdog_check"} 0.080
agriguard_task_wcet_ms{task="status_display"} 0.350

# HELP agriguard_rm_utilization_ratio Current total Rate-Monotonic CPU utilization ratio.
# TYPE agriguard_rm_utilization_ratio gauge
agriguard_rm_utilization_ratio 0.0009
```

---

## 6. Industrial Modbus/TCP Sensor Bridge (`ModbusTcpSensorBridge.java`)

Translates field transducer packets received over TCP port `5020` into physical plant dynamics.

### 6.1 10-Byte Binary Frame Specification
| Byte Offset | Field Name | Data Type | Encoding / Format | Description |
| :---: | :--- | :---: | :--- | :--- |
| `0..1` | **Sync Preamble** | 2 Bytes | `0xAA 0x55` | Fixed synchronization header |
| `2..3` | **Channel ID** | 2 Bytes | ASCII (`"T1"`, `"H1"`, `"S1"`) | Target transducer channel |
| `4..7` | **Measurement** | 4 Bytes | IEEE-754 Float32 (Big-Endian) | Calibrated physical value |
| `8..9` | **Modbus CRC-16** | 2 Bytes | Integer (Little-Endian) | Standard Modbus CRC (Poly `0xA001`, Seed `0xFFFF`) |

Includes automated loopback verification via `ModbusTcpSensorBridge.runSelfTest()` ensuring bit flips in payloads are rejected by CRC-16 checks.

---

## 7. Cryptographic Tamper-Evident SHA-256 Audit Chain (`TelemetryLogger.java`)

To prevent unauthorized manipulation, alteration, or deletion of agricultural compliance logs, `TelemetryLogger` implements a continuous SHA-256 hash chain:

```
[Genesis Seed: 0000...0000]
         |
         v
+-------------------+
| Block #1 Payload  | ---> RowHash_1 = SHA256("0000...0000" + "|" + Payload_1)
+-------------------+
         |
         v
+-------------------+
| Block #2 Payload  | ---> RowHash_2 = SHA256(RowHash_1 + "|" + Payload_2)
+-------------------+
         |
         v
+-------------------+
| Block #3 Payload  | ---> RowHash_3 = SHA256(RowHash_2 + "|" + Payload_3)
+-------------------+
```

### 7.1 Integrity Verification Engine
Operators and external auditors can verify the ledger using:
```bash
java -cp bin Main --verify-audit
```
The verification engine recomputes every block digest from the genesis seed:
1. Row 1 `PrevHash` must strictly equal `0000000000000000000000000000000000000000000000000000000000000000`.
2. For every Row $i > 1$, `PrevHash_i` must equal `RowHash_{i-1}`.
3. For every Row, `RowHash_i` must match `SHA-256(PrevHash_i + "|" + Payload_i)`.
4. Returns exit code `0` on 100% mathematical validity, `1` upon detection of tampering.

---

## 8. Headless Automated Verification Suite (`HeadlessVerificationSuite.java`)

Automated verification harness running without GUI dependencies for CI/CD pipelines:
```bash
java -Djava.awt.headless=true -cp bin Main --headless-test
```
Executes 7 deterministic verification suites:
1. **TMR Consensus Under Nominal Conditions:** Verifies 3 healthy channels produce `VoteStatus.OK`.
2. **Transient Outlier Fault Masking:** Injects $+15.0^\circ\text{C}$ bias; verifies median consensus masks outlier (`VoteStatus.MASKED`).
3. **Watchdog Deadline Omission Detection:** Simulates channel dropout; verifies timeout detection and safe state transition.
4. **Critical Safety Interlock Interdictions:** Verifies heater cutoff, forced fan, siren alarm, and manual override blockage during excursions.
5. **Automatic Fail-Safe Recovery:** Verifies recovery to `ONLINE` upon signal return.
6. **Rate-Monotonic Schedulability Check:** Verifies total utilization $U \le 4(2^{0.25}-1) \approx 0.7568$.
7. **SHA-256 Ledger Integrity & Tamper Detection:** Generates ledger, verifies integrity, injects single-byte payload corruption, and asserts tamper detection.
8. **Modbus/TCP Loopback Codec:** Verifies nominal frame encoding/decoding and CRC-16 corruption rejection.

---

## 9. Containerization & Deployment

### Docker Multi-Stage Image
```bash
docker build -t agriguard-rt-scada:latest .
docker run -d -p 8085:8085 -p 5020:5020 -v ./logs:/opt/agriguard/logs agriguard-rt-scada:latest
```

### Docker Compose (Multi-Zone Estate Deployment)
```bash
# Launch both Zone 1 (Strawberry) and Zone 2 (Tea Nursery)
docker-compose up -d

# Verify container health status
docker-compose ps

# Query Zone 1 telemetry
curl -fs http://localhost:8085/api/v1/status

# Query Zone 2 telemetry
curl -fs http://localhost:8086/api/v1/status
```
