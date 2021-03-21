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

import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.domain.data.banking.AccountType;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.common.AccountDetails;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardAccountDetails;
import com.webcohesion.ofx4j.domain.data.investment.accounts.InvestmentAccountDetails;
import com.webcohesion.ofx4j.domain.data.signup.AccountProfile;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import net.taihuapp.pachira.dc.AccountDC;
import org.apache.log4j.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.net.MalformedURLException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

public class EditAccountDirectConnectionDialogController {

    private static final Logger mLogger = Logger.getLogger(EditAccountDirectConnectionDialogController.class);

    private class DCInfoConverter extends StringConverter<DirectConnection> {
        public DirectConnection fromString(String s) { return mMainApp.getDCInfoByName(s); }
        public String toString(DirectConnection dc) { return dc == null ? "" : dc.getName(); }
    }

    private Stage mStage;
    private MainApp mMainApp;
    private AccountDC mADC;
    private final BooleanProperty mChangedProperty = new SimpleBooleanProperty(false);
    private boolean isChanged() { return mChangedProperty.get(); }

    @FXML
    private Label mAccountNameLabel;
    @FXML
    private ComboBox<DirectConnection> mDCComboBox;
    @FXML
    private CheckBox mShowAccountNumberCheckBox;
    @FXML
    private Button mSaveButton;

    // mAccountTableView for non bank account
    @FXML
    private TableView<AccountDetails> mAccountTableView;
    @FXML
    private TableColumn<AccountDetails, String> mAccountNumberLast4TableColumn;
    @FXML
    private TableColumn<AccountDetails, String> mAccountNumberTableColumn;

    // mBankAccountTableView for bank account
    @FXML
    private TableView<BankAccountDetails> mBankAccountTableView;
    @FXML
    private TableColumn<BankAccountDetails, AccountType> mBankAccountTypeTableColumn;
    @FXML
    private TableColumn<BankAccountDetails, String> mRoutineNumberTableColumn;
    @FXML
    private TableColumn<BankAccountDetails, String> mBankAccountNumberLast4TableColumn;
    @FXML
    private TableColumn<BankAccountDetails, String> mBankAccountNumberTableColumn;

    // compare if information in accountDetails matches what is stored in accountDC
    private boolean compareAccountDetailsWithADC(AccountDetails accountDetails, AccountDC accountDC) {
        if (accountDetails == null)
            return accountDC.getDCID() == 0;

        char[] clearADCAccountNumber = null;
        try {
            if (mADC.getEncryptedAccountNumber().isEmpty())
                return false;
            clearADCAccountNumber = mMainApp.decrypt(mADC.getEncryptedAccountNumber());
            return compareStringWithChars(accountDetails.getAccountNumber(), clearADCAccountNumber);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException |
                UnrecoverableKeyException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException |
                BadPaddingException | IllegalArgumentException e ) {
            mLogger.error(e.getClass(), e);
            MainApp.showExceptionDialog(mStage, e.getClass().getName(), "Decryption Error",
                    e.getMessage(), e);
        } finally {
            if (clearADCAccountNumber != null)
                Arrays.fill(clearADCAccountNumber, ' ');
        }
        return false;
    }

    private boolean compareBankAccountDetailsWithADC(BankAccountDetails bad, AccountDC adc) {
        if (bad == null)
            return adc.getDCID() == 0;

        if (!bad.getAccountType().name().equals(adc.getAccountType()))
            return false;
        if (!bad.getRoutingNumber().equals(adc.getRoutingNumber()))
            return false;

        return compareAccountDetailsWithADC(bad, adc);
    }

