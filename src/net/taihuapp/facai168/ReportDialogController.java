package net.taihuapp.facai168;

import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Created by ghe on 10/13/16.
 * Base class for all ReportDialogController classes
 */
public class ReportDialogController {

    enum ReportType { NAV, INVESTTRANS, BANKTRANS }
    enum Frequency { DAILY, MONTHLY, QUARTERLY, ANNUAL }
    enum DatePeriod {
        TODAY, YESTERDAY, LASTEOM, LASTEOQ, LASTEOY, CUSTOMDATE,
        WEEKTODATE, MONTHTODATE, QUARTERTODATE, YEARTODATE, EPOCHTODATE,
        LASTWEEK, LASTMONTH, LASTQUARTER, LASTYEAR,
        LAST7DAYS, LAST30DAYS, LAST365DAYS, CUSTOMPERIOD;

        static EnumSet<DatePeriod> groupD = EnumSet.of(TODAY, YESTERDAY, LASTEOM, LASTEOQ, LASTEOY, CUSTOMDATE);
        static EnumSet<DatePeriod> groupP = EnumSet.of(WEEKTODATE, MONTHTODATE, QUARTERTODATE, YEARTODATE, EPOCHTODATE,
                LASTWEEK, LASTMONTH, LASTQUARTER, LASTYEAR, LAST7DAYS, LAST30DAYS, LAST365DAYS, CUSTOMPERIOD);
    };

    enum ItemName { ACCOUNTID, CATEGORYID, SECURITYID, TRADEACTION }

    private static final String NOSECURITY = "No Security";
    private static final String NOCATEGORY = "No Category";

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
        private DatePeriod mDatePeriod;
        private LocalDate mStartDate;
        private LocalDate mEndDate;
        private Frequency mFrequency;
        private List<SelectedAccount> mSelectedAccountList = new ArrayList<>();
        private Set<Integer> mSelectedCategoryIDSet = new HashSet<>();
        private Set<Integer> mSelectedSecurityIDSet = new HashSet<>();
        private Set<Transaction.TradeAction> mSelectedTradeActionSet = new HashSet<>();

        Setting(ReportType type) {
            this(-1, type);
        }

        Setting(int id, ReportType type) {
            mID = id;
            mType = type;
            switch (type) {
                case NAV:
                    mDatePeriod = DatePeriod.TODAY;
                    break;
                case INVESTTRANS:
                case BANKTRANS:
                    mDatePeriod = DatePeriod.LASTMONTH;
                    break;
            }

            mStartDate = LocalDate.now();
            mEndDate = LocalDate.now();
            mFrequency = Frequency.DAILY;
        }

        // getters
        int getID() { return mID; }
        String getName() { return mName; }
        ReportType getType() { return mType; }
        DatePeriod getDatePeriod() { return mDatePeriod; }
        LocalDate getStartDate() { return mStartDate; }
        LocalDate getEndDate() { return mEndDate; }
        Frequency getFrequency() { return mFrequency; }
        List<SelectedAccount> getSelectedAccountList() { return mSelectedAccountList; }
        Set<Integer> getSelectedCategoryIDSet() { return mSelectedCategoryIDSet; }
        Set<Integer> getSelectedSecurityIDSet() { return mSelectedSecurityIDSet; }
        Set<Transaction.TradeAction> getSelectedTradeActionSet() { return mSelectedTradeActionSet; }

        // setters
        void setID(int id) { mID = id; }
        void setName(String name) { mName = name; }
        void setDatePeriod(DatePeriod dp) { mDatePeriod = dp; }
        void setStartDate(LocalDate date) { mStartDate = date; }
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
    private TabPane mTabPane;

    @FXML
    private Tab mDatesTab;
    @FXML
    Label mDatePeriodLabel;
    @FXML
    ChoiceBox<DatePeriod> mDatePeriodChoiceBox;
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
    private Tab mAccountsTab;

    @FXML
    private Tab mCategoriesTab;
    @FXML
    private TableView<Pair<Pair<String, Integer>, BooleanProperty>> mCategorySelectionTableView;
    @FXML
    private TableColumn<Pair<Pair<String, Integer>, BooleanProperty>, String> mCategoryTableColumn;
    @FXML
    private TableColumn<Pair<Pair<String, Integer>, BooleanProperty>, Boolean> mCategorySelectedTableColumn;

    @FXML
    private Tab mSecuritiesTab;
    @FXML
    private TableView<Pair<Pair<String, Integer>, BooleanProperty>> mSecuritySelectionTableView;
    @FXML
    private TableColumn<Pair<Pair<String, Integer>, BooleanProperty>, String> mSecurityTableColumn;
    @FXML
    private TableColumn<Pair<Pair<String, Integer>, BooleanProperty>, Boolean> mSecuritySelectedTableColumn;

