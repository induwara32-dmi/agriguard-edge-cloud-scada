import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Industrial SCADA Operator Dashboard for AgriGuard RT-SCADA.
 * Features:
 * - Live microclimate trend graph plotting 60-second telemetry histories.
 * - Dynamic agronomic crop profile selector with real-time VPD monitoring.
 * - Triple-redundant field sensor telemetry matrix with TMR status badges.
 * - Interactive Actuator subsystem supporting per-device AUTO vs MANUAL OVERRIDE.
 * - Field engineering diagnostics drawer for fail-safe and interlock validation.
 * - Non-blocking 300 ms refresh cycle running on the Swing Event Dispatch Thread (EDT).
 */
public class PlantationDashboardUI extends JFrame {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Industrial Theme Palette
    private static final Color BG_DARK = new Color(16, 20, 29);
    private static final Color CARD_BG = new Color(26, 33, 48);
    private static final Color CARD_BORDER = new Color(45, 55, 77);
    private static final Color TEXT_PRIMARY = new Color(243, 244, 246);
    private static final Color TEXT_MUTED = new Color(156, 163, 175);

    private static final Color COLOR_OK = new Color(46, 125, 50);
    private static final Color COLOR_WARNING = new Color(230, 81, 0);
    private static final Color COLOR_ALERT = new Color(198, 40, 40);
    private static final Color COLOR_INFO = new Color(2, 136, 209);

    private static final Color COLOR_TEMP_TREND = new Color(255, 167, 38);
    private static final Color COLOR_HUM_TREND  = new Color(41, 182, 246);
    private static final Color COLOR_SOIL_TREND = new Color(102, 187, 106);

    private final ControlTask controlTask;
    private final WatchdogMonitor watchdog;
    private final ActuatorManager actuators;
    private final SimulatedFieldBus fieldBus;
    private final TelemetryLogger telemetryLogger;

    // Header Components
    private JComboBox<CropProfile> cropSelector;
    private JPanel interlockBanner;
    private JLabel interlockLabel;
    private JLabel lblVpdValue;
    private JLabel lblVpdDiagnostic;

    // Sensor Matrix Labels
    private JLabel lblT1Val, lblT2Val, lblT3Val;
    private JLabel lblT1Status, lblT2Status, lblT3Status;
    private JLabel lblVotedTemp, lblVoteBadge, lblVoteDetail;
    private JLabel lblHumidity, lblSoilMoisture;

    // Actuator Controls
    private ActuatorControlRow rowHeater, rowFan, rowPump, rowFogger, rowAlarm;

    // Status Bar
    private JLabel lblStatusBar;
    private boolean flashToggle = false;

    // Telemetry Trend History (60 data points at 1 second intervals)
    private static class TelemetryPoint {
        final double temp;
        final double hum;
        final double soil;

        TelemetryPoint(double t, double h, double s) {
            this.temp = t;
            this.hum = h;
            this.soil = s;
        }
    }

    private final LinkedList<TelemetryPoint> trendHistory = new LinkedList<>();
    private TelemetryTrendGraph trendGraph;
    private long lastTrendTimeMs = 0;

