package net.taihuapp.facai168;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.math.BigDecimal;
import java.sql.Date;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    private MainApp mMainApp;

    @FXML
    private Menu mRecentDBMenu;
    @FXML
    private Menu mEditMenu;
    @FXML
    private MenuItem mBackupMenuItem;
    @FXML
    private MenuItem mImportQIFMenuItem;
    @FXML
    private TableView<Account> mAccountTableView;
    @FXML
    private TableColumn<Account, String> mAccountColumn;
    @FXML
    private TableColumn<Account, BigDecimal> mAccountBalanceColumn;

    @FXML
    private TableView<Transaction> mTransactionTableView;
    @FXML
    private TableColumn<Transaction, Date> mTransactionDateColumn;
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
    private TableColumn<Transaction, String> mTransactionTradeActionColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionSecurityNameColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionInvestAmountColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> mTransactionCashAmountColumn;


    public void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;
        updateRecentMenu();
        updateUI(mMainApp.isConnected());

        mAccountTableView.setItems(mMainApp.getAccountList());
        mTransactionTableView.setItems(mMainApp.getTransactionList());
    }

    @FXML
    private void handleClose() {
        Platform.exit();
    }

    @FXML
    private void handleOpen() {
        mMainApp.openDatabase(false, null);
        updateRecentMenu();
        updateUI(mMainApp.isConnected());
    }

    @FXML
    private void handleNew() {
        mMainApp.openDatabase(true, null);
        updateRecentMenu();
        updateUI(mMainApp.isConnected());
    }

    @FXML
    private void handleImportQIF() {
        mMainApp.importQIF();
        mMainApp.initAccountList();
    }

    @FXML
    private void handleBackup() {
        System.out.println("Backup");
    }

    @FXML
    private void handleClearList() {
        mMainApp.putOpenedDBNames(new ArrayList<String>());
        updateRecentMenu();
    }

    @FXML
    private void handleNewAccount() {
        Account account = new Account();
        if (mMainApp.showEditAccountDialog(account)) {
            mMainApp.insertUpdateAccountToDB(account);
            mMainApp.initAccountList();
        }
    }

    private void updateUI(boolean isConnected) {
        mEditMenu.setVisible(isConnected);
        mBackupMenuItem.setVisible(isConnected);
        mImportQIFMenuItem.setVisible(isConnected);
        mAccountTableView.setVisible(isConnected);
    }

    public void updateRecentMenu() {
        EventHandler<ActionEvent> menuAction = new EventHandler<ActionEvent> () {
            public void handle(ActionEvent t) {
                MenuItem mi = (MenuItem) t.getTarget();
                mMainApp.openDatabase(false, mi.getText());
                updateUI(mMainApp.isConnected());
            }
        };
        ObservableList<MenuItem> recentList = mRecentDBMenu.getItems();
        recentList.remove(0, recentList.size()-2);
        List<String> newList = mMainApp.getOpenedDBNames();
        int n = newList.size();
        for (int i = 0; i < n; i++) {
            MenuItem mi = new MenuItem(newList.get(n-i-1));
            mi.setOnAction(menuAction);
            recentList.add(0, mi);
        }
    }

    public void showAccountTransactions(Account account) {
        if (account == null) {
            return;
        }
        mMainApp.initTransactionList(account);
        System.out.println("Showing " + account.getName() + " transactions");

        boolean isTradingAccount = account.getType() == Account.Type.INVESTING;

        mTransactionTableView.setVisible(true);

        mTransactionTradeActionColumn.setVisible(isTradingAccount);
        mTransactionReferenceColumn.setVisible(!isTradingAccount);
        mTransactionPayeeColumn.setVisible(!isTradingAccount);
        mTransactionMemoColumn.setVisible(!isTradingAccount);
        mTransactionCategoryColumn.setVisible(!isTradingAccount);
        mTransactionPaymentColumn.setVisible(!isTradingAccount);
        mTransactionDepositColumn.setVisible(!isTradingAccount);
        mTransactionBalanceColumn.setText(isTradingAccount ? "Cash Bal" : "Balance");

        mTransactionSecurityNameColumn.setVisible(isTradingAccount);
        mTransactionInvestAmountColumn.setVisible(isTradingAccount);
        mTransactionCashAmountColumn.setVisible(isTradingAccount);
    }

    @FXML
    private void initialize() {
        mAccountColumn.setCellValueFactory(cellData->cellData.getValue().getNameProperty());
        mAccountBalanceColumn.setCellValueFactory(cellData -> cellData.getValue().getCurrentBalanceProperty());

        mAccountBalanceColumn.setCellFactory(column -> {
            return new TableCell<Account, BigDecimal>() {
                @Override
                protected void updateItem(BigDecimal item, boolean empty) {
                    super.updateItem(item, empty);

                    if (item == null || empty) {
                        setText("");
                    } else {
                        // format
                        setText((new DecimalFormat("#0.00")).format(item));
                    }
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            };
        });

        mAccountTableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showAccountTransactions(newValue));

        // transactiontable
        mTransactionDateColumn.setCellValueFactory(cellData->cellData.getValue().getDateProperty());
        mTransactionTradeActionColumn.setCellValueFactory(cellData -> cellData.getValue().getTradeActionProperty());
        mTransactionSecurityNameColumn.setCellValueFactory(cellData -> cellData.getValue().getSecurityNameProperty());
        mTransactionReferenceColumn.setCellValueFactory(cellData -> cellData.getValue().getReferenceProperty());

        mTransactionPayeeColumn.setCellValueFactory(cellData -> cellData.getValue().getPayeeProperty());

        mTransactionMemoColumn.setCellValueFactory(cellData -> cellData.getValue().getMemoProperty());

        mTransactionCategoryColumn.setCellValueFactory(cellData -> cellData.getValue().getCategoryProperty());

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
                                    setText((new DecimalFormat("#0.00")).format(item));
                                }
                                setStyle("-fx-alignment: CENTER-RIGHT;");
                            }
                        };
                    }
                };

        mTransactionPaymentColumn.setCellValueFactory(cellData->cellData.getValue().getPaymentProperty());
        mTransactionPaymentColumn.setCellFactory(dollarCentsCF);

        mTransactionDepositColumn.setCellValueFactory(cellData->cellData.getValue().getDepositProperty());
        mTransactionDepositColumn.setCellFactory(dollarCentsCF);

        mTransactionInvestAmountColumn.setCellValueFactory(cellData->cellData.getValue().getInvestAmountProperty());
        mTransactionInvestAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionCashAmountColumn.setCellValueFactory(cellData->cellData.getValue().getCashAmountProperty());
        mTransactionCashAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionBalanceColumn.setCellValueFactory(cellData->cellData.getValue().getBalanceProperty());
        mTransactionBalanceColumn.setCellFactory(dollarCentsCF);
    }
}