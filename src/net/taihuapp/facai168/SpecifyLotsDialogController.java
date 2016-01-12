package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ghe on 12/4/15.
 *
 */
public class SpecifyLotsDialogController {

    static class SpecifyLotInfo extends SecurityHolding.LotInfo {

        private ObjectProperty<BigDecimal> mSelectedSharesProperty = new SimpleObjectProperty<>();
        private ObjectProperty<BigDecimal> mRealizedPNLProperty = new SimpleObjectProperty<>();

        // constructor
        SpecifyLotInfo(SecurityHolding.LotInfo lotInfo) {
            super(lotInfo);
        }

        // getters
        ObjectProperty<BigDecimal> getSelectedSharesProperty() { return mSelectedSharesProperty; }
        ObjectProperty<BigDecimal> getRealizedPNLProperty() { return mRealizedPNLProperty; }
        BigDecimal getSelectedShares() { return getSelectedSharesProperty().get(); }

        // setters
        void setSelectedShares(BigDecimal s) { mSelectedSharesProperty.set(s); }

        // update realized pnl against a trade
        void updateRealizedPNL(Transaction t) {
            int scale = getCostBasis().scale();
            BigDecimal c0 = getCostBasis().multiply(getSelectedShares())
                    .divide(getQuantity().abs(), scale, BigDecimal.ROUND_HALF_UP);
            // t.getQuantity() is always positive
            BigDecimal c1 = t.getCostBasis().multiply(getSelectedShares())
                    .divide(t.getQuantity(), scale, BigDecimal.ROUND_HALF_UP);
            getRealizedPNLProperty().set(c1.add(c0).negate());
        }
    }

    private MainApp mMainApp;
    private Transaction mTransaction;
    private List<SecurityHolding.MatchInfo> mMatchInfoList = null;
    private Stage mDialogStage;
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
    private TableColumn<SpecifyLotInfo, String> mTypeColumn;
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
        for (SpecifyLotInfo sli : mSpecifyLotInfoList) {
            sli.setSelectedShares(BigDecimal.ZERO);
            sli.updateRealizedPNL(mTransaction);
        }
    }

    @FXML
    private void handleOK() {
        BigDecimal selectedQ = BigDecimal.ZERO;
        boolean selected = false;
        for (SpecifyLotInfo sli : mSpecifyLotInfoList) {
            if (sli.getSelectedShares() == null)
                continue; // not set yet, skip
            selected = true;
            if (sli.getSelectedShares().compareTo(BigDecimal.ZERO) != 0) {
                selectedQ = selectedQ.add(sli.getSelectedShares());
            }
        }

        if (selected) {
            if (selectedQ.compareTo(mTransaction.getQuantity().abs()) != 0) {
                // show warning dialog and go back
                String header = "";
                if (selectedQ.compareTo(mTransaction.getQuantity().abs()) > 0)
                    header = "Selected too many shares";
                else
                    header = "Selected too few shares";
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setHeaderText(header);
                alert.setContentText("Selected number of shares should match traded shares");
                alert.showAndWait();
                return;
            }

            mMatchInfoList.clear();
            for (SpecifyLotInfo sli : mSpecifyLotInfoList) {
                mMatchInfoList.add(new SecurityHolding.MatchInfo(mTransaction.getID(), sli.getTransactionID(),
                        sli.getSelectedShares()));
            }
        }
        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mDialogStage.close();
    }

    public void setMainApp(MainApp mainApp, Transaction t,
                           List<SecurityHolding.MatchInfo> matchInfoList, Stage stage) {
        mMainApp = mainApp;
        mMatchInfoList = matchInfoList;  // a link point to the input list
        mTransaction = t;
        mDialogStage = stage;

        String actionWord = "sold";
        mMainLabel0.setText("" + mTransaction.getQuantity() + " shares of "
                + mTransaction.getSecurityName() + " " + actionWord
                + " at " + mTransaction.getPrice() + "/share");
        mMainLabel1.setText("Please select share(s) to be " + actionWord);

        mMainApp.updateHoldingsList(mTransaction.getDate(), t.getID());

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

        int sliIdx = 0;  // index for running through
        for (SecurityHolding.MatchInfo mi : mMatchInfoList) {
            while (sliIdx < mSpecifyLotInfoList.size()) {
                SpecifyLotInfo sli = mSpecifyLotInfoList.get(sliIdx);
                if (sli.getTransactionID() == mi.getMatchTransactionID()) {
                    sli.setSelectedShares(mi.getMatchQuantity());
                    sli.updateRealizedPNL(t);
                    break;
                }
                sliIdx++;
            }
        }

        mLotInfoTableView.setEditable(true);
        mLotInfoTableView.setItems(mSpecifyLotInfoList);

        mDateColumn.setCellValueFactory(cellData->cellData.getValue().getDateProperty());
        mTypeColumn.setCellValueFactory(cellData->cellData.getValue().getTradeActionProperty());
        mPriceColumn.setCellValueFactory(cellData->cellData.getValue().getPriceProperty());
        mQuantityColumn.setCellValueFactory(cellData->cellData.getValue().getQuantityProperty());
        mSelectedColumn.setCellValueFactory(cellData->cellData.getValue().getSelectedSharesProperty());
        mPNLColumn.setCellValueFactory(cellData->cellData.getValue().getRealizedPNLProperty());

        mSelectedColumn.setCellFactory(TextFieldTableCell.<SpecifyLotInfo, BigDecimal>forTableColumn(
                new BigDecimalStringConverter()));
        mSelectedColumn.setOnEditCommit(
                event -> {
                    event.getTableView().getItems().get(event.getTablePosition().getRow())
                            .setSelectedShares(event.getNewValue());
                    event.getTableView().getItems().get(event.getTablePosition().getRow())
                            .updateRealizedPNL(mTransaction);
                }
        );
    }
}
