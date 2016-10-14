package net.taihuapp.facai168;

import javafx.beans.Observable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Created by ghe on 10/13/16.
 * Base class for all ReportDialogController classes
 */
public class ReportDialogController {

    enum ReportType { NAV }
    private enum Frequency { DAILY, MONTHLY, QUARTERLY, ANNUAL}
    private static class SelectedAccount {
        Account mAccount;
        IntegerProperty mSelectedOrderProperty = new SimpleIntegerProperty(-1); // -1 for not selected

        SelectedAccount(Account a) { mAccount = a; }
        IntegerProperty getSelectedOrderProperty() { return mSelectedOrderProperty;}
        Integer getSelectedOrder() { return getSelectedOrderProperty().get(); }
        void setSelectedOrder(int s) { mSelectedOrderProperty.set(s); }

        Account.Type getType() { return mAccount.getType(); }
        int getDisplayOrder() { return mAccount.getDisplayOrder(); }
        int getID() { return mAccount.getID(); }

        public String toString() { return mAccount.getName(); }
    }

    private ReportType mReportType; // report type
    private ObservableList<SelectedAccount> mAccountList = FXCollections.observableArrayList(
            a -> new Observable[] { a.getSelectedOrderProperty() });

    @FXML
    Button mSelectButton;
    @FXML
    Button mUnselectButton;
    @FXML
    Button mUpButton;
    @FXML
    Button mDownButton;
    @FXML
    Label mStartDateLabel;
    @FXML
    DatePicker mStartDatePicker;
    @FXML
    Label mEndDateLabel;
    @FXML
    DatePicker mEndDatePicker;
    @FXML
    Label mFrequencyLabel;
    @FXML
    ChoiceBox<Frequency> mFrequencyChoiceBox;

    @FXML
    TextArea mReportTextArea;

    @FXML
    ListView<SelectedAccount> mAvailableAccountListView;
    @FXML
    ListView<SelectedAccount> mSelectedAccountListView;


    private MainApp mMainApp;
    private Stage mDialogStage;

    void setMainApp(ReportType reportType, MainApp mainApp, Stage stage) {

        // set members
        mReportType = reportType;
        mMainApp = mainApp;
        mDialogStage = stage;

        // set window title
        mDialogStage.setTitle(mReportType + " Report");

        // initialize account selection controls and time period selection controls
        mAccountList.clear();
        for (Account a : mMainApp.getAccountList(null, null, true)) {
            mAccountList.add(new SelectedAccount(a));
        }

        SortedList<SelectedAccount> availableAccountList
                = new SortedList<>(new FilteredList<>(mAccountList, a->(a.getSelectedOrder() < 0)),
                Comparator.comparing(SelectedAccount::getType).thenComparing(SelectedAccount::getDisplayOrder)
                        .thenComparing(SelectedAccount::getID));

        SortedList<SelectedAccount> selectedAccountList
                = new SortedList<>(new FilteredList<>(mAccountList, a->(a.getSelectedOrder() >= 0)),
                Comparator.comparing(SelectedAccount::getSelectedOrder));

        mAvailableAccountListView.setItems(availableAccountList);
        mSelectedAccountListView.setItems(selectedAccountList);

        // add a selection change listener to the lists
        mAvailableAccountListView.getSelectionModel().selectedItemProperty().addListener(
                (ob, ov, nv)-> mSelectButton.setDisable(nv == null));
        mSelectedAccountListView.getSelectionModel().selectedItemProperty().addListener(
                (ob, ov, nv) -> {
                    int selectedIdx = mSelectedAccountListView.getSelectionModel().getSelectedIndex();
                    int numberOfRows = mSelectedAccountListView.getItems().size();
                    mUnselectButton.setDisable(nv == null);
                    mUpButton.setDisable(nv == null || selectedIdx == 0);
                    mDownButton.setDisable(nv == null || selectedIdx == numberOfRows-1);
                });

        // nothing is selected in either listview, disable these buttons
        mSelectButton.setDisable(true);
        mUnselectButton.setDisable(true);
        mUpButton.setDisable(true);
        mDownButton.setDisable(true);
        mReportTextArea.setVisible(false);
        mReportTextArea.setStyle("-fx-font-family: monospace");

        switch (mReportType) {
            case NAV:
                mStartDateLabel.setText("As of:");
                mStartDatePicker.setValue(LocalDate.now());
                mEndDateLabel.setVisible(false);
                mEndDatePicker.setVisible(false);
                mFrequencyLabel.setVisible(false);
                mFrequencyChoiceBox.setVisible(false);
                break;
            default:
        }
    }

