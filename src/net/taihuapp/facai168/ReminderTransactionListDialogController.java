package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Created by ghe on 11/26/16.
 *
 */
public class ReminderTransactionListDialogController {
    private MainApp mMainApp = null;
    private Stage mDialogStage = null;

    @FXML
    private TableView<ReminderTransaction> mReminderTransactionTableView;
    @FXML
    private TableColumn<ReminderTransaction, String> mStatusTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, LocalDate> mDueDateTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, String> mAccountTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, Reminder.Type> mTypeTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, String> mPayeeTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, BigDecimal> mAmountTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, String> mFrequencyTableColumn;

    @FXML
    private Button mEnterButton;
    @FXML
    private Button mSkipButton;
    @FXML
    private Button mEditButton;
    @FXML
    private Button mDeleteButton;

    @FXML
    private void handleEdit() {
        Reminder reminder = mReminderTransactionTableView.getSelectionModel().getSelectedItem().getReminder();
        showEditReminderDialog(new Reminder(reminder));
    }
    @FXML
    private void handleDelete() {

    }
    @FXML
    private void handleEnter() {

    }

    @FXML
    private void handleSkip() {
        Reminder reminder = mReminderTransactionTableView.getSelectionModel().getSelectedItem().getReminder();

    }
    @FXML
    private void handleNew() { showEditReminderDialog(new Reminder()); }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        mReminderTransactionTableView.setItems(mMainApp.getReminderTransactionList());
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
        // setup table columns
        mDueDateTableColumn.setCellValueFactory(cellData -> cellData.getValue().getDueDateProperty());
        mTypeTableColumn.setCellValueFactory(cellData -> cellData.getValue().getReminder().getTypeProperty());
        mPayeeTableColumn.setCellValueFactory(cellData -> cellData.getValue().getReminder().getPayeeProperty());
        mAmountTableColumn.setCellValueFactory(cellData -> cellData.getValue().getReminder().getAmountProperty());

        final String OVERDUE = "Overdue";
        final String DUESOON = "Due soon";
        mStatusTableColumn.setCellValueFactory(cellData -> {
            ReminderTransaction rt = cellData.getValue();
            Reminder reminder = cellData.getValue().getReminder();
            LocalDate today = LocalDate.now();
            if (today.isAfter(rt.getDueDate()))
                return new SimpleStringProperty(OVERDUE);
            if (rt.getTransaction() == null
                    && today.plus(reminder.getDateSchedule().getAlertDay(), DAYS).isAfter(rt.getDueDate()))
                return new SimpleStringProperty(DUESOON);
            return new SimpleStringProperty();
                });
        mStatusTableColumn.setCellFactory(column -> new TableCell<ReminderTransaction, String>() {
            @Override
            protected  void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                setText(empty ? "" : getItem());
                setGraphic(null);

                if (!isEmpty()) {
                    if (item.equals(OVERDUE))
                        setStyle("-fx-background-color:red");
                    else if (item.equals(DUESOON))
                        setStyle("-fx-background-color:yellow");
                }
            }
        });

        mFrequencyTableColumn.setCellValueFactory(cellData -> {
            DateSchedule ds = cellData.getValue().getReminder().getDateSchedule();
            return new SimpleStringProperty("Every " + ds.getNumPeriod() + " "
                    + ds.getBaseUnit().toString().toLowerCase());
        });

        mAccountTableColumn.setCellValueFactory(cellData
                -> mMainApp.getAccountByID(cellData.getValue().getReminder().getAccountID()).getNameProperty());

        mEditButton.visibleProperty().bind(Bindings.isNotEmpty(
                mReminderTransactionTableView.getSelectionModel().getSelectedItems()));
        mDeleteButton.visibleProperty().bind(Bindings.isNotEmpty(
                mReminderTransactionTableView.getSelectionModel().getSelectedItems()));
        mEnterButton.visibleProperty().bind(Bindings.isNotEmpty(
                mReminderTransactionTableView.getSelectionModel().getSelectedItems()));
        mSkipButton.visibleProperty().bind(Bindings.isNotEmpty(
                mReminderTransactionTableView.getSelectionModel().getSelectedItems()));
    }
}
