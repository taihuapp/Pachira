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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Created by ghe on 10/13/16.
 * Base class for all ReportDialogController classes
 */
public class ReportDialogController {

    enum ReportType { NAV }
    enum Frequency { DAILY, MONTHLY, QUARTERLY, ANNUAL }
    enum SpecialDay { TODAY, YESTERDAY, LASTEOM, LASTEOQ, LASTEOY, CUSTOMIZED }

    static class SelectedAccount {
        Account mAccount;
        IntegerProperty mSelectedOrderProperty = new SimpleIntegerProperty(-1); // -1 for not selected

        SelectedAccount(Account a, int displayOrder) {
            mAccount = a;
            mSelectedOrderProperty.set(displayOrder);
        }

        IntegerProperty getSelectedOrderProperty() { return mSelectedOrderProperty;}
        Integer getSelectedOrder() { return getSelectedOrderProperty().get(); }
        void setSelectedOrder(int s) { mSelectedOrderProperty.set(s); }

        Account.Type getType() { return mAccount.getType(); }
        int getDisplayOrder() { return mAccount.getDisplayOrder(); }
        int getID() { return mAccount.getID(); }

        public String toString() { return mAccount.getName(); }
    }

    static class Setting {
        private int mID = -1;
        private String mName = "";
        private final ReportType mType;
        private SpecialDay mStart;
        private LocalDate mStartDate;
        private SpecialDay mEnd;
        private LocalDate mEndDate;
        private Frequency mFrequency;
        private final List<SelectedAccount> mSelectedAccountList = new ArrayList<>();

        // default Constructor
        Setting(ReportType type) { this(-1, type); }
        Setting(int id, ReportType type) {
            mID = id;
            mType = type;
            mStart = SpecialDay.TODAY;
            mStartDate = LocalDate.now();
            mEnd = SpecialDay.TODAY;
            mEndDate = LocalDate.now();
            mFrequency = Frequency.DAILY;
        }

        // getters
        int getID() { return mID; }
        String getName() { return mName; }
        ReportType getType() { return mType; }
        SpecialDay getStart() { return mStart; }
        LocalDate getStartDate() { return mStartDate; }
        SpecialDay getEnd() { return mEnd; }
        LocalDate getEndDate() { return mEndDate; }
        Frequency getFrequency() { return mFrequency; }
        List<SelectedAccount> getSelectedAccountList() { return mSelectedAccountList; }

        // setters
        void setID(int id) { mID = id; }
        void setName(String name) { mName = name; }
        void setStart(SpecialDay start) { mStart = start; }
        void setStartDate(LocalDate date) { mStartDate = date; }
        void setEnd(SpecialDay end) { mEnd = end; }
        void setEndDate(LocalDate date) { mEndDate = date; }
        void setFrequency(Frequency f) { mFrequency = f; }
        void setSelectedAccountList(List<SelectedAccount> selectedAccountList) {
            mSelectedAccountList.clear();
            mSelectedAccountList.addAll(selectedAccountList);
        }
    }

    private Setting mSetting;
    private ObservableList<SelectedAccount> mAccountList = FXCollections.observableArrayList(
            a -> new Observable[] { a.getSelectedOrderProperty() });

    @FXML
    private Button mSelectButton;
    @FXML
    Button mUnselectButton;
    @FXML
    Button mUpButton;
    @FXML
    Button mDownButton;
    @FXML
    Label mStartDateLabel;
    @FXML
    ChoiceBox<SpecialDay> mStartChoiceBox;
    @FXML
    DatePicker mStartDatePicker;
    @FXML
    Label mEndDateLabel;
    @FXML
    ChoiceBox<SpecialDay> mEndChoiceBox;
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

    @FXML
    Button mShowReportButton;
    @FXML
    Button mSaveReportButton;
    @FXML
    Button mShowSettingButton;
    @FXML
    Button mSaveSettingButton;

    private MainApp mMainApp;
    private Stage mDialogStage;

