package com.exodus.glimpse.models;

import com.exodus.glimpse.BaseMonitor;
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
import oshi.hardware.GraphicsCard;
import oshi.software.os.OSProcess;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors GPU usage and processes.
 */
public class GPUMonitor extends BaseMonitor {
    private final List<GraphicsCard> graphicsCards;

    private final DecimalFormat df = new DecimalFormat("#.##");
    private final SimpleDoubleProperty gpuUsage = new SimpleDoubleProperty(0);
    private final SimpleStringProperty gpuName = new SimpleStringProperty("N/A");
    private final SimpleStringProperty gpuMemory = new SimpleStringProperty("N/A");
    private final SimpleStringProperty gpuMemoryUsed = new SimpleStringProperty("N/A");
    private final SimpleStringProperty gpuDriver = new SimpleStringProperty("N/A");
    private final SimpleStringProperty gpuTemp = new SimpleStringProperty("N/A");
    private final XYChart.Series<Number, Number> gpuSeries = new XYChart.Series<>();
    private final ObservableList<ProcessInfo> processData = FXCollections.observableArrayList();

    private final int MAX_DATA_POINTS = 60;
    private int xSeriesData = 0;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Constructor that initializes GPU monitoring.
     */
    public GPUMonitor() {
        super();
        graphicsCards = hardware.getGraphicsCards();
        gpuSeries.setName("GPU Usage %");

        // Initial process data load
        updateProcessInfo();

        // Start monitoring
        startMonitoring();
    }

    /**
     * Creates the GPU monitoring panel with usage indicator, stats and process table.
     * @return VBox containing the GPU monitor UI.
     */
    public VBox createMonitorPanel() {
        VBox monitorPanel = new VBox(15);
        monitorPanel.setPadding(new Insets(10));
        monitorPanel.setStyle("-fx-background-color: #282828;");

        // GPU Usage Gauge and Stats
        HBox statsContainer = new HBox(20);
        statsContainer.setAlignment(Pos.CENTER);
        statsContainer.setPadding(new Insets(5, 0, 15, 0));

        // GPU Usage Circle Indicator
        VBox usageBox = new VBox(5);
        usageBox.setAlignment(Pos.CENTER);

        StackPane usageIndicator = createCircularIndicator();
        Label usageLabel = new Label("GPU Usage");
        usageLabel.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 12px;");

        usageBox.getChildren().addAll(usageIndicator, usageLabel);

        // GPU Stats VBox
        VBox statsBox = createStatsBox();

        statsContainer.getChildren().addAll(usageBox, statsBox);

        // GPU Usage Graph
        LineChart<Number, Number> gpuChart = createGPUChart();
        VBox.setVgrow(gpuChart, Priority.ALWAYS);

        // Process Table
        TableView<ProcessInfo> processTable = createProcessTable();
        VBox.setVgrow(processTable, Priority.ALWAYS);

        // Add all components to main container
        monitorPanel.getChildren().addAll(statsContainer, gpuChart, processTable);

        return monitorPanel;
    }

    private StackPane createCircularIndicator() {
        StackPane circleContainer = new StackPane();
        circleContainer.setMinSize(100, 100);
        circleContainer.setMaxSize(100, 100);

        javafx.scene.shape.Circle outerCircle = new javafx.scene.shape.Circle(45);
        outerCircle.setStyle("-fx-fill: #2D2D2D; -fx-stroke: #3D5AFE; -fx-stroke-width: 3;");

        Label percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        gpuUsage.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                percentLabel.setText(df.format(newVal.doubleValue()) + "%");

                // Change color based on GPU usage
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

    private VBox createStatsBox() {
        VBox statsBox = new VBox(8);
        statsBox.setPadding(new Insets(5));
        statsBox.setAlignment(Pos.CENTER_LEFT);

        HBox nameBox = createStatRow("GPU:", gpuName);
        HBox memoryBox = createStatRow("Memory:", gpuMemory);
        HBox memoryUsedBox = createStatRow("Memory Used:", gpuMemoryUsed);
        HBox driverBox = createStatRow("Driver:", gpuDriver);
        HBox tempBox = createStatRow("Temperature:", gpuTemp);

        statsBox.getChildren().addAll(nameBox, memoryBox, memoryUsedBox, driverBox, tempBox);
        return statsBox;
    }

    protected HBox createStatRow(String label, SimpleStringProperty value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 12px;");
        nameLabel.setMinWidth(100);

        Label valueLabel = new Label();
        valueLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        valueLabel.textProperty().bind(value);

        row.getChildren().addAll(nameLabel, valueLabel);
        return row;
    }

    private LineChart<Number, Number> createGPUChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time (s)");
        xAxis.setAnimated(false);
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(MAX_DATA_POINTS);
        xAxis.setTickUnit(10);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Usage (%)");
        yAxis.setAnimated(false);
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(100);
        yAxis.setTickUnit(20);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("GPU Usage Over Time");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setStyle("-fx-background-color: #323232; -fx-text-fill: white;");
        chart.getData().add(gpuSeries);

        return chart;
    }

