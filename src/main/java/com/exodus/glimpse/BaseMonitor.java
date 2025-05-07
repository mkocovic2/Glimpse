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

    // Common UI components
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

    protected String formatSpeed(double kbps) {
        if (kbps < 1000) {
            return df.format(kbps) + " KB/s";
        } else {
            return df.format(kbps / 1024.0) + " MB/s";
        }
    }

    protected VBox createBaseMonitorPanel(String title) {
        VBox monitorPanel = new VBox(15);
        monitorPanel.setPadding(new Insets(10));
        monitorPanel.setStyle("-fx-background-color: #282828;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        monitorPanel.getChildren().add(titleLabel);
        return monitorPanel;
    }

    public void setRemoteStation(RemoteStation remoteStation) {
        this.remoteStation = remoteStation;
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public VBox createMonitorPanel() {
        return null;
    }

    protected abstract void startMonitoring();
}