    void setMainApp(Setting setting, MainApp mainApp, Stage stage) {

        // set members
        mSetting = setting;
        mMainApp = mainApp;
        mDialogStage = stage;

        // set window title
        mDialogStage.setTitle(mSetting.getType() + " Report");

        // initialize account selection controls and time period selection controls
        mAccountList.clear();
        for (Account a : mMainApp.getAccountList(null, null, true)) {
            int selectedOrder = -1;
            for (SelectedAccount sa : setting.getSelectedAccountList())
                if (sa.getID() == a.getID())
                    selectedOrder = sa.getSelectedOrder();
            mAccountList.add(new SelectedAccount(a, selectedOrder));
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

        mStartChoiceBox.getSelectionModel().select(setting.getStart());
        mStartDatePicker.setValue(setting.getStartDate());
        mEndChoiceBox.getSelectionModel().select(setting.getEnd());
        mEndDatePicker.setValue(setting.getEndDate());
        mFrequencyChoiceBox.getSelectionModel().select(setting.getFrequency());

        switch (mSetting.getType()) {
            case NAV:
                mStartDateLabel.setText("As of:");
                mStartChoiceBox.setVisible(true);
                mStartDatePicker.setVisible(true);
                mStartDatePicker.setValue(LocalDate.now());

                mEndDateLabel.setVisible(false);
                mEndChoiceBox.setVisible(false);
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
        switch (mSetting.getType()) {
            case NAV:
                mReportTextArea.setText(NAVReport());
                break;
            default:
                mReportTextArea.setText("Report type " + mSetting.getType() + " not implemented yet");
                break;
        }
        mReportTextArea.setVisible(true);
        mShowReportButton.setDisable(true);
        mSaveReportButton.setDisable(false);
        mShowSettingButton.setDisable(false);
    }

    @FXML
    private void handleSaveReport() {
        final FileChooser fileChooser = new FileChooser();
        final FileChooser.ExtensionFilter txtFilter = new FileChooser.ExtensionFilter("Text file",
                "*.TXT", "*.TXt", "*.TxT", "*.Txt", "*.tXT", "*.tXt", "*.txT", "*.txt");
        fileChooser.getExtensionFilters().add(txtFilter);
        fileChooser.setInitialFileName(mSetting.getName()+".txt");
        File reportFile = fileChooser.showSaveDialog(mDialogStage);

        if (reportFile != null) {
            try (PrintWriter pw = new PrintWriter(reportFile.getCanonicalPath())) {
                pw.print(mReportTextArea.getText());
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    @FXML
    private void handleShowSetting() {
        mReportTextArea.setVisible(false);  // hide the report, show settings
        mShowReportButton.setDisable(false);
        mSaveReportButton.setDisable(true);
        mShowSettingButton.setDisable(true);
    }

    @FXML
    private void handleSaveSetting() {
        mSetting.setStart(mStartChoiceBox.getValue());
        mSetting.setStartDate(mStartDatePicker.getValue());
        mSetting.setEnd(mEndChoiceBox.getValue());
        mSetting.setEndDate(mEndDatePicker.getValue());
        mSetting.setFrequency(mFrequencyChoiceBox.getValue());
        mSetting.setSelectedAccountList(mSelectedAccountListView.getItems());

        TextInputDialog tiDialog = new TextInputDialog(mSetting.getName());
        tiDialog.setTitle("Save Report Setting:");
        if (mSetting.getID() <= 0) {
            tiDialog.setHeaderText("New Report Setting, please input the name to save under:");
        } else {
            tiDialog.setHeaderText("Overwrite existing report setting, or input a new name to save under:");
        }

        Optional<String> result = tiDialog.showAndWait();
        if (result.isPresent()) {
            if (result.get().length() == 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning Dialog");
                alert.setHeaderText("Bad Report Setting Name");
                alert.setContentText("Name cannot be empty");
                alert.showAndWait();
                return;
            }

            int oldID = mSetting.getID();
            if (!result.get().equals(mSetting.getName())) {
                // name has changed, need to save as new
                mSetting.setID(-1);
            }
            mSetting.setName(result.get());
            if (mMainApp.insertUpdateReportSettingToDB(mSetting) <= 0) {
                mSetting.setID(oldID);  // put back oldID
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error Dialog");
                alert.setHeaderText("Failed to save report setting!");
                alert.setContentText("Make sure the name is not being used");
                alert.showAndWait();
            }
        }
    }

    void close() { mDialogStage.close(); }

    private String NAVReport() {
        final LocalDate date = mStartDatePicker.getValue();
        String outputStr = "NAV Report as of " + date + "\n\n";

        BigDecimal total = BigDecimal.ZERO;
        String separator0 = new String(new char[90]).replace("\0", "-");
        String separator1 = new String(new char[90]).replace("\0", "=");
        final DecimalFormat dcFormat = new DecimalFormat("#,##0.00"); // formatter for dollar & cents
        final DecimalFormat qpFormat = new DecimalFormat("#,##0.000"); // formatter for quantity and price

        for (SelectedAccount s : mSelectedAccountListView.getItems()) {
            List<SecurityHolding> shList = mMainApp.updateAccountSecurityHoldingList(s.mAccount, date, -1);
            int shListLen = shList.size();

            // aggregate total
            total = total.add(shList.get(shListLen-1).getMarketValue());

            // print account total
            outputStr += String.format("%-55s%35s\n", s.toString(),
                    dcFormat.format(shList.get(shListLen-1).getMarketValue()));

            outputStr += separator0 + "\n";

            // print out positions
            BigDecimal q, p;
            for (int i = 0; i < shListLen-1; i++) {
                SecurityHolding sh = shList.get(i);
                q = sh.getQuantity();
                p = sh.getPrice();
                outputStr += String.format("  %-50s%12s%10s%14s\n", sh.getLabel(), q == null ? "" : qpFormat.format(q),
                        p == null ? "" : qpFormat.format(p), dcFormat.format(sh.getMarketValue()));
            }
            outputStr += separator1 + "\n";
            outputStr += "\n";
        }

        // print out total
        outputStr += String.format("%-55s%35s\n", "Total", dcFormat.format(total));

        return outputStr;
    }

    private LocalDate mapSpecialDate(SpecialDay sd) {
        LocalDate today = LocalDate.now();
        switch (sd) {
            case TODAY:
                return today;
            case YESTERDAY:
                return today.minusDays(1);
            case LASTEOM:
                return today.minusDays(today.getDayOfMonth());
            case LASTEOQ:
                int year = today.getYear();
                int month = today.getMonthValue();
                switch (month) {
                    case 1:
                    case 2:
                    case 3:
                        return LocalDate.of(year-1, 12, 31);
                    case 4:
                    case 5:
                    case 6:
                        return LocalDate.of(year, 3, 31);
                    case 7:
                    case 8:
                    case 9:
                        return LocalDate.of(year, 6, 30);
                    case 10:
                    case 11:
                    case 12:
                        return LocalDate.of(year, 9, 30);
                }
            case LASTEOY:
                return LocalDate.of(today.getYear()-1, 12, 31);
            case CUSTOMIZED:
                return today;
            default:
                System.err.println("Unimplemented case in mapSpecialDate " + sd);
                return today;
        }
    }

    @FXML
    private void initialize() {
        mFrequencyChoiceBox.getItems().setAll(Frequency.values());
        mFrequencyChoiceBox.getSelectionModel().select(0);
        mStartChoiceBox.getItems().setAll(SpecialDay.values());
        mEndChoiceBox.getItems().setAll(SpecialDay.values());

        mStartChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            mStartDatePicker.setDisable(nv != SpecialDay.CUSTOMIZED);
            mStartDatePicker.setValue(mapSpecialDate(nv));
        });
        mEndChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            mEndDatePicker.setDisable(nv != SpecialDay.CUSTOMIZED);
            mEndDatePicker.setValue(mapSpecialDate(nv));
        });

        mStartChoiceBox.getSelectionModel().select(0);
        mEndChoiceBox.getSelectionModel().select(0);

        mSaveReportButton.setDisable(true);
        mShowSettingButton.setDisable(true);
    }
}
