package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.NumberStringConverter;

import java.util.concurrent.Callable;

/**
 * Created by ghe on 11/29/16.
 *
 */
public class EditReminderDialogController {

    private class TagIDConverter extends StringConverter<Integer> {
        public Integer fromString(String tagName) {
            Tag t = mMainApp.getTagByName(tagName);
            return t == null ? 0 : t.getID();
        }
        public String toString(Integer tid) {
            Tag t = mMainApp.getTagByID(tid);
            return t == null ? "" : t.getName();
        }
    }

    private class CategoryIDConverter extends StringConverter<Integer> {
        public Integer fromString(String categoryName) {
            Category c = mMainApp.getCategoryByName(categoryName);
            return c == null ? 0 : c.getID();
        }
        public String toString(Integer cid) {
            Category c = mMainApp.getCategoryByID(cid);
            return c == null ? "" : c.getName();
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
            Account a = mMainApp.getAccountByID(aid);
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

    private final ToggleGroup mDOMGroup = new ToggleGroup();
    private final ToggleGroup mFWDGroup = new ToggleGroup();

    @FXML
    private void initialize() {
        mBaseUnitChoiceBox.getItems().setAll(DateSchedule.BaseUnit.values());

        mDOMToggleButton.setToggleGroup(mDOMGroup);
        mDOWToggleButton.setToggleGroup(mDOMGroup);

        mFWDToggleButton.setToggleGroup(mFWDGroup);
        mREVToggleButton.setToggleGroup(mFWDGroup);
        // todo need a changelistenser for mCountBeforeEndTextField

        mTypeChoiceBox.getItems().setAll(Reminder.Type.values());
        final Callable<Boolean> converter0 = () -> mTypeChoiceBox.valueProperty().get() != Reminder.Type.TRANSFER;
        mCategoryIDLabel.visibleProperty().bind(Bindings.createBooleanBinding(converter0,
                mTypeChoiceBox.valueProperty()));
        mCategoryIDComboBox.visibleProperty().bind(Bindings.createBooleanBinding(converter0,
                mTypeChoiceBox.valueProperty()));

        final Callable<Boolean> converter1 = () ->  mTypeChoiceBox.valueProperty().get() == Reminder.Type.TRANSFER;
        mTransferAccountIDLabel.visibleProperty().bind(Bindings.createBooleanBinding(converter1,
                mTypeChoiceBox.valueProperty()));
        mTransferAccountIDComboBox.visibleProperty().bind(Bindings.createBooleanBinding(converter1,
                mTypeChoiceBox.valueProperty()));
    }

    void setMainApp(MainApp mainApp, Reminder reminder, Stage stage) {
        mMainApp = mainApp;
        mReminder = reminder;
        mDialogStage = stage;

        // bind the properties now
        // seems no need to do unbindbidirectional
        mTypeChoiceBox.valueProperty().bindBidirectional(mReminder.getTypeProperty());
        mPayeeTextField.textProperty().bindBidirectional(mReminder.getPayeeProperty());
        mAmountTextField.textProperty().bindBidirectional(mReminder.getAmountProperty(),
                new BigDecimalStringConverter());

        mAccountIDComboBox.setConverter(new AccountIDConverter());
        mAccountIDComboBox.getItems().clear();
        for (Account a : mMainApp.getAccountList(Account.Type.SPENDING, false, true))
            mAccountIDComboBox.getItems().add(a.getID());
        Bindings.bindBidirectional(mAccountIDComboBox.valueProperty(), mReminder.getAccountIDProperty().asObject());
        if (mAccountIDComboBox.getSelectionModel().isEmpty())
            mAccountIDComboBox.getSelectionModel().selectFirst(); // if no account selected, default the first.

        mCategoryIDComboBox.setConverter(new CategoryIDConverter());
        mCategoryIDComboBox.getItems().clear();
        mCategoryIDComboBox.getItems().add(0);
        for (Category c : mMainApp.getCategoryList())
            mCategoryIDComboBox.getItems().add(c.getID());
        Bindings.bindBidirectional(mCategoryIDComboBox.valueProperty(), mReminder.getCategoryIDProperty().asObject());

        mTagIDComboBox.setConverter(new TagIDConverter());
        mTagIDComboBox.getItems().clear();
        for (Tag t : mMainApp.getTagList())
            mTagIDComboBox.getItems().add(t.getID());
        Bindings.bindBidirectional(mTagIDComboBox.valueProperty(), mReminder.getTagIDProperty().asObject());

        mTransferAccountIDComboBox.setConverter(new AccountIDConverter());
        mTransferAccountIDComboBox.getItems().clear();
        for (Account a : mMainApp.getAccountList(Account.Type.SPENDING, false, true))
            mAccountIDComboBox.getItems().add(a.getID());
        Bindings.bindBidirectional(mTransferAccountIDComboBox.valueProperty(),
                mReminder.getTransferAccountIDProperty().asObject());

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

    }

    @FXML
    private void handleSave() {
        // validation
        // todo

        System.out.println(mReminder.getDateSchedule().getBaseUnit() + "|"
                + mReminder.getDateSchedule().getStartDate() + "|"
                + mReminder.getDateSchedule().getEndDate() + "|"
                + mReminder.getDateSchedule().getNumPeriod() + "|"
                + mReminder.getDateSchedule().getAlertDay() + "|"
                + mReminder.getDateSchedule().isDOMBased() + "|"
                + mReminder.getDateSchedule().isForward());
        // enter
        mMainApp.insertUpdateReminderToDB(mReminder);
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
