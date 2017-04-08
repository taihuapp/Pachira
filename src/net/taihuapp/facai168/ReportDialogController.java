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
import javafx.util.Pair;

import java.io.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Created by ghe on 10/13/16.
 * Base class for all ReportDialogController classes
 */
public class ReportDialogController {

    enum ReportType { NAV, INVESTINCOME, INVESTTRANS, BANKTRANS }
    enum Frequency { DAILY, MONTHLY, QUARTERLY, ANNUAL }
    enum DatePeriod {
        TODAY, YESTERDAY, LASTEOM, LASTEOQ, LASTEOY, CUSTOMDATE,
        WEEKTODATE, MONTHTODATE, QUARTERTODATE, YEARTODATE, EPOCHTODATE,
        LASTWEEK, LASTMONTH, LASTQUARTER, LASTYEAR,
        LAST7DAYS, LAST30DAYS, LAST365DAYS, CUSTOMPERIOD;

        static EnumSet<DatePeriod> groupD = EnumSet.of(TODAY, YESTERDAY, LASTEOM, LASTEOQ, LASTEOY, CUSTOMDATE);
        static EnumSet<DatePeriod> groupP = EnumSet.of(WEEKTODATE, MONTHTODATE, QUARTERTODATE, YEARTODATE, EPOCHTODATE,
                LASTWEEK, LASTMONTH, LASTQUARTER, LASTYEAR, LAST7DAYS, LAST30DAYS, LAST365DAYS, CUSTOMPERIOD);
    }

    enum ItemName { ACCOUNTID, CATEGORYID, SECURITYID, TRADEACTION }

    private static final String NOSECURITY = "(No Security)";
    private static final String NOCATEGORY = "(No Category)";

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

        Account getAccount() { return mAccount; }
        Account.Type getType() { return getAccount().getType(); }
        int getDisplayOrder() { return mAccount.getDisplayOrder(); }
        int getID() { return getAccount().getID(); }

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
                case INVESTINCOME:
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

