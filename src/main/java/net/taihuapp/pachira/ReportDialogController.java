/*
 * Copyright (C) 2018-2021.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of Pachira.
 *
 * Pachira is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * Pachira is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.pachira;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.TilePane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReportDialogController {

    private static final Logger mLogger = Logger.getLogger(ReportDialogController.class);

    public enum ReportType { NAV, INVESTINCOME, INVESTTRANS, BANKTRANS, CAPITALGAINS }
    public enum Frequency { DAILY, MONTHLY, QUARTERLY, ANNUAL }
    public enum DatePeriod {
        TODAY, YESTERDAY, LASTEOM, LASTEOQ, LASTEOY, CUSTOMDATE,
        WEEKTODATE, MONTHTODATE, QUARTERTODATE, YEARTODATE, EPOCHTODATE,
        LASTWEEK, LASTMONTH, LASTQUARTER, LASTYEAR,
        LAST7DAYS, LAST30DAYS, LAST365DAYS, CUSTOMPERIOD;

        static EnumSet<DatePeriod> groupD = EnumSet.of(TODAY, YESTERDAY, LASTEOM, LASTEOQ, LASTEOY, CUSTOMDATE);
        static EnumSet<DatePeriod> groupP = EnumSet.of(WEEKTODATE, MONTHTODATE, QUARTERTODATE, YEARTODATE, EPOCHTODATE,
                LASTWEEK, LASTMONTH, LASTQUARTER, LASTYEAR, LAST7DAYS, LAST30DAYS, LAST365DAYS, CUSTOMPERIOD);
    }

    public enum ItemName { ACCOUNTID, CATEGORYID, SECURITYID, TRADEACTION }

    private static final String NOSECURITY = "(No Security)";
    private static final String NOCATEGORY = "(No Category)";

    public static class Setting {
        private int mID;
        private String mName = "";
        private final ReportType mType;
        private DatePeriod mDatePeriod;
        private LocalDate mStartDate;
        private LocalDate mEndDate;
        private Frequency mFrequency;
        private final Set<Integer> mSelectedAccountIDSet = new HashSet<>();
        private final Set<Integer> mSelectedCategoryIDSet = new HashSet<>();
        private final Set<Integer> mSelectedSecurityIDSet = new HashSet<>();
        private final Set<Transaction.TradeAction> mSelectedTradeActionSet = new HashSet<>();
        private String mPayeeContains = "";
        private Boolean mPayeeRegEx = false;
        private String mMemoContains = "";
        private Boolean mMemoRegEx = false;


        Setting(ReportType type) {
            this(-1, type);
        }

        public Setting(int id, ReportType type) {
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
                case CAPITALGAINS:
                    mDatePeriod = DatePeriod.LASTYEAR;
                    break;
                default:
                    mDatePeriod = DatePeriod.CUSTOMPERIOD;
                    break;
            }

            mStartDate = LocalDate.now();
            mEndDate = LocalDate.now();
            mFrequency = Frequency.DAILY;
        }

        // getters
        public int getID() { return mID; }
        public String getName() { return mName; }
        public ReportType getType() { return mType; }
        public DatePeriod getDatePeriod() { return mDatePeriod; }
        public LocalDate getStartDate() { return mStartDate; }
        public LocalDate getEndDate() { return mEndDate; }
        public Frequency getFrequency() { return mFrequency; }
        public String getPayeeContains() { return mPayeeContains; }
        public boolean getPayeeRegEx() { return mPayeeRegEx; }
        public String getMemoContains() { return mMemoContains; }
        public boolean getMemoRegEx() { return mMemoRegEx; }

        public Set<Integer> getSelectedAccountIDSet() { return mSelectedAccountIDSet; }
        List<Account> getSelectedAccountList(MainModel mainModel) {
            Predicate<Account> predicate = a -> !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME)
                    && getSelectedAccountIDSet().contains(a.getID());
            if (getType() == ReportType.INVESTINCOME || getType() == ReportType.INVESTTRANS
                || getType() == ReportType.CAPITALGAINS)
                predicate = predicate.and(a -> a.getType().isGroup(Account.Type.Group.INVESTING));

            return mainModel.getAccountList(predicate);
        }
        public Set<Integer> getSelectedCategoryIDSet() { return mSelectedCategoryIDSet; }
        public Set<Integer> getSelectedSecurityIDSet() { return mSelectedSecurityIDSet; }
        public Set<Transaction.TradeAction> getSelectedTradeActionSet() { return mSelectedTradeActionSet; }

        // setters
        void setID(int id) { mID = id; }
        public void setName(String name) { mName = name; }
        public void setDatePeriod(DatePeriod dp) { mDatePeriod = dp; }
        public void setStartDate(LocalDate date) { mStartDate = date; }
        public void setEndDate(LocalDate date) { mEndDate = date; }
        public void setFrequency(Frequency f) { mFrequency = f; }
        public void setPayeeContains(String p) { mPayeeContains = p; }
        public void setPayeeRegEx(boolean r) { mPayeeRegEx = r; }
        public void setMemoContains(String p) { mMemoContains = p; }
        public void setMemoRegEx(boolean r) { mMemoRegEx = r; }
    }

    private Setting mSetting;

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
    private TableView<Pair<Account, BooleanProperty>> mAccountSelectionTableView;
    @FXML
    private TableColumn<Pair<Account, BooleanProperty>, String> mAccountTableColumn;
    @FXML
    private TableColumn<Pair<Account, BooleanProperty>, Boolean> mAccountSelectedTableColumn;

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
    private Tab mTextMatchTab;
    @FXML
    private TextField mPayeeContainsTextField;
    @FXML
    private CheckBox mPayeeRegExCheckBox;
    @FXML
    private TextField mMemoContainsTextField;
    @FXML
    private CheckBox mMemoRegExCheckBox;

    @FXML
    private TilePane mRightTilePane;
    @FXML
    TextArea mReportTextArea;
    @FXML
    Button mShowReportButton;
    @FXML
    Button mSaveReportButton;
    @FXML
    Button mShowSettingButton;
    @FXML
    Button mSaveSettingButton;

    private MainModel mainModel;

    void setMainModel(MainModel mainModel, Setting setting) {

        mSetting = setting;
        this.mainModel = mainModel;

        // set window title
        ((Stage) mTabPane.getScene().getWindow()).setTitle(mSetting.getType() + " Report");

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
            case CAPITALGAINS:
                setupCapitalGainsReport();
                break;
            default:
                break;
        }
    }

    private Set<String> mapSecurityIDSetToNameSet(Set<Integer> idSet) {
        return idSet.stream().map(id -> mainModel.getSecurity(s -> s.getID() == id)
                .map(Security::getName).orElse(NOSECURITY)).collect(Collectors.toSet());
    }

    private void setupCapitalGainsReport() {
        setupDatesTab(true);
        setupAccountsTab(Set.of(Account.Type.Group.INVESTING));
        mCategoriesTab.setDisable(true);
        setupSecuritiesTab();
        mTradeActionTab.setDisable(true);
        mTextMatchTab.setDisable(true);
    }

    private void setupNAVReport() {
        setupDatesTab(false);  // just one date
        setupAccountsTab(Set.of(Account.Type.Group.values())); // show all accounts
        mCategoriesTab.setDisable(true);
        mSecuritiesTab.setDisable(true);
        mTradeActionTab.setDisable(true);
        mTextMatchTab.setDisable(true);
    }

    private void setupInvestIncomeReport() {
        setupDatesTab(true);
        setupAccountsTab(Set.of(Account.Type.Group.INVESTING)); // show investing accounts only
        mCategoriesTab.setDisable(true);
        setupSecuritiesTab();
        setupTradeActionTab();
        mTextMatchTab.setDisable(true);
    }

    private void setupInvestTransactionReport() {
        setupDatesTab(true);
        setupAccountsTab(Set.of(Account.Type.Group.INVESTING)); // show investing accounts only
        mCategoriesTab.setDisable(true);
        setupSecuritiesTab();
        setupTradeActionTab();
        mTextMatchTab.setDisable(true);
    }

    private void setupBankTransactionReport() {
        setupDatesTab(true);
        setupAccountsTab(Set.of(Account.Type.Group.values())); // show all accounts
        setupCategoriesTab();
        setupSecuritiesTab();
        mTradeActionTab.setDisable(true); // no need for TradeAction
        setupTextMatchTab();
    }

    private void setupTextMatchTab() {
        mPayeeContainsTextField.setText(mSetting.getPayeeContains());
        mPayeeRegExCheckBox.setSelected(mSetting.getPayeeRegEx());
        mMemoContainsTextField.setText(mSetting.getMemoContains());
        mMemoRegExCheckBox.setSelected(mSetting.getMemoRegEx());
    }

    private void setupDatesTab(boolean showPeriod) {
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
        mFrequencyLabel.setVisible(false);
        mFrequencyChoiceBox.setVisible(false);

        mDatePeriodChoiceBox.getSelectionModel().select(mSetting.getDatePeriod());
        mFrequencyChoiceBox.getSelectionModel().select(mSetting.getFrequency());
    }

    // show accounts with group included in groupSet
    private void setupAccountsTab(Set<Account.Type.Group> groupSet) {
        // a list of Pair<Pair<account, displayOrder>, selected>
        ObservableList<Pair<Account, BooleanProperty>> abList = FXCollections.observableArrayList();

        for (Account account : mainModel.getAccountList(a -> groupSet.contains(a.getType().getGroup())
                && !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME))) {
            abList.add(new Pair<>(account,
                    new SimpleBooleanProperty(mSetting.getSelectedAccountIDSet().contains(account.getID()))));
        }

        mAccountSelectionTableView.setItems(abList);
        mAccountTableColumn.setCellValueFactory(cd -> cd.getValue().getKey().getNameProperty());
        mAccountSelectedTableColumn.setCellValueFactory(cd -> cd.getValue().getValue());
        mAccountSelectedTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(mAccountSelectedTableColumn));
        mAccountSelectedTableColumn.setEditable(true);
    }

    private void setupCategoriesTab() {
        ObservableList<Pair<Pair<String, Integer>, BooleanProperty>> sibList = FXCollections.observableArrayList();
        boolean newSetting = mSetting.getID() < 0;  // we pre-select all categories for new setting
        sibList.add(new Pair<>(new Pair<>(NOCATEGORY, 0),
                new SimpleBooleanProperty(newSetting
                        || mSetting.getSelectedCategoryIDSet().contains(0))));
        for (Category c : mainModel.getCategoryList()) {
            sibList.add(new Pair<>(new Pair<>(c.getNameProperty().get(), c.getID()),
                    new SimpleBooleanProperty(newSetting
                            || mSetting.getSelectedCategoryIDSet().contains(c.getID()))));
        }
        for (Account a : mainModel.getAccountList(a -> !a.getHiddenFlag()
                && !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME))) {
            sibList.add(new Pair<>(new Pair<>("[" + a.getName() + "]", -a.getID()),
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
        for (Security s : mainModel.getSecurityList()) {
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
        } else if (currentTab.equals(mAccountsTab)) {
            for (Pair<Account, BooleanProperty> ab : mAccountSelectionTableView.getItems())
                ab.getValue().set(selected);
        } else
            mLogger.error("Other tab?");
    }

    @FXML
    private void handleClose() {
        close();
    }

    @FXML
    private void handleShowReport() {
        updateSetting();
        try {
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
                case CAPITALGAINS:
                    mReportTextArea.setText(CapitalGainsReport());
                    break;
                default:
                    mReportTextArea.setText("Report type " + mSetting.getType() + " not implemented yet");
                    break;
            }
            mReportTextArea.setVisible(true);
            mShowReportButton.setDisable(true);
            mSaveReportButton.setDisable(false);
            mShowSettingButton.setDisable(false);
        } catch (DaoException | ModelException e) {
            final String msg =  e.getClass().getName() + " when showing report";
            mLogger.error(msg, e);
            DialogUtil.showExceptionDialog((Stage) mTabPane.getScene().getWindow(), e.getClass().getName(),
                    msg, e.toString(), e);
        }
    }

    @FXML
    private void handleSaveReport() {
        final FileChooser fileChooser = new FileChooser();
        final FileChooser.ExtensionFilter txtFilter = new FileChooser.ExtensionFilter("Text file",
                "*.TXT", "*.TXt", "*.TxT", "*.Txt", "*.tXT", "*.tXt", "*.txT", "*.txt");
        fileChooser.getExtensionFilters().add(txtFilter);
        fileChooser.setInitialFileName(mSetting.getName()+".txt");
        File reportFile = fileChooser.showSaveDialog(mTabPane.getScene().getWindow());

        if (reportFile != null) {
            try (PrintWriter pw = new PrintWriter(reportFile.getCanonicalPath())) {
                pw.print(mReportTextArea.getText());
            } catch (IOException e) {
                mLogger.error("IOException", e);
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
        mSetting.getSelectedAccountIDSet().clear();
        for (Pair<Account, BooleanProperty> ab : mAccountSelectionTableView.getItems()) {
            if (ab.getValue().get())
                mSetting.getSelectedAccountIDSet().add(ab.getKey().getID());
        }

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

        if (!mTextMatchTab.isDisable()) {
            mSetting.setPayeeContains(mPayeeContainsTextField.getText());
            mSetting.setPayeeRegEx(mPayeeRegExCheckBox.isSelected());
            mSetting.setMemoContains(mMemoContainsTextField.getText());
            mSetting.setMemoRegEx(mMemoRegExCheckBox.isSelected());
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

        Stage stage = (Stage) mTabPane.getScene().getWindow();
        Optional<String> result = tiDialog.showAndWait();
        if (result.isPresent()) {
            String settingName = result.get();
            if (settingName.length() == 0 || settingName.length() > MainModel.SAVEDREPORTS_NAME_LEN) {
                DialogUtil.showWarningDialog(stage, "Warning", "Bad Report Setting Name",
                        "Name length should be between 1 and " + MainModel.SAVEDREPORTS_NAME_LEN);
            } else {
                if (!result.get().equals(mSetting.getName())) {
                    // name has changed, need to save as new
                    mSetting.setID(-1);
                    mSetting.setName(result.get());
                }

                try {
                    mainModel.insertUpdateReportSetting(mSetting);
                } catch (DaoException e) {
                    final String msg = e.getErrorCode() + " when saving report setting";
                    mLogger.error(msg, e);
                    DialogUtil.showExceptionDialog((Stage) mTabPane.getScene().getWindow(), e.getClass().getName(),
                            msg, e.toString(), e);
                }
            }
        }
    }

    void close() { ((Stage) mTabPane.getScene().getWindow()).close(); }

    private String InvestIncomeReport() throws DaoException, ModelException {
        StringBuilder reportStr = new StringBuilder("Investment Income Report from "
                + mSetting.getStartDate() + " to " + mSetting.getEndDate() + "\n");
        if (mSetting.getSelectedTradeActionSet().isEmpty()) {
            reportStr.append("No TradeAction selected.");
            return reportStr.toString();
        }

        class Income {
            private BigDecimal dividend = BigDecimal.ZERO;
            private BigDecimal interest = BigDecimal.ZERO;
            private BigDecimal ltcgdist = BigDecimal.ZERO;
            private BigDecimal mtcgdist = BigDecimal.ZERO;
            private BigDecimal stcgdist = BigDecimal.ZERO;
            private BigDecimal realized = BigDecimal.ZERO;
            private BigDecimal misc_inc = BigDecimal.ZERO;

            BigDecimal total() {
                return dividend.add(interest).add(ltcgdist).add(mtcgdist).add(stcgdist).add(realized).add(misc_inc);
            }
            Income add(Income addend) {
                Income i = new Income();
                i.dividend = this.dividend.add(addend.dividend);
                i.interest = this.interest.add(addend.interest);
                i.ltcgdist = this.ltcgdist.add(addend.ltcgdist);
                i.mtcgdist = this.mtcgdist.add(addend.mtcgdist);
                i.stcgdist = this.stcgdist.add(addend.stcgdist);
                i.realized = this.realized.add(addend.realized);
                i.misc_inc = this.misc_inc.add(addend.misc_inc);
                return i;
            }
        }

        final DecimalFormat qpFormat = new DecimalFormat("#,##0.000"); // formatter for quantity and price
        Income fieldUsed = new Income(); // use this to keep track the field being used
        List<Map<String, Income>> accountSecurityIncomeList = new ArrayList<>();
        for (Account account : mainModel.getAccountList(a -> a.getType().isGroup(Account.Type.Group.INVESTING)
                && !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME))) {

            if (!mSetting.getSelectedAccountIDSet().contains(account.getID()))
                continue;

            Map<String, Income> securityIncomeMap = new TreeMap<>();
            accountSecurityIncomeList.add(securityIncomeMap);
            for (Transaction t : account.getTransactionList()) {
                LocalDate tDate = t.getTDate();
                if (tDate.isBefore(mSetting.getStartDate()))
                    continue;
                if (tDate.isAfter(mSetting.getEndDate()))
                    break; // we are done with this account

                final String sName = (t.getSecurityName() == null || t.getSecurityName().isEmpty()) ?
                    NOSECURITY : t.getSecurityName();
                final int sID = mainModel.getSecurity(security -> security.getName().equals(t.getSecurityName()))
                            .map(Security::getID).orElse(0);
                if (!mSetting.getSelectedSecurityIDSet().contains(sID))
                    continue;

                Income income = securityIncomeMap.get(sName);
                if (income == null)
                    income = new Income();
                switch (t.getTradeAction()) {
                    case SELL:
                    case CVTSHRT:
                        fieldUsed.realized = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        BigDecimal realized = mainModel.calcRealizedGain(t);
                        if (realized == null) {
                            reportStr.append("**********************\n" + "* Lot Matching Error *\n" + "* Account:  ")
                                    .append(account.getName()).append("\n").append("* Date:     ").append(t.getTDate())
                                    .append("\n").append("* Security: ").append(t.getSecurityName()).append("\n")
                                    .append("* Action:   ").append(t.getTradeAction().name()).append("\n")
                                    .append("* Quantity: ").append(qpFormat.format(t.getQuantity())).append("\n");
                            return reportStr.toString();
                        }
                        income.realized = income.realized.add(realized);
                        break;
                    case DIV:
                    case REINVDIV:
                        fieldUsed.dividend = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        income.dividend = income.dividend.add(t.getAmount());
                        break;
                    case INTINC:
                    case REINVINT:
                        fieldUsed.interest = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        income.interest = income.interest.add(t.getAmount());
                        break;
                    case CGLONG:
                    case REINVLG:
                        fieldUsed.ltcgdist = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        income.ltcgdist = income.ltcgdist.add(t.getAmount());
                        break;
                    case CGMID:
                    case REINVMD:
                        fieldUsed.mtcgdist = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        income.mtcgdist = income.mtcgdist.add(t.getAmount());
                        break;
                    case CGSHORT:
                    case REINVSH:
                        fieldUsed.stcgdist = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        income.stcgdist = income.stcgdist.add(t.getAmount());
                        break;
                    case MISCEXP:
                        fieldUsed.misc_inc = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        income.misc_inc = income.misc_inc.subtract(t.getAmount());
                        break;
                    case MISCINC:
                        fieldUsed.misc_inc = BigDecimal.ONE;
                        securityIncomeMap.put(sName, income);
                        income.misc_inc = income.misc_inc.add(t.getAmount());
                        break;
                    case RTRNCAP:
                    case SHTSELL:
                    case MARGINT:
                    case DEPOSIT:
                    case WITHDRAW:
                    case BUY:
                    case STKSPLIT:
                    case SHRSIN:
                    case SHRSOUT:
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
                divident = df.format(income.dividend);
                interest = df.format(income.interest);
                ltcgdist = df.format(income.ltcgdist);
                mtcgdist = df.format(income.mtcgdist);
                stcgdist = df.format(income.stcgdist);
                realized = df.format(income.realized);
                miscinc = df.format(income.misc_inc);
                total = df.format(income.total());
            }
        }

        int gap = 2;
        int sNameLen = 10;
        int dividendLen = 4;
        int interestLen = 4;
        int ltcgdistLen = 4;
        int mtcgdistLen = 4;
        int stcgdistLen = 4;
        int realizedLen = 4;
        int misc_incLen = 4;
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
        for (Account account : mSetting.getSelectedAccountList(mainModel)) {
            final Map<String, Income> securityIncomeMap = accountSecurityIncomeList.get(accountIdx++);

            final Line accountLine = new Line();
            accountLine.sName = account.getName();
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
            final Line accountTotalLine = new Line(account.getName()+ " Total",
                    accountTotal, dcFormat);

            totalTotal = totalTotal.add(accountTotal);

            lineList.add(accountTotalLine);
            lineList.add(emptyLine);
        }

        if (mSetting.getSelectedAccountIDSet().size() > 1) {
            lineList.add(separator0);
            lineList.add(new Line("Total", totalTotal, dcFormat));
        }

        for (Line line : lineList) {
            sNameLen = Math.max(sNameLen, line.sName.length());
            dividendLen = Math.max(dividendLen, line.divident.length());
            interestLen = Math.max(interestLen, line.interest.length());
            ltcgdistLen = Math.max(ltcgdistLen, line.ltcgdist.length());
            mtcgdistLen = Math.max(mtcgdistLen, line.mtcgdist.length());
            stcgdistLen = Math.max(stcgdistLen, line.stcgdist.length());
            realizedLen = Math.max(realizedLen, line.realized.length());
            misc_incLen = Math.max(misc_incLen, line.miscinc.length());
            totalLen = Math.max(totalLen, line.total.length());
        }

        separator0.sName = new String(new char[sNameLen]).replace("\0", "=");
        separator0.divident = new String(new char[dividendLen+gap]).replace("\0", "=");
        separator0.interest = new String(new char[interestLen+gap]).replace("\0", "=");
        separator0.ltcgdist = new String(new char[ltcgdistLen+gap]).replace("\0", "=");
        separator0.mtcgdist = new String(new char[mtcgdistLen+gap]).replace("\0", "=");
        separator0.stcgdist = new String(new char[stcgdistLen+gap]).replace("\0", "=");
        separator0.realized = new String(new char[realizedLen+gap]).replace("\0", "=");
        separator0.miscinc = new String(new char[misc_incLen+gap]).replace("\0", "=");
        separator0.total = new String(new char[totalLen+gap]).replace("\0", "=");

        separator1.sName = new String(new char[sNameLen]).replace("\0", "-");
        separator1.divident = new String(new char[dividendLen+gap]).replace("\0", "-");
        separator1.interest = new String(new char[interestLen+gap]).replace("\0", "-");
        separator1.ltcgdist = new String(new char[ltcgdistLen+gap]).replace("\0", "-");
        separator1.mtcgdist = new String(new char[mtcgdistLen+gap]).replace("\0", "-");
        separator1.stcgdist = new String(new char[stcgdistLen+gap]).replace("\0", "-");
        separator1.realized = new String(new char[realizedLen+gap]).replace("\0", "-");
        separator1.miscinc = new String(new char[misc_incLen+gap]).replace("\0", "-");
        separator1.total = new String(new char[totalLen+gap]).replace("\0", "-");

        for (Line l : lineList) {
            reportStr.append(String.format("%-" + sNameLen + "s", l.sName));
            if (fieldUsed.dividend.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (dividendLen + gap) + "s", l.divident));
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
            if (fieldUsed.misc_inc.compareTo(BigDecimal.ZERO) != 0)
                reportStr.append(String.format("%" + (misc_incLen + gap) + "s", l.miscinc));

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
        Set<String> securityNameSet = mapSecurityIDSetToNameSet(mSetting.getSelectedSecurityIDSet());

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
        for (Account account : mSetting.getSelectedAccountList(mainModel)) {
            for (Transaction t : account.getTransactionList()) {
                LocalDate tDate = t.getTDate();
                if (tDate.isBefore(mSetting.getStartDate()))
                    continue;
                if(tDate.isAfter(mSetting.getEndDate()))
                    break; // we are done with this account

                String sName = t.getSecurityName().isEmpty() ? NOSECURITY : t.getSecurityName();
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
                    BigDecimal cash = t.cashFlow();
                    if (cash != null) {
                        line.cashAmt = dcFormat.format(cash);
                        totalCashAmt = totalCashAmt.add(cash);
                    }
                    BigDecimal inv = t.getInvestAmount();
                    if (inv != null) {
                        line.invAmt = dcFormat.format(inv);
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

    private String CapitalGainsReport() throws DaoException, ModelException {
        class Line {
            private String aName = "";  // account name
            private String sName = "";  // security name
            private String quantity = "";
            private String bDate = ""; // buy date
            private String sDate = ""; // sell date
            private String proceeds = ""; // gross proceeds
            private String costBasis = ""; // cost basis
            private String realizedGL = ""; // realized gain and loss

            String format(String fmtStr) {
                return String.format(fmtStr, aName, sName, quantity, bDate, sDate,
                        proceeds, costBasis, realizedGL);
            }
        }

        BigDecimal totalSTCostBasis = BigDecimal.ZERO;
        BigDecimal totalSTProceeds = BigDecimal.ZERO;
        BigDecimal totalLTCostBasis = BigDecimal.ZERO;
        BigDecimal totalLTProceeds = BigDecimal.ZERO;

        final List<Line> detailLTGLines = new ArrayList<>();  // long term gain details
        final List<Line> detailSTGLines = new ArrayList<>();  // short term gain details
        final List<Line> transactionLTGLines = new ArrayList<>(); // long term gain for each transaction
        final List<Line> transactionSTGLines = new ArrayList<>(); // short term gain for each transaction

        final Line title = new Line();
        title.aName = "Account";
        title.sName = "Security";
        title.quantity = "Shares";
        title.bDate = "Bought";
        title.sDate = "Sold";
        title.proceeds = "Gross Proceeds";
        title.costBasis = "Cost Basis";
        title.realizedGL = "Realized G/L";

        final List<String> errMsgs = new ArrayList<>();

        final DecimalFormat dcFormat = new DecimalFormat("#,##0.00"); // formatter for dollar & cents
        final DecimalFormat qpFormat = new DecimalFormat("#,##0.000"); // formatter for quantity and price

        // selected security tab has only IDs, transaction contains security name only,
        // convert ID's to names
        Set<String> securityNameSet = mapSecurityIDSetToNameSet(mSetting.getSelectedSecurityIDSet());

        final LocalDate sDate1 = mSetting.mStartDate.minusDays(1); // one day before start date
        final LocalDate eDate1 = mSetting.mEndDate.plusDays(1); // one day after end date
        for (int accountID : mSetting.getSelectedAccountIDSet()) {
            Account account = mainModel.getAccount(a -> a.getID() == accountID).orElse(null);
            if (account == null)
                continue;
            for (Transaction t : new FilteredList<>(account.getTransactionList(), p -> {
                final String sName = p.getSecurityName().isEmpty() ? NOSECURITY : p.getSecurityName();
                return (securityNameSet.contains(sName) &&
                        (p.getTradeAction() == Transaction.TradeAction.SELL ||
                                p.getTradeAction() == Transaction.TradeAction.CVTSHRT) &&
                        p.getTDate().isAfter(sDate1) && p.getTDate().isBefore(eDate1));
            })) {
                BigDecimal matchedQuantity = BigDecimal.ZERO;
                CapitalGainItem transactionSTG = null; // keep track short term gain for the transaction
                CapitalGainItem transactionLTG = null; // keep track long term gain for the transaction
                for (CapitalGainItem cgi : mainModel.getCapitalGainItemList(t)) {
                    matchedQuantity = matchedQuantity.add(cgi.getQuantity());
                    Line line = new Line();
                    line.aName = account.getName();
                    line.sName = t.getSecurityName();
                    line.quantity = qpFormat.format(cgi.getQuantity());
                    Transaction matchT = cgi.getMatchTransaction();
                    if (t.getTradeAction().equals(Transaction.TradeAction.SELL)) {
                        line.sDate = t.getTDate().toString();
                        line.bDate = matchT.getTDate().toString();
                    } else {
                        line.sDate = matchT.getTDate().toString();
                        line.bDate = t.getTDate().toString();
                    }
                    line.proceeds = dcFormat.format(cgi.getProceeds());
                    line.costBasis = dcFormat.format(cgi.getCostBasis());
                    line.realizedGL = dcFormat.format(cgi.getProceeds().subtract(cgi.getCostBasis()));
                    if (cgi.isShortTerm()) {
                        detailSTGLines.add(line);
                        if (transactionSTG == null) {
                            transactionSTG = new CapitalGainItem(cgi);
                        } else {
                            if ((transactionSTG.getMatchTransaction() != null) &&
                                    !transactionSTG.getMatchTransaction().getTDate().equals(
                                            cgi.getMatchTransaction().getTDate())) {
                                // matching transactions on multiple days, set to null
                                transactionSTG.setMatchTransaction(null);
                            }
                            transactionSTG.setCostBasis(transactionSTG.getCostBasis().add(cgi.getCostBasis()));
                            transactionSTG.setProceeds(transactionSTG.getProceeds().add(cgi.getProceeds()));
                        }

                        totalSTCostBasis = totalSTCostBasis.add(cgi.getCostBasis());
                        totalSTProceeds = totalSTProceeds.add(cgi.getProceeds());
                    } else {
                        detailLTGLines.add(line);
                        if (transactionLTG == null) {
                            transactionLTG = new CapitalGainItem(cgi);
                        } else {
                            if ((transactionLTG.getMatchTransaction() != null) &&
                                    !transactionLTG.getMatchTransaction().getTDate().equals(
                                            cgi.getMatchTransaction().getTDate())) {
                                // matching transactions on multiple days, set to null
                                transactionLTG.setMatchTransaction(null);
                            }
                            transactionLTG.setCostBasis(transactionLTG.getCostBasis().add(cgi.getCostBasis()));
                            transactionLTG.setProceeds(transactionLTG.getProceeds().add(cgi.getProceeds()));
                        }

                        totalLTCostBasis = totalLTCostBasis.add(cgi.getCostBasis());
                        totalLTProceeds = totalLTProceeds.add(cgi.getProceeds());
                    }
                }
                if (transactionSTG != null) {
                    Line line = new Line();
                    line.aName = account.getName();
                    line.sName = t.getSecurityName();
                    line.quantity = qpFormat.format(t.getQuantity());
                    if (t.getTradeAction().equals(Transaction.TradeAction.SELL)) {
                        line.sDate = t.getTDate().toString();
                        if (transactionSTG.getMatchTransaction() == null)
                            line.bDate = "Various";
                        else
                            line.bDate = transactionSTG.getMatchTransaction().getTDate().toString();
                    } else {
                        line.bDate = t.getTDate().toString();
                        if (transactionSTG.getMatchTransaction() == null)
                            line.sDate = "Various";
                        else
                            line.sDate = transactionSTG.getMatchTransaction().getTDate().toString();
                    }
                    line.costBasis = dcFormat.format(transactionSTG.getCostBasis());
                    line.proceeds = dcFormat.format(transactionSTG.getProceeds());
                    line.realizedGL =
                            dcFormat.format(transactionSTG.getProceeds().subtract(transactionSTG.getCostBasis()));
                    transactionSTGLines.add(line);
                }
                if (transactionLTG != null) {
                    Line line = new Line();
                    line.aName = account.getName();
                    line.sName = t.getSecurityName();
                    line.quantity = qpFormat.format(t.getQuantity());
                    if (t.getTradeAction().equals(Transaction.TradeAction.SELL)) {
                        line.sDate = t.getTDate().toString();
                        if (transactionLTG.getMatchTransaction() == null)
                            line.bDate = "Various";
                        else
                            line.bDate = transactionLTG.getMatchTransaction().getTDate().toString();
                    } else {
                        line.bDate = t.getTDate().toString();
                        if (transactionLTG.getMatchTransaction() == null)
                            line.sDate = "Various";
                        else
                            line.sDate = transactionLTG.getMatchTransaction().getTDate().toString();
                    }
                    line.costBasis = dcFormat.format(transactionLTG.getCostBasis());
                    line.proceeds = dcFormat.format(transactionLTG.getProceeds());
                    line.realizedGL =
                            dcFormat.format(transactionLTG.getProceeds().subtract(transactionLTG.getCostBasis()));
                    transactionLTGLines.add(line);
                }
                if (!matchedQuantity.equals(t.getQuantity())) {
                    errMsgs.add(account.getName() + " " + t.getTradeAction() + " "
                            + t.getQuantity() + ", matched " + matchedQuantity + ". Difference = "
                            + t.getQuantity().subtract(matchedQuantity));
                }
            }
        }

        Line totalSTGLine = new Line();
        Line totalLTGLine = new Line();
        Line totalLine = new Line();
        totalSTGLine.aName = "Overall";
        totalSTGLine.sName = "Short Term";
        totalSTGLine.costBasis = dcFormat.format(totalSTCostBasis);
        totalSTGLine.proceeds = dcFormat.format(totalSTProceeds);
        totalSTGLine.realizedGL = dcFormat.format(totalSTProceeds.subtract(totalSTCostBasis));

        totalLTGLine.aName = "Overall";
        totalLTGLine.sName = "Long Term";
        totalLTGLine.costBasis = dcFormat.format(totalLTCostBasis);
        totalLTGLine.proceeds = dcFormat.format(totalLTProceeds);
        totalLTGLine.realizedGL = dcFormat.format(totalLTProceeds.subtract(totalLTCostBasis));

        totalLine.aName = "Overall";
        totalLine.costBasis = dcFormat.format(totalLTCostBasis.add(totalSTCostBasis));
        totalLine.proceeds = dcFormat.format(totalLTProceeds.add(totalSTProceeds));
        totalLine.realizedGL = dcFormat.format(totalLTProceeds.subtract(totalLTCostBasis)
                .add(totalSTProceeds).subtract(totalSTCostBasis));

        final Line total = new Line();
        total.aName = "Total";

        int aNameLen = 12;
        int sNameLen = 24;
        int quantityLen = 16;
        int bDateLen = 11;
        int sDateLen = 11;
        int proceedsLen = 10;
        int costBasisLen = 10;
        int realizedGLLen = 10;

        List<Line> allLines = new ArrayList<>();
        allLines.add(title);
        allLines.addAll(detailSTGLines);
        allLines.addAll(detailLTGLines);
        allLines.addAll(transactionSTGLines);
        allLines.addAll(transactionLTGLines);
        allLines.add(totalSTGLine);
        allLines.add(totalLTGLine);
        allLines.add(totalLine);

        for (Line line : allLines) {
            aNameLen = Math.max(line.aName.length(), aNameLen);
            sNameLen = Math.max(line.sName.length(), sNameLen);
            quantityLen = Math.max(line.quantity.length(), quantityLen);
            bDateLen = Math.max(line.bDate.length(), bDateLen);
            sDateLen = Math.max(line.sDate.length(), sDateLen);
            proceedsLen = Math.max(line.proceeds.length(), proceedsLen);
            costBasisLen = Math.max(line.costBasis.length(), costBasisLen);
            realizedGLLen = Math.max(line.realizedGL.length(), realizedGLLen);
        }

        int gap = 2;
        final String formatStr = "%-" + aNameLen + "s" // left adjust
                + "%-" + (gap+sNameLen) + "s"
                + "%" + (gap+quantityLen) + "s"
                + "%" + (gap+bDateLen) + "s"
                + "%" + (gap+sDateLen) + "s"
                + "%" + (gap+proceedsLen) + "s"
                + "%" + (gap+costBasisLen) + "s"
                + "%" + (gap+realizedGLLen) + "s"
                + "\n";

        final String s0 = new String(new char[aNameLen + (gap+sNameLen)
                + (gap+quantityLen) + (gap+bDateLen) + (gap+sDateLen) + (gap+proceedsLen)
                + (gap+costBasisLen) + (gap+realizedGLLen)]).replace("\0", "=") + "\n";
        final String s1 = s0.replace("=", "-");

        StringBuilder reportSB = new StringBuilder("Capital Gains Report from ")
                .append(mSetting.getStartDate()).append(" to ").append(mSetting.getEndDate()).append("\n")
                .append("Generated on ").append(LocalDate.now()).append("\n");

        if (!errMsgs.isEmpty()) {
            reportSB.append("\n*** Start of Error Messages ***\n");
            for (String s : errMsgs)
                reportSB.append(s).append("\n");
            reportSB.append("*** End of Error Messages ***\n");
        }

        reportSB.append("\n").append(title.format(formatStr)).append(s0);
        if (!detailSTGLines.isEmpty())
            reportSB.append(totalSTGLine.format(formatStr));
        if (!detailLTGLines.isEmpty())
            reportSB.append(totalLTGLine.format(formatStr));
        reportSB.append(s1).append(totalLine.format(formatStr));

        reportSB.append("\n\n").append(title.format(formatStr)).append(s0);
        if (!detailSTGLines.isEmpty()) {
            reportSB.append("Short Term (by Transaction)\n").append(s1);
            for (Line l : transactionSTGLines) {
                reportSB.append(l.format(formatStr));
            }
            reportSB.append(s1).append(totalSTGLine.format(formatStr));
        }

        if (!detailLTGLines.isEmpty()) {
            reportSB.append("\nLong Term (by Transaction)\n").append(s1);
            for (Line l : transactionLTGLines) {
                reportSB.append(l.format(formatStr));
            }
            reportSB.append(s1).append(totalLTGLine.format(formatStr));
        }

        reportSB.append("\n\n").append(title.format(formatStr)).append(s0);
        if (!detailSTGLines.isEmpty()) {
            reportSB.append("Short Term (Lot Matching Details)\n").append(s1);
            for (Line l : detailSTGLines) {
                reportSB.append(l.format(formatStr));
            }
            reportSB.append(s1).append(totalSTGLine.format(formatStr));
        }

        if (!detailLTGLines.isEmpty()) {
            reportSB.append("\nLong Term (Lot Matching Details)\n").append(s1);
            for (Line l : detailLTGLines) {
                reportSB.append(l.format(formatStr));
            }
            reportSB.append(s1).append(totalLTGLine.format(formatStr));
        }

        return reportSB.toString();
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
        Set<String> securityNameSet = mapSecurityIDSetToNameSet(mSetting.getSelectedSecurityIDSet());

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
        final Pattern payeePattern = mSetting.getPayeeContains().isEmpty() ?
                null : Pattern.compile(mSetting.getPayeeRegEx() ?
                mSetting.getPayeeContains() : "(?i)" + Pattern.quote(mSetting.getPayeeContains()));
        final Pattern memoPattern = mSetting.getMemoContains().isEmpty() ?
                null : Pattern.compile(mSetting.getMemoRegEx() ?
                mSetting.getMemoContains() : "(?i)" + Pattern.quote(mSetting.getMemoContains()));
        for (Account account : mSetting.getSelectedAccountList(mainModel)) {
            for (Transaction t : account.getTransactionList()) {
                LocalDate tDate = t.getTDate();
                if (tDate.isBefore(mSetting.getStartDate()))
                    continue;
                if (tDate.isAfter(mSetting.getEndDate()))
                    break; // we are done with this account

                final String sName = t.getSecurityName().isEmpty() ? NOSECURITY : t.getSecurityName();
                if (mSetting.getSelectedCategoryIDSet().contains(t.getCategoryID())
                        && securityNameSet.contains(sName)
                        && ((payeePattern == null) || payeePattern.matcher(t.getPayee()).find())
                        && ((memoPattern == null) || memoPattern.matcher(t.getMemo()).find())) {
                    Line line = new Line();
                    line.date = tDate.toString();
                    line.aName = account.getName();
                    if (account.getType().isGroup(Account.Type.Group.INVESTING))
                        line.num = t.getTradeAction().name();
                    else
                        line.num = t.getReference() == null ? "" : t.getReference();
                    line.memo = t.getMemo() == null ? "" : t.getMemo();
                    line.category = mainModel.getCategory(c -> c.getID() == t.getCategoryID()).map(Category::getName)
                            .orElse(mainModel.getAccount(a -> a.getID() == -t.getCategoryID())
                                    .map(a -> "[" + a.getName() + "]").orElse(""));
                    BigDecimal amount;
                    if (account.getType().isGroup(Account.Type.Group.INVESTING)) {
                        line.desc = t.getSecurityName() == null ? "" : t.getSecurityName();
                        amount = t.getCashAmount();
                    } else {
                        line.desc = t.getPayee() == null ? "" : t.getPayee();
                        amount = t.getDepositProperty().get().subtract(t.getPaymentProperty().get());
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

    private String NAVReport() throws DaoException {
        final LocalDate date = mSetting.getEndDate();
        StringBuilder outputStr = new StringBuilder("NAV Report as of " + date + "\n\n");

        BigDecimal total = BigDecimal.ZERO;
        String separator0 = new String(new char[90]).replace("\0", "-");
        String separator1 = new String(new char[90]).replace("\0", "=");
        final DecimalFormat dcFormat = new DecimalFormat("#,##0.00"); // formatter for dollar & cents
        final DecimalFormat qpFormat = new DecimalFormat("#,##0.000"); // formatter for quantity and price

        for (Account account : mSetting.getSelectedAccountList(mainModel)) {
            List<SecurityHolding> shList = mainModel.computeSecurityHoldings(account.getTransactionList(),
                    date, -1);
            int shListLen = shList.size();

            // aggregate total
            total = total.add(shList.get(shListLen-1).getMarketValue());

            // print account total
            outputStr.append(String.format("%-55s%35s\n", account.getName(),
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

        // bind the visibility of mRightTilePane to either mDatesTab and mTextTab is not visible
        mRightTilePane.visibleProperty().bind(Bindings.createBooleanBinding(()
                        -> (mTabPane.getSelectionModel().getSelectedItem() != mDatesTab) &&
                        (mTabPane.getSelectionModel().getSelectedItem() != mTextMatchTab),
                mTabPane.getSelectionModel().selectedItemProperty()));

        // the javafx DatePicker isn't aware of the edited value of its own text field.
        // DatePickerUtil.CaptureEditedDate is a work around for it.
        DatePickerUtil.captureEditedDate(mStartDatePicker);
        DatePickerUtil.captureEditedDate(mEndDatePicker);
    }
}
