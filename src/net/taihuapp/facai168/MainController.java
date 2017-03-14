package net.taihuapp.facai168;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    private MainApp mMainApp;

    @FXML
    private Menu mRecentDBMenu;
    @FXML
    private Menu mEditMenu;
    @FXML
    private Menu mReportsMenu;
    @FXML
    private Menu mSavedReportsMenu;
    @FXML
    private MenuItem mChangePasswordMenuItem;
    @FXML
    private MenuItem mBackupMenuItem;
    @FXML
    private MenuItem mImportQIFMenuItem;
    @FXML
    private MenuItem mFixDBMenuItem;
    @FXML
    private TableView<Account> mAccountTableView;
    @FXML
    private TableColumn<Account, String> mAccountColumn;
    @FXML
    private TableColumn<Account, BigDecimal> mAccountBalanceColumn;

    @FXML
    private VBox mTransactionVBox;

    @FXML
    private Label mTransactionAccountNameLabel;
    @FXML
    private Button mEnterTransactionButton;
    @FXML
    private Button mTransactionShowHoldingsButton;

    @FXML
    private TableView<Transaction> mTransactionTableView;
    @FXML
    private TableColumn<Transaction, LocalDate> mTransactionDateColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionReferenceColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionPayeeColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionMemoColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionCategoryColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionPaymentColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionDepositColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionBalanceColumn;

    @FXML
    private TableColumn<Transaction, Transaction.TradeAction> mTransactionTradeActionColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionSecurityNameColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionDescriptionColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionInvestAmountColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionCashAmountColumn;


    void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;
        updateRecentMenu();
        updateUI(mMainApp.isConnected());

        // get accounts with hiddenFlag == false and exDelete = true
        mAccountTableView.setItems(mMainApp.getAccountList(null, false, true));
    }

    @FXML
    private void handleClose() {
        Platform.exit();
    }

    @FXML
    private void handleOpen() {
        mMainApp.openDatabase(false, null, null);
        updateRecentMenu();
        updateUI(mMainApp.isConnected());
    }

    @FXML
    private void handleNew() {
        mMainApp.openDatabase(true, null, null);
        updateRecentMenu();
        updateUI(mMainApp.isConnected());
    }

    @FXML
    private void handleChangePassword() {
        mMainApp.changePassword();
    }

    @FXML
    private void handleImportQIF() {
        mMainApp.importQIF();
        mMainApp.initAccountList();
    }

    @FXML
    private void handleFixDB() {
        mMainApp.fixDB();
        mMainApp.initAccountList();
    }

    @FXML
    private void handleBackup() { mMainApp.doBackup(); }

    @FXML
    private void handleClearList() {
        mMainApp.putOpenedDBNames(new ArrayList<>());
        updateRecentMenu();
    }

    @FXML
    private void handleEditAccountList() { mMainApp.showAccountListDialog(); }

    @FXML
    private void handleEditSecurityList() {
        mMainApp.showSecurityListDialog();
    }

    @FXML
    private void handleReminderList() { mMainApp.showBillIncomeReminderDialog(); }

    @FXML
    private void handleNAVReport() {
        mMainApp.showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.NAV));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleInvestingTransactions() {
        mMainApp.showReportDialog(new ReportDialogController.Setting(ReportDialogController.ReportType.INVESTTRANS));
        updateSavedReportsMenu();
    }

    @FXML
    private void handleBankingTransactions() {
        System.out.println("Banking Transaction Report Not Implemented Yet.");
    }

    private void updateSavedReportsMenu() {
        List<MenuItem> menuItemList = new ArrayList<>();
        for (ReportDialogController.Setting setting : mMainApp.loadReportSetting(0)) {
            MenuItem mi = new MenuItem(setting.getName());
            mi.setUserData(setting);
            mi.setOnAction(t -> mMainApp.showReportDialog((ReportDialogController.Setting) mi.getUserData()));
            menuItemList.add(mi);
        }
        mSavedReportsMenu.getItems().setAll(menuItemList);
    }

    private void updateUI(boolean isConnected) {
        mEditMenu.setVisible(isConnected);
        mReportsMenu.setVisible(isConnected);
        mChangePasswordMenuItem.setVisible(isConnected);
        mBackupMenuItem.setVisible(isConnected);
        mImportQIFMenuItem.setVisible(isConnected);
        mFixDBMenuItem.setVisible(isConnected);
        mAccountTableView.setVisible(isConnected);
        mTransactionVBox.setVisible(mMainApp.getCurrentAccount() != null);
        if (isConnected)
            updateSavedReportsMenu();
    }

    private void updateRecentMenu() {
        EventHandler<ActionEvent> menuAction = t -> {
            MenuItem mi = (MenuItem) t.getTarget();
            mMainApp.openDatabase(false, mi.getText(), null);
            updateUI(mMainApp.isConnected());
        };
        ObservableList<MenuItem> recentList = mRecentDBMenu.getItems();
        recentList.remove(0, recentList.size() - 2);
        List<String> newList = mMainApp.getOpenedDBNames();
        int n = newList.size();
        for (int i = 0; i < n; i++) {
            MenuItem mi = new MenuItem(newList.get(n-i-1));
            mi.setOnAction(menuAction);
            recentList.add(0, mi);
        }
    }

    @FXML
    private void handleEnterTransaction() {
        mMainApp.showEditTransactionDialog(mMainApp.getStage(), null);
    }

    @FXML
    private void handleShowHoldings() {
        mMainApp.showAccountHoldings();
    }

    private void showAccountTransactions(Account account) {
        mMainApp.setCurrentAccount(account);

        mTransactionVBox.setVisible(mMainApp.getCurrentAccount() != null);

        if (account == null) {
            return;
        }

        boolean isTradingAccount = account.getType() == Account.Type.INVESTING;

        mTransactionAccountNameLabel.setVisible(true);
        mTransactionAccountNameLabel.setText(account.getName());
        mEnterTransactionButton.setVisible(true);
        mTransactionShowHoldingsButton.setVisible(isTradingAccount);

        mTransactionTableView.setVisible(true);
        mTransactionTableView.setItems(account.getTransactionList());
        mTransactionTableView.scrollTo(account.getTransactionList().size()-1);
        mTransactionTradeActionColumn.setVisible(isTradingAccount);
        mTransactionReferenceColumn.setVisible(!isTradingAccount);
        mTransactionPayeeColumn.setVisible(!isTradingAccount);
        mTransactionMemoColumn.setVisible(!isTradingAccount);
        mTransactionCategoryColumn.setVisible(!isTradingAccount);
        mTransactionPaymentColumn.setVisible(!isTradingAccount);
        mTransactionDepositColumn.setVisible(!isTradingAccount);
        mTransactionBalanceColumn.setText(isTradingAccount ? "Cash Bal" : "Balance");

        mTransactionSecurityNameColumn.setVisible(isTradingAccount);
        mTransactionDescriptionColumn.setVisible(isTradingAccount);
        mTransactionInvestAmountColumn.setVisible(isTradingAccount);
        mTransactionCashAmountColumn.setVisible(isTradingAccount);
    }

    @FXML
    private void initialize() {
        mAccountColumn.setCellValueFactory(cellData->cellData.getValue().getNameProperty());
        mAccountBalanceColumn.setCellValueFactory(cellData -> cellData.getValue().getCurrentBalanceProperty());

        mAccountBalanceColumn.setCellFactory(column -> new TableCell<Account, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText("");
                } else {
                    // format
                    //setText((new DecimalFormat("#0.00")).format(item));
                    setText((new DecimalFormat("###,##0.00")).format(item));
                }
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });

        mAccountTableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showAccountTransactions(newValue));

        // double click to edit the transaction
        mTransactionTableView.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if ((event.getClickCount() == 2) && (!row.isEmpty())) {
                    Transaction transaction = row.getItem();
                    if (transaction.getMatchID() > 0) {
                        // this is a linked transaction
                        if (transaction.getMatchSplitID() > 0) {
                            showWarningDialog("Linked to A Split Transaction",
                                    "Linked to a split transaction",
                                    "Please edit the linked split transcation.");
                            return;
                        }

                        Account account = mMainApp.getAccountByID(transaction.getAccountID());
                        if (!account.getType().equals(Account.Type.INVESTING)) {
                            // not an investing account, check linked transaction account
                            Transaction linkedTransaction = mMainApp.getTransactionByID(transaction.getMatchID());
                            if (linkedTransaction == null) {
                                showWarningDialog("Linked to An Investing Transaction",
                                        "Unable to find the linked transaction",
                                        "Call help!");
                                return;
                            } else if (linkedTransaction.getTradeAction() != Transaction.TradeAction.XIN
                                    && linkedTransaction.getTradeAction() != Transaction.TradeAction.XOUT) {
                                Account linkedAccount = mMainApp.getAccountByID(linkedTransaction.getAccountID());
                                if (linkedAccount == null) {
                                    showWarningDialog("Linked to An Investing Transaction",
                                            "Unable to find the account of linked transaction",
                                            "Call help!");
                                    return;
                                }
                                if (linkedAccount.getType().equals(Account.Type.INVESTING)) {
                                    showWarningDialog("Linked to An Investing Transaction",
                                            "Linked to an investing transaction",
                                            "Please edit the linked investing transaction.");
                                    return;
                                }
                            }
                        }
                    }
                    mMainApp.showEditTransactionDialog(mMainApp.getStage(), new Transaction(row.getItem()));
                }
            });
            return row;
        });

        // transaction table
        mTransactionDateColumn.setCellValueFactory(cellData->cellData.getValue().getTDateProperty());
        mTransactionTradeActionColumn.setCellValueFactory(cellData -> cellData.getValue().getTradeActionProperty());
        mTransactionSecurityNameColumn.setCellValueFactory(cellData -> cellData.getValue().getSecurityNameProperty());
        mTransactionDescriptionColumn.setCellValueFactory(cellData -> cellData.getValue().getDescriptionProperty());
        mTransactionReferenceColumn.setCellValueFactory(cellData -> cellData.getValue().getReferenceProperty());

        mTransactionPayeeColumn.setCellValueFactory(cellData -> cellData.getValue().getPayeeProperty());

        mTransactionMemoColumn.setCellValueFactory(cellData -> cellData.getValue().getMemoProperty());

        mTransactionCategoryColumn.setCellValueFactory(cellData -> {
            Transaction t = cellData.getValue();
            if (t.getSplitTransactionList().size() > 0)
                return new ReadOnlyStringWrapper("--Split--");
            return new ReadOnlyStringWrapper(mMainApp.mapCategoryOrAccountIDToName(t.getCategoryID()));
        });

        Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>> dollarCentsCF =
                new Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>>() {
                    @Override
                    public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> column) {
                        return new TableCell<Transaction, BigDecimal>() {
                            @Override
                            protected void updateItem(BigDecimal item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item == null || empty) {
                                    setText("");
                                } else {
                                    // format
                                    setText(item.signum() == 0 ? "" : (new DecimalFormat("###,##0.00")).format(item));
                                }
                                setStyle("-fx-alignment: CENTER-RIGHT;");
                            }
                        };
                    }
                };

        mTransactionPaymentColumn.setCellValueFactory(cellData->cellData.getValue().getPaymentProperty());
        mTransactionPaymentColumn.setCellFactory(dollarCentsCF);

        mTransactionDepositColumn.setCellValueFactory(cellData->cellData.getValue().getDepositeProperty());
        mTransactionDepositColumn.setCellFactory(dollarCentsCF);

        mTransactionInvestAmountColumn.setCellValueFactory(cellData->cellData.getValue().getInvestAmountProperty());
        mTransactionInvestAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionCashAmountColumn.setCellValueFactory(cellData->cellData.getValue().getCashAmountProperty());
        mTransactionCashAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionBalanceColumn.setCellValueFactory(cellData->cellData.getValue().getBalanceProperty());
        mTransactionBalanceColumn.setCellFactory(
                new Callback<TableColumn<Transaction, BigDecimal>, TableCell<Transaction, BigDecimal>>() {
                    @Override
                    public TableCell<Transaction, BigDecimal> call(TableColumn<Transaction, BigDecimal> column) {
                        return new TableCell<Transaction, BigDecimal>() {
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
                }
        );
    }

    private void showWarningDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}