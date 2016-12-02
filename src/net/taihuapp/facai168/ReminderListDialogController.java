package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Created by ghe on 11/26/16.
 *
 */
public class ReminderListDialogController {
    private MainApp mMainApp = null;
    private Stage mDialogStage = null;

    @FXML
    private TableView<Reminder> mReminderTableView;

    @FXML
    private Button mEditButton;

    @FXML
    private void handleNew() { showEditReminderDialog(new Reminder()); }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        // todo more work here
    }

    private void showEditReminderDialog(Reminder reminder) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("EditReminderDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Reminder:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mDialogStage);
            dialogStage.setScene(new Scene(loader.load()));

            EditReminderDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, reminder, dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void close() { mDialogStage.close(); }

    @FXML
    private void handleClose() { mDialogStage.close(); }

    @FXML
    private void initialize() {
       // mEditButton.disableProperty().bind(Bindings.isEmpty(
        //        mReminderTableView.getSelectionModel().getSelectedItems()));
    }
}
