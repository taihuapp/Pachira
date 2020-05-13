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

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.converter.NumberStringConverter;
import org.controlsfx.control.textfield.TextFields;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

public class EditReminderDialogController {

    private class TagIDConverter extends StringConverter<Integer> {
        public Integer fromString(String tagName) {
            Tag t = mMainApp.getTagByName(tagName);
            return t == null ? 0 : t.getID();
        }
        public String toString(Integer tid) {
            Tag t = (tid == null) ? null : mMainApp.getTagByID(tid);
            return t == null ? "" : t.getName();
        }
    }

    // Category and Transfer Account
    private class CategoryIDConverter extends StringConverter<Integer> {
        public Integer fromString(String name) {
            return mMainApp.mapCategoryOrAccountNameToID(name);
        }
        public String toString(Integer id) {
            if (id == null)
                return "";
            return mMainApp.mapCategoryOrAccountIDToName(id);
        }
    }

    // this converts account id to account name
    // different from the converter in EditTransactions
    private class AccountIDConverter extends StringConverter<Integer> {
        public Integer fromString(String accountName) {
            Account a = mMainApp.getAccountByName(accountName);
            return a == null ? 0 : a.getID();
        }
        public String toString(Integer aid) {
            Account a = (aid == null) ? null : mMainApp.getAccountByID(aid);
            return a == null ? "" : a.getName();
        }
    }

    private MainApp mMainApp;
    private Reminder mReminder;
    private Stage mDialogStage;

    @FXML
    private ChoiceBox<Reminder.Type> mTypeChoiceBox;
    @FXML
    private TextField mPayeeTextField;
    @FXML
    private TextField mAmountTextField;
    @FXML
    private TextField mEstimateNumOccuranceTextField;
    @FXML
    private Label mAccountIDLabel;
    @FXML
    private ComboBox<Integer> mAccountIDComboBox;
    @FXML
    private Label mCategoryIDLabel;
    @FXML
    private ComboBox<Integer> mCategoryIDComboBox;
    @FXML
    private Label mTransferAccountIDLabel;
    @FXML
    private ComboBox<Integer> mTransferAccountIDComboBox;
    @FXML
    private ComboBox<Integer> mTagIDComboBox;
    @FXML
    private TextField mMemoTextField;
    @FXML
    private ChoiceBox<DateSchedule.BaseUnit> mBaseUnitChoiceBox;
    @FXML
    private DatePicker mStartDatePicker;
    @FXML
    private DatePicker mEndDatePicker;
    @FXML
    private TextField mNumPeriodTextField;
    @FXML
    private TextField mAlertDayTextField;
    @FXML
    private ToggleButton mDOMToggleButton;
    @FXML
    private ToggleButton mDOWToggleButton;
    @FXML
    private ToggleButton mFWDToggleButton;
    @FXML
    private ToggleButton mREVToggleButton;
    @FXML
    private Label mDSDescriptionLabel;
    @FXML
    private RadioButton mFixedAmountRadioButton;
    @FXML
    private RadioButton mEstimateAmountRadioButton;

    private final ToggleGroup mDOMGroup = new ToggleGroup();
    private final ToggleGroup mFWDGroup = new ToggleGroup();
    private final ToggleGroup mAmountGroup = new ToggleGroup();

    @FXML
    private void initialize() {
        mBaseUnitChoiceBox.getItems().setAll(DateSchedule.BaseUnit.values());

        mDOMToggleButton.setToggleGroup(mDOMGroup);
        mDOWToggleButton.setToggleGroup(mDOMGroup);

        mFWDToggleButton.setToggleGroup(mFWDGroup);
        mREVToggleButton.setToggleGroup(mFWDGroup);

        mFixedAmountRadioButton.setToggleGroup(mAmountGroup);
        mEstimateAmountRadioButton.setToggleGroup(mAmountGroup);

        mAmountTextField.visibleProperty().bindBidirectional(mFixedAmountRadioButton.selectedProperty());
        mEstimateNumOccuranceTextField.visibleProperty().bindBidirectional(mEstimateAmountRadioButton.selectedProperty());

        // todo need a changelistenser for mCountBeforeEndTextField

        mTypeChoiceBox.getItems().setAll(Reminder.Type.values());
    }

