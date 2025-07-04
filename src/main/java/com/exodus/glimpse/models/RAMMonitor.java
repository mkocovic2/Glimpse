package com.exodus.glimpse.models;

import com.exodus.glimpse.BaseMonitor;
import com.exodus.glimpse.RemoteStation;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors RAM/memory usage and processes consuming memory.
 */
public class RAMMonitor extends BaseMonitor {
    private final GlobalMemory memory;

    private final DecimalFormat df = new DecimalFormat("#.##");
    private final SimpleDoubleProperty ramUsagePercent = new SimpleDoubleProperty(0);
    private final SimpleStringProperty totalRam = new SimpleStringProperty("N/A");
    private final SimpleStringProperty usedRam = new SimpleStringProperty("N/A");
    private final SimpleStringProperty freeRam = new SimpleStringProperty("N/A");
    private final SimpleStringProperty swapTotal = new SimpleStringProperty("N/A");
    private final SimpleStringProperty swapUsed = new SimpleStringProperty("N/A");
    private final XYChart.Series<Number, Number> ramSeries = new XYChart.Series<>();
    private final ObservableList<ProcessInfo> processData = FXCollections.observableArrayList();

    private final int MAX_DATA_POINTS = 60;
    private int xSeriesData = 0;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Constructor that initializes RAM monitoring components.
     * Sets up properties for memory usage tracking and initial data collection.
     */
    public RAMMonitor() {
        super();
        memory = hardware.getMemory();
        ramSeries.setName("RAM Usage %");

        updateProcessInfo();
        startMonitoring();
    }

    /**
     * Creates the RAM monitoring panel with usage chart, statistics and memory-hungry processes table.
     * @return VBox containing the complete RAM monitoring UI components
     */
    public VBox createMonitorPanel() {
        VBox monitorPanel = new VBox(15);
        monitorPanel.setPadding(new Insets(10));
        monitorPanel.setStyle("-fx-background-color: #282828;");

        // RAM Usage Gauge and Stats
        HBox statsContainer = new HBox(20);
        statsContainer.setAlignment(Pos.CENTER);
        statsContainer.setPadding(new Insets(5, 0, 15, 0));

        // RAM Usage Circle Indicator
        VBox usageBox = new VBox(5);
        usageBox.setAlignment(Pos.CENTER);

        StackPane usageIndicator = createCircularIndicator();
        Label usageLabel = new Label("RAM Usage");
        usageLabel.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 12px;");

        usageBox.getChildren().addAll(usageIndicator, usageLabel);

        // RAM Stats VBox
        VBox statsBox = createStatsBox();

        statsContainer.getChildren().addAll(usageBox, statsBox);

        // RAM Usage Graph
        LineChart<Number, Number> ramChart = createRAMChart();
        VBox.setVgrow(ramChart, Priority.ALWAYS);

        // Process Table
        TableView<ProcessInfo> processTable = createProcessTable();
        VBox.setVgrow(processTable, Priority.ALWAYS);

        // Add all components to main container
        monitorPanel.getChildren().addAll(statsContainer, ramChart, processTable);

        return monitorPanel;
    }

