package com.exodus.glimpse.models;

import com.exodus.glimpse.BaseMonitor;
import com.exodus.glimpse.RemoteStation;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import oshi.software.os.OSProcess;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Monitors and manages system processes.
 */
public class ProcessMonitor extends BaseMonitor {
    private final DecimalFormat df = new DecimalFormat("#.##");
    private final ObservableList<ProcessInfo> processData = FXCollections.observableArrayList();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private RemoteStation remoteStation;
    private SortOrder currentSortOrder = SortOrder.CPU_DESC;
    private boolean showAllProcesses = false;

    /**
     * Constructor that initializes process monitoring.
     */
    public ProcessMonitor() {
        super();
        startMonitoring();
    }

    /**
     * Creates the process monitoring panel with toolbar, process table and status bar.
     * @return VBox containing the process monitor UI.
     */
    public VBox createMonitorPanel() {
        VBox monitorPanel = new VBox(10);
        monitorPanel.setPadding(new Insets(10));
        monitorPanel.setStyle("-fx-background-color: #282828;");

        // Toolbar with controls
        ToolBar toolbar = createToolbar();

        // Process Table
        TableView<ProcessInfo> processTable = createProcessTable();
        VBox.setVgrow(processTable, Priority.ALWAYS);

        // Status bar
        HBox statusBar = createStatusBar();

        monitorPanel.getChildren().addAll(toolbar, processTable, statusBar);
        return monitorPanel;
    }

    /**
     * Creates the toolbar with sorting options and process actions.
     * @return ToolBar containing control buttons.
     */
    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.setStyle("-fx-background-color: #323232;");

