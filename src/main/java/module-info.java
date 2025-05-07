module com.exodus.glimpse {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires java.management;
    requires com.github.oshi;
    requires com.sun.jna;
    requires jsch;
    requires org.json;

    opens com.exodus.glimpse to javafx.fxml;
    exports com.exodus.glimpse;
    exports com.exodus.glimpse.models;
    opens com.exodus.glimpse.models to javafx.fxml;
}