    /**
     * Creates a circular indicator to visually display RAM usage percentage.
     * @return StackPane containing the styled circular indicator with percentage text
     */
    private StackPane createCircularIndicator() {
        StackPane circleContainer = new StackPane();
        circleContainer.setMinSize(100, 100);
        circleContainer.setMaxSize(100, 100);

        javafx.scene.shape.Circle outerCircle = new javafx.scene.shape.Circle(45);
        outerCircle.setStyle("-fx-fill: #2D2D2D; -fx-stroke: #3D5AFE; -fx-stroke-width: 3;");

        Label percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        ramUsagePercent.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                percentLabel.setText(df.format(newVal.doubleValue()) + "%");

                String color;
                if (newVal.doubleValue() < 60) {
                    color = "#3D5AFE"; // Blue for normal
                } else if (newVal.doubleValue() < 85) {
                    color = "#FBBC05"; // Yellow for medium
                } else {
                    color = "#EA4335"; // Red for high
                }
                outerCircle.setStyle("-fx-fill: #2D2D2D; -fx-stroke: " + color + "; -fx-stroke-width: 3;");
            });
        });

        circleContainer.getChildren().addAll(outerCircle, percentLabel);
        return circleContainer;
    }

    /**
     * Creates a container for displaying RAM statistics (total, used, free, swap).
     * @return VBox containing formatted rows of RAM statistics
     */
    private VBox createStatsBox() {
        VBox statsBox = new VBox(8);
        statsBox.setPadding(new Insets(5));
        statsBox.setAlignment(Pos.CENTER_LEFT);

        HBox totalBox = createStatRow("Total RAM:", totalRam);
        HBox usedBox = createStatRow("Used RAM:", usedRam);
        HBox freeBox = createStatRow("Free RAM:", freeRam);
        HBox swapTotalBox = createStatRow("Swap Total:", swapTotal);
        HBox swapUsedBox = createStatRow("Swap Used:", swapUsed);

        statsBox.getChildren().addAll(totalBox, usedBox, freeBox, swapTotalBox, swapUsedBox);
        return statsBox;
    }

    /**
     * Creates a formatted row for displaying a specific RAM statistic.
     * @param labelText The description label for the statistic
     * @param valueProperty The property containing the statistic value
     * @return HBox containing the formatted label and value
     */
    public HBox createStatRow(String labelText, SimpleStringProperty valueProperty) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        label.setMinWidth(95);
        label.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 13px;");

        Label value = new Label();
        value.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        value.textProperty().bind(valueProperty);

        row.getChildren().addAll(label, value);
        return row;
    }

    /**
     * Creates a line chart for displaying RAM usage over time.
     * @return LineChart configured for displaying RAM usage percentage
     */
    private LineChart<Number, Number> createRAMChart() {
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis(0, 100, 20);

        xAxis.setLabel("Time (seconds)");
        xAxis.setAnimated(false);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);

        yAxis.setLabel("Usage %");
        yAxis.setAnimated(false);

        final LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("RAM Usage");
        lineChart.setCreateSymbols(false);
        lineChart.setAnimated(false);
        lineChart.getData().add(ramSeries);

        lineChart.setStyle(
                "-fx-background-color: #323232; " +
                        "-fx-plot-background-color: #262626; " +
                        "-fx-text-fill: white;"
        );
        lineChart.lookup(".chart-title").setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        lineChart.lookup(".axis-label").setStyle("-fx-text-fill: #BBBBBB;");

        return lineChart;
    }

    /**
     * Creates a table view for displaying information about processes consuming memory.
     * @return TableView configured with columns for process name, PID, memory usage and percentage
     */
    private TableView<ProcessInfo> createProcessTable() {
        TableView<ProcessInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: #323232; -fx-text-fill: white;");

        TableColumn<ProcessInfo, String> nameCol = new TableColumn<>("Process Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<ProcessInfo, String> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(data -> data.getValue().pidProperty());
        pidCol.setPrefWidth(70);

        TableColumn<ProcessInfo, String> memoryCol = new TableColumn<>("Memory");
        memoryCol.setCellValueFactory(data -> data.getValue().memoryUsageProperty());
        memoryCol.setPrefWidth(100);

        TableColumn<ProcessInfo, String> memoryPercentCol = new TableColumn<>("RAM %");
        memoryPercentCol.setCellValueFactory(data -> data.getValue().memoryPercentProperty());
        memoryPercentCol.setPrefWidth(80);

        table.getColumns().addAll(nameCol, pidCol, memoryCol, memoryPercentCol);
        table.setItems(processData);

        table.setFixedCellSize(30);
        table.setPrefHeight(180);

        return table;
    }

    /**
     * Starts the monitoring scheduler that updates RAM information and process data
     * at regular intervals (every 1 second).
     */
    public void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            updateRAMInfo();
            updateProcessInfo();
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates RAM usage information from either local or remote system.
     * Updates UI components with the current RAM statistics.
     */
    private void updateRAMInfo() {
        if (remoteStation != null) {
            try {
                String response = remoteStation.getMemoryUsage();
                Platform.runLater(() -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        double usedPercent = json.getDouble("percent");
                        long totalBytes = json.getLong("total");
                        long usedBytes = json.getLong("used");
                        long freeBytes = json.getLong("free");
                        long swapTotalBytes = json.getLong("swap_total");
                        long swapUsedBytes = json.getLong("swap_used");

                        ramUsagePercent.set(usedPercent);
                        ramSeries.getData().add(new XYChart.Data<>(xSeriesData++, usedPercent));
                        if (ramSeries.getData().size() > MAX_DATA_POINTS) {
                            ramSeries.getData().remove(0);
                        }

                        totalRam.set(formatBytes(totalBytes));
                        usedRam.set(formatBytes(usedBytes));
                        freeRam.set(formatBytes(freeBytes));
                        swapTotal.set(formatBytes(swapTotalBytes));
                        swapUsed.set(formatBytes(swapUsedBytes));
                    } catch (JSONException e) {
                        System.err.println("Error parsing RAM API response: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Error fetching remote RAM data: " + e.getMessage());
            }
        } else {
            long total = memory.getTotal();
            long available = memory.getAvailable();
            long used = total - available;
            double percentUsed = (double) used / total * 100;

            long swapTotal = memory.getVirtualMemory().getSwapTotal();
            long swapUsed = memory.getVirtualMemory().getSwapUsed();

            Platform.runLater(() -> {
                ramUsagePercent.set(percentUsed);
                ramSeries.getData().add(new XYChart.Data<>(xSeriesData++, percentUsed));
                if (ramSeries.getData().size() > MAX_DATA_POINTS) {
                    ramSeries.getData().remove(0);
                }

                this.totalRam.set(formatBytes(total));
                this.usedRam.set(formatBytes(used));
                this.freeRam.set(formatBytes(available));
                this.swapTotal.set(formatBytes(swapTotal));
                this.swapUsed.set(formatBytes(swapUsed));
            });
        }
    }

    /**
     * Updates the list of top memory-consuming processes from either local or remote system.
     * Refreshes the process table with current data.
     */
    private void updateProcessInfo() {
        if (remoteStation != null) {
            // Remote process monitoring
            try {
                String response = remoteStation.getTopProcesses();
                Platform.runLater(() -> {
                    try {
                        JSONArray processes = new JSONArray(response);
                        processData.clear();

                        long totalMemory = 0;
                        try {
                            JSONObject memInfo = new JSONObject(remoteStation.getMemoryUsage());
                            totalMemory = memInfo.getLong("total");
                        } catch (Exception e) {
                            System.err.println("Error getting total memory for remote: " + e.getMessage());
                            totalMemory = 1; // Avoid division by zero
                        }

                        for (int i = 0; i < processes.length(); i++) {
                            JSONObject proc = processes.getJSONObject(i);
                            String name = proc.getString("name");
                            if (name.length() > 30) {
                                name = name.substring(0, 27) + "...";
                            }

                            long memBytes = (long)(proc.getDouble("memory_percent") * 0.01 * totalMemory);
                            String memoryUsage = formatBytes(memBytes);
                            double memoryPercent = proc.getDouble("memory_percent");

                            processData.add(new ProcessInfo(
                                    name,
                                    String.valueOf(proc.getInt("pid")),
                                    memoryUsage,
                                    df.format(memoryPercent) + "%"
                            ));
                        }
                    } catch (JSONException e) {
                        System.err.println("Error parsing process API response: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Error fetching remote process data: " + e.getMessage());
            }
        } else {
            List<OSProcess> processes = os.getProcesses();
            processes.sort((p1, p2) -> Long.compare(p2.getResidentSetSize(), p1.getResidentSetSize()));

            List<OSProcess> topProcesses = processes.subList(0, Math.min(10, processes.size()));

            Platform.runLater(() -> {
                processData.clear();
                long totalMemory = memory.getTotal();

                for (OSProcess process : topProcesses) {
                    String name = process.getName();
                    if (name.length() > 30) {
                        name = name.substring(0, 27) + "...";
                    }

                    long memBytes = process.getResidentSetSize();
                    String memoryUsage = formatBytes(memBytes);
                    double memoryPercent = (double) memBytes / totalMemory * 100;

                    processData.add(new ProcessInfo(
                            name,
                            String.valueOf(process.getProcessID()),
                            memoryUsage,
                            df.format(memoryPercent) + "%"
                    ));
                }
            });
        }
    }

    /**
     * Formats byte values into human-readable strings with appropriate units (B, KB, MB, GB).
     * @param bytes The number of bytes to format
     * @return String representation of bytes with appropriate unit suffix
     */
    public String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return df.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return df.format(bytes / (1024.0 * 1024)) + " MB";
        } else {
            return df.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
        }
    }

    /**
     * Sets the remote station for collecting RAM and process data from a remote system.
     * @param remoteStation The remote station instance to retrieve data from
     */
    public void setRemoteStation(RemoteStation remoteStation) {
        this.remoteStation = remoteStation;
    }

    /**
     * Shuts down the monitoring scheduler when the monitor is no longer needed.
     * Ensures proper resource cleanup.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Inner class that represents process information for display in the process table.
     * Contains observable properties for process name, PID, memory usage and percentage.
     */
    public static class ProcessInfo {
        private final SimpleStringProperty name;
        private final SimpleStringProperty pid;
        private final SimpleStringProperty memoryUsage;
        private final SimpleStringProperty memoryPercent;

        public ProcessInfo(String name, String pid, String memoryUsage, String memoryPercent) {
            this.name = new SimpleStringProperty(name);
            this.pid = new SimpleStringProperty(pid);
            this.memoryUsage = new SimpleStringProperty(memoryUsage);
            this.memoryPercent = new SimpleStringProperty(memoryPercent);
        }

        public String getName() { return name.get(); }
        public String getPid() { return pid.get(); }
        public String getMemoryUsage() { return memoryUsage.get(); }
        public String getMemoryPercent() { return memoryPercent.get(); }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty pidProperty() { return pid; }
        public SimpleStringProperty memoryUsageProperty() { return memoryUsage; }
        public SimpleStringProperty memoryPercentProperty() { return memoryPercent; }
    }
}
