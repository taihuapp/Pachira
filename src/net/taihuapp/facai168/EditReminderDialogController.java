package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.converter.NumberStringConverter;

/**
 * Created by ghe on 11/29/16.
 *
 */
public class EditReminderDialogController {
    private MainApp mMainApp;
    private Reminder mReminder;
    private Stage mDialogStage;

    @FXML
    private ChoiceBox<DateSchedule.BaseUnit> mBaseUnitChoiceBox;
    @FXML
    private DatePicker mStartDatePicker;
    @FXML
    private DatePicker mEndDatePicker;
    @FXML
    private TextField mNumPeriodTextField;
    @FXML
    private TextField mCountBeforeEndTextField;
    @FXML
    private RadioButton mDoMForwardRadioButton;
    @FXML
    private RadioButton mDoMBackwardRadioButton;
    @FXML
    private RadioButton mDoWForwardRadioButton;
    @FXML
    private RadioButton mDoWBackwardRadioButton;
    @FXML
    private RadioButton mNoEndRadioButton;
    @FXML
    private RadioButton mEndOnDateRadioButton;
    @FXML
    private RadioButton mEndOnCountsRadioButton;

    private final ToggleGroup mPeriodDirectionGroup;
    private final ToggleGroup mEndDateChoiceGroop;

    @FXML
    private void initialize() {
        mDoMForwardRadioButton.setToggleGroup(mPeriodDirectionGroup);
        mDoMBackwardRadioButton.setToggleGroup(mPeriodDirectionGroup);
        mDoWForwardRadioButton.setToggleGroup(mPeriodDirectionGroup);
        mDoWBackwardRadioButton.setToggleGroup(mPeriodDirectionGroup);

        mNoEndRadioButton.setToggleGroup(mEndDateChoiceGroop);
        mEndOnDateRadioButton.setToggleGroup(mEndDateChoiceGroop);
        mEndOnCountsRadioButton.setToggleGroup(mEndDateChoiceGroop);

        mBaseUnitChoiceBox.getItems().setAll(DateSchedule.BaseUnit.values());

        // todo need a changelistenser for mCountBeforeEndTextField
    }

    void setMainApp(MainApp mainApp, Reminder reminder, Stage stage) {
        mMainApp = mainApp;
        mReminder = reminder;
        mDialogStage = stage;

        // bind the properties now
        mBaseUnitChoiceBox.valueProperty().bindBidirectional(mReminder.getDateSchedule().getBaseUnitProperty());
        mStartDatePicker.valueProperty().bindBidirectional(mReminder.getDateSchedule().getStartDateProperty());
        mEndDatePicker.valueProperty().bindBidirectional(mReminder.getDateSchedule().getEndDateProperty());
        mNumPeriodTextField.textProperty().bindBidirectional(mReminder.getDateSchedule().getNumPeriodProperty(),
                new NumberStringConverter("#"));
        // we don't have anything to bind mCountBeforeEndTextField, but we have a textchangelistener for it
        // set in initialization
    }

    // constructor
    // has to be public
    public EditReminderDialogController() {
        mPeriodDirectionGroup = new ToggleGroup();
        mEndDateChoiceGroop = new ToggleGroup();
    }

    @FXML
    private void handleSave() {
        System.out.println(mReminder.getDateSchedule().getBaseUnit() + "|"
                + mReminder.getDateSchedule().getStartDate() + "|"
                + mReminder.getDateSchedule().getEndDate() + "|"
                + mReminder.getDateSchedule().getNumPeriod() + "|"
                + mReminder.getDateSchedule().isDOMBased() + "|"
                + mReminder.getDateSchedule().isForward());
    }

    @FXML
    private void handleClose() {
        close();
    }

    private void close() { mDialogStage.close(); }
}
