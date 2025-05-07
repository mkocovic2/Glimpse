package com.exodus.glimpse;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
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

public class DiskMonitor {
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private final FileSystem fileSystem;
    private final ComboBox<String> diskSelector;
    private RemoteStation remoteStation;

    private final DecimalFormat df = new DecimalFormat("#.##");
    private final SimpleStringProperty currentDisk = new SimpleStringProperty("N/A");
    private final SimpleStringProperty diskModel = new SimpleStringProperty("N/A");
    private final SimpleStringProperty diskSize = new SimpleStringProperty("N/A");
    private final SimpleStringProperty diskUsed = new SimpleStringProperty("N/A");
    private final SimpleStringProperty diskFree = new SimpleStringProperty("N/A");
    private final SimpleDoubleProperty diskUsagePercent = new SimpleDoubleProperty(0);
    private final SimpleStringProperty readSpeed = new SimpleStringProperty("0 KB/s");
    private final SimpleStringProperty writeSpeed = new SimpleStringProperty("0 KB/s");
    private final SimpleStringProperty readTime = new SimpleStringProperty("0 ms");
    private final SimpleStringProperty writeTime = new SimpleStringProperty("0 ms");

    private final ObservableList<DiskPartition> partitionData = FXCollections.observableArrayList();

    private final Map<String, DiskStats> previousStats = new HashMap<>();
    private OSFileStore currentFileStore;
    private HWDiskStore currentDiskStore;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public DiskMonitor() {
        systemInfo = new SystemInfo();
        hardware = systemInfo.getHardware();
        os = systemInfo.getOperatingSystem();
        fileSystem = os.getFileSystem();

        // Initialize disk selector
        diskSelector = new ComboBox<>();

        // Update disks list
        updateDiskList();

        // Start monitoring
        startMonitoring();
    }

