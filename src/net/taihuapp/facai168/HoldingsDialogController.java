package net.taihuapp.facai168;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Created by ghe on 6/21/15.
 * A dialog to display holdings
 */

public class HoldingsDialogController {

    private MainApp mMainApp;

    @FXML
    private AnchorPane mMainPane;
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

    private ListChangeListener<Transaction> mTransactionListChangeListener = null;

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

    void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;

        mSecurityHoldingTreeTableView.setShowRoot(false);
        mSecurityHoldingTreeTableView.setSortMode(TreeSortMode.ONLY_FIRST_LEVEL);
        mSecurityHoldingTreeTableView.setEditable(true);

        mNameColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, String> p) ->
                new ReadOnlyStringWrapper(p.getValue().getValue().getLabel()));
        mNameColumn.setComparator((o1, o2) -> {
            if (mNameColumn.getSortType() == TreeTableColumn.SortType.ASCENDING) {
                // sorting ascending
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

            // sorting descending
            if (o1.equals("TOTAL"))
                return -1;
            if (o2.equals("TOTAL"))
                return 1;
            if (o1.equals("CASH"))
                return -1;
            if (o2.equals("CASH"))
                return 1;
            return o1.compareTo(o2);
        });

        mPriceColumn.setCellValueFactory(p -> p.getValue().getValue().getPriceProperty());
        mPriceColumn.setComparator(null);

        mPriceColumn.setCellFactory(new Callback<TreeTableColumn<LotHolding, BigDecimal>,
                TreeTableCell<LotHolding, BigDecimal>>() {
            @Override
            public TreeTableCell<LotHolding, BigDecimal> call(TreeTableColumn<LotHolding,
                    BigDecimal> paramTreeTableColumn) {
                return new TextFieldTreeTableCell<LotHolding, BigDecimal>(new StringConverter<BigDecimal>() {
                    @Override
                    public String toString(BigDecimal object) {
                        if (object == null)
                            return null;
                        // format to 6 decimal places
                        DecimalFormat df = new DecimalFormat();
                        df.setMaximumFractionDigits(MainApp.PRICE_FRACTION_LEN-2);
                        df.setMinimumFractionDigits(0);
                        return df.format(object);
                    }

                    @Override
                    public BigDecimal fromString(String string) {
                        BigDecimal result;
                        try {
                            result = new BigDecimal(string);
                        } catch (NumberFormatException | NullPointerException e) {
                            result = null;
                        }
                        return result;
                    }
                }) {
                    @Override
                    public void updateItem(BigDecimal item, boolean empty) {
                        super.updateItem(item, empty);
                        TreeTableRow<LotHolding> treeTableRow = getTreeTableRow();
                        if (treeTableRow != null) {
                            TreeItem<LotHolding> treeItem = treeTableRow.getTreeItem();
                            if (treeItem != null) {
                                String label = treeItem.getValue().getLabel();
                                if (mSecurityHoldingTreeTableView.getTreeItemLevel(treeItem) > 1
                                    || label.equals("TOTAL") || label.equals("CASH")) {
                                    setEditable(false); // it seems the setEditable need to be called again and again
                                } else {
                                    setEditable(true);
                                }
                            }
                        }
                    }
                };
            }
        });
        mPriceColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        mPriceColumn.setOnEditCommit(e -> {
            Security security = mMainApp.getSecurityByName(e.getRowValue().getValue().getSecurityName());
            if (security == null)
                return;
            LocalDate date = mDatePicker.getValue();
            BigDecimal newPrice = e.getNewValue();
            if (newPrice == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning!");
                alert.setHeaderText("Bad Input Price, change discarded!");
                alert.setContentText(""
                        + "Security Name  : " + security.getName() + "\n"
                        + "Security Ticker: " + security.getTicker() + "\n"
                        + "Security ID    : " + security.getID() + "\n"
                        + "Date           : " + date);
                alert.showAndWait();
                return; // don't do anything
            }
            if (newPrice.signum() < 0)
                return; // we don't want to anything with bad input (negative price)
            if (newPrice.signum() == 0) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation Dialog");
                alert.setHeaderText("Do you want to save zero price to database?");
                alert.setContentText(""
                        + "Security Name  : " + security.getName() + "\n"
                        + "Security Ticker: " + security.getTicker() + "\n"
                        + "Security ID    : " + security.getID() + "\n"
                        + "Date           : " + date + "\n"
                        + "Price          : " + newPrice + "?");
                Optional<ButtonType> result = alert.showAndWait();
                if (!result.isPresent() || result.get() != ButtonType.OK)
                    return; // don't save, go back
            }

            if (!mMainApp.insertUpdatePriceToDB(security.getID(), date, newPrice, 3)) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setHeaderText("Failed to insert/update price:");
                alert.setContentText("Security Name: " + security.getName() + "\n"
                        + "Security Ticker: " + security.getTicker() + "\n"
                        + "Security ID    : " + security.getID() + "\n"
                        + "Date           : " + date + "\n"
                        + "Price          : " + newPrice);
                alert.showAndWait();
            } else {
                updateHoldings();
                mMainApp.updateAccountBalance(security);
            }
        });

        Callback<TreeTableColumn<LotHolding, BigDecimal>, TreeTableCell<LotHolding, BigDecimal>> dollarCentsCF =
                new Callback<TreeTableColumn<LotHolding, BigDecimal>, TreeTableCell<LotHolding, BigDecimal>>() {
                    @Override
                    public TreeTableCell<LotHolding, BigDecimal> call(TreeTableColumn<LotHolding, BigDecimal> column) {
                        return new TreeTableCell<LotHolding, BigDecimal>() {
                            @Override
                            protected void updateItem(BigDecimal item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item == null || empty) {
                                    setText("");
                                } else {
                                    // format
                                    setText((new DecimalFormat("###,##0.00")).format(item));
                                }
                                setStyle("-fx-alignment: CENTER-RIGHT;");
                            }
                        };
                    }
                };

        mQuantityColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getQuantity()));
        mQuantityColumn.setCellFactory(new Callback<TreeTableColumn<LotHolding, BigDecimal>, TreeTableCell<LotHolding, BigDecimal>>() {
            @Override
            public TreeTableCell<LotHolding, BigDecimal> call(TreeTableColumn<LotHolding, BigDecimal> param) {
                return new TreeTableCell<LotHolding, BigDecimal>() {
                    @Override
                    protected void updateItem(BigDecimal item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item == null || empty) {
                            setText("");
                        } else {
                            // format
                            DecimalFormat df = new DecimalFormat();
                            df.setMaximumFractionDigits(MainApp.QUANTITY_FRACTION_LEN-2);
                            df.setMinimumFractionDigits(0);
                            setText(df.format(item));
                        }
                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                };
            }
        });
        mQuantityColumn.setComparator(null);

        mMarketValueColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getMarketValue()));
        mMarketValueColumn.setCellFactory(dollarCentsCF);
        mMarketValueColumn.setComparator(null);

        mCostBasisColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getCostBasis()));
        mCostBasisColumn.setCellFactory(dollarCentsCF);
        mCostBasisColumn.setComparator(null);

        mPNLColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getPNL()));
        mPNLColumn.setCellFactory(dollarCentsCF);
        mPNLColumn.setComparator(null);

        mPctReturnColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<LotHolding, BigDecimal> p) ->
                new ReadOnlyObjectWrapper<>(p.getValue().getValue().getPctRet()));
        mPctReturnColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        mPctReturnColumn.setComparator(null);

        mDatePicker.setOnAction(event -> updateHoldings());
        mDatePicker.setValue(LocalDate.now());
        updateHoldings();// setValue doesn't trigger an event, call update manually.

        // set a listener on TransactionList
        mTransactionListChangeListener = c -> updateHoldings();
        mMainApp.getCurrentAccount().getTransactionList().addListener(mTransactionListChangeListener);
    }

    private void updateHoldings() {
        mMainApp.setCurrentAccountSecurityHoldingList(mDatePicker.getValue(), 0);
        populateTreeTable();
    }

    void close() { mMainApp.getCurrentAccount().getTransactionList().removeListener(mTransactionListChangeListener); }

    @FXML
    private void handleEnterTransaction() {
        mMainApp.showEditTransactionDialog((Stage) mMainPane.getScene().getWindow(),  null);
    }
}
