package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.converter.NumberStringConverter;

import java.util.concurrent.Callable;

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
    private RadioButton mNoEndRadioButton;
    @FXML
    private RadioButton mEndOnDateRadioButton;
    @FXML
    private RadioButton mEndOnCountsRadioButton;

    private final ToggleGroup mDOMGroup = new ToggleGroup();
    private final ToggleGroup mFWDGroup = new ToggleGroup();
    private final ToggleGroup mEndDateChoiceGroop = new ToggleGroup();

    @FXML
    private void initialize() {
        mBaseUnitChoiceBox.getItems().setAll(DateSchedule.BaseUnit.values());

        mDOMToggleButton.setToggleGroup(mDOMGroup);
        mDOWToggleButton.setToggleGroup(mDOMGroup);

        mFWDToggleButton.setToggleGroup(mFWDGroup);
        mREVToggleButton.setToggleGroup(mFWDGroup);
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
