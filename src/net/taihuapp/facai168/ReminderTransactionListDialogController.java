package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
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
import java.util.Collections;

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
        // create a copy of the reminder for editing
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        Reminder reminder = new Reminder(rt.getReminder());
        // put the next due date as the start date for the reminder to be edited
        reminder.getDateSchedule().setStartDate(rt.getDueDate());
        showEditReminderDialog(new Reminder(reminder));
    }
    @FXML
    private void handleDelete() {
        System.err.println("delete reminder has not been implemented yet");
    }

    @FXML
    private void handleEnter() {
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        Reminder reminder = rt.getReminder();
        Transaction.TradeAction ta;
        switch (reminder.getType()) {
            case PAYMENT:
                ta = Transaction.TradeAction.WITHDRAW;
                break;
            case TRANSFER:
                ta = Transaction.TradeAction.XOUT;
                break;
            case DEPOSIT:
            default:
                ta = Transaction.TradeAction.DEPOSIT;
                break;
        }
        int accountID = reminder.getAccountID();
        Transaction transaction = new Transaction(accountID, rt.getDueDate(), ta, reminder.getCategoryID());
        transaction.setAmount(reminder.getAmount());
        transaction.setPayee(reminder.getPayee());
        int tid = mMainApp.showEditTransactionDialog(mMainApp.getStage(), transaction,
                mMainApp.getAccountList(Account.Type.SPENDING, null, false),
                mMainApp.getAccountByID(reminder.getAccountID()), Collections.singletonList(ta));
        if (tid >= 0) {
            mMainApp.insertReminderTransactions(rt, transaction);
            mMainApp.initReminderTransactionList();
        }
    }

    @FXML
    private void handleSkip() {
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        mMainApp.insertReminderTransactions(rt, null);
        mMainApp.initReminderTransactionList();
    }

    @FXML
    private void handleNew() { showEditReminderDialog(new Reminder()); }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        mReminderTransactionTableView.setItems(mMainApp.getReminderTransactionList());
        int i;
        for (i = 0; i < mMainApp.getReminderTransactionList().size(); i++) {
            String status = mMainApp.getReminderTransactionList().get(i).getStatus();
            if (status.equals(ReminderTransaction.DUESOON) || status.equals(ReminderTransaction.OVERDUE))
                break;
        }
        mReminderTransactionTableView.scrollTo(i);
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
        mAmountTableColumn.setCellValueFactory(cellData -> {
            Transaction t = mMainApp.getTransactionByID(cellData.getValue().getTransactionID());
            if (t != null)
                return t.getAmountProperty();
            if (cellData.getValue().getStatus().equals(ReminderTransaction.SKIPPED))
                return null;
            return cellData.getValue().getReminder().getAmountProperty();
        });
        mAmountTableColumn.setStyle( "-fx-alignment: CENTER-RIGHT;");

        mStatusTableColumn.setCellValueFactory(cellData -> cellData.getValue().getStatusProperty());
        mStatusTableColumn.setCellFactory(column -> new TableCell<ReminderTransaction, String>() {
            @Override
            protected  void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                setText(empty ? "" : getItem());
                setGraphic(null);

                if (!isEmpty()) {
                    switch (item) {
                        case ReminderTransaction.OVERDUE:
                            setStyle("-fx-background-color:red");
                            break;
                        case ReminderTransaction.DUESOON:
                            setStyle("-fx-background-color:yellow");
                            break;
                        default:
                            setStyle("");
                            break;
                    }
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

        BooleanBinding visibility = Bindings.createBooleanBinding(() -> {
            ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
            return (rt != null) && !rt.getStatus().equals(ReminderTransaction.COMPLETED)
                    && !rt.getStatus().equals(ReminderTransaction.SKIPPED);
        }, mReminderTransactionTableView.getSelectionModel().getSelectedItems());

        mEditButton.visibleProperty().bind(visibility);
        mDeleteButton.visibleProperty().bind(visibility);
        mEnterButton.visibleProperty().bind(visibility);
        mSkipButton.visibleProperty().bind(visibility);
    }
}
