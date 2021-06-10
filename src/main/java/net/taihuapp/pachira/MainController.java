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

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Pair;
import net.taihuapp.pachira.dao.DaoException;
import net.taihuapp.pachira.dao.DaoManager;
import org.apache.log4j.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class MainController {

    private static final Logger mLogger = Logger.getLogger(MainController.class);

    private static final String ACKNOWLEDGE_TIMESTAMP = "ACKDT";
    private static final int MAX_OPENED_DB_HIST = 5; // keep max 5 opened files
    private static final String KEY_OPENED_DB_PREFIX = "OPENEDDB#";

    private MainApp mMainApp;
    private MainModel mainModel = null;

    @FXML
    private Menu mRecentDBMenu;
    @FXML
    private Menu mEditMenu;
    @FXML
    private Menu mOFXMenu;
    @FXML
    private MenuItem mDownloadAccountTransactionMenuItem;
    @FXML
    private MenuItem mSetAccountDirectConnectionMenuItem;
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
    private Menu mImportMenu;
    @FXML
    private MenuItem mImportOFXAccountStatementMenuItem;
    @FXML
    private Menu mExportMenu;
    @FXML
    private MenuItem mExportQIFMenuItem;
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
    }

    private MainModel getMainModel() { return mainModel; }
    private void setMainModel(MainModel m) {
        mainModel = m;

        mEditMenu.setVisible(m != null);
        mOFXMenu.setVisible(m != null);
        mReportsMenu.setVisible(m != null);
        mChangePasswordMenuItem.setVisible(m != null);
        mBackupMenuItem.setVisible(m != null);
        mExportMenu.setVisible(m != null);
        mImportMenu.setVisible(m != null);
        mAccountTreeTableView.setVisible(m != null);
        mSearchButton.setVisible(m != null);
        mSearchTextField.setVisible(m != null);

        if (m != null) {
            populateTreeTable();
            updateSavedReportsMenu();
            mImportOFXAccountStatementMenuItem.setDisable(true);
            mTransactionVBox.setVisible(false);
        }

        mDownloadAccountTransactionMenuItem.disableProperty().unbind();
        mSetAccountDirectConnectionMenuItem.disableProperty().unbind();
        mCreateMasterPasswordMenuItem.disableProperty().unbind();
        mUpdateMasterPasswordMenuItem.disableProperty().unbind();
        mDeleteMasterPasswordMenuItem.disableProperty().unbind();
        mDirectConnectionMenuItem.disableProperty().unbind();
        if (m != null) {
            mDownloadAccountTransactionMenuItem.disableProperty().bind(
                    Bindings.createBooleanBinding(() -> {
                        final Account account = m.getCurrentAccount();
                        return account == null || m.getAccountDC(account.getID()).isEmpty();
                    }, m.getCurrentAccountProperty(), m.getAccountDCList()));
            mSetAccountDirectConnectionMenuItem.disableProperty().bind(m.getCurrentAccountProperty().isNull()
                    .or(m.hasMasterPasswordProperty.not()));
            mCreateMasterPasswordMenuItem.disableProperty().bind(m.hasMasterPasswordProperty);
            mUpdateMasterPasswordMenuItem.disableProperty().bind(m.hasMasterPasswordProperty.not());
            mDeleteMasterPasswordMenuItem.disableProperty().bind(m.hasMasterPasswordProperty.not());
            mDirectConnectionMenuItem.disableProperty().bind(m.hasMasterPasswordProperty.not());
        }
    }

    private boolean isNonTrivialPermutated(ListChangeListener.Change<?> c) {
        if (!c.wasPermutated())
            return false;  // not even a permutated change

        for (int i = c.getFrom(); i < c.getTo(); i++)
            if (c.getPermutation(i) != i)
                return true;  // there is something nontrivial

        return false;
    }

    private void populateTreeTable() {
        final Account rootAccount = new Account(-1, null, "Total", "Placeholder for total asset",
                false, -1, null, BigDecimal.ZERO);
        final TreeItem<Account> root = new TreeItem<>(rootAccount);
        root.setExpanded(true);
        mAccountTreeTableView.setRoot(root);

        MainModel mainModel = getMainModel();
        if (mainModel == null)
            return;

        ObservableList<Account> groupAccountList = FXCollections.observableArrayList(account ->
                new Observable[] {account.getCurrentBalanceProperty()});
        for (Account.Type.Group g : Account.Type.Group.values()) {
            Account.Type groupAccountType = Arrays.stream(Account.Type.values()).filter(t -> t.isGroup(g))
                    .findFirst().orElse(null);
            Account groupAccount = new Account(-1, groupAccountType, g.toString(),
                    "Placeholder for " + g.toString(),
                    false, -1, null, BigDecimal.ZERO);
            groupAccountList.add(groupAccount);
            TreeItem<Account> typeNode = new TreeItem<>(groupAccount);
            typeNode.setExpanded(true);
            final ObservableList<Account> accountList = mainModel.getAccountList(g, false, true);
            for (Account a : accountList) {
                typeNode.getChildren().add(new TreeItem<>(a));
            }
            if (!accountList.isEmpty())
                root.getChildren().add(typeNode);
            ListChangeListener<Account> accountListChangeListener = c -> {
                while (c.next()) {
                    if (c.wasAdded() || c.wasRemoved() || isNonTrivialPermutated(c)) {
                        ReadOnlyObjectProperty<TreeItem<Account>> selectedItemProperty =
                                mAccountTreeTableView.getSelectionModel().selectedItemProperty();
                        // save the original selectedItem
                        Account selectedAccount = null;
                        if (selectedItemProperty.get() != null)
                            selectedAccount = selectedItemProperty.get().getValue();

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
    private void downloadAccountTransactions() {
        try {
            if (!mMainApp.hasMasterPasswordInKeyStore()) {
                List<String> passwords = mMainApp.showPasswordDialog("Enter Vault Master Password",
                        PasswordDialogController.MODE.ENTER);
                if (passwords.size() != 2 || !mMainApp.verifyMasterPassword(passwords.get(1))) {
                    // either didn't enter master password or failed to enter a correct one
                    MainApp.showWarningDialog("Download Account Transactions",
                            "Failed to input correct Master Password",
                            "Account transactions cannot be downloaded." );
                    return;
                }
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException
                | UnrecoverableKeyException e) {
            logAndDisplayException("Verify Master Password throws exception", e);
            return;
        }

        try {
            mMainApp.DCDownloadAccountStatement(mMainApp.getCurrentAccount());
        } catch (Exception e) {
            System.out.println("more work here");
        }
    }

    @FXML
    private void setAccountDirectConnection() {
        try {
            if (!mMainApp.hasMasterPasswordInKeyStore()) {
                List<String> passwords = mMainApp.showPasswordDialog("Enter Vault Master Password",
                        PasswordDialogController.MODE.ENTER);
                if (passwords.size() != 2 || !mMainApp.verifyMasterPassword(passwords.get(1))) {
                    // either didn't enter master password or failed to enter a correct one
                    MainApp.showWarningDialog("Edit Direct Connection",
                            "Failed to input correct Master Password",
                            "Direct connection cannot be edited");
                    return;
                }
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException
                | UnrecoverableKeyException e) {
            MainApp.showExceptionDialog(mMainApp.getStage(), "Exception", "Vault Exception",
                    e.getMessage(), e);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation((MainApp.class.getResource("/view/EditAccountDirectConnectionDialog.fxml")));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Direct Connection");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mMainApp.getStage());
            dialogStage.setScene(new Scene(loader.load()));

            EditAccountDirectConnectionDialogController controller = loader.getController();
            Account a = mMainApp.getCurrentAccount();
            AccountDC adc = mMainApp.getAccountDC(a.getID());
            if (adc == null) {
                java.util.Date lastDownloadDate;
                LocalDate lastReconcileDate = a.getLastReconcileDate();
                if (lastReconcileDate == null)
                    lastDownloadDate = new java.util.Date(0L);  // set to very early
                else
                    lastDownloadDate = java.util.Date.from(lastReconcileDate.atStartOfDay()
                            .atZone(ZoneId.systemDefault()).toInstant());
                adc = new AccountDC(a.getID(), "", 0, "", "", lastDownloadDate, null);
            }
            controller.setMainApp(mMainApp, dialogStage, adc);
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            MainApp.showExceptionDialog(mMainApp.getStage(), "Exception", "IOException", e.getMessage(), e);
        }
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
            MainApp.showExceptionDialog(mMainApp.getStage(), "Exception", "KeyStore Exception",
                    e.getMessage(), e);
        } catch (SQLException e) {
            mLogger.error("Database Exception", e);
            MainApp.showExceptionDialog(mMainApp.getStage(), "Exception", "Database Exception",
                    MainApp.SQLExceptionToString(e), e);
        }
    }

    // either create new or update existing master password
    private void setupVaultMasterPassword(boolean isUpdate) {
        List<String> passwords;
        if (isUpdate)
            passwords = mMainApp.showPasswordDialog("Update Vault Master Password",
                PasswordDialogController.MODE.CHANGE);
        else
            passwords = mMainApp.showPasswordDialog("Create New Vault Master Password",
                    PasswordDialogController.MODE.NEW);

        if (passwords.isEmpty()) {
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
            MainApp.showExceptionDialog(mMainApp.getStage(), "Exception", exceptionType, message, e);
            mMainApp.showInformationDialog("Create/Update Master Password",
                    "Failed to create/update master password",
                    "Master Password not " + (isUpdate ? "updated" : "created"));
        }
    }

    @FXML
    private void handleAbout() {
        showSplashScreen(false);
    }

    @FXML
    private void handleSearch() {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(stage);
        SearchResultDialog srd = new SearchResultDialog(mSearchTextField.getText().trim(), mainModel, dialogStage);
        dialogStage.showAndWait();
        Transaction t = srd.getSelectedTransaction();
        if (t != null) {
            Account a = mainModel.getAccount(account -> account.getID() == t.getAccountID()).orElse(null);
            if (a == null) {
                logAndDisplayException("Invalid account id " + t.getAccountID() + " for Transaction " + t.getID(), null);
                return;
            }
            if (a.getHiddenFlag()) {
                DialogUtil.showWarningDialog(null,"Hidden Account Transaction",
                        "Selected Transaction Belongs to a Hidden Account",
                        "Please unhide " + a.getName() + " to view/edit the transaction");
                return;
            }

            for (TreeItem<Account> tia : mAccountTreeTableView.getRoot().getChildren()) {
                if (tia.getValue().getType().isGroup(a.getType().getGroup())) {
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
        try {
            DaoManager.getInstance().closeConnection();
            ((Stage) mAccountTreeTableView.getScene().getWindow()).close();
        } catch (DaoException e) {
            logAndDisplayException("Failed to close connection", e);
        }
    }

    /**
     * return a file object of database file
     * @param isNew - prompt for a new file if isNew is true, otherwise, prompt a existing file
     * @return a File object with name ending dbPostfix.  Null is returned if the user cancelled it.
     */
    private File getDBFileFromUser(boolean isNew) {
        final String dbPostfix = DaoManager.getDBPostfix();
        final FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DB", "*" + dbPostfix));
        
        final String implementationTitle = getClass().getPackage().getImplementationTitle();
        final File file;
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        if (isNew) {
            fileChooser.setTitle("Create a new " + implementationTitle + " database...");
            file = fileChooser.showSaveDialog(stage);
        } else {
            fileChooser.setTitle("Open an existing " + implementationTitle + " database");
            file = fileChooser.showOpenDialog(stage);
        }
        
        if (file == null)
            return null;  // user cancelled

        final String fileName = file.getAbsolutePath();
        if (fileName.endsWith(dbPostfix))
            return file;

        return new File(fileName + dbPostfix);
    }

    /**
     * create or open an existing database
     * @param file - a file object of database, contains database postfix
     * @param isNew - if true, create a new database, otherwise, open an existing one
     */
    private void openDB(final File file, boolean isNew) {
        final String dbPostfix = DaoManager.getDBPostfix();
        final String dbName = file.getAbsolutePath().substring(0, file.getAbsolutePath().length()-dbPostfix.length());
        final Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();

        if (isNew && file.exists() && !file.delete()) {
            // can't delete an existing file
            mLogger.warn("unable to delete " + dbName + dbPostfix);
            DialogUtil.showWarningDialog(stage,"Unable to delete",
                    "Unable to delete existing " + dbName + dbPostfix,"");
            return; // we are done
        }

        if (!isNew && !file.exists()) {
            // if the file doesn't exist, remove it from OpenedDBNames
            putOpenedDBNames(removeFromOpenedDBNames(getOpenedDBNames(), dbName));
            updateRecentMenu();
            DialogUtil.showWarningDialog(stage, "Warning", file.getAbsolutePath() + " doesn't exist.",
                    file.getAbsolutePath() + " doesn't exist.  Can't continue.");
            return;
        }

        // get user password now
        final List<String> passwords;
        try {
            if (isNew)
                passwords = DialogUtil.showPasswordDialog(stage, "Create New Password for " + dbName,
                        PasswordDialogController.MODE.NEW);
            else
                passwords = DialogUtil.showPasswordDialog(stage, "Enter Password for " + dbName,
                        PasswordDialogController.MODE.ENTER);
            if (passwords.isEmpty())
                return; // user cancelled
        } catch (IOException e) {
            logAndDisplayException("Failure on opening password dialog", e);
            return;
        }

        // it might take some time to open and load db
        stage.getScene().setCursor(Cursor.WAIT);
        CompletableFuture.supplyAsync(() -> {
            MainModel model = null;
            try {
                DaoManager.getInstance().openConnection(dbName, passwords.get(1), isNew);
                model = new MainModel();
            } catch (DaoException | ModelException e) {
                Platform.runLater(() -> logAndDisplayException("Failed to open connection or init MainModel", e));
            }
            return model;
        }).thenAccept(m -> Platform.runLater(() -> {
            stage.getScene().setCursor(Cursor.DEFAULT);
            stage.setOnCloseRequest(e -> handleClose());
            if (m == null)
                return;  // open db failed, don't change the current model

            stage.setTitle(getClass().getPackage().getImplementationTitle() + " " + dbName);
            putOpenedDBNames(addToOpenedDBNames(getOpenedDBNames(), dbName));
            updateRecentMenu();

            // db opened and got a new model
            setMainModel(m);
        }));
    }

    @FXML
    private void handleOpen() {
        final File dbFile = getDBFileFromUser(false);
        if (dbFile == null)
            return; // user cancelled

        openDB(dbFile, false);
    }

    @FXML
    private void handleNew() {
        final File dbFile = getDBFileFromUser(true);
        if (dbFile == null)
            return;

        openDB(dbFile, true);
    }

    @FXML
    private void handleChangePassword() {
        final Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        DaoManager daoManager = DaoManager.getInstance();
        String dbName = null;
        String backupDBFileName = null;
        try {
            dbName = daoManager.getDBFileName();
            final List<String> passwords = DialogUtil.showPasswordDialog(stage, "Change password for " + dbName,
                    PasswordDialogController.MODE.CHANGE);
            if (passwords.size() != 2)
                return; // action cancelled

            backupDBFileName = daoManager.backup();
            daoManager.changeDBPassword(passwords);
            DialogUtil.showInformationDialog(stage, "Success", "Password change successful!", "");
        } catch (DaoException e) {
            final String msg;
            if (dbName == null)
                msg = e.getErrorCode() + " while try to get database metadata ";
            else if (backupDBFileName == null)
                msg = e.getErrorCode() + " while try to backup " + dbName;
            else
                msg = e.getMessage() + " while try to change password for " + dbName + System.lineSeparator()
                        + "Old database is saved in " + backupDBFileName;
            logAndDisplayException(msg, e);
        } catch (IOException e) {
            logAndDisplayException("Failed to load fxml", e);
        }
    }

    @FXML
    private void handleImportPrices() {
        // get the csv file name from the user
        final FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("csv files",
                Arrays.asList("*.csv", "*.CSV")));
        fileChooser.setTitle("Import Prices in CSV file...");
        final Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        final File file = fileChooser.showOpenDialog(stage);
        if (file == null)
            return;  // user cancelled it

        try {
            Pair<List<Pair<Security, Price>>, List<String[]>> outputPair = mainModel.importPrices(file);
            List<Pair<Security, Price>> priceList = outputPair.getKey();
            List<String[]> rejectLines = outputPair.getValue();
            StringBuilder message = new StringBuilder();
            if (rejectLines.size() > 0) {
                message.append("Skipped line(s):").append(System.lineSeparator()).append(System.lineSeparator());
                rejectLines.forEach(l -> message.append(String.join(",", l)).append(System.lineSeparator()));
            }
            DialogUtil.showInformationDialog(stage, "Import Prices", priceList.size() + " prices imported",
                    message.toString());
        } catch (IOException e) {
            logAndDisplayException("Failed to open file " + file.getAbsolutePath() + " for read", e);
        } catch (DaoException e) {
            logAndDisplayException("Database exception " + e.getErrorCode(), e);
        }
    }

    @FXML
    private void handleImportOFXAccountStatement() { mMainApp.importOFXAccountStatement(); }

    @FXML
    private void handleImportQIF() {
        mMainApp.importQIF();
        mMainApp.initAccountList();
    }

    @FXML
    private void handleExportQIF() {
        // open an export dialog window
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/ExportQIFDialog.fxml"));

            Stage stage = new Stage();
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mMainApp.getStage());
            stage.setTitle("Export to QIF");
            stage.setScene(new Scene(loader.load()));
            ExportQIFDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, stage);
            stage.showAndWait();
        } catch (IOException e) {
            logAndDisplayException("IOException when export QIF", e);
        }
    }

    @FXML
    private void handleSQLToDB() {
        mMainApp.SQLToDB();
    }

    @FXML
    private void handleDBToSQL() {
        mMainApp.DBToSQL();
    }

    @FXML
    private void handleBackup() {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        try {
            final String backupDBFileName = DaoManager.getInstance().backup();
            DialogUtil.showInformationDialog(stage, "Information", "Backup Successful",
                    "Current database was successfully saved to " + backupDBFileName);
        } catch (DaoException e) {
            logAndDisplayException("Backup failed", e);
        }
    }

    @FXML
    private void handleClearList() {
        putOpenedDBNames(new ArrayList<>());
        updateRecentMenu();
    }

    @FXML
    private void handleDirectConnectionList() { mMainApp.showDirectConnectionListDialog(); }
    @FXML
    private void handleFinancialInstitutionList() { mMainApp.showFinancialInstitutionListDialog(); }

    @FXML
    private void handleEditAccountList() {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/AccountListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Account List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));
            AccountListDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null AccountListDialog controller?");
                return;
            }
            controller.setMainModel(mainModel);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            logAndDisplayException("IOException on showing EditAccountList", e);
        }
    }

    @FXML
    private void handleEditSecurityList() {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/SecurityListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Security List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));
            SecurityListDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null controller for SecurityListDialog");
                return;
            }
            controller.setMainModel(mainModel);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            logAndDisplayException("IOException on showing security list dialog", e);
        }
    }

    @FXML
    private void handleEditCategoryList() {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/CategoryListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Category List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));
            CategoryListDialogController controller = loader.getController();
            controller.setMainModel(mainModel);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
          logAndDisplayException("IOException on showCategoryListDialog", e);
        }
    }

    @FXML
    private void handleEditTagList() {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/TagListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Tag List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));
            TagListDialogController controller = loader.getController();
            controller.setMainModel(mainModel);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            logAndDisplayException("IOException on showTagListDialog", e);
        }
    }

    @FXML
    private void handleReminderList() {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/ReminderTransactionListDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Reminder Transaction List");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));
            ReminderTransactionListDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null controller for ReminderTransactionListDialog");
                return;
            }
            //controller.setMainApp(this, dialogStage);
            controller.setMainModel(mainModel);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            logAndDisplayException("IOException on showReminderTransactionListDialog", e);
        }
    }

    @FXML
    private void handleNAVReport() {
        showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.NAV));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleInvestingTransactions() {
        showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.INVESTTRANS));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleInvestingIncome() {
        showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.INVESTINCOME));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleBankingTransactions() {
        showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.BANKTRANS));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleCapitalGains() {
        showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.CAPITALGAINS));
        updateSavedReportsMenu();
    }

    private void updateSavedReportsMenu() {
        List<MenuItem> menuItemList = new ArrayList<>();
        try {
            List<ReportDialogController.Setting> settings = mainModel.getReportSettingList();
            settings.sort(Comparator.comparing(ReportDialogController.Setting::getID));
            for (ReportDialogController.Setting setting : settings) {
                MenuItem mi = new MenuItem(setting.getName());
                mi.setUserData(setting);
                mi.setOnAction(t -> {
                    showReportDialog((ReportDialogController.Setting) mi.getUserData());
                    updateSavedReportsMenu();
                });
                menuItemList.add(mi);
            }
            mSavedReportsMenu.getItems().setAll(menuItemList);
        } catch (DaoException e) {
            logAndDisplayException(e.getErrorCode() + " when get Report settings", e);
        }
    }

    private void updateRecentMenu() {
        EventHandler<ActionEvent> menuAction = t -> {
            MenuItem mi = (MenuItem) t.getTarget();
            final File dbFile = new File(mi.getText() + DaoManager.getDBPostfix());
            openDB(dbFile, false);
        };

        ObservableList<MenuItem> recentList = mRecentDBMenu.getItems();
        // the recentList has n + 2 items, the last two are a separator and a menuItem for clear list.
        recentList.remove(0, recentList.size() - 2); // remove everything except the last two
        List<String> newList = getOpenedDBNames();
        int n = newList.size();
        for (int i = 0; i < n; i++) {
            MenuItem mi = new MenuItem(newList.get(i));
            mi.setOnAction(menuAction);
            recentList.add(i, mi);
        }
    }

    @FXML
    private void handleEnterTransaction() {
        final Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        final Account account = mainModel.getCurrentAccount();
        final List<Transaction.TradeAction> taList = account.getType().isGroup(Account.Type.Group.INVESTING) ?
                Arrays.asList(Transaction.TradeAction.values()) :
                Arrays.asList(Transaction.TradeAction.WITHDRAW, Transaction.TradeAction.DEPOSIT);
        try {
            DialogUtil.showEditTransactionDialog(mainModel, stage, null,
                    Collections.singletonList(account), account, taList);
        } catch (IOException | DaoException e) {
            logAndDisplayException(e.getClass().getName() + " when opening EditTransactionDialog", e);
        }
    }

    @FXML
    private void handleShowHoldings() {
        mMainApp.showAccountHoldings();
    }

    @FXML
    private void handleReconcile() {
        final Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/ReconcileDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setTitle("Reconcile Account: " + mainModel.getCurrentAccount().getName());
            dialogStage.setScene(new Scene(loader.load()));
            ReconcileDialogController controller = loader.getController();
            controller.setMainModel(mainModel);
            dialogStage.setOnCloseRequest(e -> controller.handleCancel());
            dialogStage.showAndWait();
        } catch (IOException e) {
            logAndDisplayException("IOException when opening reconcile dialog", e);
        }
    }

    private void showReportDialog(ReportDialogController.Setting setting) {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/ReportDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));
            ReportDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null ReportDialogController");
                return;
            }
            controller.setMainModel(mainModel, setting);
            dialogStage.setOnCloseRequest(event -> controller.close());
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }

    }

    private void showAccountTransactions(Account account) {
        mMainApp.setCurrentAccount(account);

        if (account == null) {
            return;
        }

        boolean isTradingAccount = account.getType().isGroup(Account.Type.Group.INVESTING);
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean showChangeReconciledConfirmation() {
        return MainApp.showConfirmationDialog("Confirmation","Reconciled transaction?",
                "Do you really want to change it?");
    }

    // warn user about changing Clint UID.
    // and return true of user OK's it
    // do nothing and return true if current UID is not set.
    private boolean warnChangingClientUID() throws DaoException {
        return mainModel.getClientUID().map(uuid -> MainApp.showConfirmationDialog("Changing ClientUID",
                "Current ClientUID is " + uuid.toString(),
                "May have to reestablish existing Direct Connections after reset ClientUID"))
                .orElse(true);
    }

    @FXML
    private void handleClientUID() {
        final TextArea textArea = new TextArea();
        textArea.setPrefWidth(1); // make sure it's not too wide
        textArea.setEditable(false);
        textArea.setVisible(false);
        textArea.setWrapText(true);

        final TextField currentTF = new TextField();
        currentTF.setEditable(false);
        try {
            currentTF.setText(mainModel.getClientUID().map(UUID::toString).orElse(""));
        } catch (DaoException e) {
            mLogger.error(e.getErrorCode() + " DaoException on getClientUID", e);
            textArea.setVisible(true);
            textArea.setText(e.toString());
        }
        final TextField newTF = new TextField();
        final Button randomButton = new Button("Random");
        final Button updateButton = new Button("Update");
        final Button closeButton = new Button("Close");
        final ButtonBar buttonBar = new ButtonBar();
        buttonBar.getButtons().addAll(randomButton, updateButton, closeButton);
        final GridPane gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        final ColumnConstraints cc0 = new ColumnConstraints();
        final ColumnConstraints cc1 = new ColumnConstraints();
        final ColumnConstraints cc2 = new ColumnConstraints();
        cc0.setPercentWidth(30);
        cc1.setPercentWidth(35);
        cc2.setPercentWidth(35);
        gridPane.getColumnConstraints().addAll(cc0, cc1, cc2);

        gridPane.add(textArea, 0, 0, 3, 1);
        gridPane.add(new Label("Current"), 0, 1, 1, 1);
        gridPane.add(currentTF, 1, 1, 2, 1);
        gridPane.add(new Label("New"), 0, 2, 1, 1);
        gridPane.add(newTF, 1, 2, 2, 1);
        gridPane.add(buttonBar, 0, 3, 3, 1);

        final Alert alert = new Alert(Alert.AlertType.NONE, "", ButtonType.CLOSE);
        // hide the default close button
        alert.getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);

        alert.setTitle("ClientUID");
        alert.initOwner(mMainApp.getStage());
        alert.getDialogPane().setContent(gridPane);

        randomButton.setOnAction(actionEvent -> {
            textArea.setVisible(false);
            newTF.setText(UUID.randomUUID().toString());
        });
        updateButton.setOnAction(actionEvent -> {
            try {
                if (warnChangingClientUID()) {
                    mainModel.putClientUID(UUID.fromString(newTF.getText()));
                    currentTF.setText(newTF.getText());
                    newTF.setText("");
                }
            } catch (DaoException e) {
                mLogger.error(e.getErrorCode() + " DaoException on Update ClientUID", e);
                textArea.setText(e.toString());
                textArea.setVisible(true);
            }
        });
        updateButton.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            try {
                //noinspection ResultOfMethodCallIgnored
                UUID.fromString(newTF.getText());
                textArea.setVisible(false);
                return false;
            } catch (IllegalArgumentException e) {
                if (!newTF.getText().trim().isEmpty()) {
                    textArea.setText("Input string is not a valid UUID");
                    textArea.setVisible(true);
                } else {
                    textArea.setVisible(false);
                }
                return true;
            }
        }, newTF.textProperty()));
        closeButton.setOnAction(actionEvent -> alert.close());

        alert.showAndWait();
    }

    /**
     * read Acknowledge time stamp from user pref.
     * @return the instance of acknowledge or null
     */
    private Instant getAcknowledgeTimeStamp() {
        String ldtStr = getUserPreferences().get(ACKNOWLEDGE_TIMESTAMP, null);
        if (ldtStr == null)
            return null;
        try {
            return Instant.parse(ldtStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void putAcknowledgeTimeStamp(Instant instant) {
        final Preferences userPref = getUserPreferences();
        userPref.put(ACKNOWLEDGE_TIMESTAMP, instant.toString());
        try {
            userPref.flush();
        } catch (BackingStoreException e) {
            logAndDisplayException("BackingStoreException encountered when storing Acknowledge TimeStamp.", e);
        }
    }

    private Preferences getUserPreferences() { return Preferences.userNodeForPackage(MainApp.class); }

    @FXML
    private void initialize() {
        if (getAcknowledgeTimeStamp() == null)
            showSplashScreen(true);

        MainApp.CURRENT_DATE_PROPERTY.addListener((obs, ov, nv) -> {
            try {
                getMainModel().updateAccountBalance((account) -> true);
            } catch (DaoException e){
                logAndDisplayException("UpdateAccountBalance Error", e);
            }
            mTransactionTableView.refresh();
        });

        mAccountNameTreeTableColumn.setCellValueFactory(cd -> cd.getValue().getValue().getNameProperty());
        mAccountNameTreeTableColumn.setCellFactory(column -> new TreeTableCell<>() {
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
        mAccountBalanceTreeTableColumn.setCellFactory(column -> new TreeTableCell<>() {
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

        mAccountTreeTableView.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null && nv.getValue().getID() >= MainApp.MIN_ACCOUNT_ID) {
                getMainModel().setCurrentAccount(nv.getValue());
                showAccountTransactions(nv.getValue());
                mImportOFXAccountStatementMenuItem.setDisable(false);
                mTransactionVBox.setVisible(true);
            }
        });

        mTransactionTableView.setRowFactory(tv -> new TableRow<>() {
            {
                final ContextMenu contextMenu = new ContextMenu();
                final MenuItem deleteMI = new MenuItem("Delete");
                deleteMI.setOnAction(e -> {
                    if (getItem().getStatus().equals(Transaction.Status.RECONCILED)
                            && !showChangeReconciledConfirmation())
                        return;
                    // delete this transaction
                    if (MainApp.showConfirmationDialog("Confirmation", "Delete transaction?",
                            "Do you really want to delete it?"))
                        mMainApp.alterTransaction(getItem(), null, new ArrayList<>());
                });
                final Menu moveToMenu = new Menu("Move to...");
                for (Account.Type.Group ag : Account.Type.Group.values()) {
                    final Menu agMenu = new Menu(ag.toString());
                    agMenu.getItems().add(new MenuItem(ag.toString())); // need this placeholder for setOnShowing to work
                    agMenu.setOnShowing(e -> {
                        agMenu.getItems().clear();
                        final ObservableList<Account> accountList = mMainApp.getAccountList(ag, false, true);
                        for (Account a : accountList) {
                            if (a.getID() != getItem().getAccountID()) {
                                MenuItem accountMI = new MenuItem(a.getName());
                                accountMI.setOnAction(e1 -> {
                                    if (getItem().getStatus().equals(Transaction.Status.RECONCILED)
                                            && !showChangeReconciledConfirmation())
                                        return;
                                    Transaction oldT = getItem();
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
                                            TreeItem<Account> groupNode = mAccountTreeTableView.getRoot().getChildren()
                                                    .stream()
                                                    .filter(n -> n.getValue().getType()
                                                            .isGroup(newAccount.getType().getGroup()))
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
                                agMenu.getItems().add(accountMI);
                            }
                        }
                    });
                    moveToMenu.getItems().add(agMenu);
                }
                moveToMenu.setOnShowing(e -> {
                    boolean isCash = getItem().isCash();
                    for (MenuItem mi : moveToMenu.getItems()) {
                        mi.setVisible(isCash || mi.getText().equals(Account.Type.Group.INVESTING.toString()));
                    }
                });

                // menuItem for downloaded transaction to merge with manually entered one
                final MenuItem mergeMI = new MenuItem("Merge...");
                mergeMI.disableProperty().bind(Bindings.createBooleanBinding(() ->
                        (getItem() == null || getItem().getFITID().isEmpty()), itemProperty()));
                mergeMI.setOnAction(e -> {
                    final Transaction downloadedTransaction = getItem();

                    Stage dialogStage = new Stage();
                    dialogStage.initModality(Modality.WINDOW_MODAL);
                    dialogStage.initOwner(mMainApp.getStage());
                    MergeCandidateDialog mcd = new MergeCandidateDialog(mainModel, dialogStage, downloadedTransaction);
                    dialogStage.showAndWait();
                    Transaction selected = mcd.getSelectedTransaction();
                    if (selected != null) {
                        Transaction mergedTransaction = Transaction.mergeDownloadedTransaction(
                                selected, downloadedTransaction);

                        // delete downloaded transaction, save mergedTransaction
                        if (!mMainApp.alterTransaction(downloadedTransaction, mergedTransaction,
                                mMainApp.getMatchInfoList(mergedTransaction.getID()))) {
                            DialogUtil.showWarningDialog(null, "Merge Transaction Failed",
                                    "Failed to merge a downloaded transaction with an existing one",
                                    "Transactions remained un-merged");
                        }
                    }
                });

                for (Transaction.Status status : Transaction.Status.values()) {
                    MenuItem statusMI = new MenuItem("Mark as " + status.toString());
                    statusMI.setOnAction(e -> {
                        if (getItem().getStatus().equals(Transaction.Status.RECONCILED)
                                && !showChangeReconciledConfirmation())
                            return;
                        if (mMainApp.setTransactionStatusInDB(getItem().getID(), status))
                            getItem().setStatus(status);
                        else
                            DialogUtil.showWarningDialog(null, "Database problem",
                                    "Unable to change transaction status in DB",
                                    "Transaction status unchanged.");
                    });
                    contextMenu.getItems().add(statusMI);
                }
                contextMenu.getItems().add(new SeparatorMenuItem());
                contextMenu.getItems().add(mergeMI);
                contextMenu.getItems().add(new SeparatorMenuItem());
                contextMenu.getItems().add(deleteMI);
                contextMenu.getItems().add(moveToMenu);

                contextMenuProperty().bind(Bindings.when(emptyProperty()).then((ContextMenu) null)
                        .otherwise(contextMenu));
                // double click to edit the transaction
                setOnMouseClicked(event -> {
                    if ((event.getClickCount() == 2) && (!isEmpty())) {
                        if (getItem().getStatus().equals(Transaction.Status.RECONCILED)
                                && !showChangeReconciledConfirmation())
                            return;
                        final Account account = mainModel.getCurrentAccount();
                        final Transaction transaction = getItem();
                        final int selectedTransactionID = transaction.getID();
                        final Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
                        final List<Transaction.TradeAction> taList = account.getType()
                                .isGroup(Account.Type.Group.INVESTING) ?
                                Arrays.asList(Transaction.TradeAction.values()) :
                                Arrays.asList(Transaction.TradeAction.WITHDRAW, Transaction.TradeAction.DEPOSIT);
                        try {
                            DialogUtil.showEditTransactionDialog(mainModel, stage, transaction,
                                    Collections.singletonList(account), account, taList);
                        } catch (IOException | DaoException e) {
                            logAndDisplayException(e.getClass().getName() + " when opening EditTransactionDialog", e);
                        }
                        for (int i = 0; i < mTransactionTableView.getItems().size(); i++) {
                            if (mTransactionTableView.getItems().get(i).getID() == selectedTransactionID)
                                mTransactionTableView.getSelectionModel().select(i);
                        }
                    }
                });
            }
        });
        mTransactionTableView.getStylesheets().add(MainApp.class.getResource("/css/TransactionTableView.css")
                .toExternalForm());

        // transaction table
        mTransactionStatusColumn.setCellValueFactory(cd -> cd.getValue().getStatusProperty());
        mTransactionStatusColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Transaction.Status item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(item.toChar()));
                    setStyle("-fx-alignment: CENTER;");
                    TableRow<Transaction> row = getTableRow();
                    if (row != null) {
                        row.pseudoClassStateChanged(PseudoClass.getPseudoClass("reconciled"),
                                item == Transaction.Status.RECONCILED);
                        ContextMenu contextMenu = row.getContextMenu();
                        if (contextMenu != null) {
                            for (MenuItem mi : contextMenu.getItems()) {
                                final String miText = mi.getText();
                                if (miText != null && miText.startsWith("Mark as")) {
                                    mi.setDisable(miText.endsWith(item.toString()));
                                }
                            }
                        }
                    }
                }
            }
        });
        mTransactionDateColumn.setCellValueFactory(cellData->cellData.getValue().getTDateProperty());
        mTransactionDateColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    setStyle("-fx-alignment: CENTER;");
                    TableRow<Transaction> row = getTableRow();
                    if (row != null) {
                        row.pseudoClassStateChanged(PseudoClass.getPseudoClass("future"),
                                item.isAfter(LocalDate.now()));
                    }
                }
            }
        });

        mTransactionTradeActionColumn.setCellValueFactory(cellData -> cellData.getValue().getTradeActionProperty());
        mTransactionSecurityNameColumn.setCellValueFactory(cellData -> cellData.getValue().getSecurityNameProperty());
        mTransactionDescriptionColumn.setCellValueFactory(cellData -> cellData.getValue().getDescriptionProperty());
        mTransactionReferenceColumn.setCellValueFactory(cellData -> cellData.getValue().getReferenceProperty());
        mTransactionReferenceColumn.setStyle( "-fx-alignment: CENTER;");
        mTransactionPayeeColumn.setCellValueFactory(cellData -> cellData.getValue().getPayeeProperty());
        mTransactionMemoColumn.setCellValueFactory(cellData -> cellData.getValue().getMemoProperty());

        mTransactionCategoryColumn.setCellValueFactory(cellData -> {
            Transaction t = cellData.getValue();
            if (!t.getSplitTransactionList().isEmpty())
                return new ReadOnlyStringWrapper("--Split--");
            int categoryID = t.getCategoryID();
            Optional<Category> categoryOptional = mainModel.getCategory(c -> c.getID() == categoryID);
            Optional<Account> accountOptional = mainModel.getAccount(account -> account.getID() == -categoryID);
            if (categoryOptional.isPresent()) {
                return categoryOptional.get().getNameProperty();
            } else if (accountOptional.isPresent()) {
                return Bindings.concat("[", accountOptional.get().getNameProperty(), "]");
            } else {
                return new ReadOnlyStringWrapper("");
            }
        });

        mTransactionTagColumn.setCellValueFactory(cellData -> {
            Optional<Tag> tagOptional = mainModel.getTag(t -> t.getID() == cellData.getValue().getTagID());
            if (tagOptional.isPresent())
                return tagOptional.get().getNameProperty();
            else
                return new ReadOnlyStringWrapper("");
        });

        Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>> dollarCentsCF =
                new Callback<>() {
                    @Override
                    public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> column) {
                        return new TableCell<>() {
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

        mTransactionDepositColumn.setCellValueFactory(cellData->cellData.getValue().getDepositProperty());
        mTransactionDepositColumn.setCellFactory(dollarCentsCF);

        mTransactionInvestAmountColumn.setCellValueFactory(cellData->cellData.getValue().getInvestAmountProperty());
        mTransactionInvestAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionCashAmountColumn.setCellValueFactory(cellData->cellData.getValue().getCashAmountProperty());
        mTransactionCashAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionBalanceColumn.setCellValueFactory(cellData->cellData.getValue().getBalanceProperty());
        mTransactionBalanceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> column) {
                        return new TableCell<>() {
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

        mSearchButton.disableProperty().bind(Bindings.createBooleanBinding(() ->
                mSearchTextField.getText() == null || mSearchTextField.getText().trim().isEmpty(),
                mSearchTextField.textProperty()));

        updateRecentMenu();
        setMainModel(null);
    }

    /**
     * save the list of opened db names to user preference
     * @param openedDBNames the list of opened db names
     */
    private void putOpenedDBNames(List<String> openedDBNames) {
        final String prefix = getClass().getPackage().getImplementationVersion().endsWith("SNAPSHOT") ?
                "SNAPSHOT-" : "";
        final int n = Math.min(openedDBNames.size(), MAX_OPENED_DB_HIST);
        Preferences userPreferences = getUserPreferences();
        for (int i = 0; i < n; i++) {
            userPreferences.put(prefix + KEY_OPENED_DB_PREFIX + i, openedDBNames.get(i));
        }
        for (int i = n; i < MAX_OPENED_DB_HIST; i++)
            userPreferences.remove(prefix + KEY_OPENED_DB_PREFIX + i);
        try {
            userPreferences.flush();
        } catch (BackingStoreException e) {
            logAndDisplayException("BackingStoreException when storing Opened DB names", e);
        }
    }

    /**
     * remove fileName from openedDBNames
     * @param openedDBNames - the list before update
     * @param fileName - the file name (without postfix) to be added
     * @return - updated list
     */
    private List<String> removeFromOpenedDBNames(List<String> openedDBNames, String fileName) {
        openedDBNames.remove(fileName);
        return openedDBNames;
    }

    /**
     * add file name to openedDBNames
     * @param openedDBNames - the list before update
     * @param fileName - the file name (without postfix) to be added
     * @return - updated list
     */
    private List<String> addToOpenedDBNames(List<String> openedDBNames, String fileName) {
        // remove first, if it is in the list
        openedDBNames.remove(fileName);

        // add to the top
        openedDBNames.add(0, fileName);

        // trim to the max
        if (openedDBNames.size() > MAX_OPENED_DB_HIST)
            return openedDBNames.subList(0, MAX_OPENED_DB_HIST);

        return openedDBNames;
    }

    /**
     * get a list of opened db files from user preference
     * @return list of file names
     */
    private List<String> getOpenedDBNames() {
        List<String> fileNameList = new ArrayList<>();
        final String prefix = getClass().getPackage().getImplementationVersion().endsWith("SNAPSHOT") ?
                "SNAPSHOT-" : "";

        Preferences userPreferences = getUserPreferences();
        for (int i = 0; i < MAX_OPENED_DB_HIST; i++) {
            String fileName = userPreferences.get(prefix + KEY_OPENED_DB_PREFIX + i, "");
            if (!fileName.isEmpty()) {
                fileNameList.add(fileName);
            }
        }
        return fileNameList;
    }

    private void showSplashScreen(boolean firstTime) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/SplashScreenDialog.fxml"));
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.setScene(new Scene(loader.load()));
            SplashScreenDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null SplashScreenDialogController?");
                Platform.exit();
                System.exit(0);
            }
            controller.setFirstTime(firstTime);
            dialogStage.setOnCloseRequest(e -> controller.handleClose());
            dialogStage.showAndWait();
            if (firstTime) {
                Instant ackDT = controller.getAcknowledgeDateTime();
                if (ackDT != null) {
                    putAcknowledgeTimeStamp(ackDT);
                }
            }
        } catch (IOException e) {
            logAndDisplayException("IOException when loading SplashScreenDialog.fxml", e);
        }
    }

    /**
     * log exception and display dialog for exception
     * @param msg - the message to show
     * @param e - the exception
     */
    private void logAndDisplayException(final String msg, final Exception e) {
        Stage stage = (Stage) mAccountTreeTableView.getScene().getWindow();
        mLogger.error(msg, e);
        if (e != null)
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
        else
            DialogUtil.showExceptionDialog(stage, "", msg, "", null);
    }
}