        switch (mSetting.getType()) {
            case NAV:
                setupNAVReport();
                break;
            case INVESTINCOME:
                setupInvestIncomeReport();
                break;
            case INVESTTRANS:
                setupInvestTransactionReport();
                break;
            case BANKTRANS:
                setupBankTransactionReport();
                break;
            default:
                break;
        }
    }

    private void setupNAVReport() {
        setupDatesTab(false, false);  // just one date
        setupAccountsTab(null); // show all accounts
        mCategoriesTab.setDisable(true);
        mSecuritiesTab.setDisable(true);
        mTradeActionTab.setDisable(true);
    }

    private void setupInvestIncomeReport() {
        setupDatesTab(true, false);
        setupAccountsTab(Account.Type.INVESTING); // show investing accounts only
        mCategoriesTab.setDisable(true);
        setupSecuritiesTab();
        setupTradeActionTab();
    }

    private void setupInvestTransactionReport() {
        setupDatesTab(true, false);
        setupAccountsTab(Account.Type.INVESTING); // show investing accounts only
        mCategoriesTab.setDisable(true);
        setupSecuritiesTab();
        setupTradeActionTab();
    }

    private void setupBankTransactionReport() {
        setupDatesTab(true, false);
        setupAccountsTab(null); // show all accounts
        setupCategoriesTab();
        setupSecuritiesTab();
        mTradeActionTab.setDisable(true); // no need for TradeAction
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
            mStartDatePicker.setValue(mapDatePeriod(nv).getKey());
            mEndDatePicker.setDisable(nv != DatePeriod.CUSTOMPERIOD && nv != DatePeriod.CUSTOMDATE);
            mEndDatePicker.setValue(mapDatePeriod(nv).getValue());
        });

        mFrequencyChoiceBox.getItems().setAll(Frequency.values());
        mFrequencyChoiceBox.getSelectionModel().select(0);
        mFrequencyLabel.setVisible(showFreq);
        mFrequencyChoiceBox.setVisible(showFreq);

        mDatePeriodChoiceBox.getSelectionModel().select(mSetting.getDatePeriod());
        mFrequencyChoiceBox.getSelectionModel().select(mSetting.getFrequency());
    }

    private void setupAccountsTab(Account.Type t) {

        // nothing is selected in either listview, disable these buttons
        mSelectButton.setDisable(true);
        mUnselectButton.setDisable(true);
        mUpButton.setDisable(true);
        mDownButton.setDisable(true);

        mAccountList.clear();

        for (Account a : mMainApp.getAccountList(t,null, true)) {
            int selectedOrder = -1;
            for (SelectedAccount sa : mSetting.getSelectedAccountList())
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
    }

    private void setupCategoriesTab() {
        ObservableList<Pair<Pair<String, Integer>, BooleanProperty>> sibList = FXCollections.observableArrayList();
        boolean newSetting = mSetting.getID() < 0;  // we pre-select all categories for new setting
        sibList.add(new Pair<>(new Pair<>(NOCATEGORY, 0),
                new SimpleBooleanProperty(newSetting
                        || mSetting.getSelectedCategoryIDSet().contains(0))));
        for (Category c : mMainApp.getCategoryList()) {
            sibList.add(new Pair<>(new Pair<>(c.getNameProperty().get(), c.getID()),
                    new SimpleBooleanProperty(newSetting
                            || mSetting.getSelectedCategoryIDSet().contains(c.getID()))));
        }
        for (Account a : mMainApp.getAccountList(null, false, true)) {
            sibList.add(new Pair<>(new Pair<>(MainApp.getWrappedAccountName(a), -a.getID()),
                    new SimpleBooleanProperty(newSetting
                            || mSetting.getSelectedCategoryIDSet().contains(-a.getID()))));
        }
        mCategorySelectionTableView.setItems(sibList);
        mCategoryTableColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getKey().getKey()));
        mCategorySelectedTableColumn.setCellValueFactory(cellData->cellData.getValue().getValue());
        mCategorySelectedTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(mCategorySelectedTableColumn));
        mCategorySelectedTableColumn.setEditable(true);
    }

    private void setupSecuritiesTab() {
        ObservableList<Pair<Pair<String, Integer>, BooleanProperty>> sibList = FXCollections.observableArrayList();
        boolean newSetting = mSetting.getID() < 0;
        sibList.add(new Pair<>(new Pair<>(NOSECURITY, 0),
                new SimpleBooleanProperty(newSetting || mSetting.getSelectedSecurityIDSet().contains(0))));
        for (Security s : mMainApp.getSecurityList()) {
            sibList.add(new Pair<>(new Pair<>(s.getNameProperty().get(), s.getID()),
                    new SimpleBooleanProperty(newSetting
                            || mSetting.getSelectedSecurityIDSet().contains(s.getID()))));
        }
        mSecuritySelectionTableView.setItems(sibList);
        mSecurityTableColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getKey().getKey()));
        mSecuritySelectedTableColumn.setCellValueFactory(cellData->cellData.getValue().getValue());
        mSecuritySelectedTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(mSecuritySelectedTableColumn));
        mSecuritySelectedTableColumn.setEditable(true);
    }

    private void setupTradeActionTab() {
        ObservableList<Pair<Transaction.TradeAction, BooleanProperty>> tabList = FXCollections.observableArrayList();
        boolean newSetting = mSetting.getID() < 0;
        for (Transaction.TradeAction ta : Transaction.TradeAction.values()) {
            tabList.add(new Pair<>(ta, new SimpleBooleanProperty(newSetting
                    || mSetting.getSelectedTradeActionSet().contains(ta))));
        }
        mTradeActionSelectionTableView.setItems(tabList);
        mTradeActionTableColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getKey().name()));
        mTradeActionSelectedTableColumn.setCellValueFactory(cellData->cellData.getValue().getValue());
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
                cib.getValue().set(selected);
        } else if (currentTab.equals(mSecuritiesTab)) {
            for (Pair<Pair<String, Integer>, BooleanProperty> sib : mSecuritySelectionTableView.getItems())
                sib.getValue().set(selected);
        } else if (currentTab.equals(mTradeActionTab)) {
            for (Pair<Transaction.TradeAction, BooleanProperty> tab : mTradeActionSelectionTableView.getItems())
                tab.getValue().set(selected);
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
            case INVESTINCOME:
                mReportTextArea.setText(InvestIncomeReport());
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
                if (sib.getValue().get())
                    mSetting.getSelectedCategoryIDSet().add(sib.getKey().getValue());
            }
        }

        if (!mSecuritiesTab.isDisable()) {
            mSetting.getSelectedSecurityIDSet().clear();
            for (Pair<Pair<String, Integer>, BooleanProperty> sib : mSecuritySelectionTableView.getItems()) {
                if (sib.getValue().get())
                    mSetting.getSelectedSecurityIDSet().add(sib.getKey().getValue());
            }
        }

        if (!mTradeActionTab.isDisable()) {
            mSetting.getSelectedTradeActionSet().clear();
            for (Pair<Transaction.TradeAction, BooleanProperty> tb : mTradeActionSelectionTableView.getItems()) {
                if (tb.getValue().get())
                    mSetting.getSelectedTradeActionSet().add(tb.getKey());
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
            String settingName = result.get();
            if (settingName.length() == 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning Dialog");
                alert.setHeaderText("Bad Report Setting Name");
                alert.setContentText("Name cannot be empty");
                alert.showAndWait();
            } else if (settingName.length() > MainApp.SAVEDREPORTSNAMELEN) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning Dialog");
                alert.setHeaderText("Bad Report Setting Name");
                alert.setContentText("Name cannot exceed 32 characters");
                alert.showAndWait();
            } else {
                int oldID = mSetting.getID();
                if (!result.get().equals(mSetting.getName())) {
                    // name has changed, need to save as new
                    mSetting.setID(-1);
                }
                mSetting.setName(result.get());
                try {
                    mMainApp.insertUpdateReportSettingToDB(mSetting);
                } catch (SQLException e) {
                    mSetting.setID(oldID);  // put back oldID
                    mMainApp.showExceptionDialog("Error Dialog", "Failed to save report setting!",
                            "Make sure the name is not already used.", e);
                }
            }
        }
    }

    void close() { mDialogStage.close(); }

    private String InvestIncomeReport() {
        StringBuilder reportStr = new StringBuilder("Investment Income Report from "
                + mSetting.getStartDate() + " to " + mSetting.getEndDate() + "\n");
        if (mSetting.getSelectedTradeActionSet().isEmpty()) {
            reportStr.append("No TradeAction selected.");
            return reportStr.toString();
        }

        class Income {
            private BigDecimal divident = BigDecimal.ZERO;
            private BigDecimal interest = BigDecimal.ZERO;
            private BigDecimal ltcgdist = BigDecimal.ZERO;
            private BigDecimal mtcgdist = BigDecimal.ZERO;
            private BigDecimal stcgdist = BigDecimal.ZERO;
            private BigDecimal realized = BigDecimal.ZERO;
            private BigDecimal miscinc = BigDecimal.ZERO;

            BigDecimal total() {
                return divident.add(interest).add(ltcgdist).add(mtcgdist).add(stcgdist).add(realized).add(miscinc);
            }
            Income add(Income addend) {
                Income i = new Income();
                i.divident = this.divident.add(addend.divident);
                i.interest = this.interest.add(addend.interest);
                i.ltcgdist = this.ltcgdist.add(addend.ltcgdist);
                i.mtcgdist = this.mtcgdist.add(addend.mtcgdist);
                i.stcgdist = this.stcgdist.add(addend.stcgdist);
                i.realized = this.realized.add(addend.realized);
                i.miscinc = this.miscinc.add(addend.miscinc);
                return i;
            }
        }

        final DecimalFormat qpFormat = new DecimalFormat("#,##0.000"); // formatter for quantity and price
        Income fieldUsed = new Income(); // use this to keep track the field being used
        List<Map<String, Income>> accountSecurityIncomeList = new ArrayList<>();
        for (SelectedAccount sa : mSetting.getSelectedAccountList()) {
            Map<String, Income> securityIncomeMap = new TreeMap<>();
            accountSecurityIncomeList.add(securityIncomeMap);
            Account account = sa.getAccount();
            for (Transaction t : account.getTransactionList()) {
                LocalDate tDate = t.getTDate();
                String sName = t.getSecurityName();
                if (sName == null || sName.equals(""))
                    sName = NOSECURITY;
                if (tDate.isBefore(mSetting.getStartDate()))
                    continue;
                if (tDate.isAfter(mSetting.getEndDate()))
                    break; // we are done with this account

                Income income = securityIncomeMap.get(sName);
                if (income == null)
                    income = new Income();
                switch (t.getTradeAction()) {
                    case BUY:
                        break;
                    case SELL:
                    case CVTSHRT:
                        fieldUsed.realized = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        BigDecimal realized = mMainApp.calcRealizedGain(t);
                        if (realized == null) {
                            reportStr.append("**********************\n" + "* Lot Matching Error *\n" + "* Account:  ").append(account.getName()).append("\n").append("* Date:     ").append(t.getTDate()).append("\n").append("* Security: ").append(t.getSecurityName()).append("\n").append("* Action:   ").append(t.getTradeAction().name()).append("\n").append("* Quantity: ").append(qpFormat.format(t.getQuantity())).append("\n");
                            return reportStr.toString();
                        }
                        income.realized = income.realized.add(realized);
                        break;
                    case DIV:
                    case REINVDIV:
                        fieldUsed.divident = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        income.divident = income.divident.add(t.getAmount());
                        break;
                    case INTINC:
                    case REINVINT:
                        fieldUsed.interest = BigDecimal.ONE;
                        income.interest = income.interest.add(t.getAmount());
                        break;
                    case CGLONG:
                    case REINVLG:
                        fieldUsed.ltcgdist = BigDecimal.ONE;
                        income.ltcgdist = income.ltcgdist.add(t.getAmount());
                        break;
                    case CGMID:
                    case REINVMD:
                        fieldUsed.mtcgdist = BigDecimal.ONE;
                        income.mtcgdist = income.mtcgdist.add(t.getAmount());
                        break;
                    case CGSHORT:
                    case REINVSH:
                        fieldUsed.stcgdist = BigDecimal.ONE;
                        income.stcgdist = income.stcgdist.add(t.getAmount());
                        break;
                    case STKSPLIT:
                        break;
                    case SHRSIN:
                        break;
                    case SHRSOUT:
                        break;
                    case MISCEXP:
                        fieldUsed.miscinc = BigDecimal.ONE;
                        income.miscinc = income.miscinc.subtract(t.getAmount());
                        break;
                    case MISCINC:
                        fieldUsed.miscinc = BigDecimal.ONE;
                        income.miscinc = income.miscinc.add(t.getAmount());
                        break;
                    case RTRNCAP:
                        break;
                    case SHTSELL:
                        break;
                    case MARGINT:
                    case XFRSHRS:
                    case XIN:
                    case XOUT:
                    case DEPOSIT:
                    case WITHDRAW:
                    default:
                        break;
                }
            }
        }

        class Line {
            private String sName = "";
            private String divident = "";
            private String interest = "";
            private String ltcgdist = "";
            private String mtcgdist = "";
            private String stcgdist = "";
            private String realized = "";
            private String miscinc = "";
            private String total = "";

            // default constructor
            private Line() {}

            private Line(String sn, Income income, DecimalFormat df) {
                sName = sn;
                divident = df.format(income.divident);
                interest = df.format(income.interest);
                ltcgdist = df.format(income.ltcgdist);
                mtcgdist = df.format(income.mtcgdist);
                stcgdist = df.format(income.stcgdist);
                realized = df.format(income.realized);
                miscinc = df.format(income.miscinc);
                total = df.format(income.total());
            }
        }

        int gap = 2;
        int sNameLen = 10;
        int dividentLen = 4;
        int interestLen = 4;
        int ltcgdistLen = 4;
        int mtcgdistLen = 4;
        int stcgdistLen = 4;
        int realizedLen = 4;
        int miscincLen = 4;
        int totalLen = 4;

        final List<Line> lineList = new ArrayList<>();
        final Line title = new Line();
        title.divident = "Dividend";
        title.interest = "Interest";
        title.ltcgdist = "LT CG Dist.";
        title.mtcgdist = "MT CG Dist.";
        title.stcgdist = "ST CG Dist.";
        title.realized = "Realized CG";
        title.miscinc = "Misc Inc";
        title.total = "Total";

        final Line separator0 = new Line();
        final Line separator1 = new Line();
        final Line emptyLine = new Line();

        lineList.add(title);

        final DecimalFormat dcFormat = new DecimalFormat("#,##0.00"); // formatter for dollar & cents
        Income totalTotal = new Income();
        int accountIdx = 0;
        for (SelectedAccount sa : mSetting.getSelectedAccountList()) {
            final Map<String, Income> securityIncomeMap = accountSecurityIncomeList.get(accountIdx++);

            final Line accountLine = new Line();
            accountLine.sName = sa.getAccount().getName();
            lineList.add(separator0);
            lineList.add(accountLine);
            lineList.add(separator1);
            Income accountTotal = new Income();
            for (String sName : securityIncomeMap.keySet()) {
                Income income = securityIncomeMap.get(sName);
                final Line line = new Line(sName, income, dcFormat);
                accountTotal = accountTotal.add(income);
                lineList.add(line);
            }

            lineList.add(separator1);
            final Line accountTotalLine = new Line(sa.getAccount().getName()+ " Total",
                    accountTotal, dcFormat);

            totalTotal = totalTotal.add(accountTotal);

            lineList.add(accountTotalLine);
            lineList.add(emptyLine);
        }

        if (mSetting.getSelectedAccountList().size() > 1) {
            lineList.add(separator0);
            lineList.add(new Line("Total", totalTotal, dcFormat));
        }

        for (Line line : lineList) {
            sNameLen = Math.max(sNameLen, line.sName.length());
            dividentLen = Math.max(dividentLen, line.divident.length());
            interestLen = Math.max(interestLen, line.interest.length());
            ltcgdistLen = Math.max(ltcgdistLen, line.ltcgdist.length());
            mtcgdistLen = Math.max(mtcgdistLen, line.mtcgdist.length());
            stcgdistLen = Math.max(stcgdistLen, line.stcgdist.length());
            realizedLen = Math.max(realizedLen, line.realized.length());
            miscincLen = Math.max(miscincLen, line.miscinc.length());
            totalLen = Math.max(totalLen, line.total.length());
        }

        separator0.sName = new String(new char[sNameLen]).replace("\0", "=");
        separator0.divident = new String(new char[dividentLen+gap]).replace("\0", "=");
        separator0.interest = new String(new char[interestLen+gap]).replace("\0", "=");
        separator0.ltcgdist = new String(new char[ltcgdistLen+gap]).replace("\0", "=");
        separator0.mtcgdist = new String(new char[mtcgdistLen+gap]).replace("\0", "=");
        separator0.stcgdist = new String(new char[stcgdistLen+gap]).replace("\0", "=");
        separator0.realized = new String(new char[realizedLen+gap]).replace("\0", "=");
        separator0.miscinc = new String(new char[miscincLen+gap]).replace("\0", "=");
        separator0.total = new String(new char[totalLen+gap]).replace("\0", "=");

        separator1.sName = new String(new char[sNameLen]).replace("\0", "-");
        separator1.divident = new String(new char[dividentLen+gap]).replace("\0", "-");
        separator1.interest = new String(new char[interestLen+gap]).replace("\0", "-");
        separator1.ltcgdist = new String(new char[ltcgdistLen+gap]).replace("\0", "-");
        separator1.mtcgdist = new String(new char[mtcgdistLen+gap]).replace("\0", "-");
        separator1.stcgdist = new String(new char[stcgdistLen+gap]).replace("\0", "-");
        separator1.realized = new String(new char[realizedLen+gap]).replace("\0", "-");
        separator1.miscinc = new String(new char[miscincLen+gap]).replace("\0", "-");
        separator1.total = new String(new char[totalLen+gap]).replace("\0", "-");

        for (Line l : lineList) {
            reportStr.append(String.format("%-" + sNameLen + "s", l.sName));
            if (fieldUsed.divident.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (dividentLen + gap) + "s", l.divident));
            if (fieldUsed.interest.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (interestLen + gap) + "s", l.interest));
            if (fieldUsed.ltcgdist.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (ltcgdistLen + gap) + "s", l.ltcgdist));
            if (fieldUsed.mtcgdist.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (mtcgdistLen + gap) + "s", l.mtcgdist));
            if (fieldUsed.stcgdist.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (stcgdistLen + gap) + "s", l.stcgdist));
            if (fieldUsed.realized.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (realizedLen + gap) + "s", l.realized));
            if (fieldUsed.miscinc.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (miscincLen + gap) + "s", l.miscinc));

            reportStr.append(String.format("%" + (totalLen + gap) + "s\n", l.total));
        }
        return reportStr.toString();
    }

    private String InvestTransReport() {
        StringBuilder reportStr = new StringBuilder("Investment Transaction Report from "
                + mSetting.getStartDate() + " to " + mSetting.getEndDate() + "\n");
        if (mSetting.getSelectedTradeActionSet().isEmpty()) {
            reportStr.append("No TradeAction selected.");
            return reportStr.toString();
        }

        // Transaction has only security name, not id, so we convert ids to names.
        Set<String> securityNameSet = new HashSet<>();
        for (Integer sid : mSetting.getSelectedSecurityIDSet()) {
            Security security = mMainApp.getSecurityByID(sid);
            securityNameSet.add(security == null ? NOSECURITY : security.getName());
        }

        class Line {
            private String date = "";
            private String aName = "";
            private String ta = "";
            private String sName = "";
            private String memo = "";
            private String price = "";
            private String quantity = "";
            private String commission = "";
            private String cashAmt = "";
            private String invAmt = "";
        }

        final List<Line> lineList = new ArrayList<>();
        final Line title = new Line();
        title.date = "Date";
        title.aName = "Account";
        title.ta = "Action";
        title.sName = "Security";
        title.memo = "Memo";
        title.price = "Price";
        title.quantity = "Quantity";
        title.commission = "Commission";
        title.cashAmt = "Cash Amount";
        title.invAmt = "Inv. Amount";
        lineList.add(title);

        final DecimalFormat dcFormat = new DecimalFormat("#,##0.00"); // formatter for dollar & cents
        final DecimalFormat qpFormat = new DecimalFormat("#,##0.000"); // formatter for quantity and price

        BigDecimal totalCommissionAmt = BigDecimal.ZERO;
        BigDecimal totalCashAmt = BigDecimal.ZERO;
        BigDecimal totalInvAmt = BigDecimal.ZERO;
        for (SelectedAccount sa : mSetting.getSelectedAccountList()) {
            Account account = sa.getAccount();
            for (Transaction t : account.getTransactionList()) {
                LocalDate tDate = t.getTDate();
                if (tDate.isBefore(mSetting.getStartDate()))
                    continue;
                if(tDate.isAfter(mSetting.getEndDate()))
                    break; // we are done with this account

                String sName = t.getSecurityName() == null ? NOSECURITY : t.getSecurityName();
                if (securityNameSet.contains(sName)
                        && mSetting.getSelectedTradeActionSet().contains(t.getTradeAction())) {
                    Line line = new Line();
                    line.date = tDate.toString();
                    line.aName = account.getName();
                    line.ta = t.getTradeAction().name();
                    line.sName = sName;
                    line.memo = t.getMemo() == null ? "" : t.getMemo();
                    line.price = t.getPrice() == null ? "" : qpFormat.format(t.getPrice());
                    line.quantity = t.getQuantity() == null ? "" : qpFormat.format(t.getQuantity());
                    BigDecimal comm = t.getCommission();
                    if (comm != null) {
                        line.commission = dcFormat.format(t.getCommission());
                        totalCommissionAmt = totalCommissionAmt.add(comm);
                    }
                    BigDecimal cash = t.getCashAmount();
                    if (cash != null) {
                        line.cashAmt = dcFormat.format(t.getCashAmount());
                        totalCashAmt = totalCashAmt.add(cash);
                    }
                    BigDecimal inv = t.getInvestAmount();
                    if (inv != null) {
                        line.invAmt = dcFormat.format(t.getInvestAmount());
                        totalInvAmt = totalInvAmt.add(inv);
                    }
                    lineList.add(line);
                }
            }
        }
        Line total = new Line();
        total.date = "Total";
        total.commission = dcFormat.format(totalCommissionAmt);
        total.cashAmt = dcFormat.format(totalCashAmt);
        total.invAmt = dcFormat.format(totalInvAmt);
        lineList.add(total);

        int dateLen = 11;
        int aNameLen = 12;
        int taLen = 10;
        int sNameLen = 24;
        int memoLen = 12;
        int priceLen = 12;
        int quantityLen = 16;
        int commissionLen = 10;
        int cashAmtLen = 10;
        int invAmtLen = 10;

        for (Line line : lineList) {
            aNameLen = Math.max(line.aName.length(), aNameLen);
            taLen = Math.max(line.ta.length(), taLen);
            sNameLen = Math.max(line.sName.length(), sNameLen);
            memoLen = Math.max(line.memo.length(), memoLen);
            priceLen = Math.max(line.price.length(), priceLen);
            quantityLen = Math.max(line.quantity.length(), quantityLen);
            commissionLen = Math.max(line.commission.length(), commissionLen);
            cashAmtLen = Math.max(line.cashAmt.length(), cashAmtLen);
            invAmtLen = Math.max(line.invAmt.length(), invAmtLen);
        }

        int gap = 2;
        final String formatStr = "%-" + dateLen + "s" // left adjust date
                + "%" + (gap+aNameLen) + "s"
                + "%" + (gap+taLen) + "s"
                + "%" + (gap+sNameLen) + "s"
                + "%" + (gap+memoLen) + "s"
                + "%" + (gap+priceLen) + "s"
                + "%" + (gap+quantityLen) + "s"
                + "%" + (gap+commissionLen) + "s"
                + "%" + (gap+cashAmtLen) + "s"
                + "%" + (gap+invAmtLen) + "s"
                + "\n";

        final String separator = new String(new char[dateLen + (gap+aNameLen) + (gap+taLen) + (gap+sNameLen)
                + (gap+memoLen) + (gap+priceLen) + (gap+quantityLen) + (gap+commissionLen) + (gap+cashAmtLen)
                + (gap+invAmtLen)]).replace("\0", "=");
        reportStr.append(separator).append("\n");
        for (int i = 0; i < lineList.size(); i++) {
            Line l = lineList.get(i);
            reportStr.append(String.format(formatStr, l.date, l.aName, l.ta, l.sName,
                    l.memo, l.price, l.quantity, l.commission, l.cashAmt, l.invAmt));
            if (i == 0 || i == lineList.size()-2)
                reportStr.append(separator).append("\n");
        }
        return reportStr.toString();
    }

    private String BankTransReport() {
        StringBuilder reportStr = new StringBuilder("Banking Transaction Report from "
                + mSetting.getStartDate() + " to " + mSetting.getEndDate() + "\n");

        if (mSetting.getSelectedCategoryIDSet().isEmpty()) {
            reportStr.append("No Category selected.");
            return reportStr.toString();
        }

        if (mSetting.getSelectedSecurityIDSet().isEmpty()) {
            reportStr.append("No Security selected.");
        }

        // Transaction has only security name, not id, so we convert ids to names.
        Set<String> securityNameSet = new HashSet<>();
        for (Integer sid : mSetting.getSelectedSecurityIDSet()) {
            Security security = mMainApp.getSecurityByID(sid);
            securityNameSet.add(security == null ? null : security.getName());
        }

        class Line {
            private String date = "";
            private String aName = "";
            private String num = "";
            private String desc = "";
            private String memo = "";
            private String category = "";
            private String amount = "";
        }

        final List<Line> lineList = new ArrayList<>();
        final Line title = new Line();
        title.date = "Date";
        title.aName = "Account";
        title.num = "Num";
        title.desc = "Description";
        title.memo = "Memo";
        title.category = "Category";
        title.amount = "Amount";
        lineList.add(title);

        BigDecimal totalAmount = BigDecimal.ZERO;
        final DecimalFormat dcFormat = new DecimalFormat("#,##0.00"); // formatter for dollar & cents
        for (SelectedAccount sa : mSetting.getSelectedAccountList()) {
            Account account = sa.getAccount();
            for (Transaction t : account.getTransactionList()) {
                LocalDate tDate = t.getTDate();
                if (tDate.isBefore(mSetting.getStartDate()))
                    continue;
                if (tDate.isAfter(mSetting.getEndDate()))
                    break; // we are done with this account

                if (mSetting.getSelectedCategoryIDSet().contains(t.getCategoryID())
                        && securityNameSet.contains(t.getSecurityName())) {
                    Line line = new Line();
                    line.date = tDate.toString();
                    line.aName = account.getName();
                    if (account.getType().equals(Account.Type.INVESTING))
                        line.num = t.getTradeAction().name();
                    else
                        line.num = t.getReference() == null ? "" : t.getReference();
                    line.memo = t.getMemo() == null ? "" : t.getMemo();
                    line.category = mMainApp.mapCategoryOrAccountIDToName(t.getCategoryID());
                    BigDecimal amount;
                    if (account.getType().equals(Account.Type.INVESTING)) {
                        line.desc = t.getSecurityName() == null ? "" : t.getSecurityName();
                        amount = t.getCashAmount();
                    } else {
                        line.desc = t.getPayee() == null ? "" : t.getPayee();
                        amount = t.getDepositeProperty().get().subtract(t.getPaymentProperty().get());
                    }
                    line.amount = dcFormat.format(amount);
                    totalAmount = totalAmount.add(amount);

                    lineList.add(line);
                }
            }
        }
        Line total = new Line();
        total.date = "Total";
        total.amount = dcFormat.format(totalAmount);
        lineList.add(total);

        int gap = 2;
        int dateLen = 11;
        int aNameLen = 12;
        int numLen = 6;
        int descLen = 16;
        int memoLen = 16;
        int categoryLen = 10;
        int amountLen = 10;
        for (Line line : lineList) {
            aNameLen = Math.max(aNameLen, line.aName.length());
            numLen = Math.max(numLen, line.num.length());
            descLen = Math.max(descLen, line.desc.length());
            memoLen = Math.max(memoLen, line.memo.length());
            categoryLen = Math.max(categoryLen, line.category.length());
            amountLen = Math.max(amountLen, line.amount.length());
        }

        final String formatStr = "%-" + dateLen + "s" // left adjust date
                + "%" + (gap+aNameLen) + "s"
                + "%" + (gap+numLen) + "s"
                + "%" + (gap+descLen) + "s"
                + "%" + (gap+memoLen) + "s"
                + "%" + (gap+categoryLen) + "s"
                + "%" + (gap+amountLen) + "s"
                + "\n";
        final String separator = new String(new char[dateLen + (gap+aNameLen) + (gap+numLen) + (gap+descLen)
               + (gap+memoLen) + (gap+categoryLen)+ (gap+amountLen)]).replace("\0", "=");
        for (int i = 0; i < lineList.size(); i++) {
            Line l = lineList.get(i);
            reportStr.append(String.format(formatStr, l.date, l.aName, l.num, l.desc, l.memo, l.category, l.amount));
            if (i == 0 || i == lineList.size()-2)
                reportStr.append(separator).append("\n");
        }
        return reportStr.toString();
    }

    private String NAVReport() {
        final LocalDate date = mSetting.getEndDate();
        StringBuilder outputStr = new StringBuilder("NAV Report as of " + date + "\n\n");

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
            outputStr.append(String.format("%-55s%35s\n", s.toString(),
                    dcFormat.format(shList.get(shListLen - 1).getMarketValue())));

            outputStr.append(separator0).append("\n");

            // print out positions
            BigDecimal q, p;
            for (int i = 0; i < shListLen-1; i++) {
                SecurityHolding sh = shList.get(i);
                q = sh.getQuantity();
                p = sh.getPrice();
                outputStr.append(String.format("  %-50s%12s%10s%14s\n", sh.getLabel(),
                        q == null ? "" : qpFormat.format(q),
                        p == null ? "" : qpFormat.format(p), dcFormat.format(sh.getMarketValue())));
            }
            outputStr.append(separator1).append("\n");
            outputStr.append("\n");
        }

        // print out total
        outputStr.append(String.format("%-55s%35s\n", "Total", dcFormat.format(total)));

        return outputStr.toString();
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
                startDate = mapDatePeriod(DatePeriod.LASTEOM).getKey().plusDays(1);
                endDate = today;
                break;
            case QUARTERTODATE:
                startDate = mapDatePeriod(DatePeriod.LASTEOQ).getKey().plusDays(1);
                endDate = today;
                break;
            case YEARTODATE:
                startDate = mapDatePeriod(DatePeriod.LASTEOY).getKey().plusDays(1);
                endDate = today;
                break;
            case EPOCHTODATE:
                startDate = LocalDate.MIN;
                endDate = today;
                break;
            case LASTWEEK:
                startDate = mapDatePeriod(DatePeriod.WEEKTODATE).getKey().minusDays(7);
                endDate = startDate.plusDays(6);
                break;
            case LASTMONTH:
                endDate = mapDatePeriod(DatePeriod.LASTEOM).getKey();
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

    @FXML
    private void initialize() {
        mSaveReportButton.setDisable(true);
        mShowSettingButton.setDisable(true);
        mReportTextArea.setVisible(false);
        mReportTextArea.setStyle("-fx-font-family: monospace");
    }
}
