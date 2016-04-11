package net.taihuapp.facai168;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;

/**
 * Created by ghe on 6/21/15.
 * A dialog to display holdings
 */

public class HoldingsDialogController {

    private MainApp mMainApp;

    @FXML
    private DatePicker mDatePicker;

    @FXML
    private TreeTableView<LotHolding> mSecurityHoldingTreeTableView;
    @FXML
    private TreeTableColumn<LotHolding, String> mNameColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mPriceColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mQuantityColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mMarketValueColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mCostBasisColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mPNLColumn;
    @FXML
    private TreeTableColumn<LotHolding, BigDecimal> mPctReturnColumn;

    private void populateTreeTable() {
        mSecurityHoldingTreeTableView.setRoot(new TreeItem<>(mMainApp.getRootSecurityHolding()));
        for (LotHolding l : mMainApp.getSecurityHoldingList()) {
            TreeItem<LotHolding> t = new TreeItem<>(l);
            mSecurityHoldingTreeTableView.getRoot().getChildren().add(t);
            for (LotHolding l1 : ((SecurityHolding) l).getLotInfoList()) {
                t.getChildren().add(new TreeItem<>(l1));
            }
        }

        // set initial sort order
        mNameColumn.setSortType(TreeTableColumn.SortType.ASCENDING);
        mSecurityHoldingTreeTableView.getSortOrder().add(mNameColumn);
    }

    public void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;

        mSecurityHoldingTreeTableView.setShowRoot(false);
        mSecurityHoldingTreeTableView.setSortMode(TreeSortMode.ONLY_FIRST_LEVEL);

        mNameColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, String> p) ->
                new ReadOnlyStringWrapper(p.getValue().getValue().getLabel()));
        mNameColumn.setComparator(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                if (mNameColumn.getSortType() == TreeTableColumn.SortType.ASCENDING) {
                    // sorting accending
                    if (o1.equals("TOTAL"))
                        return 1;
                    if (o2.equals("TOTAL"))
                        return -1;
                    if (o1.equals("CASH"))
                        return 1;
                    if (o2.equals("CASH"))
                        return -1;
                    return o1.compareTo(o2);
                }

                // sorting decending
                if (o1.equals("TOTAL"))
                    return -1;
                if (o2.equals("TOTAL"))
                    return 1;
                if (o1.equals("CASH"))
                    return -1;
                if (o2.equals("CASH"))
                    return 1;
                return o1.compareTo(o2);
            }
        });

        mPriceColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getPrice()));
        mPriceColumn.setComparator(null);

        mQuantityColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getQuantity()));
        mQuantityColumn.setComparator(null);

        mMarketValueColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getMarketValue()));
        mMarketValueColumn.setComparator(null);

        mCostBasisColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getCostBasis()));
        mCostBasisColumn.setComparator(null);

        mPNLColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getPNL()));
        mPNLColumn.setComparator(null);

        mPctReturnColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getPctRet()));
        mPctReturnColumn.setComparator(null);

        mDatePicker.setOnAction(event -> {updateHoldings(); });
        mDatePicker.setValue(LocalDate.now());
        updateHoldings();// setValue doesn't trigger an event, call update mannually.
    }

    private void updateHoldings() {
        LocalDate date = mDatePicker.getValue();
        mMainApp.updateHoldingsList(date, 0);
        populateTreeTable();
    }

    @FXML
    private void initialize() {}
}
