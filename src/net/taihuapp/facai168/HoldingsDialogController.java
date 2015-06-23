package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Created by ghe on 6/21/15.
 * A dialog to display holdings
 */

public class HoldingsDialogController {

    private MainApp mMainApp;

    @FXML
    private DatePicker mDatePicker;

    @FXML
    private TableColumn<SecurityHolding, String> mNameColumn;
    @FXML
    private TableColumn<SecurityHolding, BigDecimal> mPriceColumn;
    @FXML
    private TableColumn<SecurityHolding, BigDecimal> mQuantityColumn;
    @FXML
    private TableColumn<SecurityHolding, BigDecimal> mMarketValueColumn;
    @FXML
    private TableColumn<SecurityHolding, BigDecimal> mCostBasisColumn;
    @FXML
    private TableColumn<SecurityHolding, BigDecimal> mPNLColumn;
    @FXML
    private TableColumn<SecurityHolding, BigDecimal> mPctRetColumn;



    public void setMainApp(MainApp mainApp) { mMainApp = mainApp; }

    private void updateHoldings() {
        LocalDate date = mDatePicker.getValue();
        System.out.println("Selected date: " + mDatePicker.getValue());
    }

    @FXML
    private void initialize() {

        mNameColumn.setCellValueFactory(cellData->cellData.getValue().getSecurityNameProperty());
        mPriceColumn.setCellValueFactory(cellData->cellData.getValue().getPriceProperty());
        mQuantityColumn.setCellValueFactory(cellData->cellData.getValue().getQuantityProperty());
        mMarketValueColumn.setCellValueFactory(cellData->cellData.getValue().getMarketValueProperty());
        mCostBasisColumn.setCellValueFactory(cellData->cellData.getValue().getCostBasisProperty());
        mPNLColumn.setCellValueFactory(cellData->cellData.getValue().getPNLProperty());
        mPctRetColumn.setCellValueFactory(cellData -> cellData.getValue().getPctRetProperty());

        mDatePicker.setOnAction(event -> { updateHoldings(); });
        mDatePicker.setValue(LocalDate.now());


    }
}
