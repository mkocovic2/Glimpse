package com.exodus.glimpse.models;

import com.exodus.glimpse.BaseMonitor;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import oshi.hardware.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Monitors and displays hardware information.
 */
public class HardwareMonitor extends BaseMonitor {
    private final SimpleStringProperty cpuModel = new SimpleStringProperty("N/A");
    private final SimpleStringProperty cpuCores = new SimpleStringProperty("N/A");
    private final SimpleStringProperty cpuThreads = new SimpleStringProperty("N/A");
    private final SimpleStringProperty cpuFrequency = new SimpleStringProperty("N/A");
    private final SimpleStringProperty totalRam = new SimpleStringProperty("N/A");
    private final SimpleStringProperty gpuModel = new SimpleStringProperty("N/A");
    private final SimpleStringProperty gpuMemory = new SimpleStringProperty("N/A");
    private final ObservableList<DiskInfo> diskInfo = FXCollections.observableArrayList();

    /**
     * Constructor that initializes hardware monitoring.
     */
    public HardwareMonitor() {
        super();
        startMonitoring();
    }

    /**
     * Starts monitoring hardware information.
     */
    @Override
    protected void startMonitoring() {
        scheduler.scheduleAtFixedRate(this::updateHardwareInfo, 0, 2, TimeUnit.SECONDS);
    }
    
    /**
     * Creates the hardware overview panel with CPU, RAM, GPU and disk info.
     * @return VBox containing the hardware monitor UI.
     */
    @Override
    public VBox createMonitorPanel() {
        return createHardwareMonitorPanel();
    }

    public VBox createHardwareMonitorPanel() {
        VBox monitorPanel = createBaseMonitorPanel("Hardware Monitor");

        // CPU Information
        VBox cpuBox = createInfoBox("CPU Information", Color.rgb(66, 133, 244));
        cpuBox.getChildren().addAll(
                createInfoRow("Model", cpuModel),
                createInfoRow("Cores", cpuCores),
                createInfoRow("Threads", cpuThreads),
                createInfoRow("Frequency", cpuFrequency)
        );

        // RAM Information
        VBox ramBox = createInfoBox("RAM Information", Color.rgb(219, 68, 55));
        ramBox.getChildren().add(createInfoRow("Total Memory", totalRam));

        // GPU Information
        VBox gpuBox = createInfoBox("GPU Information", Color.rgb(244, 180, 0));
        gpuBox.getChildren().addAll(
                createInfoRow("Model", gpuModel),
                createInfoRow("Memory", gpuMemory)
        );

        // Disk Information
        VBox diskBox = createInfoBox("Disk Information", Color.rgb(15, 157, 88));
        TableView<DiskInfo> diskTable = createDiskTable();
        diskBox.getChildren().add(diskTable);

        monitorPanel.getChildren().addAll(cpuBox, ramBox, gpuBox, diskBox);
        return monitorPanel;
    }

    /**
     * Creates a hardware information panel with title and colored border.
     * @param title The panel title.
     * @param color The border color.
     * @return VBox containing the info box.
     */
    private VBox createInfoBox(String title, Color color) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: #333333; -fx-background-radius: 5;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        box.getChildren().add(titleLabel);

        return box;
    }

    
    private HBox createInfoRow(String label, SimpleStringProperty value) {
        return createStatRow(label, value);
    }

    /**
     * Creates a table showing disk information.
     * @return TableView configured for disk display.
     */
    private TableView<DiskInfo> createDiskTable() {
        TableView<DiskInfo> table = new TableView<>(diskInfo);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: transparent;");

        TableColumn<DiskInfo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setStyle("-fx-text-fill: white;");

        TableColumn<DiskInfo, String> modelCol = new TableColumn<>("Model");
        modelCol.setCellValueFactory(data -> data.getValue().modelProperty());
        modelCol.setStyle("-fx-text-fill: white;");

        TableColumn<DiskInfo, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(data -> data.getValue().sizeProperty());
        sizeCol.setStyle("-fx-text-fill: white;");

        table.getColumns().addAll(nameCol, modelCol, sizeCol);
        return table;
    }

    /**
     * Updates all hardware information from local or remote source.
     */
    private void updateHardwareInfo() {
        if (remoteStation != null) {
            // Remote monitoring mode
            try {
                String response = remoteStation.getCpuUsage();
                Platform.runLater(() -> {
                    try {
                        // Update CPU info
                        cpuModel.set("Remote CPU");
                        cpuCores.set("Remote");
                        cpuThreads.set("Remote");
                        cpuFrequency.set("Remote");

                        // Update RAM info
                        totalRam.set("Remote");

                        // Update GPU info
                        gpuModel.set("Remote GPU");
                        gpuMemory.set("Remote");

                        // Update disk info
                        diskInfo.clear();
                        diskInfo.add(new DiskInfo("Remote Disk", "Remote", "Remote"));
                    } catch (Exception e) {
                        System.err.println("Error parsing remote hardware data: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("Error fetching remote hardware data: " + e.getMessage());
            }
        } else {
            // Local monitoring mode
            Platform.runLater(() -> {
                // Update CPU info
                CentralProcessor processor = hardware.getProcessor();
                cpuModel.set(processor.getProcessorIdentifier().getName());
                cpuCores.set(String.valueOf(processor.getPhysicalProcessorCount()));
                cpuThreads.set(String.valueOf(processor.getLogicalProcessorCount()));
                cpuFrequency.set(df.format(processor.getProcessorIdentifier().getVendorFreq() / 1000000000.0) + " GHz");

                // Update RAM info
                GlobalMemory memory = hardware.getMemory();
                totalRam.set(formatBytes(memory.getTotal()));

                // Update GPU info
                List<GraphicsCard> gpus = hardware.getGraphicsCards();
                if (!gpus.isEmpty()) {
                    GraphicsCard gpu = gpus.get(0);
                    gpuModel.set(gpu.getName());
                    gpuMemory.set(formatBytes(gpu.getVRam()));
                } else {
                    gpuModel.set("No GPU detected");
                    gpuMemory.set("N/A");
                }

                // Update disk info
                diskInfo.clear();
                for (HWDiskStore disk : hardware.getDiskStores()) {
                    diskInfo.add(new DiskInfo(
                            disk.getName(),
                            disk.getModel(),
                            formatBytes(disk.getSize())
                    ));
                }
            });
        }
    }

    /**
     * Inner class that represents process information for display in the disk table.
     * Contains observable properties for name, model, size
     */
    public static class DiskInfo {
        private final SimpleStringProperty name;
        private final SimpleStringProperty model;
        private final SimpleStringProperty size;

        /**
         * Constructs a DiskInfo object with the specified name, model, and size.
         * Initializes each field as a SimpleStringProperty for use in JavaFX bindings.
         *
         * @param name the name of the disk
         * @param model the model identifier of the disk
         * @param size  the storage capacity of the disk
         */
        public DiskInfo(String name, String model, String size) {
            this.name = new SimpleStringProperty(name);
            this.model = new SimpleStringProperty(model);
            this.size = new SimpleStringProperty(size);
        }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty modelProperty() { return model; }
        public SimpleStringProperty sizeProperty() { return size; }
    }
}