    void setMainApp(MainApp mainApp, Reminder reminder, Stage stage) {
        mMainApp = mainApp;
        mReminder = reminder;
        mDialogStage = stage;

        // bind the properties now
        // seems no need to do unbindbidirectional
        mTypeChoiceBox.valueProperty().bindBidirectional(mReminder.getTypeProperty());
        mPayeeTextField.textProperty().bindBidirectional(mReminder.getPayeeProperty());
        TextFields.bindAutoCompletion(mPayeeTextField, mMainApp.getPayeeSet());

        mAmountTextField.textProperty().bindBidirectional(mReminder.getAmountProperty(),
                new BigDecimalStringConverter());
        mEstimateNumOccuranceTextField.textProperty().bindBidirectional(mReminder.getEstimateCountProperty(),
                new IntegerStringConverter());

        mAccountIDComboBox.setConverter(new AccountIDConverter());
        mAccountIDComboBox.getItems().clear();
        for (Account a : mMainApp.getAccountList(Account.Type.SPENDING, false, true))
            mAccountIDComboBox.getItems().add(a.getID());
        Bindings.bindBidirectional(mAccountIDComboBox.valueProperty(), mReminder.getAccountIDProperty());
        if (mAccountIDComboBox.getSelectionModel().isEmpty())
            mAccountIDComboBox.getSelectionModel().selectFirst(); // if no account selected, default the first.

        mCategoryIDComboBox.setConverter(new CategoryIDConverter());
        ObservableList<Integer> idList = FXCollections.observableArrayList();
        idList.add(0);
        for (Category category : mMainApp.getCategoryList())
            idList.add(category.getID());
        for (Account account : mainApp.getAccountList(null, false, true)) {
            idList.add(-account.getID());
        }
        new EditTransactionDialogControllerNew.CategoryTransferAccountIDComboBoxWrapper(mCategoryIDComboBox, idList);
        Bindings.bindBidirectional(mCategoryIDComboBox.valueProperty(), mReminder.getCategoryIDProperty());

        mTagIDComboBox.setConverter(new TagIDConverter());
        mTagIDComboBox.getItems().clear();
        for (Tag t : mMainApp.getTagList())
            mTagIDComboBox.getItems().add(t.getID());
        Bindings.bindBidirectional(mTagIDComboBox.valueProperty(), mReminder.getTagIDProperty());

        mTransferAccountIDComboBox.setConverter(new AccountIDConverter());
        mTransferAccountIDComboBox.getItems().clear();
        for (Account a : mMainApp.getAccountList(Account.Type.SPENDING, false, true))
            mTransferAccountIDComboBox.getItems().add(a.getID());

        mMemoTextField.textProperty().bindBidirectional(mReminder.getMemoProperty());

        // bind properties for DateSchedule fields
        mBaseUnitChoiceBox.valueProperty().bindBidirectional(mReminder.getDateSchedule().getBaseUnitProperty());
        mStartDatePicker.valueProperty().bindBidirectional(mReminder.getDateSchedule().getStartDateProperty());
        mEndDatePicker.valueProperty().bindBidirectional(mReminder.getDateSchedule().getEndDateProperty());
        mNumPeriodTextField.textProperty().bindBidirectional(mReminder.getDateSchedule().getNumPeriodProperty(),
                new NumberStringConverter("#"));
        mAlertDayTextField.textProperty().bindBidirectional(mReminder.getDateSchedule().getAlertDayProperty(),
                new NumberStringConverter("#"));

        mDOMToggleButton.textProperty().bind(Bindings.createStringBinding(
                () -> "Count days of " + mBaseUnitChoiceBox.valueProperty().get().toString().toLowerCase(),
                mBaseUnitChoiceBox.valueProperty()));


        // we don't have anything to bind mCountBeforeEndTextField, but we have a textchangelistener for it
        // set in initialization

        mDOMToggleButton.selectedProperty().bindBidirectional(mReminder.getDateSchedule().getIsDOMBasedProperty());
        mFWDToggleButton.selectedProperty().bindBidirectional(mReminder.getDateSchedule().getIsForwardProperty());
        final Callable<Boolean> converter = () -> {
            switch (mReminder.getDateSchedule().getBaseUnit()) {
                case DAY:
                case WEEK:
                    return false;
                case MONTH:
                case QUARTER:
                case YEAR:
                default:
                    return true;
            }
        };
        mDOMToggleButton.visibleProperty().bind(Bindings.createBooleanBinding(converter,
                mReminder.getDateSchedule().getBaseUnitProperty()));
        mDOWToggleButton.visibleProperty().bind(Bindings.createBooleanBinding(converter,
                mReminder.getDateSchedule().getBaseUnitProperty()));
        mFWDToggleButton.visibleProperty().bind(Bindings.createBooleanBinding(converter,
                mReminder.getDateSchedule().getBaseUnitProperty()));
        mREVToggleButton.visibleProperty().bind(Bindings.createBooleanBinding(converter,
                mReminder.getDateSchedule().getBaseUnitProperty()));

        mDSDescriptionLabel.textProperty().bind(mReminder.getDateSchedule().getDescriptionProperty());

        mEstimateAmountRadioButton.setSelected(mReminder.getEstimateCount() > 0);
    }