    @FXML
    private Tab mTradeActionTab;
    @FXML
    private TableView<Pair<Transaction.TradeAction, BooleanProperty>> mTradeActionSelectionTableView;
    @FXML
    private TableColumn<Pair<Transaction.TradeAction, BooleanProperty>, String> mTradeActionTableColumn;
    @FXML
    private TableColumn<Pair<Transaction.TradeAction, BooleanProperty>, Boolean> mTradeActionSelectedTableColumn;

    @FXML
    private Button mSelectButton;
    @FXML
    Button mUnselectButton;
    @FXML
    Button mUpButton;
    @FXML
    Button mDownButton;

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

        switch (mSetting.getType()) {
            case NAV:
                setupNAVReport();
                break;
            case INVESTTRANS:
                setupInvestTransactionReport();
                break;
            case BANKTRANS:
                break;
            default:
        }
    }

    private void setupNAVReport() {
        mCategoriesTab.setDisable(true);
        mSecuritiesTab.setDisable(true);
        mTradeActionTab.setDisable(true);

        // one one date
        setupDatesTab(false, false);
    }

    private void setupInvestTransactionReport() {
        setupDatesTab(true, false);
        setupCategoriesTab();
        setupSecuritiesTab();
        setupTradeActionTab();
    }

    private void setupDatesTab(boolean showPeriod, boolean showFreq) {
        if (showPeriod)
            mDatePeriodLabel.setText("Date Range:");
        mDatePeriodChoiceBox.getItems().setAll(
                showPeriod ? DatePeriod.groupP : DatePeriod.groupD);

        mStartDateLabel.setVisible(showPeriod);
        mStartDatePicker.setVisible(showPeriod);
        mEndDateLabel.setText(showPeriod ? "End Date" : "As Of");
        mDatePeriodChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            mSetting.setDatePeriod(nv);
            mStartDatePicker.setDisable(nv != DatePeriod.CUSTOMPERIOD);
            mStartDatePicker.setValue(mapDatePeriod(nv).getFirst());
            mEndDatePicker.setDisable(nv != DatePeriod.CUSTOMPERIOD && nv != DatePeriod.CUSTOMDATE);
            mEndDatePicker.setValue(mapDatePeriod(nv).getSecond());
        });

        mFrequencyChoiceBox.getItems().setAll(Frequency.values());
        mFrequencyChoiceBox.getSelectionModel().select(0);
        mFrequencyLabel.setVisible(showFreq);
        mFrequencyChoiceBox.setVisible(showFreq);

        mDatePeriodChoiceBox.getSelectionModel().select(mSetting.getDatePeriod());
        mFrequencyChoiceBox.getSelectionModel().select(mSetting.getFrequency());
    }

    private void setupCategoriesTab() {
        ObservableList<Pair<Pair<String, Integer>, BooleanProperty>> sibList = FXCollections.observableArrayList();
        sibList.add(new Pair<>(new Pair<>(NOCATEGORY, 0),
                new SimpleBooleanProperty(mSetting.getSelectedCategoryIDSet().contains(0))));
        for (Category c : mMainApp.getCategoryList()) {
            sibList.add(new Pair<>(new Pair<>(c.getNameProperty().get(), c.getID()),
                    new SimpleBooleanProperty(mSetting.getSelectedCategoryIDSet().contains(c.getID()))));
        }
        for (Account a : mMainApp.getAccountList(null, false, true)) {
            sibList.add(new Pair<>(new Pair<>(MainApp.getWrappedAccountName(a), -a.getID()),
                    new SimpleBooleanProperty(mSetting.getSelectedCategoryIDSet().contains(-a.getID()))));
        }
        mCategorySelectionTableView.setItems(sibList);
        mCategoryTableColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFirst().getFirst()));
        mCategorySelectedTableColumn.setCellValueFactory(cellData->cellData.getValue().getSecond());
        mCategorySelectedTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(mCategorySelectedTableColumn));
        mCategorySelectedTableColumn.setEditable(true);
    }

    private void setupSecuritiesTab() {
        ObservableList<Pair<Pair<String, Integer>, BooleanProperty>> sibList = FXCollections.observableArrayList();
        sibList.add(new Pair<>(new Pair<>(NOSECURITY, 0),
                new SimpleBooleanProperty(mSetting.getSelectedSecurityIDSet().contains(0))));
        for (Security s : mMainApp.getSecurityList()) {
            sibList.add(new Pair<>(new Pair<>(s.getNameProperty().get(), s.getID()),
                    new SimpleBooleanProperty(mSetting.getSelectedSecurityIDSet().contains(s.getID()))));
        }
        mSecuritySelectionTableView.setItems(sibList);
        mSecurityTableColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFirst().getFirst()));
        mSecuritySelectedTableColumn.setCellValueFactory(cellData->cellData.getValue().getSecond());
        mSecuritySelectedTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(mSecuritySelectedTableColumn));
        mSecuritySelectedTableColumn.setEditable(true);
    }

    private void setupTradeActionTab() {
        ObservableList<Pair<Transaction.TradeAction, BooleanProperty>> tabList = FXCollections.observableArrayList();
        for (Transaction.TradeAction ta : Transaction.TradeAction.values()) {
            tabList.add(new Pair<>(ta, new SimpleBooleanProperty(mSetting.getSelectedTradeActionSet().contains(ta))));
        }
        mTradeActionSelectionTableView.setItems(tabList);
        mTradeActionTableColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFirst().name()));
        mTradeActionSelectedTableColumn.setCellValueFactory(cellData->cellData.getValue().getSecond());
        mTradeActionSelectedTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(mTradeActionSelectedTableColumn));
        mTradeActionSelectedTableColumn.setEditable(true);
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
    private void handleSelectAll() {
        handleSetAll(true);
    }
    @FXML
    private void handleClearAll() {
        handleSetAll(false);
    }

    private void handleSetAll(boolean selected) {
        Tab currentTab = mTabPane.getSelectionModel().getSelectedItem();
        if (currentTab.equals(mCategoriesTab)) {
            for (Pair<Pair<String, Integer>, BooleanProperty> cib : mCategorySelectionTableView.getItems())
                cib.getSecond().set(selected);
        } else if (currentTab.equals(mSecuritiesTab)) {
            for (Pair<Pair<String, Integer>, BooleanProperty> sib : mSecuritySelectionTableView.getItems())
                sib.getSecond().set(selected);
        } else if (currentTab.equals(mTradeActionTab)) {
            for (Pair<Transaction.TradeAction, BooleanProperty> tab : mTradeActionSelectionTableView.getItems())
                tab.getSecond().set(selected);
        } else
            System.out.println("Other tab?");
    }

    @FXML
    private void handleClose() {
        close();
    }

    @FXML
    private void handleShowReport() {
        updateSetting();
        switch (mSetting.getType()) {
            case NAV:
                mReportTextArea.setText(NAVReport());
                break;
            case INVESTTRANS:
                mReportTextArea.setText(InvestTransReport());
                break;
            case BANKTRANS:
                mReportTextArea.setText(BankTransReport());
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

    private void updateSetting() {
        mSetting.setStartDate(mStartDatePicker.getValue());
        mSetting.setEndDate(mEndDatePicker.getValue());
        mSetting.setFrequency(mFrequencyChoiceBox.getValue());
        mSetting.setSelectedAccountList(mSelectedAccountListView.getItems());

        if (!mCategoriesTab.isDisable()) {
            // handle category selection
            mSetting.getSelectedCategoryIDSet().clear();
            for (Pair<Pair<String, Integer>, BooleanProperty> sib : mCategorySelectionTableView.getItems()) {
                if (sib.getSecond().get())
                    mSetting.getSelectedCategoryIDSet().add(sib.getFirst().getSecond());
            }
        }

        if (!mSecuritiesTab.isDisable()) {
            mSetting.getSelectedSecurityIDSet().clear();
            for (Pair<Pair<String, Integer>, BooleanProperty> sib : mSecuritySelectionTableView.getItems()) {
                if (sib.getSecond().get())
                    mSetting.getSelectedSecurityIDSet().add(sib.getFirst().getSecond());
            }
        }

        if (!mTradeActionTab.isDisable()) {
            mSetting.getSelectedTradeActionSet().clear();
            for (Pair<Transaction.TradeAction, BooleanProperty> tb : mTradeActionSelectionTableView.getItems()) {
                if (tb.getSecond().get())
                    mSetting.getSelectedTradeActionSet().add(tb.getFirst());
            }
        }
    }

    @FXML
    private void handleSaveSetting() {
        updateSetting();

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

/*    private List<Category> getSelectedCategoryList() {
        List<Category> cList = new ArrayList<>();
        for (Pair<Category, BooleanProperty> cb : mCategorySelectionTableView.getItems()) {
            if (cb.getSecond().get())
                cList.add(cb.getFirst());
        }
        return cList;
    }
    */

/*    private List<Security> getSelectedSecurityList() {
        List<Security> cList = new ArrayList<>();
        for (Pair<Security, BooleanProperty> sb : mSecuritySelectionTableView.getItems()) {
            if (sb.getSecond().get())
                cList.add(sb.getFirst());
        }
        return cList;
    }
    */

    private String InvestTransReport() {
        String reportStr = "Investment Transaction Report\n";
        reportStr += "total " + mSetting.getSelectedAccountList().size() + " accounts\n"
                + "total " + mSetting.getSelectedCategoryIDSet().size() + " categories\n"
                + "total " + mSetting.getSelectedSecurityIDSet().size() + " securities\n"
                + "total " + mSetting.getSelectedTradeActionSet().size() + " TradeActions\n"
        ;
/*
        for (Category c : getSelectedCategoryList()) {
            reportStr += c.getName() + "\n";
        }
*/
        return reportStr;
    }

    private String BankTransReport() {
        String reportStr = "Banking Transaction Report";
        return reportStr;
    }

    private String NAVReport() {
        final LocalDate date = mSetting.getEndDate();
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

    private Pair<LocalDate, LocalDate> mapDatePeriod(DatePeriod dp) {
        LocalDate startDate, endDate, today = LocalDate.now();
        int year, month;
        switch (dp) {
            case YESTERDAY:
                startDate = today.minusDays(1);
                endDate = today.minusDays(1);
                break;
            case LASTEOM:
                startDate = today.minusDays(today.getDayOfMonth());
                endDate = today.minusDays(today.getDayOfMonth());
                break;
            case LASTEOQ:
                year = today.getYear();
                month = today.getMonthValue();
                switch (month) {
                    case 1:
                    case 2:
                    case 3:
                        startDate = LocalDate.of(year-1, 12, 31);
                        endDate = LocalDate.of(year-1, 12, 31);
                        break;
                    case 4:
                    case 5:
                    case 6:
                        startDate =  LocalDate.of(year, 3, 31);
                        endDate = LocalDate.of(year, 3, 31);
                        break;
                    case 7:
                    case 8:
                    case 9:
                        startDate = LocalDate.of(year, 6, 30);
                        endDate = LocalDate.of(year, 6, 30);
                        break;
                    case 10:
                    case 11:
                    case 12:
                    default:
                        startDate = LocalDate.of(year, 9, 30);
                        endDate = LocalDate.of(year, 9, 30);
                        break;
                }
                break;
            case LASTEOY:
                startDate = LocalDate.of(today.getYear()-1, 12, 31);
                endDate = LocalDate.of(today.getYear()-1, 12, 31);
                break;
            case WEEKTODATE:
                // todo: this supposely handles locale correctly, need to test
                startDate = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1);
                endDate = today;
                break;
            case MONTHTODATE:
                startDate = mapDatePeriod(DatePeriod.LASTEOM).getFirst().plusDays(1);
                endDate = today;
                break;
            case QUARTERTODATE:
                startDate = mapDatePeriod(DatePeriod.LASTEOQ).getFirst().plusDays(1);
                endDate = today;
                break;
            case YEARTODATE:
                startDate = mapDatePeriod(DatePeriod.LASTEOY).getFirst().plusDays(1);
                endDate = today;
                break;
            case EPOCHTODATE:
                startDate = LocalDate.MIN;
                endDate = today;
                break;
            case LASTWEEK:
                startDate = mapDatePeriod(DatePeriod.WEEKTODATE).getFirst().minusDays(7);
                endDate = startDate.plusDays(6);
                break;
            case LASTMONTH:
                endDate = mapDatePeriod(DatePeriod.LASTEOM).getFirst();
                startDate = endDate.minusDays(endDate.getDayOfMonth()-1);
                break;
            case LASTQUARTER:
                year = today.getYear();
                month = today.getMonthValue();
                switch (month) {
                    case 1:
                    case 2:
                    case 3:
                        startDate = LocalDate.of(year-1, 10, 1);
                        endDate = LocalDate.of(year-1, 12, 31);
                        break;
                    case 4:
                    case 5:
                    case 6:
                        startDate =  LocalDate.of(year, 1, 1);
                        endDate = LocalDate.of(year, 3, 31);
                        break;
                    case 7:
                    case 8:
                    case 9:
                        startDate = LocalDate.of(year, 4, 1);
                        endDate = LocalDate.of(year, 6, 30);
                        break;
                    case 10:
                    case 11:
                    case 12:
                    default:
                        startDate = LocalDate.of(year, 7, 1);
                        endDate = LocalDate.of(year, 9, 30);
                        break;
                }
                break;
            case LASTYEAR:
                year = today.getYear();
                startDate = LocalDate.of(year-1, 1, 1);
                endDate = LocalDate.of(year-1,12,31);
                break;
            case LAST7DAYS:
                startDate = today.minusDays(6);
                endDate = today;
                break;
            case LAST30DAYS:
                startDate = today.minusDays(29);
                endDate = today;
                break;
            case LAST365DAYS:
                startDate = today.minusDays(364);
                endDate = today;
                break;
            case TODAY:
            case CUSTOMDATE:
            case CUSTOMPERIOD:
            default:
                startDate = today;
                endDate = today;
                break;
        }

        return new Pair<>(startDate, endDate);
    }

/*
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
*/

    @FXML
    private void initialize() {
/*
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
*/

        mSaveReportButton.setDisable(true);
        mShowSettingButton.setDisable(true);
    }
}