    public VBox createDiskMonitorPanel() {
        VBox monitorPanel = new VBox(15);
        monitorPanel.setPadding(new Insets(10));
        monitorPanel.setStyle("-fx-background-color: #282828;");

        // Disk selector
        HBox selectorPanel = new HBox(10);
        selectorPanel.setAlignment(Pos.CENTER_LEFT);
        selectorPanel.setPadding(new Insets(5, 0, 10, 0));

        Label diskLabel = new Label("Disk:");
        diskLabel.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 13px;");

        diskSelector.setPrefWidth(300);
        diskSelector.setStyle("-fx-background-color: #3D3D3D; -fx-text-fill: white;");
        diskSelector.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldVal, newVal) -> updateSelectedDisk(newVal.intValue())
        );

        selectorPanel.getChildren().addAll(diskLabel, diskSelector);

        // Disk Usage Section
        VBox usageSection = createDiskUsageSection();

        // Disk Partitions Table
        TableView<DiskPartition> partitionTable = createPartitionTable();
        VBox.setVgrow(partitionTable, Priority.ALWAYS);

        // Add all components to main container
        monitorPanel.getChildren().addAll(selectorPanel, usageSection, partitionTable);

        return monitorPanel;
    }

    private void updateDiskList() {
        if (remoteStation != null) {
            // For remote monitoring, we'll just show a single "Remote" disk
            Platform.runLater(() -> {
                diskSelector.getItems().setAll("Remote Disk");
                diskSelector.getSelectionModel().selectFirst();
                currentDisk.set("Remote");
                diskModel.set("Remote");
                diskSize.set("Remote");
                diskFree.set("Remote");
                diskUsed.set("Remote");
                diskUsagePercent.set(0);
            });
        } else {
            // Local monitoring
            List<String> diskNames = new ArrayList<>();
            List<OSFileStore> fileStores = fileSystem.getFileStores();

            for (OSFileStore store : fileStores) {
                String name = store.getName() + " (" + store.getMount() + ")";
                diskNames.add(name);

                // Initialize stats for this disk
                if (!previousStats.containsKey(store.getName())) {
                    // Try to find matching HWDiskStore
                    for (HWDiskStore disk : hardware.getDiskStores()) {
                        if (store.getName().contains(disk.getName())) {
                            previousStats.put(store.getName(), new DiskStats(
                                    disk.getReadBytes(),
                                    disk.getWriteBytes(),
                                    disk.getTimeStamp(),
                                    System.currentTimeMillis()
                            ));
                            break;
                        }
                    }
                }
            }

            Platform.runLater(() -> {
                diskSelector.getItems().setAll(diskNames);
                if (!diskNames.isEmpty()) {
                    diskSelector.getSelectionModel().selectFirst();
                    updateSelectedDisk(0);
                }
            });
        }
    }

    private VBox createDiskUsageSection() {
        VBox usageSection = new VBox(15);
        usageSection.setPadding(new Insets(10));
        usageSection.setStyle("-fx-background-color: #323232; -fx-background-radius: 5;");

        // Title
        Label titleLabel = new Label("Disk Usage");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        titleLabel.setStyle("-fx-text-fill: white;");

        // Usage Bar and Stats in a HBox
        HBox usageContainer = new HBox(30);
        usageContainer.setAlignment(Pos.CENTER_LEFT);

        // Usage Bar
        VBox usageBarBox = new VBox(10);
        usageBarBox.setAlignment(Pos.CENTER);
        usageBarBox.setPrefWidth(200);

        ProgressBar usageBar = new ProgressBar(0);
        usageBar.setPrefWidth(180);
        usageBar.setPrefHeight(20);
        usageBar.setStyle("-fx-accent: #3D5AFE;");

        diskUsagePercent.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                usageBar.setProgress(newVal.doubleValue() / 100);

                // Change color based on usage
                String color;
                if (newVal.doubleValue() < 70) {
                    color = "#3D5AFE"; // Blue for normal
                } else if (newVal.doubleValue() < 90) {
                    color = "#FBBC05"; // Yellow for medium
                } else {
                    color = "#EA4335"; // Red for high
                }
                usageBar.setStyle("-fx-accent: " + color + ";");
            });
        });

        Label usagePercentLabel = new Label();
        usagePercentLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        usagePercentLabel.textProperty().bind(
                diskUsagePercent.asString("%.1f%% Used")
        );

        usageBarBox.getChildren().addAll(usageBar, usagePercentLabel);

        // Stats Grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(10);

        // Basic Info
        Label modelLabel = new Label("Model:");
        modelLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label modelValueLabel = new Label();
        modelValueLabel.setStyle("-fx-text-fill: white;");
        modelValueLabel.textProperty().bind(diskModel);

        Label sizeLabel = new Label("Total Size:");
        sizeLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label sizeValueLabel = new Label();
        sizeValueLabel.setStyle("-fx-text-fill: white;");
        sizeValueLabel.textProperty().bind(diskSize);

        Label usedLabel = new Label("Used Space:");
        usedLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label usedValueLabel = new Label();
        usedValueLabel.setStyle("-fx-text-fill: white;");
        usedValueLabel.textProperty().bind(diskUsed);

        Label freeLabel = new Label("Free Space:");
        freeLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label freeValueLabel = new Label();
        freeValueLabel.setStyle("-fx-text-fill: white;");
        freeValueLabel.textProperty().bind(diskFree);

        // Performance Stats
        Label readSpeedLabel = new Label("Read Speed:");
        readSpeedLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label readSpeedValueLabel = new Label();
        readSpeedValueLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        readSpeedValueLabel.textProperty().bind(readSpeed);

        Label writeSpeedLabel = new Label("Write Speed:");
        writeSpeedLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label writeSpeedValueLabel = new Label();
        writeSpeedValueLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
        writeSpeedValueLabel.textProperty().bind(writeSpeed);

        Label readTimeLabel = new Label("Avg Read Time:");
        readTimeLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label readTimeValueLabel = new Label();
        readTimeValueLabel.setStyle("-fx-text-fill: white;");
        readTimeValueLabel.textProperty().bind(readTime);

        Label writeTimeLabel = new Label("Avg Write Time:");
        writeTimeLabel.setStyle("-fx-text-fill: #BBBBBB;");
        Label writeTimeValueLabel = new Label();
        writeTimeValueLabel.setStyle("-fx-text-fill: white;");
        writeTimeValueLabel.textProperty().bind(writeTime);

        // First column
        statsGrid.add(modelLabel, 0, 0);
        statsGrid.add(modelValueLabel, 0, 1);
        statsGrid.add(sizeLabel, 0, 2);
        statsGrid.add(sizeValueLabel, 0, 3);

        // Second column
        statsGrid.add(usedLabel, 1, 0);
        statsGrid.add(usedValueLabel, 1, 1);
        statsGrid.add(freeLabel, 1, 2);
        statsGrid.add(freeValueLabel, 1, 3);

        // Third column
        statsGrid.add(readSpeedLabel, 2, 0);
        statsGrid.add(readSpeedValueLabel, 2, 1);
        statsGrid.add(writeSpeedLabel, 2, 2);
        statsGrid.add(writeSpeedValueLabel, 2, 3);

        // Fourth column
        statsGrid.add(readTimeLabel, 3, 0);
        statsGrid.add(readTimeValueLabel, 3, 1);
        statsGrid.add(writeTimeLabel, 3, 2);
        statsGrid.add(writeTimeValueLabel, 3, 3);

        usageContainer.getChildren().addAll(usageBarBox, statsGrid);
        usageSection.getChildren().addAll(titleLabel, usageContainer);

        return usageSection;
    }

    private TableView<DiskPartition> createPartitionTable() {
        TableView<DiskPartition> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: #323232; -fx-text-fill: white;");

        TableColumn<DiskPartition, String> nameCol = new TableColumn<>("Volume Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<DiskPartition, String> mountCol = new TableColumn<>("Mount Point");
        mountCol.setCellValueFactory(data -> data.getValue().mountPointProperty());

        TableColumn<DiskPartition, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        typeCol.setPrefWidth(80);

        TableColumn<DiskPartition, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(data -> data.getValue().sizeProperty());
        sizeCol.setPrefWidth(100);

        TableColumn<DiskPartition, String> usedCol = new TableColumn<>("Used");
        usedCol.setCellValueFactory(data -> data.getValue().usedProperty());
        usedCol.setPrefWidth(100);

        TableColumn<DiskPartition, String> usageCol = new TableColumn<>("Usage %");
        usageCol.setCellValueFactory(data -> data.getValue().usagePercentProperty());
        usageCol.setPrefWidth(80);

        table.getColumns().addAll(nameCol, mountCol, typeCol, sizeCol, usedCol, usageCol);
        table.setItems(partitionData);

        // Style the table
        table.setFixedCellSize(30);
        table.setPrefHeight(180);

        return table;
    }

    private void startMonitoring() {
        // Update data every second
        scheduler.scheduleAtFixedRate(() -> {
            updateDiskInfo();
            updatePartitionInfo();
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void updateSelectedDisk(int index) {
        if (remoteStation != null) {
            // For remote monitoring, we don't have multiple disks
            currentDisk.set("Remote");
            diskModel.set("Remote");
            diskSize.set("Remote");
            diskFree.set("Remote");
            diskUsed.set("Remote");
            diskUsagePercent.set(0);
            return;
        }

        List<OSFileStore> fileStores = fileSystem.getFileStores();
        if (index < 0 || index >= fileStores.size()) {
            return;
        }

        currentFileStore = fileStores.get(index);
        currentDisk.set(currentFileStore.getName());

        // Find matching HWDiskStore
        for (HWDiskStore disk : hardware.getDiskStores()) {
            if (currentFileStore.getName().contains(disk.getName())) {
                currentDiskStore = disk;
                diskModel.set(disk.getModel());
                break;
            }
        }

        if (currentDiskStore == null) {
            diskModel.set("Unknown");
        }

        // Update disk space info
        updateDiskSpaceInfo();
    }

    private void updateDiskInfo() {
        if (remoteStation != null) {
            // Remote monitoring mode
            try {
                String response = remoteStation.getDiskUsage();
                Platform.runLater(() -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray partitions = json.getJSONArray("partitions");

                        if (partitions.length() > 0) {
                            JSONObject diskData = partitions.getJSONObject(0);

                            // Update disk space info
                            long totalSpace = diskData.getLong("total");
                            long usedSpace = diskData.getLong("used");
                            long freeSpace = diskData.getLong("free");
                            double usagePercentValue = (double) usedSpace / totalSpace * 100;

                            diskSize.set(formatBytes(totalSpace));
                            diskUsed.set(formatBytes(usedSpace));
                            diskFree.set(formatBytes(freeSpace));
                            diskUsagePercent.set(usagePercentValue);

                            // Update I/O stats if available
                            JSONObject ioCounters = diskData.optJSONObject("io_counters");
                            if (ioCounters != null) {
                                String diskName = "Remote";
                                DiskStats prevStats = previousStats.get(diskName);

                                long readBytes = ioCounters.getLong("read_bytes");
                                long writeBytes = ioCounters.getLong("write_bytes");
                                long currentTime = System.currentTimeMillis();

                                if (prevStats != null) {
                                    long readDiff = readBytes - prevStats.readBytes;
                                    long writeDiff = writeBytes - prevStats.writeBytes;
                                    double timeSeconds = (currentTime - prevStats.timestamp) / 1000.0;

                                    // Calculate speeds in KB/s
                                    double readRate = timeSeconds > 0 ? readDiff / (1024.0 * timeSeconds) : 0;
                                    double writeRate = timeSeconds > 0 ? writeDiff / (1024.0 * timeSeconds) : 0;

                                    // Update previous stats
                                    previousStats.put(diskName, new DiskStats(
                                            readBytes,
                                            writeBytes,
                                            currentTime, // Using current time as disk timestamp for remote
                                            currentTime
                                    ));

                                    // Update UI
                                    readSpeed.set(formatSpeed(readRate));
                                    writeSpeed.set(formatSpeed(writeRate));
                                } else {
                                    // First reading - just store the stats
                                    previousStats.put(diskName, new DiskStats(
                                            readBytes,
                                            writeBytes,
                                            currentTime,
                                            currentTime
                                    ));
                                }
                            }
                        }
                    } catch (JSONException e) {
                        System.err.println("Error parsing disk API response: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Error fetching remote disk data: " + e.getMessage());
            }
        } else {
            // Local monitoring mode
            if (currentFileStore == null) {
                return;
            }

            // Update file store
            List<OSFileStore> updatedStores = fileSystem.getFileStores();
            for (OSFileStore store : updatedStores) {
                if (store.getName().equals(currentFileStore.getName())) {
                    currentFileStore = store;
                    break;
                }
            }

            // Update disk space info
            updateDiskSpaceInfo();

            // Update I/O stats if we have a matching HWDiskStore
            if (currentDiskStore != null) {
                currentDiskStore.updateAttributes();

                String diskName = currentFileStore.getName();
                DiskStats prevStats = previousStats.get(diskName);

                if (prevStats != null) {
                    long currentReadBytes = currentDiskStore.getReadBytes();
                    long currentWriteBytes = currentDiskStore.getWriteBytes();
                    long currentTime = System.currentTimeMillis();

                    long readDiff = currentReadBytes - prevStats.readBytes;
                    long writeDiff = currentWriteBytes - prevStats.writeBytes;
                    double timeSeconds = (currentTime - prevStats.timestamp) / 1000.0;

                    // Calculate speeds in KB/s
                    double readRate = timeSeconds > 0 ? readDiff / (1024.0 * timeSeconds) : 0;
                    double writeRate = timeSeconds > 0 ? writeDiff / (1024.0 * timeSeconds) : 0;

                    // Calculate transfer times (using transfer time as approximation)
                    double currentReadTime;
                    double currentWriteTime;

                    if (currentDiskStore.getReads() > 0) {
                        currentReadTime = currentDiskStore.getTransferTime() / currentDiskStore.getReads();
                    } else {
                        currentReadTime = 0;
                    }

                    if (currentDiskStore.getWrites() > 0) {
                        currentWriteTime = currentDiskStore.getTransferTime() / currentDiskStore.getWrites();
                    } else {
                        currentWriteTime = 0;
                    }

                    // Update previous stats
                    previousStats.put(diskName, new DiskStats(
                            currentReadBytes,
                            currentWriteBytes,
                            currentDiskStore.getTimeStamp(),
                            currentTime
                    ));

                    // Update UI
                    Platform.runLater(() -> {
                        // Update speed labels
                        readSpeed.set(formatSpeed(readRate));
                        writeSpeed.set(formatSpeed(writeRate));

                        // Update time labels if we have valid values
                        if (!Double.isNaN(currentReadTime) && !Double.isInfinite(currentReadTime) && currentReadTime > 0) {
                            readTime.set(df.format(currentReadTime) + " ms");
                        }
                        if (!Double.isNaN(currentWriteTime) && !Double.isInfinite(currentWriteTime) && currentWriteTime > 0) {
                            writeTime.set(df.format(currentWriteTime) + " ms");
                        }
                    });
                }
            }
        }
    }

    private void updateDiskSpaceInfo() {
        if (remoteStation != null) {
            // For remote monitoring, this is handled in updateDiskInfo()
            return;
        }

        long totalSpace = currentFileStore.getTotalSpace();
        long usableSpace = currentFileStore.getUsableSpace();
        long usedSpace = totalSpace - usableSpace;
        double usagePercentValue = (double) usedSpace / totalSpace * 100;

        Platform.runLater(() -> {
            diskSize.set(formatBytes(totalSpace));
            diskFree.set(formatBytes(usableSpace));
            diskUsed.set(formatBytes(usedSpace));
            diskUsagePercent.set(usagePercentValue);
        });
    }

    private void updatePartitionInfo() {
        if (remoteStation != null) {
            // Remote partition info
            try {
                String response = remoteStation.getDiskUsage();
                Platform.runLater(() -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray partitions = json.getJSONArray("partitions");
                        partitionData.clear();

                        for (int i = 0; i < partitions.length(); i++) {
                            JSONObject partition = partitions.getJSONObject(i);
                            long total = partition.getLong("total");
                            long used = partition.getLong("used");
                            long free = partition.getLong("free");
                            double percentUsed = (double) used / total * 100;

                            partitionData.add(new DiskPartition(
                                    partition.optString("device", "Unknown"),
                                    partition.optString("mountpoint", "N/A"),
                                    partition.optString("fstype", "N/A"),
                                    formatBytes(total),
                                    formatBytes(used),
                                    df.format(percentUsed) + "%"
                            ));
                        }
                    } catch (JSONException e) {
                        System.err.println("Error parsing partition API response: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Error fetching remote partition data: " + e.getMessage());
            }
        } else {
            // Local partition info
            List<OSFileStore> fileStores = fileSystem.getFileStores();

            Platform.runLater(() -> {
                partitionData.clear();

                for (OSFileStore store : fileStores) {
                    long totalSpace = store.getTotalSpace();
                    if (totalSpace <= 0) continue; // Skip invalid partitions

                    long usableSpace = store.getUsableSpace();
                    long usedSpace = totalSpace - usableSpace;
                    double percentUsed = (double) usedSpace / totalSpace * 100;

                    partitionData.add(new DiskPartition(
                            store.getName(),
                            store.getMount(),
                            store.getType(),
                            formatBytes(totalSpace),
                            formatBytes(usedSpace),
                            df.format(percentUsed) + "%"
                    ));
                }
            });
        }
    }

    private String formatSpeed(double kbps) {
        if (kbps < 1000) {
            return df.format(kbps) + " KB/s";
        } else {
            return df.format(kbps / 1024.0) + " MB/s";
        }
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

    public void setRemoteStation(RemoteStation remoteStation) {
        this.remoteStation = remoteStation;
        updateDiskList();
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private static class DiskStats {
        final long readBytes;
        final long writeBytes;
        final long diskTimestamp;
        final long timestamp;

        DiskStats(long readBytes, long writeBytes, long diskTimestamp, long timestamp) {
            this.readBytes = readBytes;
            this.writeBytes = writeBytes;
            this.diskTimestamp = diskTimestamp;
            this.timestamp = timestamp;
        }
    }

    public static class DiskPartition {
        private final SimpleStringProperty name;
        private final SimpleStringProperty mountPoint;
        private final SimpleStringProperty type;
        private final SimpleStringProperty size;
        private final SimpleStringProperty used;
        private final SimpleStringProperty usagePercent;

        public DiskPartition(String name, String mountPoint, String type, String size, String used, String usagePercent) {
            this.name = new SimpleStringProperty(name);
            this.mountPoint = new SimpleStringProperty(mountPoint);
            this.type = new SimpleStringProperty(type);
            this.size = new SimpleStringProperty(size);
            this.used = new SimpleStringProperty(used);
            this.usagePercent = new SimpleStringProperty(usagePercent);
        }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty mountPointProperty() { return mountPoint; }
        public SimpleStringProperty typeProperty() { return type; }
        public SimpleStringProperty sizeProperty() { return size; }
        public SimpleStringProperty usedProperty() { return used; }
        public SimpleStringProperty usagePercentProperty() { return usagePercent; }
    }
}