/*
 * Copyright (C) 2018-2023.  Guangliang He.  All Rights Reserved.
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
import net.taihuapp.pachira.dao.DaoException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.net.MalformedURLException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

public class EditAccountDirectConnectionDialogController {

    private static final Logger mLogger = LogManager.getLogger(EditAccountDirectConnectionDialogController.class);

    private class DCInfoConverter extends StringConverter<DirectConnection> {
        public DirectConnection fromString(String s) {
            return mainModel.getDCInfo(dc -> dc.getName().equals(s)).orElse(null);
        }
        public String toString(DirectConnection dc) { return dc == null ? "" : dc.getName(); }
    }

    private MainModel mainModel;
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

    // return the stage.  It only works after the controller is properly initialized.
    private Stage getStage() { return (Stage) mAccountTableView.getScene().getWindow(); }

    // compare if information in accountDetails matches what is stored in accountDC
    private boolean compareAccountDetailsWithADC(AccountDetails accountDetails, AccountDC accountDC) {
        if (accountDetails == null)
            return accountDC.getDCID() == 0;

        char[] clearADCAccountNumber = null;
        try {
            if (mADC.getEncryptedAccountNumber().isEmpty())
                return false;
            clearADCAccountNumber = mainModel.decrypt(mADC.getEncryptedAccountNumber());
            return compareStringWithChars(accountDetails.getAccountNumber(), clearADCAccountNumber);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException |
                UnrecoverableKeyException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException |
                BadPaddingException | IllegalArgumentException e ) {
            mLogger.error(e.getClass(), e);
            MainApp.showExceptionDialog(getStage(), e.getClass().getName(), "Decryption Error",
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

    void setMainModel(MainModel mainModel, AccountDC adc) {

        this.mainModel = mainModel;
        mADC = adc;

        Account account = mainModel.getCurrentAccount();
        mDCComboBox.setConverter(new DCInfoConverter());
        mDCComboBox.getItems().clear();
        // adding null item for ComboBox would lead to a IndexOutOfBoundsException when null is selected
        // this is a bug in javafx 8 https://bugs.openjdk.java.net/browse/JDK-8134923
        mDCComboBox.getItems().add(new DirectConnection(0, "", 0, "", ""));  // add a blank one
        mDCComboBox.getItems().addAll(mainModel.getDCInfoList());

        mAccountNameLabel.setText(account.getName());

        // add change listeners
        mDCComboBox.getSelectionModel().selectedItemProperty().addListener((obs, odc, ndc) -> {
            if (ndc.getID() < 0)
                return;  // why are we here?

            Account.Type currentAccountType = mainModel.getCurrentAccount().getType();
            ObservableList<BankAccountDetails> bankAccounts = FXCollections.observableArrayList();
            ObservableList<CreditCardAccountDetails> creditCardAccounts = FXCollections.observableArrayList();
            ObservableList<InvestmentAccountDetails> investmentAccounts = FXCollections.observableArrayList();

            try {
                for (AccountProfile ap : mainModel.DCDownloadFinancialInstitutionAccountProfiles(ndc)) {
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
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException |
                    UnrecoverableKeyException | NoSuchPaddingException | InvalidKeyException |
                    InvalidAlgorithmParameterException | IllegalBlockSizeException | ModelException |
                    BadPaddingException | DaoException | OFXException | MalformedURLException e) {
                mLogger.error(e.getClass().getName(), e);
                DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), e.getClass().getName(),
                        e.toString(), e);
            }
        });
        mDCComboBox.getSelectionModel().select(mainModel.getDCInfo(dc -> dc.getID() == adc.getDCID()).orElse(null));
    }

    @FXML
    private void handleSave() {
        int dcID = mDCComboBox.getValue().getID();
        Account account = mainModel.getCurrentAccount();
        int aid = account.getID();

        try {
            AccountDetails accountDetails = mBankAccountTableView.isVisible() ?
                    mBankAccountTableView.getSelectionModel().getSelectedItem()
                    : mAccountTableView.getSelectionModel().getSelectedItem();
            if (dcID <= 0) {
                if (MainApp.showConfirmationDialog("Confirmation", "Direct connection is empty",
                        "entry will be deleted"))
                    mainModel.deleteAccountDC(aid);
            } else if (accountDetails == null) {
                if (MainApp.showConfirmationDialog("Confirmation", "No account selected",
                        "Account Direct Connection entry will be deleted"))
                    mainModel.deleteAccountDC(aid);
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
                String ean = mainModel.encrypt(accountDetails.getAccountNumber().toCharArray());
                // note, every time an AccountDC is changed, the last download date time is reset
                java.util.Date lastDownloadDate;
                LocalDate lastReconcileDate = account.getLastReconcileDate();
                if (lastReconcileDate == null)
                    lastDownloadDate = new java.util.Date(0L); // set to very early so we can download everything
                else
                    lastDownloadDate = java.util.Date.from(lastReconcileDate.atStartOfDay()
                            .atZone(ZoneId.systemDefault()).toInstant());
                mainModel.mergeAccountDC(new AccountDC(aid, at, dcID, rn, ean, lastDownloadDate, null));
            }
            getStage().close();
        } catch (DaoException e) {
            final String msg = e.getErrorCode() + " DaoException on deleting Account Direct Connection info";
            mLogger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException e) {
            mLogger.error("Encrypt throws " + e.getClass().getName(), e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), "Encryption Exception",
                    e.toString(), e);
        }
    }

    @FXML
    private void handleCancel() {
        if (isChanged() && !MainApp.showConfirmationDialog("Confirmation", "Content has been changed",
                "Do you want to discard changes?")) {
            return;
        }
        getStage().close();
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
