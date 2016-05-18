package net.taihuapp.facai168;

import javafx.application.Platform;
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
    private Menu mEditAccountMenu;
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
    private TableColumn<Transaction, String> mTransactionTradeActionColumn;
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

        mAccountTableView.setItems(mMainApp.getAccountList());
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
        mMainApp.putOpenedDBNames(new ArrayList<>());
        updateRecentMenu();
    }

    @FXML
    private void handleNewAccount() {
        handleEditAccount(new Account());
    }

    private void handleEditAccount(Account account) {
        if (mMainApp.showEditAccountDialog(account)) {
            mMainApp.insertUpdateAccountToDB(account);
            mMainApp.initAccountList();
            updateEditAccountMenu();
        }
    }

    private void updateEditAccountMenu() {
        // clear account list first, keeping item 0 and 1 which are 'New' and 'Separator'.
        ObservableList<MenuItem> editAccountMenuItems = mEditAccountMenu.getItems();
        editAccountMenuItems.remove(2, editAccountMenuItems.size());

        for (Account account : mMainApp.getAccountList()) {
            MenuItem mi = new MenuItem(account.getName());
            mi.setOnAction(event -> {
                MenuItem mi1 = (MenuItem) event.getTarget();
                handleEditAccount(mMainApp.getAccountByName(mi1.getText()));
            });
            editAccountMenuItems.add(mi);
        }
    }

    private void updateUI(boolean isConnected) {
        if (isConnected)
            updateEditAccountMenu();
        mEditMenu.setVisible(isConnected);
        mBackupMenuItem.setVisible(isConnected);
        mImportQIFMenuItem.setVisible(isConnected);
        mAccountTableView.setVisible(isConnected);
        mTransactionVBox.setVisible(mMainApp.getCurrentAccount() != null);
    }

    private void updateRecentMenu() {
        EventHandler<ActionEvent> menuAction = t -> {
            MenuItem mi = (MenuItem) t.getTarget();
            mMainApp.openDatabase(false, mi.getText());
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
        mMainApp.showEditTransactionDialog(null);
    }

    @FXML
    private void handleShowHoldings() {
        mMainApp.showAccountHoldings();
    }

    private void showEditTransactionDialog(Transaction t) {
        mMainApp.showEditTransactionDialog(t);
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
                    setText((new DecimalFormat("#0.00")).format(item));
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
                    mMainApp.showEditTransactionDialog(new Transaction(row.getItem()));
                }
            });
            return row;
        });

        // transactiontable
        mTransactionDateColumn.setCellValueFactory(cellData->cellData.getValue().getTDateProperty());
        mTransactionTradeActionColumn.setCellValueFactory(cellData -> cellData.getValue().getTradeActionProperty());
        mTransactionSecurityNameColumn.setCellValueFactory(cellData -> cellData.getValue().getSecurityNameProperty());
        mTransactionDescriptionColumn.setCellValueFactory(cellData -> cellData.getValue().getDescriptionProperty());
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

        mTransactionDepositColumn.setCellValueFactory(cellData->cellData.getValue().getDepositeProperty());
        mTransactionDepositColumn.setCellFactory(dollarCentsCF);

        mTransactionInvestAmountColumn.setCellValueFactory(cellData->cellData.getValue().getInvestAmountProperty());
        mTransactionInvestAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionCashAmountColumn.setCellValueFactory(cellData->cellData.getValue().getCashAmountProperty());
        mTransactionCashAmountColumn.setCellFactory(dollarCentsCF);

        mTransactionBalanceColumn.setCellValueFactory(cellData->cellData.getValue().getBalanceProperty());
        mTransactionBalanceColumn.setCellFactory(dollarCentsCF);
    }
}