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

import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.domain.data.banking.AccountType;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
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
import net.taihuapp.pachira.net.taihuapp.pachira.dc.AccountDC;
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
    private BooleanProperty mChangedProperty = new SimpleBooleanProperty(false);
    private boolean isChanged() { return mChangedProperty.get(); }

    @FXML
    private Label mAccountNameLabel;
    @FXML
    private ComboBox<DirectConnection> mDCComboBox;
    @FXML
    private CheckBox mShowAccountNumberCheckBox;
    @FXML
    private Button mSaveButton;

    @FXML
    private TableView<BankAccountDetails> mBankAccountTableView;
    @FXML
    private TableColumn<BankAccountDetails, AccountType> mAccountTypeTableColumn;
    @FXML
    private TableColumn<BankAccountDetails, String> mRoutineNumberTableColumn;
    @FXML
    private TableColumn<BankAccountDetails, String> mLast4AccountNumberTableColumn;
    @FXML
    private TableColumn<BankAccountDetails, String> mAccountNumberTableColumn;

    private boolean compareBankAccountDetailsWithADC(BankAccountDetails bad, AccountDC adc) {
        if (bad == null)
            return adc.getDCID() == 0;

        if (!bad.getAccountType().name().equals(adc.getAccountType()))
            return false;
        if (!bad.getRoutingNumber().equals(adc.getRoutingNumber()))
            return false;

        char[] clearADCAccountNumber = null;
        try {
            clearADCAccountNumber = mMainApp.decrypt(adc.getEncryptedAccountNumber());
            return compareStringWithChars(bad.getAccountNumber(), clearADCAccountNumber);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException |
                                UnrecoverableKeyException | NoSuchPaddingException | InvalidKeyException |
                                InvalidAlgorithmParameterException | IllegalBlockSizeException |
                                BadPaddingException e ) {
            mLogger.error(e.getClass(), e);
            mMainApp.showExceptionDialog(e.getClass().getName(), "Decryption Error",
                    e.getMessage(), e);
        } finally {
            if (clearADCAccountNumber != null)
                Arrays.fill(clearADCAccountNumber, ' ');
        }
        return false;
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
            switch (mMainApp.getCurrentAccount().getType()) {
                case SPENDING:
                    ObservableList<BankAccountDetails> bankAccounts = FXCollections.observableArrayList();
                    BankAccountDetails accountToSelect = null;
                    if (ndc.getID() > 0) {
                        try {
                            for (AccountProfile ap : mMainApp.DCDownloadFinancialInstitutionAccountProfiles(ndc)) {
                                if (ap != null && ap.getBankSpecifics() != null) {
                                    BankAccountDetails bad = ap.getBankSpecifics().getBankAccount();
                                    bankAccounts.add(bad);
                                    if (compareBankAccountDetailsWithADC(bad, mADC)) {
                                        accountToSelect = bad;
                                    }
                                }
                            }
                        } catch (MalformedURLException e) {
                            mLogger.error("MalformedURLException", e);
                            mMainApp.showExceptionDialog("MalformedURLException", "Bad URL",
                                    e.getMessage(), e);

                        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException |
                                UnrecoverableKeyException | NoSuchPaddingException | InvalidKeyException |
                                InvalidAlgorithmParameterException | IllegalBlockSizeException |
                                BadPaddingException e) {
                            mLogger.error(e.getClass(), e);
                            mMainApp.showExceptionDialog(e.getClass().getName(), "Decryption Error",
                                    e.getMessage(), e);
                        } catch (OFXException e) {
                            mLogger.error("OFXException", e);
                            mMainApp.showExceptionDialog("OFXException", "OFXCeception", e.getMessage(), e);
                        }
                    }

                    mBankAccountTableView.getItems().setAll(bankAccounts);
                    if (accountToSelect == null)
                        mBankAccountTableView.getSelectionModel().clearSelection();
                    else
                        mBankAccountTableView.getSelectionModel().select(accountToSelect);

                    break;
                case INVESTING:
                case DEBT:
                case PROPERTY:
                default:
                    break;
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
            BankAccountDetails bad = mBankAccountTableView.getSelectionModel().getSelectedItem();
            if (dcID <= 0) {
                if (MainApp.showConfirmationDialog("Confirmation", "Direct connection is empty",
                        "entry will be deleted"))
                    mMainApp.deleteAccountDCFromDB(aid);
            } else if (bad == null) {
                if (MainApp.showConfirmationDialog("Confirmation", "No account selected",
                        "Account Direct Connection entry will be deleted"))
                    mMainApp.deleteAccountDCFromDB(aid);
            } else {
                String at = bad.getAccountType().name();
                String rn = bad.getRoutingNumber();
                String ean = mMainApp.encrypt(bad.getAccountNumber().toCharArray());
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
            mMainApp.showExceptionDialog("Exception", "Database Exception",
                    MainApp.SQLExceptionToString(e), e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | KeyStoreException | UnrecoverableKeyException
                | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException e) {
            mLogger.error("Encrypt throws " + e.getClass().getName(), e);
            mMainApp.showExceptionDialog("Exception", "Encryption Exception", e.getMessage(), e);
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
        mShowAccountNumberCheckBox.selectedProperty().addListener((obs, ov, nv) -> {
            mLast4AccountNumberTableColumn.setVisible(!nv);
            mAccountNumberTableColumn.setVisible(nv);
        });

        mShowAccountNumberCheckBox.setSelected(false);

        mSaveButton.disableProperty().bind(mChangedProperty.not());

        mAccountTypeTableColumn.setCellValueFactory(cd ->
                new ReadOnlyObjectWrapper<>(cd.getValue().getAccountType()));
        mRoutineNumberTableColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getRoutingNumber()));
        mLast4AccountNumberTableColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper("x-" + (cd.getValue().getAccountNumber().length() > 4 ?
                        cd.getValue().getAccountNumber().substring(cd.getValue().getAccountNumber().length()-4) :
                        cd.getValue().getAccountNumber())));
        mAccountNumberTableColumn.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getAccountNumber()));

        mBankAccountTableView.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) ->
                mChangedProperty.set(!compareBankAccountDetailsWithADC(nv, mADC)));
    }
}
