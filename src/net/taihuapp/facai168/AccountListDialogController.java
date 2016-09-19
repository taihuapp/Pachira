package net.taihuapp.facai168;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;

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
    private void handleEdit() {}
    @FXML
    private void handleMoveUp() { System.out.println("move up"); }
    @FXML
    private void handleMoveDown() {System.out.println("move down"); }
    @FXML
    private void handleUnhide() {
        TableView<Account> tableView = (TableView<Account>) mTabPane.getSelectionModel().getSelectedItem().getContent();
        Account account = tableView.getSelectionModel().getSelectedItem();
        account.setHiddenFlag(!account.getHiddenFlag());
        mMainApp.insertUpdateAccountToDB(account);
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

            // create a tableview
            final TableView<Account> tableView = new TableView<>();
            final List<Account> sortedAccountList = mMainApp.getAccountList(t, null);
            // check display order
            if (t != null) {
                for (int i = 0; i < sortedAccountList.size(); i++) {
                    if (sortedAccountList.get(i).getDisplayOrder() != i) {
                        sortedAccountList.get(i).setDisplayOrder(i);
                        mMainApp.insertUpdateAccountToDB(sortedAccountList.get(i)); // save to DB
                    }
                }
            }

            tableView.setItems(mMainApp.getAccountList(t, null)); // hidden accounts should be shown here

            TableColumn<Account, String> accountNameTableColumn = new TableColumn<>("Name");
            accountNameTableColumn.setCellValueFactory(cellData -> cellData.getValue().getNameProperty());

            TableColumn<Account, String> accountTypeTableColumn = new TableColumn<>("Type");
            accountTypeTableColumn.setCellValueFactory(
                    cellData -> new SimpleStringProperty(cellData.getValue().getType().name()));

            TableColumn<Account, Boolean> accountHiddenFlagTableColumn = new TableColumn<>("Hidden");
            accountHiddenFlagTableColumn.setCellValueFactory(cellData -> cellData.getValue().getHiddenFlagProperty());
            accountHiddenFlagTableColumn.setCellFactory(c -> new CheckBoxTableCell<>());

            // double click to edit the account
            tableView.setRowFactory(tv -> {
                TableRow<Account> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if ((event.getClickCount() == 2) && (!row.isEmpty())) {
                        mMainApp.showEditAccountDialog(true, new Account(row.getItem()));
                    }
                });
                return row;
            });

            // add columns to tableview
            tableView.getColumns().addAll(accountNameTableColumn, accountTypeTableColumn,
                    accountHiddenFlagTableColumn);

            // add a selection change listener to the table
            tableView.getSelectionModel().selectedItemProperty().addListener((ob, ov, nv) -> {
                // is it possible nv is null?
                mEditButton.setDisable(nv == null); // if we have a selection, edit and unhide should be enabled
                mUnhideButton.setDisable(nv == null);
                if (nv != null)
                    mUnhideButton.setText(nv.getHiddenFlag() ? "Unhide" : "Hide");

                // disable move up bottun if
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
            boolean hasSelection = false;
            boolean atTop = true;
            boolean atBottom = true;

            if (newTab == null) {
                // how did we get here
                mEditButton.setDisable(true);
                mMoveUpButton.setDisable(true);
                mMoveDownButton.setDisable(true);
                mUnhideButton.setDisable(true);
                return;
            }

            TableView<Account> tableView = (TableView<Account>) newTab.getContent();
            int selectedIdx = tableView.getSelectionModel().getSelectedIndex();
            int numberOfRows = tableView.getItems().size();
            Account nv = (selectedIdx < 0 || selectedIdx >= numberOfRows) ?
                    null : tableView.getSelectionModel().getSelectedItem();

            mEditButton.setDisable(nv == null); // if we have a selection, edit and unhide should be enabled
            mUnhideButton.setDisable(nv == null);
            if (nv != null)
                mUnhideButton.setText(nv.getHiddenFlag() ? "Unhide" : "Hide");

            // disable move up bottun if
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
    }

    void close() { mDialogStage.close(); }
}
