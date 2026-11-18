#!/usr/bin/env bash
# ==============================================================================
# AgriGuard RT-SCADA: Multi-Zone Plantation Climate & Irrigation Controller
# Industrial Edge Computing Platform for Protected Agriculture (Java 17+)
# ==============================================================================

set -e

echo "=============================================================================="
echo "  AgriGuard RT-SCADA: Multi-Zone Plantation Climate & Irrigation Controller"
echo "  Industrial Edge Computing Platform for Protected Agriculture (Java 17+)"
echo "=============================================================================="
echo ""

mkdir -p bin
mkdir -p logs

echo "[BUILD] Compiling edge SCADA components into bin/..."
javac -encoding UTF-8 -d bin src/*.java

echo "[LAUNCH] Starting AgriGuard RT-SCADA Edge Controller..."
echo "------------------------------------------------------------------------------"
java -Dfile.encoding=UTF-8 -cp bin Main
