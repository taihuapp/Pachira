package net.taihuapp.facai168;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TableView;

import javax.swing.text.*;
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
    private TableColumn<Account, BigDecimal> mBalanceColumn;

    @FXML
    private TableView<Transaction> mTransactionTableView;
    @FXML
    private TableColumn<Transaction, Date> mTransactionDateColumn;
    @FXML
    private TableColumn<Transaction, String> mTransactionPayeeColumn;

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
        mTransactionTableView.setVisible(true);
    }

    @FXML
    private void initialize() {
        mAccountColumn.setCellValueFactory(cellData->cellData.getValue().getNameProperty());
        mBalanceColumn.setCellValueFactory(cellData->cellData.getValue().getCurrentBalanceProperty());

        mBalanceColumn.setCellFactory(column -> {
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
        mTransactionPayeeColumn.setCellValueFactory(cellData->cellData.getValue().getPayeeProperty());
    }
}