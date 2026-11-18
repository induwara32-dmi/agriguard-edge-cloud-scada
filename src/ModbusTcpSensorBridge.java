import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Industrial Protocol Adapter: Modbus TCP / RS-485 Binary Sensor Bridge.
 * Translates external transducer telemetry frames directly into the physical fieldbus model.
 *
 * Binary Frame Specification (10 Bytes):
 * [0..1] : Preamble / Sync Word (0xAA 0x55)
 * [2..3] : Transducer Channel ID (2-Byte ASCII, e.g. "T1", "H1", "S1")
 * [4..7] : IEEE-754 32-bit Float Value (Big-Endian)
 * [8..9] : Modbus CRC-16 (Polynomial 0xA001, Little-Endian: Low Byte, High Byte)
 *
 * Includes automated loopback self-testing and CRC-16 corruption detection.
 */
public class ModbusTcpSensorBridge {

    public static final byte SYNC_1 = (byte) 0xAA;
    public static final byte SYNC_2 = (byte) 0x55;
    public static final int FRAME_SIZE = 10;
    public static final int DEFAULT_PORT = 5020;

    public record ModbusFrame(String channelId, float value, boolean validSync, boolean validCrc) {}

    private final SimulatedFieldBus fieldBus;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong framesReceived = new AtomicLong(0);
    private final AtomicLong framesDropped = new AtomicLong(0);

    private ServerSocket serverSocket;
    private Thread listenerThread;

    public ModbusTcpSensorBridge(SimulatedFieldBus fieldBus, int port) {
        this.fieldBus = fieldBus;
        this.port = port;
    }

    /**
     * Starts the non-blocking Modbus/TCP listener daemon.
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);

        listenerThread = new Thread(this::listenLoop, "ModbusTcpBridge-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.printf("[MODBUS BRIDGE] Industrial Binary Protocol Listener active on TCP port %d%n", port);

            while (running.get()) {
                try {
                    Socket client = serverSocket.accept();
                    Thread clientHandler = new Thread(() -> handleClient(client), "ModbusClient-" + client.getRemoteSocketAddress());
                    clientHandler.setDaemon(true);
                    clientHandler.start();
                } catch (SocketException se) {
                    if (!running.get()) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.printf("[MODBUS BRIDGE] Unable to bind TCP port %d: %s%n", port, e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        try (socket; InputStream in = socket.getInputStream()) {
            byte[] frameBuf = new byte[FRAME_SIZE];
            DataInputStream dis = new DataInputStream(in);

            while (running.get()) {
                dis.readFully(frameBuf);
                ModbusFrame decoded = decodeFrame(frameBuf);

                if (decoded.validSync() && decoded.validCrc()) {
                    framesReceived.incrementAndGet();
                    if (fieldBus != null) {
                        fieldBus.setDirectChannelValue(decoded.channelId(), decoded.value());
                    }
                } else {
                    framesDropped.incrementAndGet();
                    System.err.printf("[MODBUS BRIDGE] Corrupted frame received from %s (SyncValid=%b, CrcValid=%b)%n",
                        socket.getRemoteSocketAddress(), decoded.validSync(), decoded.validCrc());
                }
            }
        } catch (IOException ignored) {
            // Client disconnected or socket closed
        }
    }

    /**
     * Encodes a transducer measurement into the 10-byte binary frame format.
     */
    public static byte[] encodeFrame(String channelId, float value) {
        byte[] frame = new byte[FRAME_SIZE];
        frame[0] = SYNC_1;
        frame[1] = SYNC_2;

        byte[] chBytes = channelId.getBytes(StandardCharsets.US_ASCII);
        frame[2] = chBytes.length > 0 ? chBytes[0] : (byte) ' ';
        frame[3] = chBytes.length > 1 ? chBytes[1] : (byte) ' ';

        int bits = Float.floatToIntBits(value);
        frame[4] = (byte) ((bits >>> 24) & 0xFF);
        frame[5] = (byte) ((bits >>> 16) & 0xFF);
        frame[6] = (byte) ((bits >>> 8) & 0xFF);
        frame[7] = (byte) (bits & 0xFF);

        int crc = computeCrc16(frame, 0, 8);
        frame[8] = (byte) (crc & 0xFF);         // CRC Low Byte
        frame[9] = (byte) ((crc >>> 8) & 0xFF);  // CRC High Byte

        return frame;
    }

