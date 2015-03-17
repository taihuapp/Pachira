package net.taihuapp.facai168;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.lang.management.PlatformLoggingMXBean;

public class MainController {

    private MainApp mMainApp;

    public void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;
    }

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
        mMainApp.newDB();
    }

    @FXML
    private void handleBackup() {
        System.out.println("Backup");
    }

    @FXML
    private void handleClearList() {
        System.out.println("Clear List");
    }
}
