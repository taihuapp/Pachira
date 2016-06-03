package net.taihuapp.facai168;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

/**
 * Created by ghe on 6/1/16.
 *
 */
public class EditSecurityPriceDialogController {

    private MainApp mMainApp;
    private Security mSecurity;
    private Stage mDialogStage;
    private ObservableList<Price> mPriceList = null;

    @FXML
    private Label mNameLabel;
    @FXML
    private Label mTickerLabel;
    @FXML
    private TableView<Price> mPriceTableView;
    @FXML
    private TableColumn<Price, LocalDate> mPriceDateTableColumn;
    @FXML
    private TableColumn<Price, BigDecimal> mPricePriceTableColumn;
    @FXML
    private DatePicker mNewDateDatePicker;
    @FXML
    private Button mAddButton;
    @FXML
    private Button mDeleteButton;
    @FXML
    private Button mCloseButton;

    void setMainApp(MainApp mainApp, Security security, Stage stage) {
        mMainApp = mainApp;
        mSecurity = security;
        mDialogStage = stage;
        mPriceList = FXCollections.observableList(mainApp.getSecurityPrice(security.getID()));

        mNameLabel.setText(security.getName());

        mTickerLabel.setText(security.getTicker());

        mPriceTableView.setItems(mPriceList);
        mPriceTableView.setEditable(true); // make it editable
        mPriceTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            mDeleteButton.setDisable(newValue == null);
        });

        mPriceDateTableColumn.setCellValueFactory(cellData->cellData.getValue().getDateProperty());

        mPricePriceTableColumn.setCellValueFactory(cellData->cellData.getValue().getPriceProperty());
        mPricePriceTableColumn.setCellFactory(TextFieldTableCell.forTableColumn(new StringConverter<BigDecimal>() {
            @Override
            public String toString(BigDecimal object) {
                if (object == null)
                    return null;
                return object.toString();
            }

            @Override
            public BigDecimal fromString(String string) {
                if (string == null)
                    return BigDecimal.ZERO;
                return new BigDecimal(string);
            }
        }));
        mPricePriceTableColumn.setOnEditCommit(e -> {
            int dbMode;
            if (e.getOldValue() == null)
                dbMode = 1; // new price, insert
            else
                dbMode = 2; // update
            BigDecimal newPrice = e.getNewValue();
            LocalDate date = e.getRowValue().getDate();
            if (newPrice.signum() < 0)
                return; // we don't want to anything with bad input (negative price)
            if (newPrice.signum() == 0) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation Dialog");
                alert.setHeaderText("Do you want to save zero price to database?");
                alert.setContentText(""
                        + "Security Name  : " + mSecurity.getName() + "\n"
                        + "Security Ticker: " + mSecurity.getTicker() + "\n"
                        + "Security ID    : " + mSecurity.getID() + "\n"
                        + "Date           : " + date + "\n"
                        + "Price          : " + newPrice + "?");
                Optional<ButtonType> result = alert.showAndWait();
                if (!result.isPresent() || result.get() != ButtonType.OK)
                    return; // don't save, go back
            }
            if (!mMainApp.insertUpdatePriceToDB(mSecurity.getID(), date, newPrice, dbMode)) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setHeaderText("Failed to insert/update price:");
                alert.setContentText("Security Name: " + mSecurity.getName() + "\n"
                        + "Security Ticker: " + mSecurity.getTicker() + "\n"
                        + "Security ID    : " + mSecurity.getID() + "\n"
                        + "Date           : " + date + "\n"
                        + "Price          : " + newPrice);
                alert.showAndWait();
            } else
                e.getRowValue().setPrice(newPrice);
        });
        // scroll to the last row
        // if size == 0, scrollTo(-1) will do nothing.
        int sizeOfList = mPriceList.size();
        mPriceTableView.scrollTo(sizeOfList-1);

        LocalDate defaultDate;
        if (sizeOfList == 0)
            defaultDate = LocalDate.now();
        else
            defaultDate = mPriceList.get(sizeOfList-1).getDate().plusDays(1);
        switch (defaultDate.getDayOfWeek()) {
            case SATURDAY:
                defaultDate = defaultDate.plusDays(2);
                break;
            case SUNDAY:
                defaultDate = defaultDate.plusDays(1);
                break;
            default:
                // do nothing
                break;
        }
        mNewDateDatePicker.setValue(defaultDate);
    }

    @FXML
    private void handleAdd() {
        Price newPrice = new Price(mNewDateDatePicker.getValue(), null);
        int index = Collections.binarySearch(mPriceList, newPrice, (o1, o2) -> o1.getDate().compareTo(o2.getDate()));
        if (index < 0) {
            index = -index - 1;
            mPriceList.add(index, newPrice);
        }
        mPriceTableView.scrollTo(index);
    }

    @FXML
    private void handleDelete() {
        int index = mPriceTableView.getSelectionModel().getSelectedIndex();
        if (index >= 0 && mMainApp.deleteSecurityPriceFromDB(mSecurity.getID(), mPriceList.get(index).getDate()))
            mPriceList.remove(index);
    }

    @FXML
    private void handleClose() { mDialogStage.close(); }
}
