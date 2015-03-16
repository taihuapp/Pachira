package net.taihuapp.facai168;

import javafx.application.Platform;
import javafx.fxml.FXML;

import java.lang.management.PlatformLoggingMXBean;

public class MainController {
    @FXML
    private void handleClose() {
        Platform.exit();
    }

    @FXML
    private void handleOpen() {
        System.out.println("Open...");
    }

    @FXML
    private void handleNew() {
        System.out.println("New");
    }

    @FXML
    private void handleBackup() {
        System.out.println("Backup");
    }
}
