package com.exodus.glimpse;

import com.exodus.glimpse.models.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.image.ImageView;
import java.util.Optional;

import java.net.URL;

/**
 * Main application class for the Glimpse task manager.
 */
public class GlimpseTaskManager extends Application {
    private final ObservableList<RemoteStation> remoteStations = FXCollections.observableArrayList();
    private final StringProperty selectedResource = new SimpleStringProperty("Hardware");
    private double xOffset = 0;
    private double yOffset = 0;
    private VBox rightSection;

    private HardwareMonitor hardwareMonitor;
    private ProcessMonitor processMonitor;
    private CPUMonitor cpuMonitor;
    private RAMMonitor ramMonitor;
    private NetworkMonitor networkMonitor;
    private DiskMonitor diskMonitor;
    private GPUMonitor gpuMonitor;

    /**
     * Starts the JavaFX application and initializes the UI.
     * @param primaryStage The primary stage for the application.
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.initStyle(StageStyle.UNDECORATED);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1E1E1E; -fx-background-radius: 10;");

        HBox titleBar = createTitleBar(primaryStage);
        root.setTop(titleBar);

        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setDividerPositions(0.15);
        mainSplitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");

        VBox leftSidebar = createLeftSidebar();

        SplitPane contentSplitPane = new SplitPane();
        contentSplitPane.setDividerPositions(0.55);
        contentSplitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");

        // Initialize monitors
        hardwareMonitor = new HardwareMonitor();
        processMonitor = new ProcessMonitor();
        cpuMonitor = new CPUMonitor();
        ramMonitor = new RAMMonitor();
        networkMonitor = new NetworkMonitor();
        diskMonitor = new DiskMonitor();
        gpuMonitor = new GPUMonitor();

        selectedResource.set("Hardware");

        VBox centerSection = createCenterSection();
        rightSection = createRightSection();

        updateRightPanel();

        // Add content to split panes
        contentSplitPane.getItems().addAll(centerSection, rightSection);
        mainSplitPane.getItems().addAll(leftSidebar, contentSplitPane);
        root.setCenter(mainSplitPane);
        root.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.5)));

        Scene scene = new Scene(root, 850, 600); // Increased initial window size
        scene.setFill(Color.TRANSPARENT);

        // Load CSS styles
        try {
            URL cssUrl = getClass().getResource("/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                System.err.println("CSS file not found. Using default styling.");
            }
        } catch (Exception e) {
            System.err.println("Error loading CSS: " + e.getMessage());
        }

        primaryStage.setTitle("Glimpse");
        URL imageUrl = getClass().getResource("/GlimpseLogo1.png");
        if (imageUrl != null) {
            Image image = new Image(imageUrl.toExternalForm());
            primaryStage.getIcons().add(image);
        }
        primaryStage.setScene(scene);
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.show();
    }

    /**
     * Creates the title bar with window controls and drag functionality.
     * @param stage The stage to control.
     * @return HBox containing the title bar UI.
     */
    private HBox createTitleBar(Stage stage) {
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_RIGHT);
        titleBar.setPadding(new Insets(5, 5, 5, 10));
        titleBar.setStyle("-fx-background-color: #252525; -fx-background-radius: 10 10 0 0;");

        HBox titleSection = new HBox(10);
        titleSection.setAlignment(Pos.CENTER_LEFT);

        ImageView logoImageView = new ImageView();
        logoImageView.setFitHeight(20);
        logoImageView.setFitWidth(20);
        logoImageView.setPreserveRatio(true);

        try {
            URL imageUrl = getClass().getResource("/GlimpseLogo1.png");
            if (imageUrl == null) {
                StackPane logoPane = new StackPane();
                logoPane.setMinSize(20, 20);
                logoPane.setMaxSize(20, 20);
                logoPane.setStyle("-fx-background-color: #0D6EFD; -fx-background-radius: 5;");
                Label logoLabel = new Label("G");
                logoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
                logoPane.getChildren().add(logoLabel);
                titleSection.getChildren().add(logoPane);
            } else {
                Image logoImage = new Image(imageUrl.toExternalForm());
                logoImageView.setImage(logoImage);
                titleSection.getChildren().add(logoImageView);
            }
        } catch (Exception e) {
            StackPane logoPane = new StackPane();
            logoPane.setMinSize(20, 20);
            logoPane.setMaxSize(20, 20);
            logoPane.setStyle("-fx-background-color: #0D6EFD; -fx-background-radius: 5;");
            Label logoLabel = new Label("G");
            logoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
            logoPane.getChildren().add(logoLabel);
            titleSection.getChildren().add(logoPane);
        }