    private boolean compareStringWithChars(String s, char[] cs) {
        if (s.length() != cs.length)
            return false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != cs[i])
                return false;
        }
        return true;
    }

    void setMainApp(MainApp mainApp, Stage stage, AccountDC adc) {
        mStage = stage;
        mMainApp = mainApp;
        mADC = adc;

        Account account = mMainApp.getCurrentAccount();
        mDCComboBox.setConverter(new DCInfoConverter());
        mDCComboBox.getItems().clear();
        // adding null item for ComboBox would lead to a IndexOutOfBoundsException when null is selected
        // this is a bug in javafx 8 https://bugs.openjdk.java.net/browse/JDK-8134923
        mDCComboBox.getItems().add(new DirectConnection(0, "", 0, "", ""));  // add a blank one
        mDCComboBox.getItems().addAll(mMainApp.getDCInfoList());

        mAccountNameLabel.setText(account.getName());

        // add change listeners
        mDCComboBox.getSelectionModel().selectedItemProperty().addListener((obs, odc, ndc) -> {
            if (ndc.getID() < 0)
                return;  // why are we here?

            Account.Type currentAccountType = mMainApp.getCurrentAccount().getType();
            ObservableList<BankAccountDetails> bankAccounts = FXCollections.observableArrayList();
            ObservableList<CreditCardAccountDetails> creditCardAccounts = FXCollections.observableArrayList();
            ObservableList<InvestmentAccountDetails> investmentAccounts = FXCollections.observableArrayList();

            try {
                for (AccountProfile ap : mMainApp.DCDownloadFinancialInstitutionAccountProfiles(ndc)) {
                    if (ap == null)
                        continue;
                    if (currentAccountType == Account.Type.CREDIT_CARD) {
                        // credit card account
                        if (ap.getCreditCardSpecifics() != null)
                            creditCardAccounts.add(ap.getCreditCardSpecifics().getCreditCardAccount());
                    } else if (currentAccountType.isGroup(Account.Type.Group.INVESTING)) {
                        // investment account
                        if (ap.getInvestmentSpecifics() != null)
                            investmentAccounts.add(ap.getInvestmentSpecifics().getInvestmentAccount());
                    } else {
                        // all other accounts
                        if (ap.getBankSpecifics() != null)
                            bankAccounts.add(ap.getBankSpecifics().getBankAccount());
                    }
                }

                if (currentAccountType == Account.Type.CREDIT_CARD) {
                    mAccountTableView.getItems().setAll(creditCardAccounts);
                    for (CreditCardAccountDetails creditCardAccountDetails : creditCardAccounts) {
                        if (compareAccountDetailsWithADC(creditCardAccountDetails, mADC)) {
                            mAccountTableView.getSelectionModel().select(creditCardAccountDetails);
                            break;
                        }
                    }
                    mAccountTableView.setVisible(true);
                    mBankAccountTableView.setVisible(false);
                } else if (currentAccountType.isGroup(Account.Type.Group.INVESTING)) {
                    System.out.println("Investment account not implemented");
                    mAccountTableView.setVisible(true);
                    mBankAccountTableView.setVisible(false);
                } else {
                    mBankAccountTableView.getItems().setAll(bankAccounts);
                    for (BankAccountDetails bankAccountDetails : bankAccounts) {
                        if (compareBankAccountDetailsWithADC(bankAccountDetails, mADC)) {
                            mBankAccountTableView.getSelectionModel().select(bankAccountDetails);
                            break;
                        }
                    }
                    mAccountTableView.setVisible(false);
                    mBankAccountTableView.setVisible(true);
                }
            } catch (MalformedURLException e) {
                mLogger.error("MalformedURLException", e);
                MainApp.showExceptionDialog(mStage,"MalformedURLException", "Bad URL",
                        e.getMessage(), e);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException |
                    UnrecoverableKeyException | NoSuchPaddingException | InvalidKeyException |
                    InvalidAlgorithmParameterException | IllegalBlockSizeException |
                    BadPaddingException e) {
                mLogger.error(e.getClass(), e);
                MainApp.showExceptionDialog(mStage, e.getClass().getName(), "Decryption Error",
                        e.getMessage(), e);
            } catch (OFXException e) {
                mLogger.error("OFXException", e);
                MainApp.showExceptionDialog(mStage,"OFXException", "OFXException", e.getMessage(), e);
            }  catch (SQLException e) {
                mLogger.error("SQLException", e);
                MainApp.showExceptionDialog(mStage, "Exception", "Database Exception",
                        MainApp.SQLExceptionToString(e), e);
            }
        });
        mDCComboBox.getSelectionModel().select(mMainApp.getDCInfoByID(adc.getDCID()));
    }

    @FXML
    private void handleSave() {
        int dcID = mDCComboBox.getValue().getID();
        Account account = mMainApp.getCurrentAccount();
        int aid = account.getID();

        try {
            AccountDetails accountDetails = mBankAccountTableView.isVisible() ?
                    mBankAccountTableView.getSelectionModel().getSelectedItem()
                    : mAccountTableView.getSelectionModel().getSelectedItem();
            if (dcID <= 0) {
                if (MainApp.showConfirmationDialog("Confirmation", "Direct connection is empty",
                        "entry will be deleted"))
                    mMainApp.deleteAccountDCFromDB(aid);
            } else if (accountDetails == null) {
                if (MainApp.showConfirmationDialog("Confirmation", "No account selected",
                        "Account Direct Connection entry will be deleted"))
                    mMainApp.deleteAccountDCFromDB(aid);
            } else {
                // leave account type and routing empty except for bank accounts
                final String at;
                final String rn;
                if (mBankAccountTableView.isVisible()) {
                    at = ((BankAccountDetails) accountDetails).getAccountType().name();
                    rn = ((BankAccountDetails) accountDetails).getRoutingNumber();
                } else {
                    at = "";
                    rn = "";
                }
                String ean = mMainApp.encrypt(accountDetails.getAccountNumber().toCharArray());
                // note, every time an AccountDC is changed, the last download date time is reset
                java.util.Date lastDownloadDate;
                LocalDate lastReconcileDate = account.getLastReconcileDate();
                if (lastReconcileDate == null)
                    lastDownloadDate = new java.util.Date(0L); // set to very early so we can download everything
                else
                    lastDownloadDate = java.util.Date.from(lastReconcileDate.atStartOfDay()
                            .atZone(ZoneId.systemDefault()).toInstant());
                mMainApp.mergeAccountDCToDB(new AccountDC(aid, at, dcID, rn, ean, lastDownloadDate, null));
            }
            mMainApp.initAccountDCList();
            mStage.close();
        } catch (SQLException e) {
            mLogger.error("SQLException", e);
            MainApp.showExceptionDialog(mStage, "Exception", "Database Exception",
                    MainApp.SQLExceptionToString(e), e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException e) {
            mLogger.error("Encrypt throws " + e.getClass().getName(), e);
            MainApp.showExceptionDialog(mStage,"Exception", "Encryption Exception", e.getMessage(), e);
        }
    }

    @FXML
    private void handleCancel() {
        if (isChanged() && !MainApp.showConfirmationDialog("Confirmation", "Content has been changed",
                "Do you want to discard changes?")) {
            return;
        }
        mStage.close();
    }

    @FXML
    private void initialize() {
        mShowAccountNumberCheckBox.setSelected(false);
        mSaveButton.disableProperty().bind(mChangedProperty.not());

        // setup mAccountTableView
        mAccountNumberTableColumn.visibleProperty().bind(mShowAccountNumberCheckBox.selectedProperty());
        mAccountNumberLast4TableColumn.visibleProperty().bind(mShowAccountNumberCheckBox.selectedProperty().not());

        mAccountNumberLast4TableColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper("x-" + (cd.getValue().getAccountNumber().length() > 4 ?
                        cd.getValue().getAccountNumber().substring(cd.getValue().getAccountNumber().length()-4) :
                        cd.getValue().getAccountNumber())));
        mAccountNumberTableColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getAccountNumber()));

        mAccountTableView.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) ->
            mChangedProperty.set(!compareAccountDetailsWithADC(nv, mADC)));

        // setup mBankAccountTableView
        mBankAccountNumberTableColumn.visibleProperty().bind(mShowAccountNumberCheckBox.selectedProperty());
        mBankAccountNumberLast4TableColumn.visibleProperty().bind(mShowAccountNumberCheckBox.selectedProperty().not());

        mBankAccountTypeTableColumn.setCellValueFactory(cd ->
                new ReadOnlyObjectWrapper<>(cd.getValue().getAccountType()));
        mRoutineNumberTableColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getRoutingNumber()));
        mBankAccountNumberLast4TableColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper("x-" + (cd.getValue().getAccountNumber().length() > 4 ?
                        cd.getValue().getAccountNumber().substring(cd.getValue().getAccountNumber().length()-4) :
                        cd.getValue().getAccountNumber())));
        mBankAccountNumberTableColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getAccountNumber()));

        mBankAccountTableView.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) ->
                mChangedProperty.set(!compareBankAccountDetailsWithADC(nv, mADC)));
    }
}
