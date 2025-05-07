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
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import oshi.hardware.CentralProcessor;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CPUMonitor extends BaseMonitor {
    private final CentralProcessor processor;
    private long[] previousTicks;

    private final SimpleDoubleProperty cpuUsage = new SimpleDoubleProperty(0);
    private final SimpleStringProperty cpuFrequency = new SimpleStringProperty("N/A");
    private final SimpleStringProperty numProcesses = new SimpleStringProperty("N/A");
    private final SimpleStringProperty numThreads = new SimpleStringProperty("N/A");
    private final SimpleStringProperty cpuTemp = new SimpleStringProperty("N/A");
    private final XYChart.Series<Number, Number> cpuSeries = new XYChart.Series<>();
    private final ObservableList<ProcessInfo> processData = FXCollections.observableArrayList();

    public CPUMonitor() {
        super(); // Initialize BaseMonitor properties
        processor = hardware.getProcessor();
        previousTicks = processor.getSystemCpuLoadTicks();
        cpuSeries.setName("CPU Usage %");

        // Initial process data load
        updateProcessInfo();

        // Start monitoring
        startMonitoring();
    }

    @Override
    public VBox createMonitorPanel() {
        VBox monitorPanel = createBaseMonitorPanel("CPU Monitor");

        // CPU Usage Gauge and Stats
        HBox statsContainer = new HBox(20);
        statsContainer.setAlignment(Pos.CENTER);
        statsContainer.setPadding(new Insets(5, 0, 15, 0));

        // CPU Usage Circle Indicator (using BaseMonitor method)
        StackPane usageIndicator = createCircularIndicator(cpuUsage, "CPU Usage");

        // CPU Stats VBox
        VBox statsBox = createStatsBox();

        statsContainer.getChildren().addAll(usageIndicator, statsBox);

        // CPU Usage Graph (using BaseMonitor method)
        LineChart<Number, Number> cpuChart = createUsageChart("CPU Usage", "Usage %", cpuSeries);
        VBox.setVgrow(cpuChart, Priority.ALWAYS);

        // Process Table
        TableView<ProcessInfo> processTable = createProcessTable();
        VBox.setVgrow(processTable, Priority.ALWAYS);

        // Add all components to main container
        monitorPanel.getChildren().addAll(statsContainer, cpuChart, processTable);

        return monitorPanel;
    }

    private VBox createStatsBox() {
        VBox statsBox = new VBox(8);
        statsBox.setPadding(new Insets(5));
        statsBox.setAlignment(Pos.CENTER_LEFT);

        // Using BaseMonitor's createStatRow method
        HBox freqBox = createStatRow("Frequency:", cpuFrequency);
        HBox processBox = createStatRow("Processes:", numProcesses);
        HBox threadBox = createStatRow("Threads:", numThreads);
        HBox tempBox = createStatRow("Temperature:", cpuTemp);

        statsBox.getChildren().addAll(freqBox, processBox, threadBox, tempBox);
        return statsBox;
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

    @Override
    protected void startMonitoring() {
        // Update data every second
        scheduler.scheduleAtFixedRate(() -> {
            updateCPUInfo();
            updateProcessInfo();
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void updateCPUInfo() {
        if (remoteStation != null) {
            try {
                String response = remoteStation.getCpuUsage();
                Platform.runLater(() -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        double usage = json.getDouble("usage_percent");

                        cpuUsage.set(usage);
                        cpuSeries.getData().add(new XYChart.Data<>(xSeriesData++, usage));
                        if (cpuSeries.getData().size() > MAX_DATA_POINTS) {
                            cpuSeries.getData().remove(0);
                        }

                        double freq = json.optDouble("frequencies", 0);
                        String freqStr = freq > 0 ? df.format(freq / 1000.0) + " GHz" : "N/A";
                        cpuFrequency.set(freqStr);

                        // For remote, we might not have these values
                        numProcesses.set("Remote");
                        numThreads.set("Remote");
                        cpuTemp.set("N/A"); // Temperature usually not available remotely
                    } catch (JSONException e) {
                        System.err.println("Error parsing CPU API response: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Error fetching remote CPU data: " + e.getMessage());
            }
        } else {
            double usage = processor.getSystemCpuLoadBetweenTicks(previousTicks) * 100;
            previousTicks = processor.getSystemCpuLoadTicks();

            long[] freqs = processor.getCurrentFreq();
            long maxFreq = 0;
            for (long freq : freqs) {
                if (freq > maxFreq) {
                    maxFreq = freq;
                }
            }
            String freqStr = maxFreq > 0 ? df.format(maxFreq / 1_000_000.0) + " GHz" : "N/A";

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
    }

    private void updateProcessInfo() {
        if (remoteStation != null) {
            // Remote process monitoring
            try {
                String response = remoteStation.getTopProcesses();
                Platform.runLater(() -> {
                    try {
                        JSONArray processes = new JSONArray(response);
                        processData.clear();

                        for (int i = 0; i < processes.length(); i++) {
                            JSONObject proc = processes.getJSONObject(i);
                            String name = proc.getString("name");
                            if (name.length() > 30) {
                                name = name.substring(0, 27) + "...";
                            }

                            double cpuUsage = proc.getDouble("cpu_percent");
                            double memPercent = proc.getDouble("memory_percent");
                            long memBytes = (long) (memPercent * 0.01 * hardware.getMemory().getTotal());

                            processData.add(new ProcessInfo(
                                    name,
                                    String.valueOf(proc.getInt("pid")),
                                    df.format(cpuUsage) + "%",
                                    formatBytes(memBytes)
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
            // Local process monitoring
            List<oshi.software.os.OSProcess> processes = os.getProcesses();
            processes.sort((p1, p2) -> {
                double cpu1 = 100d * (p1.getKernelTime() + p1.getUserTime()) / p1.getUpTime();
                double cpu2 = 100d * (p2.getKernelTime() + p2.getUserTime()) / p2.getUpTime();
                return Double.compare(cpu2, cpu1);
            });

            List<oshi.software.os.OSProcess> topProcesses = processes.subList(0, Math.min(10, processes.size()));

            Platform.runLater(() -> {
                processData.clear();
                for (oshi.software.os.OSProcess process : topProcesses) {
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
    }

    // Keep the setRemoteStation method override
    @Override
    public void setRemoteStation(RemoteStation remoteStation) {
        super.setRemoteStation(remoteStation);
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