package com.exodus.glimpse.models;

import com.exodus.glimpse.BaseMonitor;
import com.exodus.glimpse.RemoteStation;
import javafx.application.Platform;
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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import oshi.hardware.NetworkIF;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkMonitor extends BaseMonitor {
    private final List<NetworkIF> networkInterfaces;
    private final ComboBox<String> interfaceSelector;

    private final DecimalFormat df = new DecimalFormat("#.##");
    private final SimpleStringProperty currentInterface = new SimpleStringProperty("N/A");
    private final SimpleStringProperty ipAddress = new SimpleStringProperty("N/A");
    private final SimpleStringProperty macAddress = new SimpleStringProperty("N/A");
    private final SimpleStringProperty downloadSpeed = new SimpleStringProperty("0 KB/s");
    private final SimpleStringProperty uploadSpeed = new SimpleStringProperty("0 KB/s");
    private final SimpleStringProperty totalDownloaded = new SimpleStringProperty("0 KB");
    private final SimpleStringProperty totalUploaded = new SimpleStringProperty("0 KB");
    private final SimpleStringProperty connectionStatus = new SimpleStringProperty("Disconnected");

    private final XYChart.Series<Number, Number> downloadSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> uploadSeries = new XYChart.Series<>();

    private final ObservableList<ConnectionEntry> connectionData = FXCollections.observableArrayList();

    private final int MAX_DATA_POINTS = 60;
    private int xSeriesData = 0;

    private final Map<String, NetworkStats> previousStats = new HashMap<>();
    private NetworkIF currentNetworkIF;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public NetworkMonitor() {
        super();
        networkInterfaces = hardware.getNetworkIFs();

        interfaceSelector = new ComboBox<>();

        updateNetworkInterfaces();

        downloadSeries.setName("Download");
        uploadSeries.setName("Upload");

        startMonitoring();
    }

    public VBox createMonitorPanel() {
        VBox monitorPanel = new VBox(15);
        monitorPanel.setPadding(new Insets(10));
        monitorPanel.setStyle("-fx-background-color: #282828;");

        HBox topPanel = new HBox(20);
        topPanel.setAlignment(Pos.CENTER_LEFT);
        topPanel.setPadding(new Insets(5, 0, 15, 0));

        Label interfaceLabel = new Label("Interface:");
        interfaceLabel.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 13px;");

        interfaceSelector.setPrefWidth(250);
        interfaceSelector.setStyle("-fx-background-color: #3D3D3D; -fx-text-fill: white;");
        interfaceSelector.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldVal, newVal) -> updateSelectedInterface(newVal.intValue())
        );

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-font-weight: bold;");
        statusLabel.textProperty().bind(connectionStatus);
        connectionStatus.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                if (newVal.equals("Connected")) {
                    statusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                } else {
                    statusLabel.setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold;");
                }
            });
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topPanel.getChildren().addAll(interfaceLabel, interfaceSelector, spacer, statusLabel);

        // Network stats
        VBox statsBox = createStatsBox();

        // Network Usage Graph
        LineChart<Number, Number> networkChart = createNetworkChart();
        VBox.setVgrow(networkChart, Priority.ALWAYS);

        // Connection Table
        TableView<ConnectionEntry> connectionTable = createConnectionTable();
        VBox.setVgrow(connectionTable, Priority.ALWAYS);

        // Add all components to main container
        monitorPanel.getChildren().addAll(topPanel, statsBox, networkChart, connectionTable);

        return monitorPanel;
    }

    private void updateNetworkInterfaces() {
        if (remoteStation != null) {
            // For remote monitoring, we'll just show a single "Remote" interface
            Platform.runLater(() -> {
                interfaceSelector.getItems().setAll("Remote Connection");
                interfaceSelector.getSelectionModel().selectFirst();
                currentInterface.set("Remote");
                ipAddress.set("Remote");
                macAddress.set("Remote");
                connectionStatus.set("Connected");
            });
        } else {
            // Local monitoring
            List<String> interfaceNames = new ArrayList<>();

            for (NetworkIF netIF : networkInterfaces) {
                netIF.updateAttributes();
                String name = netIF.getName() + " (" + netIF.getDisplayName() + ")";
                interfaceNames.add(name);

                // Initialize stats for this interface
                if (!previousStats.containsKey(netIF.getName())) {
                    previousStats.put(netIF.getName(), new NetworkStats(
                            netIF.getBytesRecv(),
                            netIF.getBytesSent(),
                            System.currentTimeMillis()
                    ));
                }
            }

            Platform.runLater(() -> {
                interfaceSelector.getItems().setAll(interfaceNames);
                if (!interfaceNames.isEmpty()) {
                    interfaceSelector.getSelectionModel().selectFirst();
                    updateSelectedInterface(0);
                }
            });
        }
    }

    private VBox createStatsBox() {
        VBox statsBox = new VBox(15);
        statsBox.setPadding(new Insets(10));
        statsBox.setStyle("-fx-background-color: #323232; -fx-background-radius: 5;");

        // Header
        Label headerLabel = new Label("Network Statistics");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        headerLabel.setStyle("-fx-text-fill: white;");

        // Stats Grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(40);
        statsGrid.setVgap(10);

        // Column 1: Network Interface Details
        Label ipLabel = new Label("IP Address:");
        ipLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label ipValueLabel = new Label();
        ipValueLabel.setStyle("-fx-text-fill: white;");
        ipValueLabel.textProperty().bind(ipAddress);

        Label macLabel = new Label("MAC Address:");
        macLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label macValueLabel = new Label();
        macValueLabel.setStyle("-fx-text-fill: white;");
        macValueLabel.textProperty().bind(macAddress);

        // Column 2: Current Speeds
        Label downloadSpeedLabel = new Label("Download Speed:");
        downloadSpeedLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label downloadSpeedValueLabel = new Label();
        downloadSpeedValueLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        downloadSpeedValueLabel.textProperty().bind(downloadSpeed);

        Label uploadSpeedLabel = new Label("Upload Speed:");
        uploadSpeedLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label uploadSpeedValueLabel = new Label();
        uploadSpeedValueLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
        uploadSpeedValueLabel.textProperty().bind(uploadSpeed);

        // Column 3: Total Transferred
        Label totalDownloadedLabel = new Label("Total Downloaded:");
        totalDownloadedLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label totalDownloadedValueLabel = new Label();
        totalDownloadedValueLabel.setStyle("-fx-text-fill: white;");
        totalDownloadedValueLabel.textProperty().bind(totalDownloaded);

        Label totalUploadedLabel = new Label("Total Uploaded:");
        totalUploadedLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label totalUploadedValueLabel = new Label();
        totalUploadedValueLabel.setStyle("-fx-text-fill: white;");
        totalUploadedValueLabel.textProperty().bind(totalUploaded);

        // Add to grid
        statsGrid.add(ipLabel, 0, 0);
        statsGrid.add(ipValueLabel, 0, 1);
        statsGrid.add(macLabel, 0, 2);
        statsGrid.add(macValueLabel, 0, 3);

        statsGrid.add(downloadSpeedLabel, 1, 0);
        statsGrid.add(downloadSpeedValueLabel, 1, 1);
        statsGrid.add(uploadSpeedLabel, 1, 2);
        statsGrid.add(uploadSpeedValueLabel, 1, 3);

        statsGrid.add(totalDownloadedLabel, 2, 0);
        statsGrid.add(totalDownloadedValueLabel, 2, 1);
        statsGrid.add(totalUploadedLabel, 2, 2);
        statsGrid.add(totalUploadedValueLabel, 2, 3);

        statsBox.getChildren().addAll(headerLabel, statsGrid);
        return statsBox;
    }

    private LineChart<Number, Number> createNetworkChart() {
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis();

        xAxis.setLabel("Time (seconds)");
        xAxis.setAnimated(false);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);

        yAxis.setLabel("Speed (KB/s)");
        yAxis.setAnimated(false);

        final LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Network Usage");
        lineChart.setCreateSymbols(false);
        lineChart.setAnimated(false);
        lineChart.getData().add(downloadSeries);
        lineChart.getData().add(uploadSeries);

        lineChart.setStyle(
                "-fx-background-color: #323232; " +
                        "-fx-plot-background-color: #262626; " +
                        "-fx-text-fill: white;"
        );

        // Style the series
        downloadSeries.getNode().lookup(".chart-series-line").setStyle("-fx-stroke: #4CAF50; -fx-stroke-width: 2px;");
        uploadSeries.getNode().lookup(".chart-series-line").setStyle("-fx-stroke: #2196F3; -fx-stroke-width: 2px;");

        lineChart.lookup(".chart-title").setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        lineChart.lookup(".axis-label").setStyle("-fx-text-fill: #BBBBBB;");

        return lineChart;
    }

    private TableView<ConnectionEntry> createConnectionTable() {
        TableView<ConnectionEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: #323232; -fx-text-fill: white;");

        TableColumn<ConnectionEntry, String> localAddressCol = new TableColumn<>("Local Address");
        localAddressCol.setCellValueFactory(data -> data.getValue().localAddressProperty());

        TableColumn<ConnectionEntry, String> remoteAddressCol = new TableColumn<>("Remote Address");
        remoteAddressCol.setCellValueFactory(data -> data.getValue().remoteAddressProperty());

        TableColumn<ConnectionEntry, String> protocolCol = new TableColumn<>("Protocol");
        protocolCol.setCellValueFactory(data -> data.getValue().protocolProperty());
        protocolCol.setPrefWidth(80);

        TableColumn<ConnectionEntry, String> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(data -> data.getValue().stateProperty());
        stateCol.setPrefWidth(100);

        table.getColumns().addAll(localAddressCol, remoteAddressCol, protocolCol, stateCol);
        table.setItems(connectionData);

        // Style the table
        table.setFixedCellSize(30);
        table.setPrefHeight(180);

        return table;
    }

    public void startMonitoring() {
        // Update data every second
        scheduler.scheduleAtFixedRate(() -> {
            updateNetworkInfo();
            updateConnectionInfo();
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void updateSelectedInterface(int index) {
        if (remoteStation != null) {
            // For remote monitoring, we don't have multiple interfaces
            currentInterface.set("Remote");
            ipAddress.set("Remote");
            macAddress.set("Remote");
            connectionStatus.set("Connected");
            return;
        }

        if (index < 0 || index >= networkInterfaces.size()) {
            return;
        }

        currentNetworkIF = networkInterfaces.get(index);
        currentNetworkIF.updateAttributes();

        currentInterface.set(currentNetworkIF.getName());

        // Update interface details
        String[] ipv4Addresses = currentNetworkIF.getIPv4addr();
        ipAddress.set(ipv4Addresses.length > 0 ? ipv4Addresses[0] : "N/A");
        macAddress.set(currentNetworkIF.getMacaddr());

        // Update connection status
        boolean isConnected = currentNetworkIF.getSpeed() > 0;
        connectionStatus.set(isConnected ? "Connected" : "Disconnected");

        // Reset chart data
        Platform.runLater(() -> {
            downloadSeries.getData().clear();
            uploadSeries.getData().clear();
            xSeriesData = 0;
        });

        // Update total bytes
        updateTotalBytes(currentNetworkIF);
    }

    private void updateNetworkInfo() {
        if (remoteStation != null) {
            try {
                String response = remoteStation.getNetworkUsage();
                Platform.runLater(() -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray interfaces = json.getJSONArray("interfaces");

                        if (!interfaces.isEmpty()) {
                            JSONObject netData = interfaces.getJSONObject(0);

                            // Calculate speeds
                            long bytesRecv = netData.getLong("bytes_recv");
                            long bytesSent = netData.getLong("bytes_sent");

                            String interfaceName = "Remote";
                            NetworkStats prevStats = previousStats.get(interfaceName);

                            if (prevStats != null) {
                                long currentTime = System.currentTimeMillis();
                                long byteDiff = bytesRecv - prevStats.bytesReceived;
                                long sentDiff = bytesSent - prevStats.bytesSent;
                                double timeSeconds = (currentTime - prevStats.timestamp) / 1000.0;

                                // Calculate speeds in KB/s
                                double downloadRate = byteDiff / (1024.0 * timeSeconds);
                                double uploadRate = sentDiff / (1024.0 * timeSeconds);

                                // Update previous stats
                                previousStats.put(interfaceName, new NetworkStats(
                                        bytesRecv,
                                        bytesSent,
                                        currentTime
                                ));

                                // Update UI
                                downloadSpeed.set(formatSpeed(downloadRate));
                                uploadSpeed.set(formatSpeed(uploadRate));

                                // Update chart data
                                downloadSeries.getData().add(new XYChart.Data<>(xSeriesData, downloadRate));
                                uploadSeries.getData().add(new XYChart.Data<>(xSeriesData, uploadRate));
                                xSeriesData++;

                                // Remove old data points
                                if (downloadSeries.getData().size() > MAX_DATA_POINTS) {
                                    downloadSeries.getData().remove(0);
                                    uploadSeries.getData().remove(0);
                                }
                            } else {
                                // First reading - just store the stats
                                previousStats.put(interfaceName, new NetworkStats(
                                        bytesRecv,
                                        bytesSent,
                                        System.currentTimeMillis()
                                ));
                            }

                            // Update total bytes
                            totalDownloaded.set(formatBytes(bytesRecv));
                            totalUploaded.set(formatBytes(bytesSent));
                        }
                    } catch (JSONException e) {
                        System.err.println("Error parsing network API response: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Error fetching remote network data: " + e.getMessage());
            }
        } else {
            // Local monitoring mode
            if (currentNetworkIF == null) {
                return;
            }

            // Update network interface
            currentNetworkIF.updateAttributes();

            // Check if interface is still connected
            boolean isConnected = currentNetworkIF.getSpeed() > 0;
            Platform.runLater(() -> connectionStatus.set(isConnected ? "Connected" : "Disconnected"));

            // Calculate speeds
            String interfaceName = currentNetworkIF.getName();
            NetworkStats prevStats = previousStats.get(interfaceName);

            if (prevStats != null) {
                long currentBytes = currentNetworkIF.getBytesRecv();
                long currentSent = currentNetworkIF.getBytesSent();
                long currentTime = System.currentTimeMillis();

                long byteDiff = currentBytes - prevStats.bytesReceived;
                long sentDiff = currentSent - prevStats.bytesSent;
                double timeSeconds = (currentTime - prevStats.timestamp) / 1000.0;

                // Calculate speeds in KB/s
                double downloadRate = byteDiff / (1024.0 * timeSeconds);
                double uploadRate = sentDiff / (1024.0 * timeSeconds);

                // Update previous stats
                previousStats.put(interfaceName, new NetworkStats(
                        currentBytes,
                        currentSent,
                        currentTime
                ));

                // Update UI
                Platform.runLater(() -> {
                    // Update speed labels
                    downloadSpeed.set(formatSpeed(downloadRate));
                    uploadSpeed.set(formatSpeed(uploadRate));

                    // Update chart data
                    downloadSeries.getData().add(new XYChart.Data<>(xSeriesData, downloadRate));
                    uploadSeries.getData().add(new XYChart.Data<>(xSeriesData, uploadRate));
                    xSeriesData++;

                    // Remove old data points
                    if (downloadSeries.getData().size() > MAX_DATA_POINTS) {
                        downloadSeries.getData().removeFirst();
                        uploadSeries.getData().removeFirst();
                    }

                    updateTotalBytes(currentNetworkIF);
                });
            }
        }
    }

    private void updateTotalBytes(NetworkIF netIF) {
        if (remoteStation != null) {
            return;
        }
        totalDownloaded.set(formatBytes(netIF.getBytesRecv()));
        totalUploaded.set(formatBytes(netIF.getBytesSent()));
    }

    private void updateConnectionInfo() {
        Platform.runLater(() -> {
            connectionData.clear();

            if (remoteStation != null) {
                // Remote connection info
                try {
                    String response = remoteStation.getNetworkUsage();
                    JSONObject json = new JSONObject(response);
                    JSONArray connections = json.getJSONArray("connections");

                    for (int i = 0; i < connections.length(); i++) {
                        JSONObject conn = connections.getJSONObject(i);
                        connectionData.add(new ConnectionEntry(
                                conn.optString("laddr", "N/A"),
                                conn.optString("raddr", "N/A"),
                                conn.optString("type", "N/A"),
                                conn.optString("status", "N/A")
                        ));
                    }
                } catch (Exception e) {
                    System.err.println("Error fetching remote connection data: " + e.getMessage());
                    connectionData.add(new ConnectionEntry(
                            "Remote:443",
                            "192.168.1.1:53",
                            "TCP",
                            "ESTABLISHED"
                    ));
                }
            } else if (currentNetworkIF != null && connectionStatus.get().equals("Connected")) {
                connectionData.add(new ConnectionEntry(
                        ipAddress.get() + ":443",
                        "192.168.1.1:53",
                        "TCP",
                        "ESTABLISHED"
                ));
                connectionData.add(new ConnectionEntry(
                        ipAddress.get() + ":80",
                        "172.217.22.14:443",
                        "TCP",
                        "TIME_WAIT"
                ));
            }
        });
    }

    public String formatSpeed(double kbps) {
        if (kbps < 1000) {
            return df.format(kbps) + " KB/s";
        } else {
            return df.format(kbps / 1024.0) + " MB/s";
        }
    }

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

    public void setRemoteStation(RemoteStation remoteStation) {
        this.remoteStation = remoteStation;
        updateNetworkInterfaces();
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private static class NetworkStats {
        final long bytesReceived;
        final long bytesSent;
        final long timestamp;

        NetworkStats(long bytesReceived, long bytesSent, long timestamp) {
            this.bytesReceived = bytesReceived;
            this.bytesSent = bytesSent;
            this.timestamp = timestamp;
        }
    }

    public static class ConnectionEntry {
        private final SimpleStringProperty localAddress;
        private final SimpleStringProperty remoteAddress;
        private final SimpleStringProperty protocol;
        private final SimpleStringProperty state;

        public ConnectionEntry(String localAddress, String remoteAddress, String protocol, String state) {
            this.localAddress = new SimpleStringProperty(localAddress);
            this.remoteAddress = new SimpleStringProperty(remoteAddress);
            this.protocol = new SimpleStringProperty(protocol);
            this.state = new SimpleStringProperty(state);
        }

        public SimpleStringProperty localAddressProperty() { return localAddress; }
        public SimpleStringProperty remoteAddressProperty() { return remoteAddress; }
        public SimpleStringProperty protocolProperty() { return protocol; }
        public SimpleStringProperty stateProperty() { return state; }
    }
}