// ===================== GUI.java =====================
package org.example;

// ===================== What the imports are for =====================
// javax.swing.*  => Swing UI components: JFrame, JPanel, JButton, JLabel, JComboBox, JCheckBox, JSlider, JScrollPane etc.
// javax.swing.border.TitledBorder => titled border drawn around a panel (heading on top of the box)
// java.awt.*     => UI layout + colors + fonts + dimensions + cursor etc.
// java.awt.image.BufferedImage => image type (used to pass a chart snapshot to the PDF export)
// java.util.Hashtable => slider labels (JSlider label table needs a Hashtable)
// java.util.Locale => keeps number formatting stable (avoids decimal dot/comma issues)

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.Locale;

public final class GUI {

    // Private constructor => this class cannot be instantiated
    // The GUI is used only through the static launch() method.
    private GUI() {}

    // ===================== UI Theme =====================
    // These colors define the look of the UI.
    // HEX color: 0xRRGGBB format.
    private static final Color BG_DARK = new Color(0x0F172A);   // main window background (dark)
    private static final Color BG_PANEL = new Color(0x111827);  // side panels background
    private static final Color ACCENT_BLUE = new Color(0x3B82F6);   // blue buttons
    private static final Color ACCENT_GREEN = new Color(0x22C55E);  // green buttons
    private static final Color ACCENT_RED = new Color(0xEF4444);    // red buttons
    private static final Color TEXT_MUTED = new Color(0x9CA3AF);    // border title text color
    private static final Color BORDER_COL = new Color(0x1F2937);    // border line color

    // ===================== FILTER STATE =====================
    // MapVisualisation.MapPanel draws the vehicles.
    // The filter decides which vehicles are drawn and which are hidden.
    public static class VehicleFilter implements MapVisualisation.Filter {

        // volatile: keeps the latest value visible across threads.
        // The UI thread changes the checkboxes, the SUMO thread draws the map.
        public volatile boolean showCars = true;
        public volatile boolean showTrucks = true;
        public volatile boolean showBuses = true;

        // min speed filter (m/s)
        public volatile double minSpeedMps = 0.0;

        // vehicle type allow/hide
        private boolean allowsType(String type) {
            // Main.TYPE_CAR etc string constants (e.g., "car")
            if (Main.TYPE_CAR.equals(type)) return showCars;
            if (Main.TYPE_TRUCK.equals(type)) return showTrucks;
            if (Main.TYPE_BUS.equals(type)) return showBuses;

            // unknown type => show by default
            return true;
        }

        // Called by MapPanel: "is this vehicle allowed?"
        @Override
        public boolean allows(String type, double speedMps) {
            // allow only if type allowed AND speed >= minSpeed
            return allowsType(type) && speedMps >= minSpeedMps;
        }
    }

    // Global filter: GUI checkboxes and sliders update the values of this object
    public static final VehicleFilter FILTER = new VehicleFilter();

    // ===================== UI Labels =====================
    // These labels in the right panel are updated live (by LiveConnectionSumo)
    private static final JLabel activeVehiclesLabel = new JLabel("Active Vehicles (all): 0");
    private static final JLabel visibleVehiclesLabel = new JLabel("Visible Vehicles (filtered): 0");
    private static final JLabel byTypeLabel = new JLabel("By Type: car=0 truck=0 bus=0");

    private static final JLabel avgWaitLabel = new JLabel("Avg Wait Time: 0.0 s");
    private static final JLabel congestionLabel = new JLabel("Congestion Index: 0.00");
    private static final JLabel throughputLabel = new JLabel("Throughput: 0 v/h");
    private static final JLabel meanSpeedLabel = new JLabel("Mean Speed: 0.0 m/s");

    // Traffic light status label (selected TLS, state etc)
    private static final JLabel tlStateLabel = new JLabel("TL State: -");

    // ===================== UI Parts holder =====================
    // Problem: the GUI has many components => too many local variables
    // Solution: store them all in UiParts => clean access in wireActions(...).
    private static class UiParts {
        JFrame frame;                               // main window
        MapVisualisation.MapPanel mapPanel;         // center map panel
        MapVisualisation.TrendChartPanel trendChart;// chart panel (right side)