    /**
     * Decodes a 10-byte binary frame with strict CRC-16 and preamble validation.
     */
    public static ModbusFrame decodeFrame(byte[] frame) {
        if (frame == null || frame.length < FRAME_SIZE) {
            return new ModbusFrame("??", 0.0f, false, false);
        }

        boolean validSync = (frame[0] == SYNC_1 && frame[1] == SYNC_2);

        String channelId = new String(frame, 2, 2, StandardCharsets.US_ASCII).trim();

        int bits = ((frame[4] & 0xFF) << 24)
                 | ((frame[5] & 0xFF) << 16)
                 | ((frame[6] & 0xFF) << 8)
                 | (frame[7] & 0xFF);
        float value = Float.intBitsToFloat(bits);

        int recordedCrc = (frame[8] & 0xFF) | ((frame[9] & 0xFF) << 8);
        int computedCrc = computeCrc16(frame, 0, 8);
        boolean validCrc = (recordedCrc == computedCrc);

        return new ModbusFrame(channelId, value, validSync, validCrc);
    }

    /**
     * Standard Modbus CRC-16 computation (Polynomial 0xA001, Seed 0xFFFF).
     */
    public static int computeCrc16(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc = (crc >>> 1);
                }
            }
        }
        return crc & 0xFFFF;
    }

    /**
     * Executes deterministic loopback verification of encoding, decoding, and CRC fault detection.
     *
     * @return true if all codec and error detection tests pass
     */
    public static boolean runSelfTest() {
        System.out.println("[MODBUS BRIDGE] Running Loopback Codec & CRC-16 Self-Test...");

        // 1. Encode valid telemetry frame
        String testChannel = "T1";
        float testValue = 26.75f;
        byte[] frame = encodeFrame(testChannel, testValue);

        if (frame.length != FRAME_SIZE) {
            System.err.printf("[MODBUS SELF-TEST FAILED] Expected 10 bytes, got %d%n", frame.length);
            return false;
        }

        // 2. Decode nominal frame
        ModbusFrame decoded = decodeFrame(frame);
        if (!decoded.validSync() || !decoded.validCrc()) {
            System.err.println("[MODBUS SELF-TEST FAILED] Nominal frame failed sync or CRC check!");
            return false;
        }
        if (!testChannel.equals(decoded.channelId()) || Math.abs(testValue - decoded.value()) > 0.001f) {
            System.err.printf("[MODBUS SELF-TEST FAILED] Decoded payload mismatch (Ch=%s, Val=%.2f)%n",
                decoded.channelId(), decoded.value());
            return false;
        }

        // 3. Inject payload corruption (bit flip in payload byte)
        byte[] corruptedFrame = frame.clone();
        corruptedFrame[5] ^= 0x01; // Flip single bit in float payload
        ModbusFrame corruptDecoded = decodeFrame(corruptedFrame);
        if (corruptDecoded.validCrc()) {
            System.err.println("[MODBUS SELF-TEST FAILED] Corrupted frame was mistakenly accepted by CRC-16!");
            return false;
        }

        // 4. Inject preamble corruption
        byte[] syncCorruptFrame = frame.clone();
        syncCorruptFrame[0] = 0x00;
        ModbusFrame syncDecoded = decodeFrame(syncCorruptFrame);
        if (syncDecoded.validSync()) {
            System.err.println("[MODBUS SELF-TEST FAILED] Corrupted preamble was accepted!");
            return false;
        }

        System.out.printf("[MODBUS BRIDGE] Self-Test Passed: Codec deterministic, CRC-16 polynomial 0xA001 verified.%n");
        return true;
    }

    public synchronized void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }

    public int getPort() {
        return port;
    }

    public long getFramesReceived() {
        return framesReceived.get();
    }

    public long getFramesDropped() {
        return framesDropped.get();
    }
}
