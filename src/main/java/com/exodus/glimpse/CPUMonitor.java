package com.exodus.glimpse;

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
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CPUMonitor {
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private final CentralProcessor processor;

    private final DecimalFormat df = new DecimalFormat("#.##");
    private final SimpleDoubleProperty cpuUsage = new SimpleDoubleProperty(0);
    private final SimpleStringProperty cpuFrequency = new SimpleStringProperty("N/A");
    private final SimpleStringProperty numProcesses = new SimpleStringProperty("N/A");
    private final SimpleStringProperty numThreads = new SimpleStringProperty("N/A");
    private final SimpleStringProperty cpuTemp = new SimpleStringProperty("N/A");
    private final XYChart.Series<Number, Number> cpuSeries = new XYChart.Series<>();
    private final ObservableList<ProcessInfo> processData = FXCollections.observableArrayList();

    private final int MAX_DATA_POINTS = 60;
    private int xSeriesData = 0;
    private long[] previousTicks;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public CPUMonitor() {
        systemInfo = new SystemInfo();
        hardware = systemInfo.getHardware();
        os = systemInfo.getOperatingSystem();
        processor = hardware.getProcessor();
        previousTicks = processor.getSystemCpuLoadTicks();
        cpuSeries.setName("CPU Usage %");

        // Initial process data load
        updateProcessInfo();

        // Start monitoring
        startMonitoring();
    }

    public VBox createCPUMonitorPanel() {
        VBox monitorPanel = new VBox(15);
        monitorPanel.setPadding(new Insets(10));
        monitorPanel.setStyle("-fx-background-color: #282828;");

        // CPU Usage Gauge and Stats
        HBox statsContainer = new HBox(20);
        statsContainer.setAlignment(Pos.CENTER);
        statsContainer.setPadding(new Insets(5, 0, 15, 0));

        // CPU Usage Circle Indicator
        VBox usageBox = new VBox(5);
        usageBox.setAlignment(Pos.CENTER);

        StackPane usageIndicator = createCircularIndicator();
        Label usageLabel = new Label("CPU Usage");
        usageLabel.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 12px;");

        usageBox.getChildren().addAll(usageIndicator, usageLabel);

        // CPU Stats VBox
        VBox statsBox = createStatsBox();

        statsContainer.getChildren().addAll(usageBox, statsBox);

        // CPU Usage Graph
        LineChart<Number, Number> cpuChart = createCPUChart();
        VBox.setVgrow(cpuChart, Priority.ALWAYS);

        // Process Table
        TableView<ProcessInfo> processTable = createProcessTable();
        VBox.setVgrow(processTable, Priority.ALWAYS);

        // Add all components to main container
        monitorPanel.getChildren().addAll(statsContainer, cpuChart, processTable);

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

        cpuUsage.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                percentLabel.setText(df.format(newVal.doubleValue()) + "%");

                // Change color based on CPU usage
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

        HBox freqBox = createStatRow("Frequency:", cpuFrequency);
        HBox processBox = createStatRow("Processes:", numProcesses);
        HBox threadBox = createStatRow("Threads:", numThreads);
        HBox tempBox = createStatRow("Temperature:", cpuTemp);

        statsBox.getChildren().addAll(freqBox, processBox, threadBox, tempBox);
        return statsBox;
    }

    private HBox createStatRow(String labelText, SimpleStringProperty valueProperty) {
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

    private LineChart<Number, Number> createCPUChart() {
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
        lineChart.setTitle("CPU Usage");
        lineChart.setCreateSymbols(false);
        lineChart.setAnimated(false);
        lineChart.getData().add(cpuSeries);

        lineChart.setStyle(
                "-fx-background-color: #323232; " +
                        "-fx-plot-background-color: #262626; " +
                        "-fx-text-fill: white;"
        );
        lineChart.lookup(".chart-title").setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        lineChart.lookup(".axis-label").setStyle("-fx-text-fill: #BBBBBB;");

        return lineChart;
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

        TableColumn<ProcessInfo, String> cpuCol = new TableColumn<>("CPU %");
        cpuCol.setCellValueFactory(data -> data.getValue().cpuUsageProperty());
        cpuCol.setPrefWidth(80);

        TableColumn<ProcessInfo, String> memoryCol = new TableColumn<>("Memory");
        memoryCol.setCellValueFactory(data -> data.getValue().memoryUsageProperty());
        memoryCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, pidCol, cpuCol, memoryCol);
        table.setItems(processData);

        // Style the table
        table.setFixedCellSize(30);
        table.setPrefHeight(180);

        return table;
    }

    private void startMonitoring() {
        // Update data every second
        scheduler.scheduleAtFixedRate(() -> {
            updateCPUInfo();
            updateProcessInfo();
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void updateCPUInfo() {
        double usage = processor.getSystemCpuLoadBetweenTicks(previousTicks) * 100;
        previousTicks = processor.getSystemCpuLoadTicks();

        // Get CPU frequency
        long[] freqs = processor.getCurrentFreq();
        long maxFreq = 0;
        for (long freq : freqs) {
            if (freq > maxFreq) {
                maxFreq = freq;
            }
        }
        String freqStr = maxFreq > 0 ? df.format(maxFreq / 1_000_000.0) + " GHz" : "N/A";

        // Get CPU temperature
        double temp = hardware.getSensors().getCpuTemperature();
        String tempStr = temp > 0 ? df.format(temp) + "°C" : "N/A";

        Platform.runLater(() -> {
            cpuUsage.set(usage);
            cpuSeries.getData().add(new XYChart.Data<>(xSeriesData++, usage));
            if (cpuSeries.getData().size() > MAX_DATA_POINTS) {
                cpuSeries.getData().remove(0);
            }
            cpuFrequency.set(freqStr);
            numProcesses.set(String.valueOf(os.getProcessCount()));
            numThreads.set(String.valueOf(os.getThreadCount()));
            cpuTemp.set(tempStr);
        });
    }

    private void updateProcessInfo() {
        List<OSProcess> processes = os.getProcesses();
        processes.sort((p1, p2) -> {
            double cpu1 = 100d * (p1.getKernelTime() + p1.getUserTime()) / p1.getUpTime();
            double cpu2 = 100d * (p2.getKernelTime() + p2.getUserTime()) / p2.getUpTime();
            return Double.compare(cpu2, cpu1);
        });

        List<OSProcess> topProcesses = processes.subList(0, Math.min(10, processes.size()));

        Platform.runLater(() -> {
            processData.clear();
            for (OSProcess process : topProcesses) {
                String name = process.getName();
                if (name.length() > 30) {
                    name = name.substring(0, 27) + "...";
                }

                double cpuUsage = 100d * (process.getKernelTime() + process.getUserTime()) / process.getUpTime();
                long memBytes = process.getResidentSetSize();
                String memoryUsage = formatBytes(memBytes);

                processData.add(new ProcessInfo(
                        name,
                        String.valueOf(process.getProcessID()),
                        df.format(cpuUsage) + "%",
                        memoryUsage
                ));
            }
        });
    }

    private String formatBytes(long bytes) {
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
        private final SimpleStringProperty cpuUsage;
        private final SimpleStringProperty memoryUsage;

        public ProcessInfo(String name, String pid, String cpuUsage, String memoryUsage) {
            this.name = new SimpleStringProperty(name);
            this.pid = new SimpleStringProperty(pid);
            this.cpuUsage = new SimpleStringProperty(cpuUsage);
            this.memoryUsage = new SimpleStringProperty(memoryUsage);
        }

        public String getName() { return name.get(); }
        public String getPid() { return pid.get(); }
        public String getCpuUsage() { return cpuUsage.get(); }
        public String getMemoryUsage() { return memoryUsage.get(); }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty pidProperty() { return pid; }
        public SimpleStringProperty cpuUsageProperty() { return cpuUsage; }
        public SimpleStringProperty memoryUsageProperty() { return memoryUsage; }
    }
}