        Label titleLabel = new Label("Glimpse");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        titleSection.getChildren().add(titleLabel);
        HBox.setHgrow(titleSection, Priority.ALWAYS);

        Button minimizeBtn = createWindowButton("—", "#555555", e -> stage.setIconified(true));
        Button maximizeBtn = createWindowButton("□", "#555555", e -> {
            if (stage.isMaximized()) {
                stage.setMaximized(false);
            } else {
                stage.setMaximized(true);
            }
        });
        Button closeBtn = createWindowButton("✕", "#E81123", e -> stage.close());

        titleBar.setOnMousePressed(mouseEvent -> {
            xOffset = mouseEvent.getSceneX();
            yOffset = mouseEvent.getSceneY();
        });

        titleBar.setOnMouseDragged(mouseEvent -> {
            if (!stage.isMaximized()) {
                stage.setX(mouseEvent.getScreenX() - xOffset);
                stage.setY(mouseEvent.getScreenY() - yOffset);
            }
        });

        titleBar.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                if (stage.isMaximized()) {
                    stage.setMaximized(false);
                } else {
                    stage.setMaximized(true);
                }
            }
        });

        titleBar.getChildren().addAll(titleSection, minimizeBtn, maximizeBtn, closeBtn);
        return titleBar;
    }

    /**
     * Creates a window control button (minimize, maximize, close).
     * @param text The button text.
     * @param hoverColor The color when hovered.
     * @param action The action to perform on click.
     * @return Button configured as a window control.
     */
    private Button createWindowButton(String text, String hoverColor, EventHandler<ActionEvent> action) {
        Button button = new Button(text);
        button.setMinSize(30, 20);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 10px;");

        button.setOnMouseEntered(e ->
                button.setStyle("-fx-background-color: " + hoverColor + "; -fx-text-fill: white; -fx-font-size: 10px;")
        );
        button.setOnMouseExited(e ->
                button.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 10px;")
        );

        button.setOnAction(action);
        return button;
    }

    /**
     * Creates the left sidebar with device and resource navigation.
     * @return VBox containing the sidebar UI.
     */
    private VBox createLeftSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(10));
        sidebar.setPrefWidth(180);
        sidebar.setStyle("-fx-background-color: #252525;");

        HBox searchBox = new HBox();
        TextField searchField = new TextField();
        searchField.setPromptText("Search Devices");
        searchField.setPrefWidth(220);
        searchField.setStyle("-fx-background-color: #3D3D3D; -fx-text-fill: white; -fx-prompt-text-fill: #888888;");
        searchBox.getChildren().add(searchField);

        Label typesHeader = new Label("Devices");
        typesHeader.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        typesHeader.setPadding(new Insets(10, 0, 5, 0));

        Button addStationBtn = createSidebarButton("+ Add Remote Station", "#888888");
        addStationBtn.setDisable(false);

        addStationBtn.setOnAction(e -> {
            AddStationDialog dialog = new AddStationDialog();
            Optional<RemoteStation> result = dialog.showAndWait();

            result.ifPresent(station -> {
                remoteStations.add(station);
                Button stationBtn = createSidebarButton("🌐 " + dialog.nameField.getText(), "white");
                stationBtn.setOnAction(event -> {
                    processMonitor.setRemoteStation(station);
                    cpuMonitor.setRemoteStation(station);
                    ramMonitor.setRemoteStation(station);
                    networkMonitor.setRemoteStation(station);
                    diskMonitor.setRemoteStation(station);

                    updateRightPanel();
                });
                sidebar.getChildren().add(sidebar.getChildren().size() - 2, stationBtn);
            });
        });

        Button localMonitorBtn = createSidebarButton("🖥️ Local Monitoring", "#BBBBBB");
        localMonitorBtn.setOnAction(e -> {
            processMonitor.setRemoteStation(null);
            cpuMonitor.setRemoteStation(null);
            ramMonitor.setRemoteStation(null);
            networkMonitor.setRemoteStation(null);
            diskMonitor.setRemoteStation(null);

            updateRightPanel();
        });

        sidebar.getChildren().addAll(typesHeader, localMonitorBtn, addStationBtn);
        return sidebar;
    }

    /**
     * Creates a sidebar button with hover effects.
     * @param text The button text.
     * @param textColor The text color.
     * @return Button configured for the sidebar.
     */
    private Button createSidebarButton(String text, String textColor) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-padding: 8px;");

        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #3D3D3D; -fx-text-fill: " + textColor + "; -fx-padding: 8px;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-padding: 8px;"));

        return button;
    }

    /**
     * Creates the center section with resource selection buttons.
     * @return VBox containing the center UI.
     */
    private VBox createCenterSection() {
        VBox centerSection = new VBox(5);
        centerSection.setPadding(new Insets(10));
        centerSection.setStyle("-fx-background-color: #2D2D2D;");

        VBox entriesBox = new VBox(1);

        Button summaryEntry = createResourceEntry("Hardware", "Device Name", new Color(0.42, 0.6, 0.85, 1), "\uD83D\uDD0C");
        Button processEntry = createResourceEntry("Processes", "Processes", new Color(0.42, 0.6, 0.85, 1), "🖥️");
        Button cpuEntry = createResourceEntry("CPU", "Current Process", new Color(0.42, 0.6, 0.85, 1), "\uD83C\uDFFF");
        Button gpuEntry = createResourceEntry("GPU", "Current Process", new Color(0.16, 0.5, 0.73, 1), "⚙");
        Button ramEntry = createResourceEntry("RAM", "Current Process", new Color(0.23, 0.35, 0.6, 1), "\uD83C\uDF9F");
        Button networkEntry = createResourceEntry("Network", "Current Process", new Color(0.8, 0.2, 0.2, 1), "📡");
        Button diskEntry = createResourceEntry("Disk", "Current Process", new Color(0.8, 0.2, 0.2, 1), "\uD83D\uDCBF");

        // Highlight the Hardware button by default
        summaryEntry.setStyle("-fx-background-color: #3D5AFE; -fx-background-radius: 5;");

        setupResourceSelection(processEntry);
        setupResourceSelection(summaryEntry);
        setupResourceSelection(cpuEntry);
        setupResourceSelection(gpuEntry);
        setupResourceSelection(ramEntry);
        setupResourceSelection(networkEntry);
        setupResourceSelection(diskEntry);

        entriesBox.getChildren().addAll(summaryEntry, processEntry, cpuEntry, gpuEntry, ramEntry, networkEntry, diskEntry);
        centerSection.getChildren().addAll(entriesBox);

        return centerSection;
    }

    /**
     * Creates a resource entry button with icon and labels.
     * @param resource The resource name.
     * @param process The process description.
     * @param color The background color for the icon.
     * @param icon The icon character.
     * @return Button configured as a resource entry.
     */
    private Button createResourceEntry(String resource, String process, Color color, String icon) {
        Button entry = new Button();
        entry.setMaxWidth(Double.MAX_VALUE);
        entry.setPadding(new Insets(10));
        entry.setUserData(resource);

        HBox contentBox = new HBox(10);
        contentBox.setAlignment(Pos.CENTER_LEFT);

        StackPane iconPane = new StackPane();
        iconPane.setMinSize(30, 30);
        iconPane.setMaxSize(30, 30);
        iconPane.setStyle("-fx-background-color: " + toRGBCode(color) + "; -fx-background-radius: 5;");

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        iconPane.getChildren().add(iconLabel);

        VBox textBox = new VBox(3);
        Label resourceLabel = new Label(resource);
        resourceLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        Label processLabel = new Label(process);
        processLabel.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 11px;");
        textBox.getChildren().addAll(resourceLabel, processLabel);

        contentBox.getChildren().addAll(iconPane, textBox);
        entry.setGraphic(contentBox);
        entry.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");

        return entry;
    }

    /**
     * Sets up selection behavior for resource buttons.
     * @param resourceButton The button to configure.
     */
    private void setupResourceSelection(Button resourceButton) {
        resourceButton.setOnAction(e -> {
            Scene scene = resourceButton.getScene();
            if (scene != null) {
                VBox entriesBox = (VBox) resourceButton.getParent();
                entriesBox.getChildren().forEach(node -> {
                    if (node instanceof Button) {
                        Button btn = (Button) node;
                        btn.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
                    }
                });
            }
            resourceButton.setStyle("-fx-background-color: #3D5AFE; -fx-background-radius: 5;");

            String resourceName = (String) resourceButton.getUserData();
            selectedResource.set(resourceName);
            updateRightPanel();
        });
    }

    /**
     * Updates the right panel based on the selected resource.
     */
    private void updateRightPanel() {
        if (rightSection == null) return;

        rightSection.getChildren().clear();

        // Create a ScrollPane to wrap the content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #282828; -fx-border-color: #282828;");

        // Create a container for all content
        VBox contentBox = new VBox(15);

        HBox iconBox = new HBox();
        iconBox.setAlignment(Pos.CENTER);

        StackPane iconPane = new StackPane();
        iconPane.setMinSize(60, 60);
        iconPane.setMaxSize(60, 60);
        iconPane.setStyle("-fx-background-radius: 5;");

        Label iconLabel = new Label();
        iconLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 24px;");

        HBox labelBox = new HBox();
        labelBox.setAlignment(Pos.CENTER);
        labelBox.setPadding(new Insets(15, 0, 0, 0));

        Label titleLabel = new Label();
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");

        // Where the functionality of the application is
        switch (selectedResource.get()) {
            case "Hardware":
                contentBox.getChildren().add(hardwareMonitor.createMonitorPanel());
                break;
            case "Processes":
                contentBox.getChildren().add(processMonitor.createMonitorPanel());
                break;
            case "CPU":
                contentBox.getChildren().add(cpuMonitor.createMonitorPanel());
                break;
            case "GPU":
                contentBox.getChildren().add(gpuMonitor.createMonitorPanel());
                break;
            case "RAM":
                contentBox.getChildren().add(ramMonitor.createMonitorPanel());
                break;
            case "Network":
                contentBox.getChildren().add(networkMonitor.createMonitorPanel());
                break;
            case "Disk":
                contentBox.getChildren().add(diskMonitor.createMonitorPanel());
                break;
            default:
                iconPane.setStyle("-fx-background-color: #4285F4; " + iconPane.getStyle());
                iconLabel.setText("💻");
                titleLabel.setText("Current Device");
                break;
        }

        iconPane.getChildren().add(iconLabel);
        iconBox.getChildren().add(iconPane);
        labelBox.getChildren().add(titleLabel);

        if (contentBox.getChildren().isEmpty()) {
            contentBox.getChildren().addAll(iconBox, labelBox);
        }

        scrollPane.setContent(contentBox);

        rightSection.getChildren().add(scrollPane);
    }

    /**
     * Creates the right section for displaying detailed resource information.
     * @return VBox containing the right section UI.
     */
    private VBox createRightSection() {
        VBox rightSection = new VBox(15);
        rightSection.setPadding(new Insets(15));
        rightSection.setPrefWidth(900);
        rightSection.setStyle("-fx-background-color: #282828;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #282828; -fx-border-color: #282828;");

        HBox iconBox = new HBox();
        iconBox.setAlignment(Pos.CENTER);

        StackPane iconPane = new StackPane();
        iconPane.setMinSize(60, 60);
        iconPane.setMaxSize(60, 60);
        iconPane.setStyle("-fx-background-color: #4285F4; -fx-background-radius: 5;");

        Label iconLabel = new Label("💻");
        iconLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 24px;");
        iconPane.getChildren().add(iconLabel);
        iconBox.getChildren().add(iconPane);

        HBox labelBox = new HBox();
        labelBox.setAlignment(Pos.CENTER);
        labelBox.setPadding(new Insets(15, 0, 0, 0));

        Label titleLabel = new Label("Hardware Overview");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");
        labelBox.getChildren().add(titleLabel);

        VBox contentBox = new VBox(15);
        contentBox.getChildren().addAll(iconBox, labelBox);

        scrollPane.setContent(contentBox);
        rightSection.getChildren().add(scrollPane);

        return rightSection;
    }

    /**
     * Converts a JavaFX Color to an RGB string.
     * @param color The Color to convert.
     * @return RGB string representation.
     */
    private String toRGBCode(Color color) {
        return String.format("rgba(%d, %d, %d, %.1f)",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                color.getOpacity());
    }

    /**
     * Cleans up resources when the application stops.
     */
    @Override
    public void stop() throws Exception {
        // Stops the monitoring process when needed
        if (processMonitor != null) {
            processMonitor.shutdown();
        }
        if (cpuMonitor != null) {
            cpuMonitor.shutdown();
        }
        if (gpuMonitor != null) {
            gpuMonitor.shutdown();
        }
        if (ramMonitor != null) {
            ramMonitor.shutdown();
        }
        if (diskMonitor != null) {
            diskMonitor.shutdown();
        }
        if (networkMonitor != null) {
            networkMonitor.shutdown();
        }

        Platform.exit();
        System.exit(0);

        super.stop();
    }
    public static void main(String[] args) {
        launch(args);
    }
}