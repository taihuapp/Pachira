package net.taihuapp.facai168;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ghe on 9/13/16.
 *
 */
public class AccountListDialogController {
    private MainApp mMainApp = null;
    private Stage mDialogStage = null;

    @FXML
    private TabPane mTabPane;
    @FXML
    private Button mEditButton;
    @FXML
    private Button mMoveUpButton;
    @FXML
    private Button mMoveDownButton;
    @FXML
    private Button mUnhideButton;

    @FXML
    private void handleNew() {
        // first find out which tab we are on
        Account.Type t;
        try {
            t = Account.Type.valueOf(mTabPane.getSelectionModel().getSelectedItem().getText());
        } catch (IllegalArgumentException e) {
            t = null;
        }

        Account account = new Account();
        if (t != null) {
            try {
                account.setType(t);
            } catch (Exception e) {
                // we shouldn't be here anyway.
                e.printStackTrace();
            }
        }

        // if t != null, we are on one of the sub tabs, lock the account type
        mMainApp.showEditAccountDialog(t != null, account);
    }

    @FXML
    private void handleEdit() {
        // the type cast is create a warning message, suppress it.
        @SuppressWarnings("unchecked")
        TableView<Account> tableView = (TableView<Account>) mTabPane.getSelectionModel().getSelectedItem().getContent();
        Account account = tableView.getSelectionModel().getSelectedItem();
        if (account != null)
            mMainApp.showEditAccountDialog(true, account);
    }

    @FXML
    private void handleMoveUp() {
        // the type cast is create a warning message, suppress it.
        @SuppressWarnings("unchecked")
        TableView<Account> tableView = (TableView<Account>) mTabPane.getSelectionModel().getSelectedItem().getContent();
        int selectedIdx = tableView.getSelectionModel().getSelectedIndex();
        Account account = tableView.getSelectionModel().getSelectedItem();
        if (account == null || selectedIdx <= 0)
            return; // how we got here

        Account accountAbove = tableView.getItems().get(selectedIdx-1);
        if (accountAbove == null || accountAbove.getType() != account.getType())
            return; // how did this happen?

        account.setDisplayOrder(account.getDisplayOrder()-1);
        accountAbove.setDisplayOrder(account.getDisplayOrder()+1);
        mMainApp.insertUpdateAccountToDB(account, false);
        mMainApp.insertUpdateAccountToDB(accountAbove, false);
    }

    @FXML
    private void handleMoveDown() {
        // the type cast is create a warning message, suppress it.
        @SuppressWarnings("unchecked")
        TableView<Account> tableView = (TableView<Account>) mTabPane.getSelectionModel().getSelectedItem().getContent();
        int selectedIdx = tableView.getSelectionModel().getSelectedIndex();
        int numberOfRows = tableView.getItems().size();
        Account account = tableView.getSelectionModel().getSelectedItem();
        if (account == null || selectedIdx < 0 || selectedIdx >= numberOfRows-1)
            return; // we shouldn't be here

        Account accountBelow = tableView.getItems().get(selectedIdx+1);
        if (accountBelow == null || accountBelow.getType() != account.getType())
            return;

        account.setDisplayOrder(account.getDisplayOrder()+1);
        accountBelow.setDisplayOrder(accountBelow.getDisplayOrder()-1);

        // these two accounts are in MainApp.mAccountList, no need to update
        mMainApp.insertUpdateAccountToDB(account, false);
        mMainApp.insertUpdateAccountToDB(accountBelow, false);
    }

    @FXML
    private void handleUnhide() {
        // the type cast is create a warning message, suppress it.
        @SuppressWarnings("unchecked")
        TableView<Account> tableView = (TableView<Account>) mTabPane.getSelectionModel().getSelectedItem().getContent();
        Account account = tableView.getSelectionModel().getSelectedItem();
        if (account == null)
            return;  // is this necessary?
        // working on the account in mMainApp.mAccountList
        account.setHiddenFlag(!account.getHiddenFlag());
        mUnhideButton.setText(account.getHiddenFlag() ? "Unhide" : "Hide");
        mMainApp.insertUpdateAccountToDB(account, false);
    }

    @FXML
    private void handleClose() { close(); }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        List<Account.Type> accountTypes = new ArrayList<>(Arrays.asList(Account.Type.values()));
        accountTypes.add(null);  // add the null for all accounts