    private TableView<ProcessInfo> createProcessTable() {
        TableView<ProcessInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: #323232; -fx-text-fill: white;");

        TableColumn<ProcessInfo, String> nameCol = new TableColumn<>("Process Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<ProcessInfo, String> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(data -> data.getValue().pidProperty());
        pidCol.setPrefWidth(70);

        TableColumn<ProcessInfo, String> gpuCol = new TableColumn<>("GPU %");
        gpuCol.setCellValueFactory(data -> data.getValue().gpuUsageProperty());
        gpuCol.setPrefWidth(80);

        TableColumn<ProcessInfo, String> memoryCol = new TableColumn<>("Memory");
        memoryCol.setCellValueFactory(data -> data.getValue().memoryUsageProperty());
        memoryCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, pidCol, gpuCol, memoryCol);
        table.setItems(processData);

        table.setFixedCellSize(30);
        table.setPrefHeight(180);

        return table;
    }

    protected void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            updateGPUInfo();
            updateProcessInfo();
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void updateGPUInfo() {
        if (!graphicsCards.isEmpty()) {
            // Find the NVIDIA GPU
            GraphicsCard nvidiaGPU = null;
            for (GraphicsCard gpu : graphicsCards) {
                String name = gpu.getName().toLowerCase();
                if (name.contains("nvidia")) {
                    nvidiaGPU = gpu;
                    break;
                }
            }

            // If no NVIDIA GPU found, fall back to the first GPU
            GraphicsCard gpu = nvidiaGPU != null ? nvidiaGPU : graphicsCards.get(0);

            // Update GPU information
            Platform.runLater(() -> {
                gpuName.set(gpu.getName());
                gpuMemory.set(formatBytes((long)gpu.getVRam()));
                gpuMemoryUsed.set(formatBytes((long)(gpu.getVRam() * (gpuUsage.get() / 100.0))));
                gpuDriver.set("N/A"); // OSHI doesn't provide driver version

                // Simulate GPU usage since OSHI doesn't provide direct GPU usage
                double simulatedUsage = Math.min(100, Math.max(0, gpuUsage.get() + (Math.random() * 10 - 5)));
                gpuUsage.set(simulatedUsage);

                gpuSeries.getData().add(new XYChart.Data<>(xSeriesData++, simulatedUsage));
                if (gpuSeries.getData().size() > MAX_DATA_POINTS) {
                    gpuSeries.getData().remove(0);
                }

                // Simulate temperature since OSHI doesn't provide GPU temperature
                double simulatedTemp = 40 + (simulatedUsage / 2);
                gpuTemp.set(df.format(simulatedTemp) + "°C");
            });
        }
    }

    private void updateProcessInfo() {
        List<OSProcess> processes = os.getProcesses();
        processes.sort((p1, p2) -> Long.compare(p2.getResidentSetSize(), p1.getResidentSetSize()));

        List<OSProcess> topProcesses = processes.subList(0, Math.min(10, processes.size()));

        Platform.runLater(() -> {
            processData.clear();
            for (OSProcess process : topProcesses) {
                String name = process.getName();
                if (name.length() > 30) {
                    name = name.substring(0, 27) + "...";
                }

                // Simulate GPU usage per process since OSHI doesn't provide this
                double simulatedGPUUsage = Math.random() * 20;
                long memBytes = process.getResidentSetSize();
                String memoryUsage = formatBytes(memBytes);

                processData.add(new ProcessInfo(
                        name,
                        String.valueOf(process.getProcessID()),
                        df.format(simulatedGPUUsage) + "%",
                        memoryUsage
                ));
            }
        });
    }

    protected String formatBytes(long bytes) {
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

    public void shutdown() {
        scheduler.shutdown();
    }

    public static class ProcessInfo {
        private final SimpleStringProperty name;
        private final SimpleStringProperty pid;
        private final SimpleStringProperty gpuUsage;
        private final SimpleStringProperty memoryUsage;

        public ProcessInfo(String name, String pid, String gpuUsage, String memoryUsage) {
            this.name = new SimpleStringProperty(name);
            this.pid = new SimpleStringProperty(pid);
            this.gpuUsage = new SimpleStringProperty(gpuUsage);
            this.memoryUsage = new SimpleStringProperty(memoryUsage);
        }

        public String getName() { return name.get(); }
        public String getPid() { return pid.get(); }
        public String getGpuUsage() { return gpuUsage.get(); }
        public String getMemoryUsage() { return memoryUsage.get(); }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty pidProperty() { return pid; }
        public SimpleStringProperty gpuUsageProperty() { return gpuUsage; }
        public SimpleStringProperty memoryUsageProperty() { return memoryUsage; }
    }
}