    public PlantationDashboardUI(
            ControlTask controlTask,
            WatchdogMonitor watchdog,
            ActuatorManager actuators,
            SimulatedFieldBus fieldBus,
            TelemetryLogger telemetryLogger) {
        super("AgriGuard RT-SCADA — Multi-Zone Plantation Climate & Irrigation Controller");
        this.controlTask = controlTask;
        this.watchdog = watchdog;
        this.actuators = actuators;
        this.fieldBus = fieldBus;
        this.telemetryLogger = telemetryLogger;

        initUI();

        // Refresh at 300 ms on EDT
        Timer refreshTimer = new Timer(300, e -> updateDashboard());
        refreshTimer.start();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Main.shutdownSystem();
            }
        });

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(BG_DARK);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. TOP HEADER SECTION
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setOpaque(false);

        // Header Title & Crop Selector
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);

        JPanel branding = new JPanel(new GridLayout(2, 1, 0, 2));
        branding.setOpaque(false);
        JLabel lblTitle = new JLabel("AGRIGUARD RT-SCADA — MULTI-ZONE CLIMATE CONTROLLER");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(TEXT_PRIMARY);
        JLabel lblSub = new JLabel("Edge Industrial SCADA | Rate-Monotonic Core | Fault-Tolerant TMR Voting");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(TEXT_MUTED);
        branding.add(lblTitle);
        branding.add(lblSub);

        JPanel headerControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerControls.setOpaque(false);

        JLabel lblCropTitle = new JLabel("Active Crop Profile:");
        lblCropTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblCropTitle.setForeground(TEXT_PRIMARY);

        cropSelector = new JComboBox<>(CropProfile.values());
        cropSelector.setSelectedItem(controlTask.getActiveProfile());
        cropSelector.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cropSelector.setBackground(CARD_BG);
        cropSelector.setForeground(Color.BLACK);
        cropSelector.addActionListener(e -> {
            CropProfile selected = (CropProfile) cropSelector.getSelectedItem();
            if (selected != null) {
                controlTask.setCropProfile(selected);
            }
        });

        headerControls.add(lblCropTitle);
        headerControls.add(cropSelector);

        titleBar.add(branding, BorderLayout.WEST);
        titleBar.add(headerControls, BorderLayout.EAST);

        // System Safety Interlock Banner
        interlockBanner = new JPanel(new BorderLayout());
        interlockBanner.setPreferredSize(new Dimension(940, 38));
        interlockBanner.setBackground(COLOR_OK);
        interlockBanner.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 40), 1));

        interlockLabel = new JLabel("SYSTEM STATUS: NORMAL — All Safety Interlocks Satisfied (Closed-Loop AUTO Active)", SwingConstants.CENTER);
        interlockLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        interlockLabel.setForeground(Color.WHITE);
        interlockBanner.add(interlockLabel, BorderLayout.CENTER);

        topPanel.add(titleBar, BorderLayout.NORTH);
        topPanel.add(interlockBanner, BorderLayout.SOUTH);
        root.add(topPanel, BorderLayout.NORTH);

        // 2. CENTER SECTION: Trend Graph & Field Telemetry Matrix
        JPanel centerContainer = new JPanel(new BorderLayout(10, 10));
        centerContainer.setOpaque(false);

        // Live Scrolling Trend Graph
        trendGraph = new TelemetryTrendGraph();
        trendGraph.setPreferredSize(new Dimension(940, 175));
        centerContainer.add(trendGraph, BorderLayout.NORTH);

        // Telemetry Grid & Actuators
        JPanel middleGrid = new JPanel(new GridLayout(1, 2, 10, 0));
        middleGrid.setOpaque(false);

        // Column A: Redundant Sensor Matrix & VPD
        JPanel sensorMatrixCard = createCardPanel("Canopy Telemetry & TMR Consensus Matrix");
        sensorMatrixCard.setLayout(new BorderLayout(0, 8));

        // T1, T2, T3 Channel Cards
        JPanel channelsRow = new JPanel(new GridLayout(1, 3, 8, 0));
        channelsRow.setOpaque(false);
        channelsRow.add(createSensorMiniCard("T1 Canopy Temp", lblT1Val = new JLabel("--.-°C"), lblT1Status = new JLabel("ONLINE")));
        channelsRow.add(createSensorMiniCard("T2 Canopy Temp", lblT2Val = new JLabel("--.-°C"), lblT2Status = new JLabel("ONLINE")));
        channelsRow.add(createSensorMiniCard("T3 Canopy Temp", lblT3Val = new JLabel("--.-°C"), lblT3Status = new JLabel("ONLINE")));
        sensorMatrixCard.add(channelsRow, BorderLayout.NORTH);

        // Consensus & Ambient Readouts
        JPanel consensusBox = new JPanel(new GridLayout(2, 2, 8, 8));
        consensusBox.setOpaque(false);

        JPanel pnlVoted = new JPanel(new GridLayout(3, 1, 0, 2));
        pnlVoted.setOpaque(false);
        JLabel lblVotedHdr = new JLabel("Voted Canopy Temp (TMR)", SwingConstants.CENTER);
        lblVotedHdr.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblVotedHdr.setForeground(TEXT_MUTED);
        lblVotedTemp = new JLabel("24.50°C", SwingConstants.CENTER);
        lblVotedTemp.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblVotedTemp.setForeground(Color.WHITE);
        lblVoteBadge = createBadgeLabel("CONSENSUS OK", COLOR_OK);
        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(lblVoteBadge);
        pnlVoted.add(lblVotedHdr);
        pnlVoted.add(lblVotedTemp);
        pnlVoted.add(badgeWrap);

        JPanel pnlVpd = new JPanel(new GridLayout(3, 1, 0, 2));
        pnlVpd.setOpaque(false);
        JLabel lblVpdHdr = new JLabel("Vapor Pressure Deficit (VPD)", SwingConstants.CENTER);
        lblVpdHdr.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblVpdHdr.setForeground(TEXT_MUTED);
        lblVpdValue = new JLabel("1.05 kPa", SwingConstants.CENTER);
        lblVpdValue.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblVpdValue.setForeground(new Color(186, 104, 200));
        lblVpdDiagnostic = new JLabel("OPTIMAL (Target: 0.8–1.2)", SwingConstants.CENTER);
        lblVpdDiagnostic.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblVpdDiagnostic.setForeground(new Color(186, 104, 200));
        pnlVpd.add(lblVpdHdr);
        pnlVpd.add(lblVpdValue);
        pnlVpd.add(lblVpdDiagnostic);

        JPanel pnlHum = new JPanel(new GridLayout(2, 1, 0, 2));
        pnlHum.setOpaque(false);
        JLabel lblHumHdr = new JLabel("Relative Humidity (H1)", SwingConstants.CENTER);
        lblHumHdr.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblHumHdr.setForeground(TEXT_MUTED);
        lblHumidity = new JLabel("68.0%", SwingConstants.CENTER);
        lblHumidity.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblHumidity.setForeground(COLOR_HUM_TREND);
        pnlHum.add(lblHumHdr);
        pnlHum.add(lblHumidity);

        JPanel pnlSoil = new JPanel(new GridLayout(2, 1, 0, 2));
        pnlSoil.setOpaque(false);
        JLabel lblSoilHdr = new JLabel("Root Zone Soil (S1)", SwingConstants.CENTER);
        lblSoilHdr.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSoilHdr.setForeground(TEXT_MUTED);
        lblSoilMoisture = new JLabel("48.0%", SwingConstants.CENTER);
        lblSoilMoisture.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblSoilMoisture.setForeground(COLOR_SOIL_TREND);
        pnlSoil.add(lblSoilHdr);
        pnlSoil.add(lblSoilMoisture);

        consensusBox.add(pnlVoted);
        consensusBox.add(pnlVpd);
        consensusBox.add(pnlHum);
        consensusBox.add(pnlSoil);
        sensorMatrixCard.add(consensusBox, BorderLayout.CENTER);

        lblVoteDetail = new JLabel("Consensus: All 3 canopy channels in mutual agreement", SwingConstants.CENTER);
        lblVoteDetail.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblVoteDetail.setForeground(new Color(209, 213, 219));
        sensorMatrixCard.add(lblVoteDetail, BorderLayout.SOUTH);

        middleGrid.add(sensorMatrixCard);

        // Column B: Actuator Control Subsystem (AUTO vs MANUAL OVERRIDE)
        JPanel actuatorCard = createCardPanel("Actuator Subsystem (AUTO Closed-Loop vs Manual Override)");
        actuatorCard.setLayout(new GridLayout(5, 1, 0, 6));

        rowHeater = new ActuatorControlRow("Thermal Curtain / Heater", actuators::setHeaterMode, actuators::setManualHeater);
        rowFan    = new ActuatorControlRow("Exhaust Ventilation Fan", actuators::setFanMode,    actuators::setManualFan);
        rowPump   = new ActuatorControlRow("Drip Irrigation Pump",   actuators::setPumpMode,   actuators::setManualPump);
        rowFogger = new ActuatorControlRow("High-Pressure Fogger",   actuators::setFoggerMode, actuators::setManualFogger);
        rowAlarm  = new ActuatorControlRow("Audio/Visual Siren",     actuators::setAlarmMode,  actuators::setManualAlarm);

        actuatorCard.add(rowHeater);
        actuatorCard.add(rowFan);
        actuatorCard.add(rowPump);
        actuatorCard.add(rowFogger);
        actuatorCard.add(rowAlarm);

        middleGrid.add(actuatorCard);
        centerContainer.add(middleGrid, BorderLayout.CENTER);
        root.add(centerContainer, BorderLayout.CENTER);

        // 3. BOTTOM SECTION: Field Diagnostics Drawer & Status Bar
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
        bottomPanel.setOpaque(false);

        JPanel diagnosticsCard = createCardPanel("Field Commissioning & Fault Simulation Drawer");
        diagnosticsCard.setLayout(new GridLayout(2, 5, 6, 6));

        diagnosticsCard.add(createActionButton("Inject T2 Bias (+15°C)", e -> Main.injectF1()));
        diagnosticsCard.add(createActionButton("Disconnect T3 Channel", e -> Main.injectF2()));
        diagnosticsCard.add(createActionButton("Dual-Channel Failure", e -> Main.injectF3()));
        diagnosticsCard.add(createActionButton("Thermal Surge (+14°C)", e -> Main.injectF5()));
        diagnosticsCard.add(createActionButton("Freeze T1 Sensor", e -> Main.injectStuck()));

        diagnosticsCard.add(createActionButton("Simulate Controller Fault", e -> Main.injectException()));
        diagnosticsCard.add(createActionButton("Reset Field Bus", e -> Main.resetAll()));

        JButton btnAudit = createActionButton("Run Concurrency Audit", e -> new Thread(Main::injectF4, "SCADA-Benchmark").start());
        diagnosticsCard.add(btnAudit);

        JButton btnExport = createActionButton("Export Telemetry CSV", e -> exportTelemetryCsv());
        btnExport.setBackground(new Color(21, 101, 192));
        diagnosticsCard.add(btnExport);

        JButton btnShutdown = createActionButton("Shutdown & Timing Audit", e -> Main.shutdownSystem());
        btnShutdown.setBackground(new Color(183, 28, 28));
        diagnosticsCard.add(btnShutdown);

        bottomPanel.add(diagnosticsCard, BorderLayout.CENTER);

        lblStatusBar = new JLabel("System Operational. Edge Historian logging active to logs/plantation_telemetry.csv");
        lblStatusBar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblStatusBar.setForeground(TEXT_MUTED);
        bottomPanel.add(lblStatusBar, BorderLayout.SOUTH);

        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(980, 750));
        setLocationRelativeTo(null);
    }

    /**
     * Periodic telemetry update scheduled every 300 ms on EDT.
     */
    private void updateDashboard() {
        flashToggle = !flashToggle;
        long now = System.currentTimeMillis();

        // 1. Safety Interlock Banner
        boolean isSafe = watchdog.isSafeState();
        if (isSafe) {
            Color alertBg = flashToggle ? COLOR_ALERT : new Color(130, 20, 20);
            interlockBanner.setBackground(alertBg);
            interlockLabel.setText("⚠ FAIL-SAFE INTERLOCK ACTIVE — Heater: OFF | Fan: ON | Alarm: ON | Pump: OFF");
        } else {
            interlockBanner.setBackground(COLOR_OK);
            interlockLabel.setText(String.format("SYSTEM STATUS: NORMAL — Closed-Loop Auto [%s] Active",
                controlTask.getActiveProfile().getDisplayName()));
        }

        // 2. Redundant Sensor Channels T1, T2, T3
        updateChannelCard("T1", lblT1Val, lblT1Status);
        updateChannelCard("T2", lblT2Val, lblT2Status);
        updateChannelCard("T3", lblT3Val, lblT3Status);

        // 3. TMR Consensus Voting & Ambient Telemetry
        TMRVoter.VoteResult vr = controlTask.getLatestVoteResult();
        lblVotedTemp.setText(String.format("%.2f°C", vr.temperature()));
        lblVoteBadge.setText(vr.getDisplayLabel());
        lblVoteBadge.setBackground(switch (vr.status()) {
            case OK -> COLOR_OK;
            case MASKED -> COLOR_WARNING;
            case DEGRADED -> new Color(245, 127, 23);
            case FAILED -> COLOR_ALERT;
        });
        lblVoteDetail.setText("Consensus: " + vr.detail());

        double hum = controlTask.getLatestHumidity();
        double soil = controlTask.getLatestSoilMoisture();
        lblHumidity.setText(String.format("%.1f%%", hum));
        lblSoilMoisture.setText(String.format("%.1f%%", soil));

        // 4. Vapor Pressure Deficit (VPD)
        double vpd = controlTask.getLatestVpd();
        lblVpdValue.setText(String.format("%.2f kPa", vpd));
        CropProfile profile = controlTask.getActiveProfile();
        String vpdStatus = profile.evaluateVpdStatus(vpd);
        lblVpdDiagnostic.setText(String.format("%s (Target: %.2f–%.2f)",
            vpdStatus, profile.getTargetVpdMin(), profile.getTargetVpdMax()));

        // 5. Update Actuator Control Rows
        rowHeater.updateState(actuators.isHeaterOn(), actuators.getHeaterMode(), isSafe);
        rowFan.updateState(actuators.isFanOn(),       actuators.getFanMode(),    isSafe);
        rowPump.updateState(actuators.isPumpOn(),     actuators.getPumpMode(),   isSafe);
        rowFogger.updateState(actuators.isFoggerOn(), actuators.getFoggerMode(), isSafe);
        rowAlarm.updateState(actuators.isAlarmOn(),   actuators.getAlarmMode(),  isSafe);

        // 6. Record to 60-second live trend buffer (once per second)
        if (now - lastTrendTimeMs >= 950) {
            lastTrendTimeMs = now;
            trendHistory.addLast(new TelemetryPoint(vr.temperature(), hum, soil));
            if (trendHistory.size() > 60) {
                trendHistory.removeFirst();
            }
            trendGraph.repaint();
        }

        lblStatusBar.setText(String.format("[%s] Profile: %s | Field Bus: OK | Interlock: %s | Historian: logs/plantation_telemetry.csv",
            LocalTime.now().format(TIME_FMT),
            profile.getDisplayName(),
            isSafe ? "TRIPPED" : "NOMINAL"
        ));
    }

    private void updateChannelCard(String chId, JLabel lblVal, JLabel lblStatus) {
        boolean active = watchdog.isSensorAlive(chId);
        SensorReading reading = controlTask.getLastReading(chId);

        if (!active) {
            lblVal.setText("OFFLINE");
            lblVal.setForeground(COLOR_ALERT);
            lblStatus.setText("DISCONNECTED");
            lblStatus.setForeground(COLOR_ALERT);
        } else if (reading != null) {
            lblVal.setText(String.format("%.2f°C", reading.value()));
            lblVal.setForeground(Color.WHITE);

            if (reading.value() > 34.0 || reading.value() < 17.0) {
                lblStatus.setText("BIAS OUTLIER");
                lblStatus.setForeground(new Color(255, 183, 77));
            } else {
                lblStatus.setText("ONLINE");
                lblStatus.setForeground(new Color(129, 199, 132));
            }
        } else {
            lblVal.setText("--.-°C");
            lblStatus.setText("SCANNING");
            lblStatus.setForeground(TEXT_MUTED);
        }
    }

    private void exportTelemetryCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("plantation_audit_export.csv"));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            boolean ok = telemetryLogger.exportAuditLog(dest);
            if (ok) {
                JOptionPane.showMessageDialog(this,
                    "Telemetry audit log successfully exported to:\n" + dest.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Failed to export telemetry audit log.",
                    "Export Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- Helper Component Builders ---

    private JPanel createCardPanel(String title) {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1),
            " " + title + " ",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 12),
            TEXT_PRIMARY
        );
        card.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(6, 8, 8, 8)));
        return card;
    }

    private JPanel createSensorMiniCard(String name, JLabel lblVal, JLabel lblStatus) {
        JPanel pnl = new JPanel(new GridLayout(3, 1, 0, 2));
        pnl.setBackground(new Color(33, 41, 59));
        pnl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1),
            new EmptyBorder(4, 6, 4, 6)
        ));

        JLabel lblName = new JLabel(name, SwingConstants.CENTER);
        lblName.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblName.setForeground(TEXT_MUTED);

        lblVal.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblVal.setHorizontalAlignment(SwingConstants.CENTER);
        lblVal.setForeground(Color.WHITE);

        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblStatus.setForeground(new Color(129, 199, 132));

        pnl.add(lblName);
        pnl.add(lblVal);
        pnl.add(lblStatus);
        return pnl;
    }

    private JLabel createBadgeLabel(String text, Color bg) {
        JLabel badge = new JLabel(text, SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(Color.WHITE);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return badge;
    }

    private JButton createActionButton(String text, java.awt.event.ActionListener al) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBackground(new Color(45, 55, 77));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        btn.addActionListener(al);
        return btn;
    }

    // --- Actuator Control Row Component ---

    private static class ActuatorControlRow extends JPanel {
        private final JLabel lblName;
        private final JLabel lblStateBadge;
        private final JButton btnMode;
        private final JButton btnToggle;

        private final java.util.function.Consumer<ActuatorManager.ControlMode> modeSetter;
        private final java.util.function.Consumer<Boolean> manualSetter;

        private boolean currentState = false;
        private ActuatorManager.ControlMode currentMode = ActuatorManager.ControlMode.AUTO;

        ActuatorControlRow(
                String name,
                java.util.function.Consumer<ActuatorManager.ControlMode> modeSetter,
                java.util.function.Consumer<Boolean> manualSetter) {
            super(new BorderLayout(8, 0));
            this.modeSetter = modeSetter;
            this.manualSetter = manualSetter;
            setOpaque(false);

            lblName = new JLabel(name);
            lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblName.setForeground(TEXT_PRIMARY);
            lblName.setPreferredSize(new Dimension(180, 24));

            lblStateBadge = new JLabel("OFF", SwingConstants.CENTER);
            lblStateBadge.setOpaque(true);
            lblStateBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lblStateBadge.setBackground(new Color(55, 65, 81));
            lblStateBadge.setForeground(TEXT_MUTED);
            lblStateBadge.setPreferredSize(new Dimension(50, 24));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            controls.setOpaque(false);

            btnMode = new JButton("AUTO");
            btnMode.setFont(new Font("Segoe UI", Font.BOLD, 10));
            btnMode.setBackground(new Color(38, 50, 56));
            btnMode.setForeground(Color.WHITE);
            btnMode.setFocusPainted(false);
            btnMode.setPreferredSize(new Dimension(75, 24));
            btnMode.addActionListener(e -> {
                ActuatorManager.ControlMode target = (currentMode == ActuatorManager.ControlMode.AUTO)
                    ? ActuatorManager.ControlMode.MANUAL_OVERRIDE
                    : ActuatorManager.ControlMode.AUTO;
                modeSetter.accept(target);
            });

            btnToggle = new JButton("TOGGLE");
            btnToggle.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            btnToggle.setBackground(new Color(55, 65, 81));
            btnToggle.setForeground(Color.WHITE);
            btnToggle.setFocusPainted(false);
            btnToggle.setEnabled(false);
            btnToggle.setPreferredSize(new Dimension(80, 24));
            btnToggle.addActionListener(e -> manualSetter.accept(!currentState));

            controls.add(btnMode);
            controls.add(btnToggle);

            add(lblName, BorderLayout.WEST);
            add(lblStateBadge, BorderLayout.CENTER);
            add(controls, BorderLayout.EAST);
        }

        void updateState(boolean state, ActuatorManager.ControlMode mode, boolean isSafe) {
            this.currentState = state;
            this.currentMode = mode;

            if (state) {
                lblStateBadge.setText("ON");
                lblStateBadge.setBackground(new Color(0, 150, 136));
                lblStateBadge.setForeground(Color.WHITE);
            } else {
                lblStateBadge.setText("OFF");
                lblStateBadge.setBackground(new Color(55, 65, 81));
                lblStateBadge.setForeground(TEXT_MUTED);
            }

            if (mode == ActuatorManager.ControlMode.AUTO) {
                btnMode.setText("AUTO");
                btnMode.setBackground(new Color(30, 70, 40));
                btnToggle.setEnabled(false);
            } else {
                btnMode.setText("MANUAL");
                btnMode.setBackground(new Color(130, 80, 20));
                btnToggle.setEnabled(!isSafe);
            }
        }
    }

    // --- Custom Telemetry Trend Graph Panel ---

    private class TelemetryTrendGraph extends JPanel {

        TelemetryTrendGraph() {
            setBackground(new Color(20, 26, 38));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1),
                new EmptyBorder(6, 6, 6, 6)
            ));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int padLeft = 45;
            int padBottom = 25;
            int padTop = 15;
            int padRight = 15;

            int graphW = w - padLeft - padRight;
            int graphH = h - padTop - padBottom;

            // Draw Background Gridlines
            g2.setColor(new Color(35, 45, 65));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));

            for (int yVal = 0; yVal <= 100; yVal += 25) {
                int yPos = padTop + graphH - (int) ((yVal / 100.0) * graphH);
                g2.drawLine(padLeft, yPos, padLeft + graphW, yPos);
                g2.setColor(TEXT_MUTED);
                g2.drawString(yVal + "", 10, yPos + 4);
                g2.setColor(new Color(35, 45, 65));
            }

            // X-axis time marks
            g2.setColor(TEXT_MUTED);
            g2.drawString("-60s", padLeft, h - 8);
            g2.drawString("-30s", padLeft + (graphW / 2) - 10, h - 8);
            g2.drawString("Now", padLeft + graphW - 24, h - 8);

            // Draw Legend at Top Right
            int legX = w - 340;
            g2.setColor(COLOR_TEMP_TREND);
            g2.fillRect(legX, 6, 10, 10);
            g2.setColor(TEXT_PRIMARY);
            g2.drawString("Temp (°C)", legX + 14, 15);

            g2.setColor(COLOR_HUM_TREND);
            g2.fillRect(legX + 105, 6, 10, 10);
            g2.setColor(TEXT_PRIMARY);
            g2.drawString("Humidity (%)", legX + 119, 15);

            g2.setColor(COLOR_SOIL_TREND);
            g2.fillRect(legX + 220, 6, 10, 10);
            g2.setColor(TEXT_PRIMARY);
            g2.drawString("Soil Moisture (%)", legX + 234, 15);

            // Plot data points
            synchronized (trendHistory) {
                int size = trendHistory.size();
                if (size > 1) {
                    double xStep = (double) graphW / 60.0;
                    int startIdx = 60 - size;

                    // Plot Temperature
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.setColor(COLOR_TEMP_TREND);
                    for (int i = 0; i < size - 1; i++) {
                        int x1 = padLeft + (int) ((startIdx + i) * xStep);
                        int y1 = padTop + graphH - (int) ((Math.min(100.0, trendHistory.get(i).temp) / 100.0) * graphH);
                        int x2 = padLeft + (int) ((startIdx + i + 1) * xStep);
                        int y2 = padTop + graphH - (int) ((Math.min(100.0, trendHistory.get(i + 1).temp) / 100.0) * graphH);
                        g2.drawLine(x1, y1, x2, y2);
                    }

                    // Plot Humidity
                    g2.setColor(COLOR_HUM_TREND);
                    for (int i = 0; i < size - 1; i++) {
                        int x1 = padLeft + (int) ((startIdx + i) * xStep);
                        int y1 = padTop + graphH - (int) ((Math.min(100.0, trendHistory.get(i).hum) / 100.0) * graphH);
                        int x2 = padLeft + (int) ((startIdx + i + 1) * xStep);
                        int y2 = padTop + graphH - (int) ((Math.min(100.0, trendHistory.get(i + 1).hum) / 100.0) * graphH);
                        g2.drawLine(x1, y1, x2, y2);
                    }

                    // Plot Soil Moisture
                    g2.setColor(COLOR_SOIL_TREND);
                    for (int i = 0; i < size - 1; i++) {
                        int x1 = padLeft + (int) ((startIdx + i) * xStep);
                        int y1 = padTop + graphH - (int) ((Math.min(100.0, trendHistory.get(i).soil) / 100.0) * graphH);
                        int x2 = padLeft + (int) ((startIdx + i + 1) * xStep);
                        int y2 = padTop + graphH - (int) ((Math.min(100.0, trendHistory.get(i + 1).soil) / 100.0) * graphH);
                        g2.drawLine(x1, y1, x2, y2);
                    }
                }
            }

            g2.dispose();
        }
    }
}