        for (Account.Type t : accountTypes) {
            Tab tab = new Tab();

            if (t != null)
                tab.setText(t.name());
            else
                tab.setText("All");

            // create a tableView
            final TableView<Account> tableView = new TableView<>();
            final List<Account> sortedAccountList = mMainApp.getAccountList(t, null);
            // check display order
            if (t != null) {
                for (int i = 0; i < sortedAccountList.size(); i++) {
                    Account a = sortedAccountList.get(i);
                    if (a.getDisplayOrder() != i) {
                        a.setDisplayOrder(i);
                        mMainApp.insertUpdateAccountToDB(a, false); // save to DB
                    }
                }
            }

            tableView.setItems(mMainApp.getAccountList(t, null)); // hidden accounts should be shown here

            TableColumn<Account, String> accountNameTableColumn = new TableColumn<>("Name");
            accountNameTableColumn.setCellValueFactory(cellData -> cellData.getValue().getNameProperty());

            TableColumn<Account, String> accountTypeTableColumn = new TableColumn<>("Type");
            accountTypeTableColumn.setCellValueFactory(
                    cellData -> new SimpleStringProperty(cellData.getValue().getType().name()));

            TableColumn<Account, BigDecimal> accountBalanceTableColumn = new TableColumn<>("Balance");
            accountBalanceTableColumn.setCellValueFactory(cellData -> cellData.getValue().getCurrentBalanceProperty());
            accountBalanceTableColumn.setCellFactory(column -> new TableCell<Account, BigDecimal>() {
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
            TableColumn<Account, Boolean> accountHiddenFlagTableColumn = new TableColumn<>("Hidden");
            accountHiddenFlagTableColumn.setCellValueFactory(cellData -> cellData.getValue().getHiddenFlagProperty());
            accountHiddenFlagTableColumn.setCellFactory(c -> new CheckBoxTableCell<>());

            // double click to edit the account
            tableView.setRowFactory(tv -> {
                TableRow<Account> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if ((event.getClickCount() == 2) && (!row.isEmpty())) {
                        mMainApp.showEditAccountDialog(true, row.getItem());
                    }
                });
                return row;
            });

            // we don't want to sort the table at all
            accountNameTableColumn.setSortable(false);
            accountTypeTableColumn.setSortable(false);
            accountBalanceTableColumn.setSortable(false);
            accountHiddenFlagTableColumn.setSortable(false);

            // add columns to tableView
            tableView.getColumns().add(accountNameTableColumn);
            tableView.getColumns().add(accountTypeTableColumn);
            tableView.getColumns().add(accountBalanceTableColumn);
            tableView.getColumns().add(accountHiddenFlagTableColumn);

            // add a selection change listener to the table
            tableView.getSelectionModel().selectedItemProperty().addListener((ob, ov, nv) -> {
                // is it possible nv is null?
                mEditButton.setDisable(nv == null); // if we have a selection, edit and unhide should be enabled
                mUnhideButton.setDisable(nv == null);
                if (nv != null)
                    mUnhideButton.setText(nv.getHiddenFlag() ? "Unhide" : "Hide");

                // disable move up button if
                // 1) nv is null or at the top (selectedIdx <= 0)
                // 2) nv is with different type from the account above it
                int selectedIdx = tableView.getSelectionModel().getSelectedIndex();
                int numberOfRows = tableView.getItems().size();
                mMoveUpButton.setDisable(nv == null || selectedIdx == 0
                        || nv.getType() != tableView.getItems().get(selectedIdx-1).getType());

                // disable move down button if
                // 1) nv is null or at the bottom (selectedIdx < 0 || selectedIdx > numberOfRows)
                // 2) nv is with different type from the account below it
                mMoveDownButton.setDisable(nv == null || selectedIdx == numberOfRows-1
                        || nv.getType() != tableView.getItems().get(selectedIdx+1).getType());
            });

            // add the borderPane to tab
            tab.setContent(tableView);

            // add the tab to tabPane
            mTabPane.getTabs().add(tab);
        }

        // add a listener for tab change event
        mTabPane.getSelectionModel().selectedItemProperty().addListener((ov, oldTab, newTab) -> {
            if (newTab == null) {
                // how did we get here
                mEditButton.setDisable(true);
                mMoveUpButton.setDisable(true);
                mMoveDownButton.setDisable(true);
                mUnhideButton.setDisable(true);
                return;
            }
            @SuppressWarnings("unchecked")
            TableView<Account> tableView = (TableView<Account>) newTab.getContent();
            int selectedIdx = tableView.getSelectionModel().getSelectedIndex();
            int numberOfRows = tableView.getItems().size();
            Account nv = (selectedIdx < 0 || selectedIdx >= numberOfRows) ?
                    null : tableView.getSelectionModel().getSelectedItem();

            mEditButton.setDisable(nv == null); // if we have a selection, edit and unhide should be enabled
            mUnhideButton.setDisable(nv == null);
            if (nv != null)
                mUnhideButton.setText(nv.getHiddenFlag() ? "Unhide" : "Hide");

            // disable move up button if
            // 1) nv is null or at the top (selectedIdx <= 0)
            // 2) nv is with different type from the account above it
            mMoveUpButton.setDisable(nv == null || selectedIdx == 0
                    || nv.getType() != tableView.getItems().get(selectedIdx-1).getType());

            // disable move down button if
            // 1) nv is null or at the bottom (selectedIdx < 0 || selectedIdx > numberOfRows)
            // 2) nv is with different type from the account below it
            mMoveDownButton.setDisable(nv == null || selectedIdx == numberOfRows-1
                    || nv.getType() != tableView.getItems().get(selectedIdx+1).getType());
        });

        // disable a few buttons when nothing is selected
        mEditButton.setDisable(true);
        mMoveUpButton.setDisable(true);
        mMoveDownButton.setDisable(true);
        mUnhideButton.setDisable(true);
    }

    void close() { mDialogStage.close(); }
}
