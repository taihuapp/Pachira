/*
 * Copyright (C) 2018-2024.  Guangliang He.  All Rights Reserved.
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
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ReconcileDialogController {

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

        ObjectProperty<BigDecimal> getClearedBalanceProperty() { return mClearedBalanceProperty; }
        BigDecimal getClearedBalance() { return getClearedBalanceProperty().get(); }

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

    private static final Logger logger = LogManager.getLogger(ReconcileDialogController.class);

    private MainModel mainModel;

    private final ObservableList<Transaction> transactionList = FXCollections.observableArrayList();
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

    private TransactionTableView mTransactionTableView;
    private final Map<Integer, Transaction.Status> mOriginalStatusMap = new HashMap<>();

    // Mark all unreconciled transaction as cleared
    private void handleMarkAll() {
        mTransactionTableView.getItems().forEach(t -> {
            if (!t.getTDate().isAfter(mEndDatePicker.getValue()))
                t.setStatus(Transaction.Status.CLEARED);
        });
        updateClearedBalance();
    }

    private void handleFinish() {
        Stage stage = (Stage) mVBox.getScene().getWindow();
        LocalDate d = mEndDatePicker.getValue();
        Account account = mainModel.getCurrentAccount();
        try {
            mainModel.reconcileCurrentAccount(d);
        } catch (ModelException e) {
            final String msg = "ModelException " + e.getErrorCode() + " when reconcile account " + account.getName();
            logger.error(msg);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
        }

        stage.close();
    }

    void handleCancel() {
        mTransactionTableView.getItems().forEach(t -> t.setStatus(mOriginalStatusMap.get(t.getID())));
        ((Stage) mVBox.getScene().getWindow()).close();
    }

    // update the cleared balance for the items in the TableView.
    // Obviously, it should be called after the table is populated.
    private void updateClearedBalance() {
        try {
            final List<SecurityHolding> holdings = mainModel.computeSecurityHoldings(transactionList
                    .filtered(t -> !t.getStatus().equals(Transaction.Status.UNCLEARED)), LocalDate.MAX, -1);
            for (SecurityBalance sb : mSecurityBalanceTableView.getItems()) {
                if (sb.getName().equals(SecurityHolding.CASH)) {
                    // cash doesn't have quantity, get market value
                    sb.getClearedBalanceProperty().set(holdings.stream()
                            .filter(h -> h.getSecurityName().equals(sb.getName())).findAny()
                            .map(SecurityHolding::getCostBasis).orElse(BigDecimal.ZERO)
                            .subtract(sb.getOpeningBalance()));
                } else {
                    sb.getClearedBalanceProperty().set(holdings.stream()
                            .filter(h -> h.getSecurityName().equals(sb.getName())).findAny()
                            .map(SecurityHolding::getQuantity).orElse(BigDecimal.ZERO)
                            .subtract(sb.getOpeningBalance()));
                }
            }
        } catch (ModelException e) {
            logger.error("{} DaoException", e.getErrorCode(), e);
            throw new RuntimeException(e);
        }
    }

    void setMainModel(MainModel mainModel) throws ModelException {
        this.mainModel = mainModel;

        final Account account = mainModel.getCurrentAccount();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
        final LocalDate lastReconcileDate = account.getLastReconcileDate();
        if (lastReconcileDate == null) {
            mPrevDateLabel.setText("NA");
            mEndDatePicker.setValue(LocalDate.now());
        } else {
            mPrevDateLabel.setText(formatter.format(lastReconcileDate));
            mEndDatePicker.setValue(lastReconcileDate.plusMonths(1));
        }

        // process transaction list and calculate balances
        // make sure we are observing STATUS property
        transactionList.addAll(mainModel.getCurrentAccountTransactionList());

        // keep original Status in case we want to undo
        for (Transaction t : transactionList) {
            if (!t.getStatus().equals(Transaction.Status.RECONCILED)) {
                mOriginalStatusMap.put(t.getID(), t.getStatus());
            }
        }

        // count all RECONCILED transactions as opening position
        final List<Transaction> reconciledTransactionList = transactionList
                .filtered(t -> t.getStatus().equals(Transaction.Status.RECONCILED));
        final List<SecurityHolding> reconciledHoldings;
        reconciledHoldings = mainModel
                .computeSecurityHoldings(reconciledTransactionList, LocalDate.MAX, -1).stream()
                .filter(h -> !h.getLabel().equals(SecurityHolding.TOTAL)) // exclude TOTAL
                .collect(Collectors.toList());

        final Set<String> reconciledSecurityNameSet = reconciledHoldings.stream()
                .filter(h -> !h.getLabel().equals(SecurityHolding.CASH)) // exclude CASH
                .map(SecurityHolding::getSecurityName).collect(Collectors.toSet());

        final Set<Integer> unreconciledSecurityIDSet = transactionList.stream()
                .filter(t -> !t.getStatus().equals(Transaction.Status.RECONCILED))
                .map(Transaction::getSecurityID).filter(i -> i > 0).collect(Collectors.toSet());
        final Set<String> unreconciledSecurityNameSet = new HashSet<>();
        for (Integer id : unreconciledSecurityIDSet) {
            unreconciledSecurityNameSet.add(mainModel.getSecurity(id).map(Security::getName)
                    .orElseThrow(() -> new ModelException(ModelException.ErrorCode.INVALID_SECURITY,
                            "Cannot find security " + id, null)));
        }

        final SecurityBalance cashBalance = new SecurityBalance("CASH");
        cashBalance.getOpeningBalanceProperty().set(BigDecimal.ZERO);
        cashBalance.getClearedBalanceProperty().set(BigDecimal.ZERO);
        final ObservableList<SecurityBalance> sbList = FXCollections.observableArrayList(sb
                -> new Observable[] {sb.getBalanceDifferenceProperty()} );
        for (SecurityHolding sh : reconciledHoldings) {
            if (sh.getSecurityName().equals(SecurityHolding.CASH)) {
                cashBalance.getOpeningBalanceProperty().set(sh.getMarketValue());
            } else {
                final SecurityBalance sb = new SecurityBalance(sh.getSecurityName());
                sb.getOpeningBalanceProperty().set(sh.getQuantity());
                sbList.add(sb);
            }
        }
        // loop through names in unreconciledSecurityNameSet but not in reconciledSecurityNameSet
        for (String name : unreconciledSecurityNameSet.stream().filter(s -> !reconciledSecurityNameSet.contains(s))
                .collect(Collectors.toSet())) {
            final SecurityBalance sb = new SecurityBalance(name);
            sb.getOpeningBalanceProperty().set(BigDecimal.ZERO);
            sb.getClearedBalanceProperty().set(BigDecimal.ZERO);
            sbList.add(sb);
        }

        sbList.sort(Comparator.comparing(SecurityBalance::getName));
        sbList.add(cashBalance);

        mSecurityBalanceTableView.setItems(sbList);

        // calculated initial cleared balance
        updateClearedBalance();

        mTransactionTableView = new TransactionTableView(mainModel,
                transactionList.filtered(t -> !t.getStatus().equals(Transaction.Status.RECONCILED)));

        for (TableColumn<Transaction, ?> tc : Arrays.asList(
                mTransactionTableView.mTransactionAccountColumn,
                mTransactionTableView.mTransactionDescriptionColumn,
                mTransactionTableView.mTransactionInvestAmountColumn,
                mTransactionTableView.mTransactionMemoColumn,
                mTransactionTableView.mTransactionCashAmountColumn,
                mTransactionTableView.mTransactionPaymentColumn,
                mTransactionTableView.mTransactionDepositColumn,
                mTransactionTableView.mTransactionBalanceColumn
        )) {
            tc.setVisible(false);
        }
        for (TableColumn<Transaction, ?> tc : Arrays.asList(
                mTransactionTableView.mTransactionSecurityNameColumn,
                mTransactionTableView.mTransactionQuantityColumn
        )) {
            tc.setVisible(account.getType().isGroup(Account.Type.Group.INVESTING));
        }

        for (TableColumn<Transaction, ?> tc : Arrays.asList(
                mTransactionTableView.mTransactionReferenceColumn,
                mTransactionTableView.mTransactionPayeeColumn,
                mTransactionTableView.mTransactionCategoryColumn
        )) {
            tc.setVisible(!account.getType().isGroup(Account.Type.Group.INVESTING));
        }


        Callback<TableView<Transaction>, TableRow<Transaction>> callback
                = mTransactionTableView.getRowFactory();
        mTransactionTableView.setRowFactory(tv -> {
            final TableRow<Transaction> row = callback.call(tv);
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) {
                    if (row.getItem().getStatus().equals(Transaction.Status.CLEARED))
                        row.getItem().setStatus(Transaction.Status.UNCLEARED);
                    else
                        row.getItem().setStatus(Transaction.Status.CLEARED);
                    updateClearedBalance();
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

        try {
            AccountDC adc = mainModel.getAccountDC(account.getID()).orElse(null);
            mUseDownloadCheckBox.setDisable(true); // disable as default
            if (adc != null && adc.getLastDownloadLedgeBalance() != null) {
                // we have a good download here
                mLastDownloadDate =
                        adc.getLastDownloadDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                mDownloadedLedgeBalance = adc.getLastDownloadLedgeBalance();
                if (lastReconcileDate == null || !mLastDownloadDate.isBefore(lastReconcileDate)) {
                    mUseDownloadCheckBox.setDisable(false);
                }
            }
        } catch (DaoException e) {
            Stage stage = (Stage) mVBox.getScene().getWindow();
            final String msg = "DaoException " + e.getErrorCode() + " on getAccountDC(" + account.getID() + ")";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
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
                = new Callback<>() {
            @Override
            public TableCell<SecurityBalance, BigDecimal> call(TableColumn<SecurityBalance, BigDecimal> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(BigDecimal item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null || empty) {
                            setText("");
                        } else {
                            // format
                            setText(ConverterUtil.getPriceQuantityFormatInstance().format(item));
                        }
                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                };
            }
        };
        mOpeningBalanceTableColumn.setCellFactory(converter);
        mClearedBalanceTableColumn.setCellFactory(converter);
        mBalanceDifferenceTableColumn.setCellFactory(converter);
        mEndingBalanceTableColumn.setCellFactory(cell -> new EditableTableCell<>(
                ConverterUtil.getPriceQuantityStringConverterInstance(),
                c -> RegExUtil.getPriceQuantityInputRegEx(true)
                                .matcher(c.getControlNewText()).matches() ? c : null));
        mEndingBalanceTableColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        // javafx DatePicker is not aware of edited value in its TextField
        // this is a work around
        DatePickerUtil.captureEditedDate(mEndDatePicker);
    }
}
