package com.exodus.glimpse;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.text.DecimalFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Abstract base class for monitoring system resources with common UI components and utilities.
 */
public abstract class BaseMonitor {
    protected final SystemInfo systemInfo;
    protected final HardwareAbstractionLayer hardware;
    protected final OperatingSystem os;
    protected RemoteStation remoteStation;

    protected final DecimalFormat df = new DecimalFormat("#.##");
    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    protected static final int MAX_DATA_POINTS = 60;
    protected int xSeriesData = 0;

    public BaseMonitor() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
    }

    /**
     * Creates a circular indicator for displaying usage percentages with dynamic color changes.
     * @param usageProperty The property binding to the usage value.
     * @param labelText The label text to display below the indicator.
     * @return StackPane containing the circular indicator.
     */
    protected StackPane createCircularIndicator(SimpleDoubleProperty usageProperty, String labelText) {
        StackPane circleContainer = new StackPane();
        circleContainer.setMinSize(100, 100);
        circleContainer.setMaxSize(100, 100);

        javafx.scene.shape.Circle outerCircle = new javafx.scene.shape.Circle(45);
        outerCircle.setStyle("-fx-fill: #2D2D2D; -fx-stroke: #3D5AFE; -fx-stroke-width: 3;");

        Label percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        usageProperty.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                percentLabel.setText(df.format(newVal.doubleValue()) + "%");

                // Change color based on usage
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

        Label usageLabel = new Label(labelText);
        usageLabel.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 12px;");

        VBox container = new VBox(5, circleContainer, usageLabel);
        container.setAlignment(Pos.CENTER);

        StackPane stackPane = new StackPane();
        stackPane.getChildren().addAll(outerCircle, percentLabel);
        circleContainer.getChildren().add(stackPane);

        return circleContainer;
    }

    /**
     * Creates a horizontal row for displaying a label and its corresponding value.
     * @param labelText The label text.
     * @param valueProperty The property binding to the value.
     * @return HBox containing the label and value.
     */
    protected HBox createStatRow(String labelText, SimpleStringProperty valueProperty) {
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
     * Creates a line chart for displaying usage data over time.
     * @param title The title of the chart.
     * @param yAxisLabel The label for the Y-axis.
     * @param series The data series to display.
     * @return LineChart configured with the provided data.
     */
    protected LineChart<Number, Number> createUsageChart(String title, String yAxisLabel, XYChart.Series<Number, Number> series) {
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis(0, 100, 20);

        xAxis.setLabel("Time (seconds)");
        xAxis.setAnimated(false);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);

        yAxis.setLabel(yAxisLabel);
        yAxis.setAnimated(false);

        final LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle(title);
        lineChart.setCreateSymbols(false);
        lineChart.setAnimated(false);
        lineChart.getData().add(series);

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
     * Formats bytes into a human-readable string (e.g., KB, MB, GB).
     * @param bytes The number of bytes to format.
     * @return Formatted string representation.
     */
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
     * Formats network speed into a human-readable string (e.g., KB/s, MB/s).
     * @param kbps The speed in kilobits per second.
     * @return Formatted string representation.
     */
    protected String formatSpeed(double kbps) {
        if (kbps < 1000) {
            return df.format(kbps) + " KB/s";
        } else {
            return df.format(kbps / 1024.0) + " MB/s";
        }
    }

    /**
     * Creates a base panel for monitoring with a title.
     * @param title The title of the panel.
     * @return VBox configured as the base panel.
     */
    protected VBox createBaseMonitorPanel(String title) {
        VBox monitorPanel = new VBox(15);
        monitorPanel.setPadding(new Insets(10));
        monitorPanel.setStyle("-fx-background-color: #282828;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        monitorPanel.getChildren().add(titleLabel);
        return monitorPanel;
    }

    /**
     * Sets the remote station for monitoring.
     * @param remoteStation The RemoteStation object to monitor.
     */
    public void setRemoteStation(RemoteStation remoteStation) {
        this.remoteStation = remoteStation;
    }

    /**
     * Shuts down the monitoring scheduler.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Creates the monitor panel (to be implemented by subclasses).
     * @return VBox containing the monitor UI.
     */
    public VBox createMonitorPanel() {
        return null;
    }

    /**
     * Starts monitoring (to be implemented by subclasses).
     */
    protected abstract void startMonitoring();
}