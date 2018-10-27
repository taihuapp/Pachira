/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
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

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.log4j.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.math.BigDecimal;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    private static final Logger mLogger = Logger.getLogger(MainController.class);

    private MainApp mMainApp;
    private final ChangeListener<TreeItem<Account>> mSelectedTreeItemChangeListener = (obs, ov, nv) -> {
        if (nv != null && nv.getValue().getID() >= MainApp.MIN_ACCOUNT_ID) {
            showAccountTransactions(nv.getValue());
        }
    };

    @FXML
    private Menu mRecentDBMenu;
    @FXML
    private Menu mEditMenu;
    @FXML
    private Menu mOFXMenu;
    @FXML
    private MenuItem mCreateMasterPasswordMenuItem;
    @FXML
    private MenuItem mUpdateMasterPasswordMenuItem;
    @FXML
    private MenuItem mDeleteMasterPasswordMenuItem;
    @FXML
    private MenuItem mDirectConnectionMenuItem;
    @FXML
    private Menu mReportsMenu;
    @FXML
    private Menu mSavedReportsMenu;
    @FXML
    private MenuItem mChangePasswordMenuItem;
    @FXML
    private MenuItem mBackupMenuItem;
    @FXML
    private MenuItem mImportQIFMenuItem;
    @FXML
    private MenuItem mFixDBMenuItem;
    @FXML
    private TreeTableView<Account> mAccountTreeTableView;
    @FXML
    private TreeTableColumn<Account, String> mAccountNameTreeTableColumn;
    @FXML
    private TreeTableColumn<Account, BigDecimal> mAccountBalanceTreeTableColumn;

    @FXML
    private VBox mTransactionVBox;

    @FXML
    private Label mTransactionAccountNameLabel;
    @FXML
    private Button mEnterTransactionButton;
    @FXML
    private Button mTransactionShowHoldingsButton;

    @FXML
    private TableView<Transaction> mTransactionTableView;
    @FXML
    private TableColumn<Transaction, Transaction.Status> mTransactionStatusColumn;
    @FXML
    private TableColumn<Transaction, LocalDate> mTransactionDateColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionReferenceColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionPayeeColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionMemoColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionCategoryColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionTagColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionPaymentColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionDepositColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionBalanceColumn;

    @FXML
    private TableColumn<Transaction, Transaction.TradeAction> mTransactionTradeActionColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionSecurityNameColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionDescriptionColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionInvestAmountColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionCashAmountColumn;
    @FXML
    private Button mSearchButton;
    @FXML
    private TextField mSearchTextField;

    void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;
        updateRecentMenu();
        updateUI(mMainApp.isConnected());

        mCreateMasterPasswordMenuItem.visibleProperty().bind(mMainApp.hasMasterPasswordProperty().not());
        mUpdateMasterPasswordMenuItem.visibleProperty().bind(mMainApp.hasMasterPasswordProperty());
        mDeleteMasterPasswordMenuItem.visibleProperty().bind(mMainApp.hasMasterPasswordProperty());
        mDirectConnectionMenuItem.visibleProperty().bind(mMainApp.hasMasterPasswordProperty());

        if (mMainApp.getAcknowledgeTimeStamp() == null)
            mMainApp.showSplashScreen(true);
    }

    private void populateTreeTable() {
        final Account rootAccount = new Account(-1, null, "Total", "Placeholder for total asset",
                false, -1, null, BigDecimal.ZERO);
        final TreeItem<Account> root = new TreeItem<>(rootAccount);
        root.setExpanded(true);
        mAccountTreeTableView.setRoot(root);

        ObservableList<Account> groupAccountList = FXCollections.observableArrayList(account ->
                new Observable[] {account.getCurrentBalanceProperty()});
        for (Account.Type t : Account.Type.values()) {
            Account groupAccount = new Account(-1, t, t.toString(),"Placeholder for " + t.toString(),
                    false, -1, null, BigDecimal.ZERO);
            groupAccountList.add(groupAccount);
            TreeItem<Account> typeNode = new TreeItem<>(groupAccount);
            typeNode.setExpanded(true);
            final ObservableList<Account> accountList = mMainApp.getAccountList(t, false, true);
            for (Account a : accountList) {
                typeNode.getChildren().add(new TreeItem<>(a));
            }
            if (!accountList.isEmpty())
                root.getChildren().add(typeNode);
            ListChangeListener<Account> accountListChangeListener = c -> {
                while (c.next()) {
                    if (c.wasAdded() || c.wasRemoved() || c.wasPermutated()) {
                        ReadOnlyObjectProperty<TreeItem<Account>> selectedItemProperty =
                                mAccountTreeTableView.getSelectionModel().selectedItemProperty();
                        // save the original selectedItem
                        Account selectedAccount = null;
                        if (selectedItemProperty.get() != null)
                            selectedAccount = selectedItemProperty.get().getValue();
                        selectedItemProperty.removeListener(mSelectedTreeItemChangeListener); // remove listener for now

                        // rebuild children of the typeNode
                        typeNode.getChildren().clear();
                        for (Account a : accountList) {
                            typeNode.getChildren().add(new TreeItem<>(a));
                        }

                        if (typeNode.getChildren().isEmpty()) {
                            // remove typeNode if it is empty
                            root.getChildren().remove(typeNode);
                        } else {
                            // not empty, add to the right spot under root
                            boolean added = false;
                            for (int i = 0; i < root.getChildren().size(); i++) {
                                Account.Type type = root.getChildren().get(i).getValue().getType();
                                if (type == typeNode.getValue().getType()) {
                                    // already added
                                    added = true;
                                    break;
                                } else if (type.ordinal() > typeNode.getValue().getType().ordinal()) {
                                    // typeNode is not in, add now
                                    root.getChildren().add(i, typeNode);
                                    added = true;
                                    break;
                                }
                            }
                            if (!added)
                                root.getChildren().add(typeNode);
                        }

                        boolean hasNewSelection = false;
                        if (selectedAccount != null) {
                            // we had a selection, check further
                            for (TreeItem<Account> groupNode : root.getChildren()) {
                                if (groupNode.getValue().getType() == selectedAccount.getType()) {
                                    // the original selection was in the type group
                                    for (TreeItem<Account> accountNode : groupNode.getChildren()) {
                                        if (accountNode.getValue().getID() == selectedAccount.getID()) {
                                            mAccountTreeTableView.getSelectionModel().select(accountNode);
                                            hasNewSelection = true;
                                        }
                                    }
                                }
                            }
                        }
                        // add back the listener
                        mAccountTreeTableView.getSelectionModel().selectedItemProperty()
                                .addListener(mSelectedTreeItemChangeListener);
                        if (!hasNewSelection) {
                            // clear Selection here to make sure changeListener is called with
                            // null account.
                            mAccountTreeTableView.getSelectionModel().clearSelection();
                        }
                    }
                }
            };

            accountList.addListener(accountListChangeListener);
            groupAccount.getCurrentBalanceProperty().bind(Bindings.createObjectBinding(() ->
                            accountList.stream().map(Account::getCurrentBalance)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add), accountList));
        }
        rootAccount.getCurrentBalanceProperty().bind(Bindings.createObjectBinding(() ->
                groupAccountList.stream().map(Account::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add),
                groupAccountList));
    }

    @FXML
    private void createVaultMasterPassword() {
        setupVaultMasterPassword(false);
    }

    @FXML
    private void updateVaultMasterPassword() {
        setupVaultMasterPassword(true);
    }

    @FXML
    private void deleteVaultMasterPassword() {
        if (!MainApp.showConfirmationDialog("Delete Master Password",
                "Delete Master Password will also delete all encrypted Direct Connection info!",
                "Do you want to delete master password?")) {
                return;  // user choose not to continue
        }

        try {
            mMainApp.deleteMasterPassword();
            mMainApp.showInformationDialog("Delete Master Password",
                    "Delete Master Password Successful",
                    "Master password successfully deleted");
        } catch (KeyStoreException e) {
            mLogger.error("KeyStore exception", e);
            mMainApp.showExceptionDialog("Exception", "KeyStore Exception", e.getMessage(), e);
        } catch (SQLException e) {
            mLogger.error("Database Exception", e);
            mMainApp.showExceptionDialog("Exception", "Database Exception",
                    MainApp.SQLExceptionToString(e), e);
        }
    }

    // either create new or update existing master password
    private void setupVaultMasterPassword(boolean isUpdate) {
        List<String> passwords = mMainApp.showPasswordDialog(isUpdate ?
                PasswordDialogController.MODE.CHANGE : PasswordDialogController.MODE.NEW);

        if (passwords.size() == 0) {
            String title = "Warning";
            String header = "Password not entered";
            String content = "Master Password not " + (isUpdate ? "updated" : "created");
            MainApp.showWarningDialog(title, header, content);
            return;
        }

        try {
            String title, header, content;
            if (isUpdate) {
                if (mMainApp.updateMasterPassword(passwords.get(0), passwords.get(1))) {
                    title = "Update Master Password";
                    header = "Update master password successful";
                    content = "Master password successfully updated.";
                } else {
                    title = "Update Master Password";
                    header = "Master Password Not Updated";
                    content = "Current password doesn't match.  Master password is not updated";
                }
            } else {
                mMainApp.setMasterPassword(passwords.get(1));
                title = "Create Master Password";
                header = "Create master password successful";
                content = "Master password successfully created";
            }
            mMainApp.showInformationDialog(title, header, content);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException
                | IllegalBlockSizeException | BadPaddingException | SQLException e) {
            String exceptionType, message;
            if (e instanceof SQLException) {
                exceptionType = "Database exception";
                message = MainApp.SQLExceptionToString((SQLException) e);
            } else {
                exceptionType = "Vault exception";
                message = e.getMessage();
            }
            mLogger.error(exceptionType, e);
            mMainApp.showExceptionDialog("Exception", exceptionType, message, e);
            mMainApp.showInformationDialog("Create/Update Master Password",
                    "Failed to create/update master password",
                    "Master Password not " + (isUpdate ? "updated" : "created"));
        }
    }

    @FXML
    private void handleAbout() {
        mMainApp.showSplashScreen(false);
    }

    @FXML
    private void handleSearch() {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(mMainApp.getStage());
        SearchResultDialog srd = new SearchResultDialog(mSearchTextField.getText().trim(), mMainApp, dialogStage);
        dialogStage.showAndWait();
        Transaction t = srd.getSelectedTransaction();
        if (t != null) {
            Account a = mMainApp.getAccountByID(t.getAccountID());
            if (a.getHiddenFlag()) {
                showWarningDialog("Hidden Account Transaction",
                        "Selected Transaction Belongs to a Hidden Account",
                        "Please unhide " + a.getName() + " to view/edit the transaction");
                return;
            }

            for (TreeItem<Account> tia : mAccountTreeTableView.getRoot().getChildren()) {
                if (tia.getValue().getType() == a.getType()) {
                    for (TreeItem<Account> tia1 : tia.getChildren()) {
                        if (tia1.getValue().getID() == a.getID()) {
                            mAccountTreeTableView.getSelectionModel().select(tia1);
                            for (Transaction t1 : mTransactionTableView.getItems()) {
                                if (t1.getID() == t.getID()) {
                                    mTransactionTableView.getSelectionModel().select(t1);
                                    mTransactionTableView.scrollTo(t1);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @FXML
    private void handleClose() {
        Platform.exit();
    }

    @FXML
    private void handleOpen() {
        mMainApp.openDatabase(false, null, null);
        updateRecentMenu();
        updateUI(mMainApp.isConnected());
    }

    @FXML
    private void handleNew() {
        mMainApp.openDatabase(true, null, null);
        updateRecentMenu();
        updateUI(mMainApp.isConnected());
    }

    @FXML
    private void handleChangePassword() {
        mMainApp.changePassword();
    }

    @FXML
    private void handleImportQIF() {
        mMainApp.importQIF();
        mMainApp.initAccountList();
    }

    @FXML
    private void handleFixDB() {
        mMainApp.fixDB();
        mMainApp.initAccountList();
    }

    @FXML
    private void handleBackup() { mMainApp.doBackup(); }

    @FXML
    private void handleClearList() {
        mMainApp.putOpenedDBNames(new ArrayList<>());
        updateRecentMenu();
    }

    @FXML
    private void handleDirectConnectionList() { mMainApp.showDirectConnectionListDialog(); }
    @FXML
    private void handleFinancialInstitutionList() { mMainApp.showFinancialInstitutionListDialog(); }

    @FXML
    private void handleEditAccountList() { mMainApp.showAccountListDialog(); }

    @FXML
    private void handleEditSecurityList() {
        mMainApp.showSecurityListDialog();
    }

    @FXML
    private void handleEditCategoryList() {
        mMainApp.showCategoryListDialog();
    }

    @FXML
    private void handleEditTagList() { mMainApp.showTagListDialog(); }

    @FXML
    private void handleReminderList() { mMainApp.showBillIncomeReminderDialog(); }

    @FXML
    private void handleNAVReport() {
        mMainApp.showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.NAV));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleInvestingTransactions() {
        mMainApp.showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.INVESTTRANS));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleInvestingIncome() {
        mMainApp.showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.INVESTINCOME));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleBankingTransactions() {
        mMainApp.showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.BANKTRANS));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleCapitalGains() {
        mMainApp.showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.CAPITALGAINS));
        updateSavedReportsMenu();
    }

    private void updateSavedReportsMenu() {
        List<MenuItem> menuItemList = new ArrayList<>();
        for (ReportDialogController.Setting setting : mMainApp.loadReportSetting(0)) {
            MenuItem mi = new MenuItem(setting.getName());
            mi.setUserData(setting);
            mi.setOnAction(t -> mMainApp.showReportDialog((ReportDialogController.Setting) mi.getUserData()));
            menuItemList.add(mi);
        }
        mSavedReportsMenu.getItems().setAll(menuItemList);
    }

    private void updateUI(boolean isConnected) {
        mEditMenu.setVisible(isConnected);
        mOFXMenu.setVisible(isConnected);
        mReportsMenu.setVisible(isConnected);
        mChangePasswordMenuItem.setVisible(isConnected);
        mBackupMenuItem.setVisible(isConnected);
        mImportQIFMenuItem.setVisible(isConnected);
        mFixDBMenuItem.setVisible(isConnected);
        mAccountTreeTableView.setVisible(isConnected);
        mSearchButton.setVisible(isConnected);
        mSearchTextField.setVisible(isConnected);
        if (isConnected) {
            updateSavedReportsMenu();
            populateTreeTable();
        }
    }

    private void updateRecentMenu() {
        EventHandler<ActionEvent> menuAction = t -> {
            MenuItem mi = (MenuItem) t.getTarget();
            mMainApp.openDatabase(false, mi.getText(), null);
            updateUI(mMainApp.isConnected());
        };
        ObservableList<MenuItem> recentList = mRecentDBMenu.getItems();
        recentList.remove(0, recentList.size() - 2);
        List<String> newList = mMainApp.getOpenedDBNames();
        int n = newList.size();
        for (int i = 0; i < n; i++) {
            MenuItem mi = new MenuItem(newList.get(n-i-1));
            mi.setOnAction(menuAction);
            recentList.add(0, mi);
        }
    }

    @FXML
    private void handleEnterTransaction() {
        mMainApp.showEditTransactionDialog(mMainApp.getStage(), null);
    }

    @FXML
    private void handleShowHoldings() {
        mMainApp.showAccountHoldings();
    }

    @FXML
    private void handleReconcile() {
        mMainApp.showReconcileDialog();
    }

    private void showAccountTransactions(Account account) {
        mMainApp.setCurrentAccount(account);

        if (account == null) {
            return;
        }

        boolean isTradingAccount = account.getType() == Account.Type.INVESTING;

        mTransactionAccountNameLabel.setVisible(true);
        mTransactionAccountNameLabel.setText(account.getName());
        mEnterTransactionButton.setVisible(true);
        mTransactionShowHoldingsButton.setVisible(isTradingAccount);

        //mTransactionTableView.setVisible(true);
        mTransactionTableView.setItems(account.getTransactionList());
        int selectedIdx = mTransactionTableView.getSelectionModel().getSelectedIndex();
        if (selectedIdx >= 0)
            mTransactionTableView.scrollTo(selectedIdx);
        else
            mTransactionTableView.scrollTo(account.getTransactionList().size()-1);
        mTransactionStatusColumn.setVisible(true);
        mTransactionTradeActionColumn.setVisible(isTradingAccount);
        mTransactionReferenceColumn.setVisible(!isTradingAccount);
        mTransactionPayeeColumn.setVisible(!isTradingAccount);
        mTransactionMemoColumn.setVisible(!isTradingAccount);
        mTransactionCategoryColumn.setVisible(!isTradingAccount);
        mTransactionPaymentColumn.setVisible(!isTradingAccount);
        mTransactionDepositColumn.setVisible(!isTradingAccount);
        mTransactionBalanceColumn.setText(isTradingAccount ? "Cash Bal" : "Balance");

        mTransactionSecurityNameColumn.setVisible(isTradingAccount);
        mTransactionDescriptionColumn.setVisible(isTradingAccount);
        mTransactionInvestAmountColumn.setVisible(isTradingAccount);
        mTransactionCashAmountColumn.setVisible(isTradingAccount);
    }

    private boolean showChangeReconciledConfirmation() {
        return MainApp.showConfirmationDialog("Confirmation","Reconciled transaction?",
                "Do you really want to change it?");
    }

    @FXML
    private void initialize() {
        mAccountNameTreeTableColumn.setCellValueFactory(cd -> cd.getValue().getValue().getNameProperty());
        mAccountNameTreeTableColumn.setCellFactory(column -> new TreeTableCell<Account, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText("");
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });

        mAccountBalanceTreeTableColumn.setCellValueFactory(cd -> cd.getValue().getValue().getCurrentBalanceProperty());
        mAccountBalanceTreeTableColumn.setCellFactory(column -> new TreeTableCell<Account, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText("");
                } else {
                    // format
                    setText(MainApp.DOLLAR_CENT_FORMAT.format(item));
                }
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });

        mAccountTreeTableView.getSelectionModel().selectedItemProperty().addListener(mSelectedTreeItemChangeListener);

        PseudoClass future = PseudoClass.getPseudoClass("future");
        PseudoClass reconciled = PseudoClass.getPseudoClass("reconciled");

        mTransactionTableView.setRowFactory(tv -> {
            final TableRow<Transaction> row = new TableRow<>();
            final ContextMenu contextMenu = new ContextMenu();
            final MenuItem deleteMI = new MenuItem("Delete");
            deleteMI.setOnAction(e -> {
                if (row.getItem().getStatus().equals(Transaction.Status.RECONCILED)
                        && !showChangeReconciledConfirmation())
                    return;
                // delete this transaction
                if (MainApp.showConfirmationDialog("Confirmation","Delete transaction?",
                "Do you really want to delete it?"))
                    mMainApp.alterTransaction(row.getItem(), null, new ArrayList<>());
            });
            final Menu moveToMenu = new Menu("Move to...");
            for (Account.Type at : Account.Type.values()) {
                final Menu atMenu = new Menu(at.name());
                atMenu.getItems().add(new MenuItem(at.name())); // need this placeholder for setOnShowing to work
                atMenu.setOnShowing(e -> {
                    atMenu.getItems().clear();
                    final ObservableList<Account> accountList = mMainApp.getAccountList(at, false, true);
                    for (Account a : accountList) {
                        if (a.getID() != row.getItem().getAccountID()) {
                            MenuItem accountMI = new MenuItem(a.getName());
                            accountMI.setOnAction(e1 -> {
                                if (row.getItem().getStatus().equals(Transaction.Status.RECONCILED)
                                        && !showChangeReconciledConfirmation())
                                    return;
                                Transaction oldT = row.getItem();
                                List<SecurityHolding.MatchInfo> matchInfoList = mMainApp.getMatchInfoList(oldT.getID());
                                if (matchInfoList.isEmpty() || MainApp.showConfirmationDialog("Confirmation",
                                        "Transaction with Lot Matching",
                                        "The lot matching information will be lost. " +
                                                "Do you want to continue?")) {
                                    // either this transaction doesn't have lot matching information,
                                    // or user choose to ignore lot matching information
                                    Account newAccount = mMainApp.getAccountByName(accountMI.getText());
                                    if (newAccount != null) {
                                        // let show transaction table for the new account
                                        TreeItem<Account> groupNode = mAccountTreeTableView.getRoot().getChildren().stream()
                                                .filter(n -> n.getValue().getType().equals(newAccount.getType()))
                                                .findFirst().orElse(null);
                                        if (groupNode != null) {
                                            TreeItem<Account> accountNode = groupNode.getChildren().stream()
                                                    .filter(n -> n.getValue().getID() == newAccount.getID())
                                                    .findFirst().orElse(null);
                                            if (accountNode != null) {
                                                Transaction newT = new Transaction(oldT);
                                                newT.setAccountID(newAccount.getID());
                                                Transaction.ValidationStatus vs = newT.validate();
                                                if (vs.isValid()) {
                                                    mAccountTreeTableView.getSelectionModel().select(accountNode);
                                                    mMainApp.alterTransaction(oldT, newT, new ArrayList<>());
                                                } else {
                                                    MainApp.showWarningDialog("Invalid Transaction",
                                                            "Move Cancelled", vs.getMessage());
                                                }
                                            }
                                        }
                                    }
                                }
                            });
                            atMenu.getItems().add(accountMI);
                        }
                    }
                });
                moveToMenu.getItems().add(atMenu);
            }
            moveToMenu.setOnShowing(e-> {
                boolean isCash = row.getItem().isCash();
                for (MenuItem mi : moveToMenu.getItems()) {
                    mi.setVisible(isCash || mi.getText().equals(Account.Type.INVESTING.name()));
                }
            });

            for (Transaction.Status status : Transaction.Status.values()) {
                MenuItem statusMI = new MenuItem("Mark as " + status.toString());
                statusMI.setOnAction(e -> {
                    if (row.getItem().getStatus().equals(Transaction.Status.RECONCILED)
                            && !showChangeReconciledConfirmation())
                        return;
                    if (mMainApp.setTransactionStatusInDB(row.getItem().getID(), status))
                        row.getItem().setStatus(status);
                    else
                        showWarningDialog("Database problem",
                                    "Unable to change transaction status in DB",
                                    "Transaction status unchanged.");
                });
                contextMenu.getItems().add(statusMI);
            }
            contextMenu.getItems().add(new SeparatorMenuItem());
            contextMenu.getItems().add(deleteMI);
            contextMenu.getItems().add(moveToMenu);

            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null)
                    .otherwise(contextMenu));
            // double click to edit the transaction
            row.setOnMouseClicked(event -> {
                if ((event.getClickCount() == 2) && (!row.isEmpty())) {
                    if (row.getItem().getStatus().equals(Transaction.Status.RECONCILED)
                            && !showChangeReconciledConfirmation())
                        return;
                    final Transaction transaction = row.getItem();
                    int selectedTransactionID = transaction.getID();
                    if (transaction.getMatchID() > 0) {
                        // this is a linked transaction
                        if (transaction.getMatchSplitID() > 0) {
                            showWarningDialog("Linked to A Split Transaction",
                                    "Linked to a split transaction",
                                    "Please edit the linked split transaction.");
                            return;
                        }

                        if (transaction.isCash()) {
                            Transaction linkedTransaction = mMainApp.getTransactionByID(transaction.getMatchID());
                            if (linkedTransaction == null) {
                                showWarningDialog("Linked to An Investing Transaction",
                                        "Unable to find the linked transaction",
                                        "Call help!");
                                return;
                            }
                            if (!linkedTransaction.isCash()) {
                                 showWarningDialog("Linked to An Investing Transaction",
                                            "Linked to an investing transaction",
                                            "Please edit the linked investing transaction.");
                                 return;
                            }
                        }
                    }
                    mMainApp.showEditTransactionDialog(mMainApp.getStage(), new Transaction(row.getItem()));
                    for (int i = 0; i < mTransactionTableView.getItems().size(); i++) {
                        if (mTransactionTableView.getItems().get(i).getID() == selectedTransactionID)
                            mTransactionTableView.getSelectionModel().select(i);
                    }
                }
            });

            // setup pseudoclasses
            ChangeListener<Transaction.Status> statusChangeListener = (obs, oStatus, nStatus) -> {
                row.pseudoClassStateChanged(reconciled, nStatus.equals(Transaction.Status.RECONCILED));
                for (MenuItem mi : contextMenu.getItems()) {
                    mi.setDisable((mi.getText() != null) && mi.getText().endsWith(nStatus.toString()));
                }
            };
            ChangeListener<LocalDate> dateChangeListener = (obs, oDate, nDate)
                    -> row.pseudoClassStateChanged(future, nDate.isAfter(LocalDate.now()));

            row.itemProperty().addListener((obs, oTransaction, nTransaction) -> {
                if (oTransaction != null) {
                    oTransaction.getStatusProperty().removeListener(statusChangeListener);
                    oTransaction.getTDateProperty().removeListener(dateChangeListener);
                }
                if (nTransaction != null) {
                    nTransaction.getStatusProperty().addListener(statusChangeListener);
                    nTransaction.getTDateProperty().addListener(dateChangeListener);

                    row.pseudoClassStateChanged(reconciled,
                            nTransaction.getStatus().equals(Transaction.Status.RECONCILED));
                    row.pseudoClassStateChanged(future, nTransaction.getTDate().isAfter(LocalDate.now()));

                    for (MenuItem mi : contextMenu.getItems()) {
                        mi.setDisable((mi.getText() != null)
                                && mi.getText().endsWith(nTransaction.getStatus().toString()));
                    }
                } else {
                    row.pseudoClassStateChanged(reconciled, false);
                    row.pseudoClassStateChanged(future, false);
                }
            });

            return row;
        });
        mTransactionTableView.getStylesheets().add(getClass().getResource("/css/TransactionTableView.css").toExternalForm());

        // transaction table
        mTransactionStatusColumn.setCellValueFactory(cd -> cd.getValue().getStatusProperty());
        mTransactionStatusColumn.setCellFactory(c -> new TableCell<Transaction, Transaction.Status>() {
            @Override
            protected void updateItem(Transaction.Status item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(item.toChar()));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });
        mTransactionDateColumn.setCellValueFactory(cellData->cellData.getValue().getTDateProperty());
        mTransactionDateColumn.setStyle( "-fx-alignment: CENTER;");
        mTransactionTradeActionColumn.setCellValueFactory(cellData -> cellData.getValue().getTradeActionProperty());
        mTransactionSecurityNameColumn.setCellValueFactory(cellData -> cellData.getValue().getSecurityNameProperty());
        mTransactionDescriptionColumn.setCellValueFactory(cellData -> cellData.getValue().getDescriptionProperty());
        mTransactionReferenceColumn.setCellValueFactory(cellData -> cellData.getValue().getReferenceProperty());
        mTransactionReferenceColumn.setStyle( "-fx-alignment: CENTER;");
        mTransactionPayeeColumn.setCellValueFactory(cellData -> cellData.getValue().getPayeeProperty());
        mTransactionMemoColumn.setCellValueFactory(cellData -> cellData.getValue().getMemoProperty());

        mTransactionCategoryColumn.setCellValueFactory(cellData -> {
            Transaction t = cellData.getValue();
            if (t.getSplitTransactionList().size() > 0)
                return new ReadOnlyStringWrapper("--Split--");
            return new ReadOnlyStringWrapper(mMainApp.mapCategoryOrAccountIDToName(t.getCategoryID()));
        });

        mTransactionTagColumn.setCellValueFactory(cellData -> {
            Tag tag = mMainApp.getTagByID(cellData.getValue().getTagID());
            return new ReadOnlyStringWrapper(tag == null ? "" : tag.getName());
        });

        Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>> dollarCentsCF =
                new Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>>() {
                    @Override
                    public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> column) {
                        return new TableCell<Transaction, BigDecimal>() {
                            @Override
                            protected void updateItem(BigDecimal item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item == null || empty) {
                                    setText("");
                                } else {
                                    // format
                                    setText(item.signum() == 0 ? "" : MainApp.DOLLAR_CENT_FORMAT.format(item));
                                }
                                setStyle("-fx-alignment: CENTER-RIGHT;");
                            }
                        };
                    }
                };

        mTransactionPaymentColumn.setCellValueFactory(cellData->cellData.getValue().getPaymentProperty());
        mTransactionPaymentColumn.setCellFactory(dollarCentsCF);

        mTransactionDepositColumn.setCellValueFactory(cellData->cellData.getValue().getDepositeProperty());
        mTransactionDepositColumn.setCellFactory(dollarCentsCF);

        mTransactionInvestAmountColumn.setCellValueFactory(cellData->cellData.getValue().getInvestAmountProperty());
        mTransactionInvestAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionCashAmountColumn.setCellValueFactory(cellData->cellData.getValue().getCashAmountProperty());
        mTransactionCashAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionBalanceColumn.setCellValueFactory(cellData->cellData.getValue().getBalanceProperty());
        mTransactionBalanceColumn.setCellFactory(
                new Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>>() {
                    @Override
                    public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> column) {
                        return new TableCell<Transaction, BigDecimal>() {
                            @Override
                            protected void updateItem(BigDecimal item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item == null || empty) {
                                    setText("");
                                } else {
                                    // format
                                    setText(MainApp.DOLLAR_CENT_FORMAT.format(item));
                                }
                                setStyle("-fx-alignment: CENTER-RIGHT;");
                            }
                        };
                    }
                }
        );
        mTransactionVBox.visibleProperty().bind(mAccountTreeTableView.getSelectionModel()
                .selectedItemProperty().isNotNull());

        mSearchButton.disableProperty().bind(Bindings.createBooleanBinding(() ->
                mSearchTextField.getText() == null || mSearchTextField.getText().trim().isEmpty(),
                mSearchTextField.textProperty()));
    }

    private void showWarningDialog(String title, String header, String content) {
        MainApp.showWarningDialog(title, header, content);
    }
}