package net.taihuapp.facai168;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ghe on 7/10/15.
 * Controller for EditTransactionDialog
 */
public class EditTransactionDialogController {

    private final ObservableList<String> mTransactionTypeList = FXCollections.observableArrayList(
            "Investment Transactions", "Cash Transactions"
    );
    
    private final ObservableList<String> mInvestmentTransactionList = FXCollections.observableArrayList(
            "Buy - Shares Bought", "Sell - Shares Sold"
    );

    private final ObservableList<String> mCashTransactionList = FXCollections.observableArrayList(
            "Write Check", "Deposit", "Withdraw", "Online Payment", "Other Cash Transaction"
    );

    @FXML
    private ChoiceBox<String> mTypeChoiceBox;
    @FXML
    private ChoiceBox<String> mTransactionChoiceBox;

    private MainApp mMainApp;

    public void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;
    }

    @FXML
    private void initialize() {
        mTypeChoiceBox.getItems().addAll(mTransactionTypeList);
        mTypeChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            System.out.println(newValue);
            switch (newValue) {
                case "Investment Transactions":
                    mTransactionChoiceBox.getItems().setAll(mInvestmentTransactionList);
                    break;
                case "Cash Transactions":
                    mTransactionChoiceBox.getItems().setAll(mCashTransactionList);
                    break;
                default:
                    System.err.println("Unknown choice " + newValue);
            }
            mTransactionChoiceBox.getSelectionModel().selectFirst();
        });

        mTypeChoiceBox.getSelectionModel().selectFirst();

    }
}