        // Buttons
        JButton startBtn, stopBtn, exportBtn, exportPdfBtn;
        JButton carBtn, truckBtn, busBtn;
        JButton tlRedBtn, tlGreenBtn, tlResetBtn;

        // Input widgets
        JSlider simSpeedSlider, minSpeedSlider;
        JTextField numVehiclesField;
        JComboBox<VehicleInjection.RouteDef> routeCombo;
        JComboBox<TrafficControl.TlsItem> tlCombo;

        // Filter toggles
        JCheckBox showCarsCb, showTrucksCb, showBusesCb;
        JCheckBox ruleBasedTlsCb;

        // Min speed label
        JLabel minSpeedValueLabel;
    }

    // ===================== Launch =====================
    // Builds and shows the complete GUI.
    public static void launch() {

        // 1) Set Look & Feel: native OS look (Windows/Mac)
        setSystemLookAndFeel();

        // 2) UiParts holder create
        UiParts ui = new UiParts();

        // 3) Frame build (window)
        ui.frame = buildFrame();

        // 4) Map panel build + add center
        ui.mapPanel = buildMapPanel();
        ui.frame.add(ui.mapPanel, BorderLayout.CENTER);

        // 5) Left controls build (scrollable) + add WEST
        // Important: buildControlsPanel returns JScrollPane
        JScrollPane controlsScroll = buildControlsPanel(ui);
        ui.frame.add(controlsScroll, BorderLayout.WEST);

        // 6) Right metrics build + add EAST
        JPanel metricsPanel = buildMetricsPanel(ui);
        ui.frame.add(metricsPanel, BorderLayout.EAST);

        // 7) Window show in center
        ui.frame.setLocationRelativeTo(null); // screen center
        ui.frame.setVisible(true);            // display it

        // 8) onStopped => close the app when the simulation stops
        Runnable onStopped = () -> {
            ui.frame.dispose();  // window close
            System.exit(0);      // JVM exit
        };

        // 9) TrafficControl: handles the TLS dropdown and status label
        TrafficControl trafficControl = new TrafficControl(ui.tlCombo, tlStateLabel);

        // 10) LiveConnectionSumo: live connection to SUMO + metrics + map updates
        LiveConnectionSumo live = new LiveConnectionSumo(
                ui.frame,
                ui.mapPanel,
                ui.trendChart,
                FILTER,
                activeVehiclesLabel,
                visibleVehiclesLabel,
                byTypeLabel,
                avgWaitLabel,
                congestionLabel,
                throughputLabel,
                meanSpeedLabel,
                tlStateLabel,
                ui.routeCombo,
                ui.tlCombo,
                trafficControl,
                onStopped
        );

        // 11) Register listeners for buttons and sliders
        wireActions(ui, live, trafficControl);

        Logging.LOG.info("UI ready. Scenarios + long variants will populate after SUMO starts.");
    }

    // ===================== Frame =====================
    private static JFrame buildFrame() {
        // JFrame = main window
        JFrame frame = new JFrame("Traffic Grid Simulation");

        // close button (X) press => program exit
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // window size
        frame.setSize(1220, 680);

        // BorderLayout: WEST(left), CENTER(middle), EAST(right)
        frame.setLayout(new BorderLayout());

        // background set
        frame.getContentPane().setBackground(BG_DARK);
        return frame;
    }

    // ===================== Map panel =====================
    private static MapVisualisation.MapPanel buildMapPanel() {
        // MapPanel takes the filter => decides which vehicles to draw
        MapVisualisation.MapPanel mapPanel = new MapVisualisation.MapPanel(FILTER);

        // white map background
        mapPanel.setBackground(Color.WHITE);
        return mapPanel;
    }

