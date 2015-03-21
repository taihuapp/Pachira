package net.taihuapp.facai168;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class MainController {

    private MainApp mMainApp;

    @FXML
    private Menu mRecentDBMenu;

    @FXML
    private Menu mEditMenu;

    @FXML
    private MenuItem mNewAccountMenuItem;

    @FXML
    private ListView<Account> mAccountListView;

    public void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;
        updateRecentMenu();
        //todo
        // should I call updateUI here?

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
            mMainApp.getAccountList().add(account);
        }
    }

    private void updateUI(boolean isConnected) {
        System.out.println("updateUI " + isConnected);
        mEditMenu.setVisible(isConnected);
        mAccountListView.setVisible(isConnected);
        if (isConnected) {
            mAccountListView.setItems(mMainApp.getAccountList());
            mAccountListView.setCellFactory((list) -> {
                return new ListCell<Account>() {
                    @Override
                    protected void updateItem(Account item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null || empty) {
                            setText(null);
                        } else {
                            setText(item.getName());
                        }
                    }
                };
            });
        }
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
}