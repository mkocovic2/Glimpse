package com.exodus.glimpse;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class AddStationDialog extends Dialog<RemoteStation> {
    final TextField nameField = new TextField();
    private final TextField apiUrlField = new TextField();
    private final TextField apiKeyField = new TextField();

    public AddStationDialog() {
        setTitle("Add Remote Station");
        setHeaderText("Enter remote station details");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("API URL:"), 0, 1);
        grid.add(apiUrlField, 1, 1);
        grid.add(new Label("API Key:"), 0, 2);
        grid.add(apiKeyField, 1, 2);

        getDialogPane().setContent(grid);

        setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return new RemoteStation(
                        apiUrlField.getText(),
                        apiKeyField.getText()
                );
            }
            return null;
        });
    }
}