    @FXML
    private void handleSelect() {
        int n = mSelectedAccountListView.getItems().size();
        mAvailableAccountListView.getSelectionModel().getSelectedItem().setSelectedOrder(n);
    }

    @FXML
    private void handleUnselect() {
        SelectedAccount a = mSelectedAccountListView.getSelectionModel().getSelectedItem();

        // without the remove/add process, java throw ArrayIndexOutOfBoundsException
        mAccountList.remove(a);
        a.setSelectedOrder(-1);
        mAccountList.add(a);

        // reset selectedOrder to fill up the hole left by unselecting the account
        for (int i = 0; i < mSelectedAccountListView.getItems().size(); i++) {
            mSelectedAccountListView.getItems().get(i).setSelectedOrder(i);
        }
    }

    @FXML
    private void handleUp() {
        int n = mSelectedAccountListView.getSelectionModel().getSelectedIndex();
        SelectedAccount a0 = mSelectedAccountListView.getItems().get(n);
        SelectedAccount a1 = mSelectedAccountListView.getItems().get(n-1);
        a0.setSelectedOrder(n-1);
        a1.setSelectedOrder(n);
    }

    @FXML
    private void handleDown() {
        int n = mSelectedAccountListView.getSelectionModel().getSelectedIndex();
        SelectedAccount a0 = mSelectedAccountListView.getItems().get(n);
        SelectedAccount a1 = mSelectedAccountListView.getItems().get(n+1);
        a0.setSelectedOrder(n+1);
        a1.setSelectedOrder(n);
    }

    @FXML
    private void handleClose() {
        close();
    }

    @FXML
    private void handleShowReport() {
        switch (mReportType) {
            case NAV:
                mReportTextArea.setText(NAVReport());
                break;
            default:
                mReportTextArea.setText("Report type " + mReportType + " not implemented yet");
                break;
        }
        mReportTextArea.setVisible(true);
    }

    @FXML
    private void handleSaveReport() {
        System.out.println("Save Report not implemented yet");
    }

    @FXML
    private void handleShowSetting() {
        mReportTextArea.setVisible(false);  // hide the report, show settings
    }

    @FXML
    private void handleSaveSetting() {
        System.out.println("save setting not implemented yet");
    }

    void close() { mDialogStage.close(); }

    private String NAVReport() {
        int accountNameLen = 8; // minimum 8 char long
        int amountLen = 16;

        // loop once to find out the max account name length
        for (SelectedAccount s : mSelectedAccountListView.getItems()) {
            int l = s.toString().length();
            if (l > accountNameLen)
                accountNameLen = l;
        }

        LocalDate date = mStartDatePicker.getValue();
        String outputStr = "NAV Report as of " + date + "\n";
        final DecimalFormat df = new DecimalFormat("#,##0.00");
        for (SelectedAccount s : mSelectedAccountListView.getItems()) {
            List<SecurityHolding> shList = mMainApp.updateAccountSecurityHoldingList(s.mAccount, date, -1);
            outputStr += String.format("%-" + accountNameLen + "s%" + amountLen + "s\n", s.toString(),
                    df.format(shList.get(shList.size()-1).getMarketValue()));
        }
        return outputStr;
    }

    @FXML
    private void initialize() { mFrequencyChoiceBox.getItems().setAll(Frequency.values()); }
}
