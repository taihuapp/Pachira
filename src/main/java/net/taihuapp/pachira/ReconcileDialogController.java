/*
 * Copyright (C) 2018-2019.  Guangliang He.  All Rights Reserved.
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

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.BigDecimalStringConverter;
import net.taihuapp.pachira.net.taihuapp.pachira.dc.AccountDC;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ReconcileDialogController {

    static class ReconcileTransactionTableView extends TransactionTableView {
        @Override
        final void setColumnVisibility() {
            for (TableColumn<Transaction, ?> tc : Arrays.asList(
                    mTransactionAccountColumn,
                    mTransactionDescriptionColumn,
                    mTransactionInvestAmountColumn,
                    mTransactionMemoColumn,
                    mTransactionCashAmountColumn,
                    mTransactionPaymentColumn,
                    mTransactionDepositColumn,
                    mTransactionBalanceColumn
            )) {
                tc.setVisible(false);
            }
        }

        @Override
        final void setColumnSortability() {}  // all columns remains sortable

        ReconcileTransactionTableView(MainApp mainApp, ObservableList<Transaction> tList) {
            super(mainApp, tList);
        }
    }

    static class SecurityBalance {
        final StringProperty mNameProperty = new SimpleStringProperty("");
        final ObjectProperty<BigDecimal> mOpeningBalanceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
        final ObjectProperty<BigDecimal> mClearedBalanceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
        final ObjectProperty<BigDecimal> mEndingBalanceProperty = new SimpleObjectProperty<>(null);
        final ObjectProperty<BigDecimal> mBalanceDifferenceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);

        String getName() { return getNameProperty().get(); }
        StringProperty getNameProperty() { return mNameProperty; }

        ObjectProperty<BigDecimal> getOpeningBalanceProperty() { return mOpeningBalanceProperty; }
        BigDecimal getOpeningBalance() { return getOpeningBalanceProperty().get(); }
        void setOpeningBalance(BigDecimal b) { getOpeningBalanceProperty().set(b); }

        ObjectProperty<BigDecimal> getClearedBalanceProperty() { return mClearedBalanceProperty; }
        BigDecimal getClearedBalance() { return getClearedBalanceProperty().get(); }
        void setClearedBalance(BigDecimal b) { getClearedBalanceProperty().set(b); }

        ObjectProperty<BigDecimal> getEndingBalanceProperty() { return mEndingBalanceProperty; }
        BigDecimal getEndingBalance() { return getEndingBalanceProperty().get(); }

        ObjectProperty<BigDecimal> getBalanceDifferenceProperty() { return mBalanceDifferenceProperty; }
        BigDecimal getBalanceDifference() { return getBalanceDifferenceProperty().get(); }

        SecurityBalance(String n) {
            getNameProperty().set(n);
            getBalanceDifferenceProperty().bind(Bindings.createObjectBinding(this::computeDiff,
                    getOpeningBalanceProperty(), getEndingBalanceProperty(), getClearedBalanceProperty()));
        }

        private BigDecimal computeDiff() {
            BigDecimal ob = getOpeningBalance();
            BigDecimal cb = getClearedBalance(); // should not be null
            BigDecimal eb = getEndingBalance();  // initially null
            return (eb == null) ? null : ob.add(cb).subtract(eb);
        }
    }

    private MainApp mMainApp;
    private Stage mStage;

    @FXML
    private VBox mVBox;
    @FXML
    private Label mPrevDateLabel;
    @FXML
    private DatePicker mEndDatePicker;
    @FXML
    private TableView<SecurityBalance> mSecurityBalanceTableView;
    @FXML
    private TableColumn<SecurityBalance, String> mSecurityNameTableColumn;
    @FXML
    private TableColumn<SecurityBalance, BigDecimal> mOpeningBalanceTableColumn;
    @FXML
    private TableColumn<SecurityBalance, BigDecimal> mClearedBalanceTableColumn;
    @FXML
    private TableColumn<SecurityBalance, BigDecimal> mEndingBalanceTableColumn;
    @FXML
    private TableColumn<SecurityBalance, BigDecimal> mBalanceDifferenceTableColumn;
    @FXML
    private CheckBox mUseDownloadCheckBox;

    private LocalDate mLastDownloadDate = null;
    private BigDecimal mDownloadedLedgeBalance = null;

    private ReconcileTransactionTableView mTransactionTableView;
    private Map<Integer, Transaction.Status> mOriginalStatusMap = new HashMap<>();

    // Mark all unreconciled transaction as cleared
    private void handleMarkAll() {
        mTransactionTableView.getItems().forEach(t -> t.setStatus(Transaction.Status.CLEARED));
    }

    private void handleFinish() {
        Account account = mMainApp.getCurrentAccount();
        LocalDate d = mEndDatePicker.getValue();
        if (mMainApp.reconcileAccountToDB(account, d)) {
            // successful update database, now update in memory.
            // we can't loop through a filteredlist and change status, so we have to
            // copy the elements over to a new list and loop through the new list
            new ArrayList<>(account.getTransactionList().filtered(t -> t.getStatus()
                    .equals(Transaction.Status.CLEARED)))
                    .forEach(t -> t.setStatus(Transaction.Status.RECONCILED));
            account.setLastReconcileDate(d);
        } else {
            MainApp.showWarningDialog("Database Error", "Failed to update transaction status in database",
                    "Please check LOG for more details");
        }
        mStage.close();
    }

    void handleCancel() {
        mTransactionTableView.getItems().forEach(t -> t.setStatus(mOriginalStatusMap.get(t.getID())));
        mStage.close();
    }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mStage = stage;

        Account account = mMainApp.getCurrentAccount();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
        LocalDate lastReconcileDate = account.getLastReconcileDate();
        if (lastReconcileDate == null) {
            mPrevDateLabel.setText("NA");
            mEndDatePicker.setValue(LocalDate.now());
        } else {
            mPrevDateLabel.setText(formatter.format(lastReconcileDate));
            mEndDatePicker.setValue(lastReconcileDate.plusMonths(1));
        }

        // process transaction list and calculate balances
        // make sure we are observe STATUS property
        ObservableList<Transaction> transactionList = FXCollections.observableArrayList(t
                -> new Observable[] {t.getStatusProperty()});
        transactionList.addAll(account.getTransactionList());

        // keep original Status in case we want to undo
        for (Transaction t : transactionList) {
            if (!t.getStatus().equals(Transaction.Status.RECONCILED)) {
                mOriginalStatusMap.put(t.getID(), t.getStatus());
            }
        }

        Set<String> securityNameSet = transactionList.stream()
                .map(Transaction::getSecurityName).filter(s -> (!s.isEmpty()))
                .distinct().collect(Collectors.toSet());
        Set<String> unreconciledSecurityNameSet = transactionList.stream()
                .filter(t -> !t.getStatus().equals(Transaction.Status.RECONCILED))
                .map(Transaction::getSecurityName).filter(s -> !s.isEmpty())
                .distinct().collect(Collectors.toSet());

        SecurityBalance cashBalance = new SecurityBalance("CASH");
        cashBalance.getOpeningBalanceProperty().bind(Bindings.createObjectBinding(()
                -> transactionList.stream().filter(t -> t.getStatus().equals(Transaction.Status.RECONCILED))
                .map(Transaction::getCashAmount).reduce(BigDecimal.ZERO, BigDecimal::add), transactionList));
        cashBalance.getClearedBalanceProperty().bind(Bindings.createObjectBinding(()
                -> transactionList.stream().filter(t->t.getStatus().equals(Transaction.Status.CLEARED))
                .map(Transaction::getCashAmount).reduce(BigDecimal.ZERO, BigDecimal::add), transactionList));

        ObservableList<SecurityBalance> sbList = FXCollections.observableArrayList(sb
                -> new Observable[] {sb.getBalanceDifferenceProperty()} );
        for (String name : securityNameSet) {
            SecurityBalance sb = new SecurityBalance(name);
            sb.getOpeningBalanceProperty().bind(Bindings.createObjectBinding(()
                    -> transactionList.stream().filter(t -> t.getStatus().equals(Transaction.Status.RECONCILED)
                    && t.getSecurityName().equals(name) && Transaction.hasQuantity(t.getTradeAction()))
                    .map(Transaction::getSignedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add), transactionList));
            if ((sb.getOpeningBalance().compareTo(BigDecimal.ZERO) != 0)
                    || unreconciledSecurityNameSet.contains(name)) {
                sb.getClearedBalanceProperty().bind(Bindings.createObjectBinding(()
                                -> transactionList.stream().filter(t -> t.getStatus().equals(Transaction.Status.CLEARED)
                                && t.getSecurityName().equals(name) && Transaction.hasQuantity(t.getTradeAction()))
                                .map(Transaction::getSignedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                        transactionList));
                sbList.add(sb);
            }
        }
        sbList.sort(Comparator.comparing(SecurityBalance::getName));
        sbList.add(cashBalance);

        mSecurityBalanceTableView.setItems(sbList);


        mTransactionTableView = new ReconcileTransactionTableView(mainApp,
                transactionList.filtered(t -> !t.getStatus().equals(Transaction.Status.RECONCILED)));
        mTransactionTableView.setRowFactory(tv -> {
            final TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) {
                    if (row.getItem().getStatus().equals(Transaction.Status.CLEARED))
                        row.getItem().setStatus(Transaction.Status.UNCLEARED);
                    else
                        row.getItem().setStatus(Transaction.Status.CLEARED);
                }
            });
            return row;
        });

        Button markAllButton = new Button("Mark All");
        Button cancelButton = new Button("Cancel");
        Button finishButton = new Button("Finish");
        finishButton.disableProperty().bind(Bindings.createBooleanBinding(()
                -> mSecurityBalanceTableView.getItems().stream().map(SecurityBalance::getBalanceDifference)
                .anyMatch(v -> (v == null) || (v.compareTo(BigDecimal.ZERO) != 0))
                || (mEndDatePicker.getValue() == null),
                mSecurityBalanceTableView.getItems(), mEndDatePicker.valueProperty()));

        markAllButton.setOnAction(e -> handleMarkAll());
        cancelButton.setOnAction(e -> handleCancel());
        finishButton.setOnAction(e -> handleFinish());

        markAllButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        cancelButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        finishButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        TilePane tilePane = new TilePane(Orientation.HORIZONTAL);
        tilePane.setPadding(new Insets(5,5,5,5));
        tilePane.setHgap(10);
        tilePane.setVgap(8);
        tilePane.getChildren().addAll(markAllButton, cancelButton, finishButton);

        mVBox.getChildren().addAll(mTransactionTableView, tilePane);

        AccountDC adc = mMainApp.getAccountDC(account.getID());
        mUseDownloadCheckBox.setDisable(true); // disable as default
        if (adc != null && adc.getLastDownloadLedgeBalance() != null) {
            // we have a good download here
            mLastDownloadDate =
                    adc.getLastDownloadDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            mDownloadedLedgeBalance = adc.getLastDownloadLedgeBalance();
            if (lastReconcileDate == null || mLastDownloadDate.compareTo(lastReconcileDate) >= 0) {
                mUseDownloadCheckBox.setDisable(false);
            }
        }
        mUseDownloadCheckBox.selectedProperty().addListener((obs, ov, nv) -> {
            if ((nv != null) && nv) {
                mEndDatePicker.setValue(mLastDownloadDate);
                for (SecurityBalance sb : mSecurityBalanceTableView.getItems()) {
                    if (sb.getName().equals("CASH"))
                        sb.mEndingBalanceProperty.set(mDownloadedLedgeBalance);
                }
            }
        });
     }

    @FXML
    private void initialize() {
        mSecurityBalanceTableView.setEditable(true);

        mSecurityNameTableColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        mOpeningBalanceTableColumn.setCellValueFactory(cd -> cd.getValue().getOpeningBalanceProperty());
        mClearedBalanceTableColumn.setCellValueFactory(cd -> cd.getValue().getClearedBalanceProperty());
        mEndingBalanceTableColumn.setCellValueFactory(cd -> cd.getValue().getEndingBalanceProperty());
        mBalanceDifferenceTableColumn.setCellValueFactory(cd -> cd.getValue().getBalanceDifferenceProperty());
        Callback<TableColumn<SecurityBalance, BigDecimal>, TableCell<SecurityBalance, BigDecimal>> converter
                = new Callback<TableColumn<SecurityBalance, BigDecimal>, TableCell<SecurityBalance,BigDecimal>>() {
            @Override
            public TableCell<SecurityBalance, BigDecimal> call(TableColumn<SecurityBalance, BigDecimal> param) {
                return new TableCell<SecurityBalance, BigDecimal>() {
                    @Override
                    protected void updateItem(BigDecimal item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null || empty) {
                            setText("");
                        } else {
                            // format
                            DecimalFormat df = new DecimalFormat();
                            df.setMaximumFractionDigits(MainApp.QUANTITY_FRACTION_DISP_LEN);
                            df.setMinimumFractionDigits(0);
                            setText(df.format(item));
                        }
                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                };
            }};
        mOpeningBalanceTableColumn.setCellFactory(converter);
        mClearedBalanceTableColumn.setCellFactory(converter);
        mBalanceDifferenceTableColumn.setCellFactory(converter);
        mEndingBalanceTableColumn.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        mEndingBalanceTableColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    }
}