        // Sort ComboBox
        ComboBox<SortOrder> sortComboBox = new ComboBox<>();
        sortComboBox.getItems().addAll(
                SortOrder.CPU_DESC,
                SortOrder.CPU_ASC,
                SortOrder.RAM_DESC,
                SortOrder.RAM_ASC,
                SortOrder.NAME_ASC,
                SortOrder.NAME_DESC,
                SortOrder.PID_ASC,
                SortOrder.PID_DESC
        );
        sortComboBox.setValue(currentSortOrder);
        sortComboBox.setConverter(new SortOrderStringConverter());
        sortComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            currentSortOrder = newVal;
            updateProcessInfo();
        });

        // Toggle for showing all processes
        ToggleButton showAllToggle = new ToggleButton("Show All Processes");
        showAllToggle.setStyle("-fx-text-fill: white;");
        showAllToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            showAllProcesses = newVal;
            updateProcessInfo();
        });

        // Refresh button
        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-text-fill: white;");
        refreshButton.setOnAction(e -> updateProcessInfo());

        // Kill process button
        Button killButton = new Button("Kill Process");
        killButton.setStyle("-fx-text-fill: white; -fx-background-color: #EA4335;");
        killButton.setOnAction(e -> killSelectedProcess());

        toolbar.getItems().addAll(
                new Label("Sort by:"),
                sortComboBox,
                new Separator(),
                showAllToggle,
                new Separator(),
                refreshButton,
                new Separator(),
                killButton
        );

        return toolbar;
    }

    /**
     * Creates a table showing system processes with sorting capabilities.
     * @return TableView configured for process display.
     */
    private TableView<ProcessInfo> createProcessTable() {
        TableView<ProcessInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color: #323232; -fx-text-fill: white;");
        table.setPlaceholder(new Label("No processes found"));
        table.setRowFactory(tv -> {
            TableRow<ProcessInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showProcessDetails(row.getItem());
                }
            });
            return row;
        });

        // Process Name column
        TableColumn<ProcessInfo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setComparator(String::compareToIgnoreCase);

        // PID column
        TableColumn<ProcessInfo, Integer> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(new PropertyValueFactory<>("pid"));
        pidCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // CPU Usage column
        TableColumn<ProcessInfo, Double> cpuCol = new TableColumn<>("CPU %");
        cpuCol.setCellValueFactory(new PropertyValueFactory<>("cpuUsage"));
        cpuCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        cpuCol.setCellFactory(column -> new TableCell<ProcessInfo, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(df.format(item) + "%");
                    // Color coding based on CPU usage
                    if (item > 70) {
                        setStyle("-fx-text-fill: #EA4335;"); // Red
                    } else if (item > 30) {
                        setStyle("-fx-text-fill: #FBBC05;"); // Yellow
                    } else {
                        setStyle("-fx-text-fill: white;");
                    }
                }
            }
        });

        // Memory Usage column
        TableColumn<ProcessInfo, Long> memoryCol = new TableColumn<>("Memory");
        memoryCol.setCellValueFactory(new PropertyValueFactory<>("memoryBytes"));
        memoryCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        memoryCol.setCellFactory(column -> new TableCell<ProcessInfo, Long>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatBytes(item));
                }
            }
        });

        // Threads column
        TableColumn<ProcessInfo, Integer> threadsCol = new TableColumn<>("Threads");
        threadsCol.setCellValueFactory(new PropertyValueFactory<>("threadCount"));
        threadsCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // User column
        TableColumn<ProcessInfo, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("user"));

        table.getColumns().addAll(nameCol, pidCol, cpuCol, memoryCol, threadsCol, userCol);
        table.setItems(processData);

        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem killItem = new MenuItem("Kill Process");
        killItem.setOnAction(e -> killSelectedProcess());
        MenuItem detailsItem = new MenuItem("View Details");
        detailsItem.setOnAction(e -> showProcessDetails(table.getSelectionModel().getSelectedItem()));
        contextMenu.getItems().addAll(detailsItem, killItem);
        table.setContextMenu(contextMenu);

        return table;
    }

    /**
     * Creates a status bar showing process count and memory usage.
     * @return HBox containing status information.
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: #323232;");

        Label processCountLabel = new Label();
        processCountLabel.setStyle("-fx-text-fill: white;");
        processCountLabel.textProperty().bind(Bindings.concat("Processes: ", Bindings.size(processData)));

        Label totalMemoryLabel = new Label();
        totalMemoryLabel.setStyle("-fx-text-fill: white;");

        processData.addListener((javafx.collections.ListChangeListener<ProcessInfo>) c -> {
            long totalMemory = processData.stream().mapToLong(ProcessInfo::getMemoryBytes).sum();
            totalMemoryLabel.setText("Total Memory: " + formatBytes(totalMemory));
        });

        statusBar.getChildren().addAll(processCountLabel, new Separator(), totalMemoryLabel);
        return statusBar;
    }

    /**
     * Starts monitoring system processes.
     */
    protected void startMonitoring() {
        scheduler.scheduleAtFixedRate(this::updateProcessInfo, 0, 2, TimeUnit.SECONDS);
    }

    /**
     * Updates process information from local or remote source.
     */
    private void updateProcessInfo() {
        if (remoteStation != null) {
            updateRemoteProcessInfo();
        } else {
            updateLocalProcessInfo();
        }
    }

    /**
     * Updates process information from local system.
     */
    private void updateLocalProcessInfo() {
        List<OSProcess> processes = os.getProcesses();

        List<ProcessInfo> processInfoList = processes.stream()
                .map(process -> {
                    String name = process.getName();
                    if (name.length() > 30) {
                        name = name.substring(0, 27) + "...";
                    }

                    double cpuUsage = 100d * (process.getKernelTime() + process.getUserTime()) / process.getUpTime();
                    long memBytes = process.getResidentSetSize();

                    return new ProcessInfo(
                            name,
                            process.getProcessID(),
                            cpuUsage,
                            memBytes,
                            process.getThreadCount(),
                            process.getUser()
                    );
                })
                .collect(Collectors.toList());

        // Sort based on current sort order
        processInfoList.sort(currentSortOrder.getComparator());

        Platform.runLater(() -> {
            processData.setAll(processInfoList);
        });
    }

    /**
     * Updates process information from remote station.
     */
    private void updateRemoteProcessInfo() {
        try {
            String response = remoteStation.getTopProcesses();
            JSONArray processes = new JSONArray(response);
            List<ProcessInfo> processInfoList = new ArrayList<>();

            for (int i = 0; i < processes.length(); i++) {
                try {
                    JSONObject proc = processes.getJSONObject(i);
                    String name = proc.getString("name");
                    if (name.length() > 30) {
                        name = name.substring(0, 27) + "...";
                    }

                    double cpuUsage = proc.getDouble("cpu_percent");
                    long memBytes = proc.has("memory_bytes") ?
                            proc.getLong("memory_bytes") :
                            (long)(proc.getDouble("memory_percent") * 0.01 * 8 * 1024 * 1024 * 1024); // Estimate if bytes not available

                    int pid = proc.getInt("pid");
                    int threadCount = proc.optInt("thread_count", 0);
                    String user = proc.optString("username", "N/A");

                    processInfoList.add(new ProcessInfo(
                            name,
                            pid,
                            cpuUsage,
                            memBytes,
                            threadCount,
                            user
                    ));
                } catch (JSONException e) {
                    System.err.println("Error parsing process data: " + e.getMessage());
                }
            }

            // Sort based on current sort order
            processInfoList.sort(currentSortOrder.getComparator());

            Platform.runLater(() -> {
                processData.setAll(processInfoList);
            });
        } catch (Exception e) {
            System.err.println("Remote process monitoring error: " + e.getMessage());
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Remote Monitoring Error");
                alert.setHeaderText("Failed to fetch remote process data");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    /**
     * Attempts to kill the selected process.
     */
    private void killSelectedProcess() {
        // Implementation for killing processes would go here
        // This would need to be different for local vs remote
        // We didn't really have time to implement this yet
    }

    /**
     * Shows detailed information about a process.
     * @param process The process to display.
     */
    private void showProcessDetails(ProcessInfo process) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Process Details");
        alert.setHeaderText("Details for " + process.getName() + " (PID: " + process.getPid() + ")");

        String content = String.format(
                "Name: %s\n" +
                        "PID: %d\n" +
                        "CPU Usage: %.2f%%\n" +
                        "Memory Usage: %s\n" +
                        "Threads: %d\n" +
                        "User: %s",
                process.getName(),
                process.getPid(),
                process.getCpuUsage(),
                formatBytes(process.getMemoryBytes()),
                process.getThreadCount(),
                process.getUser()
        );

        alert.setContentText(content);
        alert.showAndWait();
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

    /**
     * Sets the remote station for monitoring.
     * @param remoteStation The RemoteStation to monitor.
     */
    public void setRemoteStation(RemoteStation remoteStation) {
        this.remoteStation = remoteStation;
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public enum SortOrder {
        CPU_DESC(Comparator.comparingDouble(ProcessInfo::getCpuUsage).reversed()),
        CPU_ASC(Comparator.comparingDouble(ProcessInfo::getCpuUsage)),
        RAM_DESC(Comparator.comparingLong(ProcessInfo::getMemoryBytes).reversed()),
        RAM_ASC(Comparator.comparingLong(ProcessInfo::getMemoryBytes)),
        NAME_ASC(Comparator.comparing(ProcessInfo::getName, String::compareToIgnoreCase)),
        NAME_DESC(Comparator.comparing(ProcessInfo::getName, String::compareToIgnoreCase).reversed()),
        PID_ASC(Comparator.comparingInt(ProcessInfo::getPid)),
        PID_DESC(Comparator.comparingInt(ProcessInfo::getPid).reversed());

        private final Comparator<ProcessInfo> comparator;

        SortOrder(Comparator<ProcessInfo> comparator) {
            this.comparator = comparator;
        }

        public Comparator<ProcessInfo> getComparator() {
            return comparator;
        }
    }

    private static class SortOrderStringConverter extends javafx.util.StringConverter<SortOrder> {
        @Override
        public String toString(SortOrder sortOrder) {
            if (sortOrder == null) return "";
            switch (sortOrder) {
                case CPU_DESC: return "CPU Usage (High to Low)";
                case CPU_ASC: return "CPU Usage (Low to High)";
                case RAM_DESC: return "Memory (High to Low)";
                case RAM_ASC: return "Memory (Low to High)";
                case NAME_ASC: return "Name (A-Z)";
                case NAME_DESC: return "Name (Z-A)";
                case PID_ASC: return "PID (Ascending)";
                case PID_DESC: return "PID (Descending)";
                default: return sortOrder.name();
            }
        }

        @Override
        public SortOrder fromString(String string) {
            return null; // Not needed for this use case
        }
    }

    /**
     * Inner class that represents process information for display in the process table.
     * Contains observable properties for process name, PID, memory usage and percentage.
     */
    public static class ProcessInfo {
        private final String name;
        private final int pid;
        private final double cpuUsage;
        private final long memoryBytes;
        private final int threadCount;
        private final String user;

        /**
         * Creates a new ProcessInfo object with detailed process metrics.
         * 
         * @param name The name of the process.
         * @param pid The process ID as an integer.
         * @param cpuUsage The CPU usage percentage as a double.
         * @param memoryBytes The memory usage in bytes.
         * @param threadCount The number of threads used by the process.
         * @param user The username of the process owner.
         */
        public ProcessInfo(String name, int pid, double cpuUsage, long memoryBytes, int threadCount, String user) {
            this.name = name;
            this.pid = pid;
            this.cpuUsage = cpuUsage;
            this.memoryBytes = memoryBytes;
            this.threadCount = threadCount;
            this.user = user;
        }

        public String getName() { return name; }
        public int getPid() { return pid; }
        public double getCpuUsage() { return cpuUsage; }
        public long getMemoryBytes() { return memoryBytes; }
        public int getThreadCount() { return threadCount; }
        public String getUser() { return user; }
    }
}
