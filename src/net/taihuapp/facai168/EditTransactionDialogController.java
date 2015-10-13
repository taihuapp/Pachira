package net.taihuapp.facai168;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;

/**
 * Created by ghe on 7/10/15.
 * Controller for EditTransactionDialog
 */
public class EditTransactionDialogController {

    class AccountConverter extends StringConverter<Account> {
        public Account fromString(String accountName) {
            return mMainApp.getAccountByName(accountName);
        }
        public String toString(Account account) {
            return account.getName();
        }
    }

    class SecurityConverter extends StringConverter<Security> {
        public Security fromString(String s) {
            return mMainApp.getSecurityByName(s);
        }
        public String toString(Security security) {
            return security.getName();
        }
    }

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
    @FXML
    private Label mTransactionLabel;
    @FXML
    private DatePicker mDatePicker;
    @FXML
    private TextField mAccountNameTextField;
    @FXML
    private Label mTransferAccountLabel;
    @FXML
    private ChoiceBox<Account> mTransferAccountChoiceBox;
    @FXML
    private TextField mMemoTextField;

    @FXML
    private ChoiceBox<Security> mSecurityChoiceBox;
    @FXML
    private TextField mSharesTextField;
    @FXML
    private TextField mPriceTextField;
    @FXML
    private TextField mCommissionTextField;
    @FXML
    private Label mTotalLabel;
    @FXML
    private TextField mTotalTextField;


    @FXML
    private Button mCancelButton;
    @FXML
    private Button mClearButton;
    @FXML
    private Button mEnterNewButton;
    @FXML
    private Button mEnterDoneButton;

    private MainApp mMainApp;
    private Account mAccount;
    private Stage mDialogStage;

    public void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;
        mAccount = mMainApp.getCurrentAccount();

        mTransactionChoiceBox.getSelectionModel().selectedItemProperty()
                .addListener((observable1, oldValue, newValue) -> {
                    setupTransactionDialog(newValue);
                });
        mTypeChoiceBox.getItems().addAll(mTransactionTypeList);
        mTypeChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
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

        mTransferAccountChoiceBox.setConverter(new AccountConverter());
        mTransferAccountChoiceBox.setItems(mMainApp.getAccountList());
        mTransferAccountChoiceBox.getSelectionModel().select(mAccount);

        mSecurityChoiceBox.setConverter(new SecurityConverter());
        mSecurityChoiceBox.setItems(mMainApp.getSecurityList());

        mSharesTextField.addEventFilter(KeyEvent.KEY_TYPED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (!"0123456789.".contains(event.getCharacter()))
                    event.consume();
            }
        });
        mPriceTextField.addEventFilter(KeyEvent.KEY_TYPED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (!"0123456789.".contains(event.getCharacter()))
                    event.consume();
            }
        });
        mCommissionTextField.addEventFilter(KeyEvent.KEY_TYPED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (!"0123456789.".contains(event.getCharacter()))
                    event.consume();
            }
        });
        mTotalTextField.addEventFilter(KeyEvent.KEY_TYPED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (!"0123456789.".contains(event.getCharacter()))
                    event.consume();
            }
        });

    }

    public void setDialogStage(Stage stage) {          System.out.println("setupDialogStage");mDialogStage = stage; }

    private void enterTransaction() {
        System.out.println("Enter Transaction");
    }
    @FXML
    private void handleCancel() {
        mDialogStage.close();
    }

    @FXML
    private void handleClear() {
        System.out.println("Clear");
    }

    @FXML
    private void handleEnterDone() {
        enterTransaction();
        mDialogStage.close();
    }

    @FXML
    private void handleEnterNew() {
        enterTransaction();
    }

    @FXML
    private void initialize() {
    }

    private void setupTransactionDialog(String tValue) {
        if (!mInvestmentTransactionList.contains(tValue) && !mCashTransactionList.contains(tValue)) {
            // we really should never be here.
            System.err.println("Invalid Transaction: " + tValue);
            return;
        }

        mDatePicker.setValue(LocalDate.now());
        mTransactionLabel.setText(tValue);
        mAccountNameTextField.setText(mAccount.getName());
        switch (tValue) {
            case "Buy - Shares Bought":
                setupBuy();
                break;
            case "Sell - Shares Sold":
                setupSell();
                break;
            default:
                System.err.println("Unknown case " + tValue);
                return;
        }
    }

    private void setupBuy() {
        mTransferAccountLabel.setText("Use Cash From:");
    }

    private void setupSell() {
        mTransferAccountLabel.setText("Put Cash Into:");
    }
}
