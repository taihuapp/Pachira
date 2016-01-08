package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.NumberStringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Created by ghe on 12/4/15.
 *
 */
public class SpecifyLotsDialogController {

    static class SpecifyLotInfo {
        private int mTransactionID;
        private ObjectProperty<LocalDate> mDateProperty = new SimpleObjectProperty<>();
        private ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
        private ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
        private ObjectProperty<BigDecimal> mSelectedSharesProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
        private ObjectProperty<BigDecimal> mPNLProperty = new SimpleObjectProperty<>();

        SpecifyLotInfo(SecurityHolding.LotInfo sl) {
            mTransactionID = sl.getTransactionID();
            mDateProperty.set(sl.getDate());
            mPriceProperty.set(sl.getPrice());
            mQuantityProperty.set(sl.getQuantity());
            mSelectedSharesProperty.set(BigDecimal.ZERO);
            mPNLProperty.set(BigDecimal.ZERO);
        }

        // getters
        ObjectProperty<LocalDate> getDateProperty() { return mDateProperty; }
        ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
        ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
        ObjectProperty<BigDecimal> getSelectedSharesProperty() { return mSelectedSharesProperty; }
        ObjectProperty<BigDecimal> getPNLProperty() { return mPNLProperty; }

        // setters
        void setSelectedShares(BigDecimal s) { mSelectedSharesProperty.set(s); }
    }

    private MainApp mMainApp;
    private Transaction mTransaction;
    private ObservableList<SpecifyLotInfo> mSpecifyLotInfoList = FXCollections.observableArrayList();

    @FXML
    private Button mResetButton;
    @FXML
    private Button mOKButton;
    @FXML
    private Button mCancelButton;
    @FXML
    private Label mMainLabel0;
    @FXML
    private Label mMainLabel1;

    @FXML
    private TableView<SpecifyLotInfo> mLotInfoTableView;
    @FXML
    private TableColumn<SpecifyLotInfo, LocalDate> mDateColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, BigDecimal> mPriceColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, BigDecimal> mQuantityColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, BigDecimal> mSelectedColumn;
    @FXML
    private TableColumn<SpecifyLotInfo, BigDecimal> mPNLColumn;

    @FXML
    private void handleReset() {
        System.out.println("Reset");
    }

    @FXML
    private void handleOK() {
        System.out.println("OK");
    }

    @FXML
    private void handleCancel() {
        System.out.println("Cancel");
    }

    public void setMainApp(MainApp mainApp, Transaction t) {
        mMainApp = mainApp;
        mTransaction = t;

        String actionWord = "sold";
        mMainLabel0.setText("" + mTransaction.getQuantity() + " shares of "
                + mTransaction.getSecurityName() + " " + actionWord
                + " at " + mTransaction.getPrice() + "/share");
        mMainLabel1.setText("Please select share(s) to be " + actionWord);

        mMainApp.updateHoldingsList(mTransaction.getDate());

        mSpecifyLotInfoList.clear(); // make sure nothing in the list
        for (SecurityHolding s : mMainApp.getSecurityHoldingList()) {
            if (s.getSecurityName().equals(mTransaction.getSecurityName())) {
                // we found the right security
                for (SecurityHolding.LotInfo sl : s.getLotInfoList()) {
                    mSpecifyLotInfoList.add(new SpecifyLotInfo(sl));
                }
                break;
            } else {
                System.out.println(s.getSecurityName() + "/" + mTransaction.getSecurityName());
            }
        }
        if (mSpecifyLotInfoList == null) {
            System.err.println("Null LotInfoList in SpecifiyLots...");
            return;
        }

        mLotInfoTableView.setEditable(true);
        mLotInfoTableView.setItems(mSpecifyLotInfoList);

        mDateColumn.setCellValueFactory(cellData->cellData.getValue().getDateProperty());
        mPriceColumn.setCellValueFactory(cellData->cellData.getValue().getPriceProperty());
        mQuantityColumn.setCellValueFactory(cellData->cellData.getValue().getQuantityProperty());
        mSelectedColumn.setCellValueFactory(cellData->cellData.getValue().getSelectedSharesProperty());
        mPNLColumn.setCellValueFactory(cellData->cellData.getValue().getPNLProperty());

        mSelectedColumn.setCellFactory(TextFieldTableCell.<SpecifyLotInfo, BigDecimal>forTableColumn(
                new BigDecimalStringConverter()));
        mSelectedColumn.setOnEditCommit(
                new EventHandler<TableColumn.CellEditEvent<SpecifyLotInfo, BigDecimal>>() {
                    @Override
                    public void handle(TableColumn.CellEditEvent<SpecifyLotInfo, BigDecimal> event) {
                        event.getTableView().getItems().get(event.getTablePosition().getRow())
                                .setSelectedShares(event.getNewValue());
                    }
                }
        );
    }

}