    @FXML
    private void handleSplit() {
        BigDecimal netAmount = BigDecimal.ZERO;
        if (mFixedAmountRadioButton.isSelected()) {
            netAmount = mReminder.getAmount();
            if (mReminder.getType() == Reminder.Type.DEPOSIT)
                netAmount = netAmount.negate();
        }

        List<SplitTransaction> outputSplitTransactionList = mMainApp.showSplitTransactionsDialog(mDialogStage,
                mReminder.getSplitTransactionList(), netAmount);

        if (outputSplitTransactionList != null) {
            // splitTransactionList changed
            mReminder.setSplitTransactionList(outputSplitTransactionList);
        }
    }

    @FXML
    private void handleSave() {
        // validation
        // todo

        List<SplitTransaction> stList = mReminder.getSplitTransactionList();
        if (!stList.isEmpty()) {
            BigDecimal netAmount = mReminder.getAmount();
            if (mReminder.getType() == Reminder.Type.DEPOSIT)
                netAmount = netAmount.negate();
            for (SplitTransaction st : stList) {
                netAmount = netAmount.add(st.getAmount());
            }
            if (netAmount.compareTo(BigDecimal.ZERO) != 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText("Split Transaction amount not match with total amount.");
                alert.setContentText("Please check split");
                alert.showAndWait();
                return;
            }
        }

        if (!mEstimateAmountRadioButton.isSelected())
            mReminder.setEstimateCount(0);

        // enter
        try {
            if (!mMainApp.setDBSavepoint()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("DB Save Point unexpected set.");
                alert.setContentText("Something is wrong.  Please restart.");
                alert.showAndWait();
                return;
            }
            mMainApp.insertUpdateReminderToDB(mReminder);
            mMainApp.commitDB();
        } catch (SQLException e) {
            try {
                mMainApp.showExceptionDialog("Datebase Error", "insert or update Reminder failed",
                        MainApp.SQLExceptionToString(e), e);
                mMainApp.rollbackDB();
            } catch (SQLException e1) {
                mMainApp.showExceptionDialog("Database Error",
                        "Failed to rollback reminder database update",
                        MainApp.SQLExceptionToString(e), e);
            }
        } finally {
            try {
                mMainApp.releaseDBSavepoint();
            } catch (SQLException e) {
                mMainApp.showExceptionDialog("Database Error",
                        "set autocommit failed after insert update reminder",
                        MainApp.SQLExceptionToString(e), e);
            }
        }
        mMainApp.initReminderMap();
        mMainApp.initReminderTransactionList();
        close();
    }

    @FXML
    private void handleClose() {
        close();
    }

    private void close() { mDialogStage.close(); }
}