    // ===================== Left controls (scrollable) =====================
    private static JScrollPane buildControlsPanel(UiParts ui) {

        // Font for section titles
        Font titleFont = new Font("SansSerif", Font.BOLD, 13);

        // controls panel (vertical)
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS)); // top-to-bottom layout
        controls.setBackground(BG_PANEL);
        styleTitledBorder(controls, "Simulation Controls");            // panel heading + border
        controls.setPreferredSize(new Dimension(400, 0));              // width fixed, height auto

        // ===================== Widgets create =====================

        // Start/Stop
        ui.startBtn = new JButton("Start");
        ui.stopBtn = new JButton("Stop");

        // Speed slider
        JLabel speedLabel = new JLabel("Speed");
        ui.simSpeedSlider = buildSpeedSlider();

        // Export
        ui.exportBtn = new JButton("Export CSV");
        ui.exportPdfBtn = new JButton("Export PDF");

        // Route dropdown (populated after start => initially disabled)
        JLabel routeLabel = new JLabel("Select scenario (same destination, 4 long variants)");
        ui.routeCombo = new JComboBox<>();
        ui.routeCombo.setEnabled(false);
        ui.routeCombo.setMaximumSize(new Dimension(360, 30));
        ui.routeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Vehicles count text field
        JLabel numVehiclesLabel = new JLabel("Vehicles");
        ui.numVehiclesField = new JTextField("10");
        ui.numVehiclesField.setFont(new Font("SansSerif", Font.BOLD, 14));
        ui.numVehiclesField.setAlignmentX(Component.LEFT_ALIGNMENT);
        ui.numVehiclesField.setMaximumSize(new Dimension(160, 28));

        // Spawn type buttons
        JLabel vehicleTypeLabel = new JLabel("Spawn type");
        ui.carBtn = new JButton("Car");
        ui.truckBtn = new JButton("Truck");
        ui.busBtn = new JButton("Bus");

        // Buttons placed in a single row panel (FlowLayout)
        JPanel vehicleTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        vehicleTypePanel.setBackground(BG_PANEL);
        vehicleTypePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        vehicleTypePanel.add(ui.carBtn);
        vehicleTypePanel.add(ui.truckBtn);
        vehicleTypePanel.add(ui.busBtn);

        // Map filters checkboxes
        JLabel filterTitle = new JLabel("Map Filters");
        ui.showCarsCb = styleCheckBox(new JCheckBox("Show Cars", true));
        ui.showTrucksCb = styleCheckBox(new JCheckBox("Show Trucks", true));
        ui.showBusesCb = styleCheckBox(new JCheckBox("Show Buses", true));

        // Min speed slider + label
        JLabel minSpeedTitle = new JLabel("Min Speed Filter");
        ui.minSpeedValueLabel = new JLabel(">= 0.0 m/s (0 km/h)");
        ui.minSpeedValueLabel.setForeground(new Color(220, 220, 220));
        ui.minSpeedValueLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        ui.minSpeedValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        ui.minSpeedSlider = new JSlider(0, 35, 0);
        ui.minSpeedSlider.setMajorTickSpacing(5);
        ui.minSpeedSlider.setPaintTicks(true);
        ui.minSpeedSlider.setBackground(BG_PANEL);
        ui.minSpeedSlider.setForeground(Color.WHITE);
        ui.minSpeedSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        ui.minSpeedSlider.setMaximumSize(new Dimension(320, 44));

        // Traffic light controls
        JLabel tlLabel = new JLabel("Traffic Light");
        ui.tlCombo = new JComboBox<>();
        ui.tlCombo.setEnabled(false); // populated after SUMO starts
        ui.tlCombo.setMaximumSize(new Dimension(360, 30));
        ui.tlCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        ui.tlRedBtn = new JButton("Red");
        ui.tlGreenBtn = new JButton("Green");
        ui.tlResetBtn = new JButton("Reset");

        JPanel tlButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tlButtonPanel.setBackground(BG_PANEL);
        tlButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tlButtonPanel.add(ui.tlRedBtn);
        tlButtonPanel.add(ui.tlGreenBtn);
        tlButtonPanel.add(ui.tlResetBtn);

        // Rule-based TLS checkbox
        ui.ruleBasedTlsCb = styleCheckBox(new JCheckBox("Rule-based TLS (stop/go)", false));

        // ===================== Styling =====================
        // labels
        styleSectionLabel(speedLabel, titleFont);
        styleSectionLabel(routeLabel, new Font("SansSerif", Font.BOLD, 12));
        styleSectionLabel(numVehiclesLabel, titleFont);
        styleSectionLabel(vehicleTypeLabel, titleFont);
        styleSectionLabel(filterTitle, titleFont);
        styleSectionLabel(minSpeedTitle, new Font("SansSerif", Font.BOLD, 12));
        styleSectionLabel(tlLabel, titleFont);

        // buttons colors
        stylePrimaryButton(ui.startBtn, ACCENT_GREEN);
        stylePrimaryButton(ui.stopBtn, ACCENT_RED);
        stylePrimaryButton(ui.exportBtn, ACCENT_BLUE);
        stylePrimaryButton(ui.exportPdfBtn, ACCENT_BLUE);

        stylePrimaryButton(ui.carBtn, ACCENT_BLUE);
        stylePrimaryButton(ui.truckBtn, ACCENT_BLUE);
        stylePrimaryButton(ui.busBtn, ACCENT_BLUE);

        stylePrimaryButton(ui.tlRedBtn, ACCENT_RED);
        stylePrimaryButton(ui.tlGreenBtn, ACCENT_GREEN);
        stylePrimaryButton(ui.tlResetBtn, ACCENT_BLUE);

        // ===================== Layout add order =====================
        // Pattern: vertical strut = gap (spacing)
        // controls.add(component) = add the component to the panel

        controls.add(Box.createVerticalStrut(6));
        controls.add(rowPanel(ui.startBtn, ui.stopBtn));
        controls.add(Box.createVerticalStrut(6));
        controls.add(rowPanel(ui.exportBtn, ui.exportPdfBtn));

        controls.add(Box.createVerticalStrut(10));
        controls.add(speedLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(ui.simSpeedSlider);

        controls.add(Box.createVerticalStrut(10));
        controls.add(routeLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(ui.routeCombo);

        controls.add(Box.createVerticalStrut(10));
        controls.add(numVehiclesLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(ui.numVehiclesField);

        controls.add(Box.createVerticalStrut(10));
        controls.add(vehicleTypeLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(vehicleTypePanel);

        controls.add(Box.createVerticalStrut(12));
        controls.add(filterTitle);
        controls.add(Box.createVerticalStrut(4));
        controls.add(ui.showCarsCb);
        controls.add(ui.showTrucksCb);
        controls.add(ui.showBusesCb);

        controls.add(Box.createVerticalStrut(8));
        controls.add(minSpeedTitle);
        controls.add(Box.createVerticalStrut(2));
        controls.add(ui.minSpeedValueLabel);
        controls.add(Box.createVerticalStrut(2));
        controls.add(ui.minSpeedSlider);

        controls.add(Box.createVerticalStrut(12));
        controls.add(tlLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(ui.tlCombo);
        controls.add(Box.createVerticalStrut(4));
        controls.add(tlButtonPanel);

        controls.add(Box.createVerticalStrut(6));
        controls.add(ui.ruleBasedTlsCb);

        controls.add(Box.createVerticalGlue()); // remaining space push down

        // ===================== Scroll wrapper =====================
        // JScrollPane = scrollable container
        JScrollPane controlsScroll = new JScrollPane(
                controls,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );

        controlsScroll.setBorder(null);
        controlsScroll.getVerticalScrollBar().setUnitIncrement(14); // scroll speed
        controlsScroll.getViewport().setBackground(BG_PANEL);
        controlsScroll.setPreferredSize(new Dimension(420, 0)); // fixed width

        return controlsScroll;
    }

    // Speed slider with labels 1x..10x
    private static JSlider buildSpeedSlider() {
        JSlider s = new JSlider(1, 10, 1);
        s.setMajorTickSpacing(1);
        s.setPaintTicks(true);

        // Label table: slider value -> label component
        Hashtable<Integer, JLabel> speedLabelTable = new Hashtable<>();
        JLabel minL = new JLabel("1x"), maxL = new JLabel("10x");
        minL.setForeground(Color.WHITE);
        maxL.setForeground(Color.WHITE);
        speedLabelTable.put(1, minL);
        speedLabelTable.put(10, maxL);

        s.setLabelTable(speedLabelTable);
        s.setPaintLabels(true);
        s.setBackground(BG_PANEL);
        s.setForeground(Color.WHITE);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(320, 44));

        return s;
    }

    // ===================== Right metrics =====================
    private static JPanel buildMetricsPanel(UiParts ui) {
        JPanel metrics = new JPanel();
        metrics.setLayout(new BoxLayout(metrics, BoxLayout.Y_AXIS)); // vertical
        metrics.setBackground(BG_PANEL);
        styleTitledBorder(metrics, "Metrics");

        // Labels style
        Font metricsFont = new Font("SansSerif", Font.BOLD, 12);
        for (JLabel l : new JLabel[]{
                activeVehiclesLabel, visibleVehiclesLabel, byTypeLabel,
                avgWaitLabel, congestionLabel, throughputLabel, meanSpeedLabel,
                tlStateLabel
        }) {
            l.setForeground(Color.WHITE);
            l.setFont(metricsFont);
        }

        // Chart panel (graph of metrics)
        ui.trendChart = new MapVisualisation.TrendChartPanel(120);
        ui.trendChart.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Add components (same pattern: gap + component)
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(ui.trendChart);
        metrics.add(Box.createVerticalStrut(8));

        metrics.add(activeVehiclesLabel);
        metrics.add(Box.createVerticalStrut(4));
        metrics.add(visibleVehiclesLabel);
        metrics.add(Box.createVerticalStrut(4));
        metrics.add(byTypeLabel);

        metrics.add(Box.createVerticalStrut(8));
        metrics.add(avgWaitLabel);
        metrics.add(Box.createVerticalStrut(4));
        metrics.add(congestionLabel);
        metrics.add(Box.createVerticalStrut(4));
        metrics.add(throughputLabel);
        metrics.add(Box.createVerticalStrut(4));
        metrics.add(meanSpeedLabel);

        metrics.add(Box.createVerticalStrut(10));
        metrics.add(tlStateLabel);
        metrics.add(Box.createVerticalGlue());

        return metrics;
    }

    // ===================== Wiring actions =====================
    private static void wireActions(UiParts ui, LiveConnectionSumo live, TrafficControl trafficControl) {

        // Start button
        ui.startBtn.addActionListener(e -> {
            live.startSimulation();        // SUMO connect + start loop
            ui.startBtn.setEnabled(false); // avoid double start
        });

        // Stop button
        ui.stopBtn.addActionListener(e -> live.stopSimulation());

        // Speed slider
        ui.simSpeedSlider.addChangeListener(e -> {
            int factor = ui.simSpeedSlider.getValue(); // 1..10

            // delay formula: 1x => 100ms, 10x => 10ms
            int delayMs = 110 - 10 * factor;

            live.setSpeedDelay(delayMs, factor);
            Logging.LOG.info("Sim speed set: " + factor + "x (delay=" + delayMs + "ms)");
        });

        // Export CSV
        ui.exportBtn.addActionListener(e -> {
            Object sel = ui.routeCombo.getSelectedItem();
            String routeName = (sel instanceof VehicleInjection.RouteDef)
                    ? ((VehicleInjection.RouteDef) sel).name
                    : "";
            live.exportMetricsCsv(ui.frame, routeName);
        });

        // Export PDF: chart screenshot + summary
        ui.exportPdfBtn.addActionListener(e -> {
            Object sel = ui.routeCombo.getSelectedItem();
            String routeName = (sel instanceof VehicleInjection.RouteDef)
                    ? ((VehicleInjection.RouteDef) sel).name
                    : "";
            BufferedImage chartImg = MapVisualisation.renderComponentToImage(ui.trendChart, 900, 320);
            live.exportSummaryPdf(ui.frame, routeName, chartImg);
        });

        // Vehicle count safe supplier: invalid input => 1
        java.util.function.Supplier<Integer> numVehiclesSupplier = () -> {
            try { return Math.max(1, Integer.parseInt(ui.numVehiclesField.getText().trim())); }
            catch (Exception ex) { return 1; }
        };

        // Inject vehicles by type
        ui.carBtn.addActionListener(e -> VehicleInjection.injectVehicles(ui.frame, Main.TYPE_CAR,
                (VehicleInjection.RouteDef) ui.routeCombo.getSelectedItem(), numVehiclesSupplier.get()));
        ui.truckBtn.addActionListener(e -> VehicleInjection.injectVehicles(ui.frame, Main.TYPE_TRUCK,
                (VehicleInjection.RouteDef) ui.routeCombo.getSelectedItem(), numVehiclesSupplier.get()));
        ui.busBtn.addActionListener(e -> VehicleInjection.injectVehicles(ui.frame, Main.TYPE_BUS,
                (VehicleInjection.RouteDef) ui.routeCombo.getSelectedItem(), numVehiclesSupplier.get()));

        // TLS dropdown selection => pass the selected id to TrafficControl
        ui.tlCombo.addActionListener(e -> {
            TrafficControl.TlsItem item = (TrafficControl.TlsItem) ui.tlCombo.getSelectedItem();
            trafficControl.setSelectedTls(item == null ? null : item.id);
        });

        // Force RED / GREEN / Reset
        ui.tlRedBtn.addActionListener(e -> {
            TrafficControl.TlsItem item = (TrafficControl.TlsItem) ui.tlCombo.getSelectedItem();
            String tlsId = (item == null) ? null : item.id;
            if (tlsId != null) trafficControl.forceTrafficLightRed(tlsId);
        });

        ui.tlGreenBtn.addActionListener(e -> {
            TrafficControl.TlsItem item = (TrafficControl.TlsItem) ui.tlCombo.getSelectedItem();
            String tlsId = (item == null) ? null : item.id;
            if (tlsId != null) trafficControl.forceTrafficLightGreen(tlsId);
        });

        ui.tlResetBtn.addActionListener(e -> trafficControl.resetAllForcedTrafficLights());

        // Rule-based TLS ON/OFF
        ui.ruleBasedTlsCb.addActionListener(e ->
                trafficControl.setRuleBasedTlsEnabled(ui.ruleBasedTlsCb.isSelected()));

        // Filters => update global FILTER (MapPanel reads it)
        ui.showCarsCb.addActionListener(e -> FILTER.showCars = ui.showCarsCb.isSelected());
        ui.showTrucksCb.addActionListener(e -> FILTER.showTrucks = ui.showTrucksCb.isSelected());
        ui.showBusesCb.addActionListener(e -> FILTER.showBuses = ui.showBusesCb.isSelected());

        // Min speed slider => update FILTER + label text
        ui.minSpeedSlider.addChangeListener(e -> {
            int v = ui.minSpeedSlider.getValue(); // m/s
            FILTER.minSpeedMps = v;

            // km/h conversion: 1 m/s = 3.6 km/h
            double kmh = v * 3.6;

            // Locale.US => always use a decimal dot (German locale would use a comma)
            ui.minSpeedValueLabel.setText(
                    String.format(Locale.US, ">= %.1f m/s (%.0f km/h)", (double) v, kmh)
            );
        });
    }

    // Look & Feel set
    private static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Logging.LOG.log(java.util.logging.Level.WARNING, "LookAndFeel set failed", e);
        }
    }

    // ===================== UI HELPERS =====================
    // Button style helper: size + background + font etc
    private static void stylePrimaryButton(AbstractButton b, Color bg) {
        b.setBackground(bg);
        b.setForeground(Color.BLACK);
        b.setFocusPainted(false); // dotted focus border remove
        b.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        b.setFont(new Font("SansSerif", Font.BOLD, 11));

        // fixed size button
        Dimension d = new Dimension(110, 26);
        b.setPreferredSize(d);
        b.setMinimumSize(d);
        b.setMaximumSize(d);

        // hand cursor on hover
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // make sure background color actually shows
        b.setOpaque(true);
        b.setContentAreaFilled(true);

        b.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    // Label style helper
    private static void styleSectionLabel(JLabel l, Font f) {
        l.setForeground(Color.WHITE);
        l.setFont(f);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    // Titled border helper
    private static void styleTitledBorder(JPanel panel, String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COL),
                title,
                TitledBorder.LEADING,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12),
                TEXT_MUTED
        );

        // compound border: titled border + padding inside
        panel.setBorder(BorderFactory.createCompoundBorder(
                tb,
                BorderFactory.createEmptyBorder(8, 12, 10, 12)
        ));
    }

    // Checkbox style helper
    private static JCheckBox styleCheckBox(JCheckBox cb) {
        cb.setForeground(Color.WHITE);
        cb.setBackground(BG_PANEL);
        cb.setFocusPainted(false);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setFont(new Font("SansSerif", Font.BOLD, 12));
        return cb;
    }

    // Row panel helper: two buttons in one row
    private static JPanel rowPanel(Component... comps) {
        JPanel p = new JPanel(new GridLayout(1, comps.length, 8, 0)); // 1 row, n cols
        p.setBackground(BG_PANEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component c : comps) p.add(c);
        return p;
    }
}