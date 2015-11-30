package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
            if (account == null)
                return null;
            return account.getName();
        }
    }

    class AccountCategoryConverter extends StringConverter<Account> {
        public Account fromString(String wrapedAccountName) {
            return mMainApp.getAccountByWrapedName(wrapedAccountName);
        }
        public String toString(Account account) {
            return mMainApp.getWrappedAccountName(account);
        }
    }

    class SecurityConverter extends StringConverter<Security> {
        public Security fromString(String s) {
            return mMainApp.getSecurityByName(s);
        }
        public String toString(Security security) {
            if (security == null)
                return null;
            return security.getName();
        }
    }

    enum TransactionType {
        INVESTMENT("Investment Transactions"), CASH("Cash Transactions");

        private final String mDesc;
        TransactionType(String d) { mDesc = d; }
        @Override
        public String toString() { return mDesc; }
        public static TransactionType fromString(String s) {
            if (s != null) {
                for (TransactionType tt : TransactionType.values()) {
                    if (s.equals(tt.toString()))
                        return tt;
                }
            }
            return null;
        }
        public static List<String> names() {
            List<String> names = new ArrayList<>();
            TransactionType[] tts = values();
            for (TransactionType tt : tts) {
                names.add(tt.toString());
            }
            return names;
        }
    }

    enum InvestmentTransaction {
        BUY("Buy - Shares Bought"), SELL("Sell - Shares Sold");

        private final String mDesc;
        InvestmentTransaction(String d) { mDesc = d; }
        @Override
        public String toString() { return mDesc; }
        public static InvestmentTransaction fromString(String s) {
            if (s != null) {
                for (InvestmentTransaction it : InvestmentTransaction.values()) {
                    if (s.equals(it.toString()))
                        return it;
                }
            }
            return null;
        }
        public static List<String> names() {
            List<String> names = new ArrayList<>();
            InvestmentTransaction[] its = values();
            for (InvestmentTransaction it : its) {
                names.add(it.toString());
            }
            return names;
        }
    }

    enum CashTransaction {
        CHECK("Write Check"), DEP("Deposit"), WITHDRAW("Withdraw"),
        ONLINE("Online Payment"), OTHER("Other Cash Transaction");

        private final String mDesc;
        CashTransaction(String d) { mDesc = d; }
        @Override
        public String toString() { return mDesc; }
        public static CashTransaction fromString(String s) {
            if (s != null) {
                for (CashTransaction ct : CashTransaction.values()) {
                    if (s.equals(ct.toString()))
                        return ct;
                }
            }
            return null;
        }
        public static List<String> names() {
            List<String> names = new ArrayList<>();
            CashTransaction[] cts = values();
            for (CashTransaction ct : cts) {
                names.add(ct.toString());
            }
            return names;
        }
    }

    private final ObservableList<String> mTransactionTypeList = FXCollections.observableArrayList(
            TransactionType.names()
            //"Investment Transactions", "Cash Transactions"
    );
    
    private final ObservableList<String> mInvestmentTransactionList = FXCollections.observableArrayList(
            InvestmentTransaction.names()
            //"Buy - Shares Bought", "Sell - Shares Sold"
    );

    private final ObservableList<String> mCashTransactionList = FXCollections.observableArrayList(
            CashTransaction.names()
            //"Write Check", "Deposit", "Withdraw", "Online Payment", "Other Cash Transaction"
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

    private Transaction mTransaction = null;

    // used for mSharesTextField, mPriceTextField, mCommissionTextField
    private void addEventFilter(TextField tf) {
        // add an event filter so only numerical values are permitted
        tf.addEventFilter(KeyEvent.KEY_TYPED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (!"0123456789.".contains(event.getCharacter()))
                    event.consume();
            }
        });
    }

    public void setMainApp(MainApp mainApp, Transaction transaction, Stage stage) {
        mDialogStage = stage;
        mMainApp = mainApp;
        mAccount = mMainApp.getCurrentAccount();

        mSecurityChoiceBox.setConverter(new SecurityConverter());
        mSecurityChoiceBox.setItems(mMainApp.getSecurityList());

        if (transaction == null) {
            mTransaction = new Transaction(mAccount.getID(), LocalDate.now());
        } else {
            mTransaction = transaction;
        }
        mTransferAccountChoiceBox.setConverter(new AccountConverter());
        mTransferAccountChoiceBox.setItems(mMainApp.getAccountList());
        mTransferAccountChoiceBox.getSelectionModel().select(mAccount);

        mTransactionChoiceBox.getSelectionModel().selectedItemProperty()
                .addListener((observable1, oldValue, newValue) -> {
                    setupTransactionDialog(newValue);
                });
        mTypeChoiceBox.getItems().addAll(mTransactionTypeList);
        mTypeChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            switch (TransactionType.fromString(newValue)) {
                case INVESTMENT:
                    mTransactionChoiceBox.getItems().setAll(mInvestmentTransactionList);
                    break;
                case CASH:
                    mTransactionChoiceBox.getItems().setAll(mCashTransactionList);
                    break;
                default:
                    System.err.println("Unknown choice " + newValue);
            }
            mTransactionChoiceBox.getSelectionModel().selectFirst();
        });

        mTypeChoiceBox.getSelectionModel().selectFirst();

        // binding


        addEventFilter(mSharesTextField);
        addEventFilter(mPriceTextField);
        addEventFilter(mCommissionTextField);
    }

    // return true if enter worked
    // false if something is not quite right
    private boolean enterTransaction() {
        System.out.println("enterTransaction: only handling InvestmentTransaction for now");
        if (validateTransaction()) {
            mMainApp.insertUpDateTransactionToDB(mTransaction);
            Transaction.TradeAction ta = Transaction.TradeAction.valueOf(mTransaction.getTradeAction());
            BigDecimal transferAmount = BigDecimal.ZERO;
            switch (ta) {
                case BUYX:
                    transferAmount = mTransaction.getAmount().negate();
                    break;
                case SELLX:
                    transferAmount = mTransaction.getAmount();
                    break;
                default:
                    System.err.println("enterTransaction: Trade Action " + ta + " not implemented yet.");
                    return false;
            }
            if (transferAmount == BigDecimal.ZERO)
                return true;

            int tID = mTransaction.getMatchID();
            String wrappedTransferAccountName = mTransaction.getCategory();
            Account transferAccount = mMainApp.getAccountByWrapedName(wrappedTransferAccountName);
            if (transferAccount == null) {
                System.err.println("Bad transfer account name: " + wrappedTransferAccountName);
                return false;
            }

            Transaction linkedTransaction = new Transaction(tID, transferAccount.getID(), mTransaction.getDate(),
                    "", mTransaction.getSecurityName(), mTransaction.getMemo(),
                    mMainApp.getWrappedAccountName(mAccount), transferAmount, mTransaction.getID(), -1);
            mMainApp.insertUpDateTransactionToDB(linkedTransaction);
            if (mTransaction.getMatchID() != linkedTransaction.getID()) {
                mTransaction.setMatchID(linkedTransaction.getID(), 0);
                mMainApp.insertUpDateTransactionToDB(mTransaction);
            }

            mMainApp.initTransactionList(mAccount);
            return true;
        } else {
            return false;
        }
    }

    private boolean validateTransaction() {
        if (TransactionType.fromString(mTypeChoiceBox.getValue()) == TransactionType.CASH) {
            System.err.println("CASH Transaction not implemented yet");
            return false;
        }

        Security security = mSecurityChoiceBox.getValue();
        if (security == null) {
            showWarningDialog("Warning", "Empty Security", "Please select a valid security");
            return false;
        }

        return true;
    }

    private void showWarningDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
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
        if (enterTransaction())
            mDialogStage.close();
    }

    @FXML
    private void handleEnterNew() {
        enterTransaction();
    }

    @FXML
    private void initialize() {
    }

    private void setupCashTransactionDialog(String tValue) {
        System.err.println("CashTransactionDialog not implemented yet");
    }

    private void setupTransactionDialog(String tValue) {

        //mDatePicker.setValue(LocalDate.now());
        mDatePicker.valueProperty().bindBidirectional(mTransaction.getDateProperty());
        mTransactionLabel.setText(tValue);
        mAccountNameTextField.setText(mAccount.getName());

        mTransaction.getTradeActionProperty().bind(new StringBinding() {
            { super.bind(mTransferAccountChoiceBox.valueProperty(),
                    mTransactionChoiceBox.valueProperty()); }
            @Override
            protected String computeValue() {

                String t = InvestmentTransaction.fromString(mTransactionChoiceBox.valueProperty().get()).name();

                if (mTransferAccountChoiceBox.valueProperty().get().getID() !=
                        mAccount.getID()) {
                    return t + "X";
                } else {
                    return t;
                }
            }
        });

        switch (TransactionType.fromString(mTypeChoiceBox.getValue())) {
            case INVESTMENT:
                setupInvestmentTransactionDialog(InvestmentTransaction.fromString(tValue));
                return;
            case CASH:
                setupCashTransactionDialog(tValue);
                return;
            default:
                System.err.println("setupTransactionDialog: " + mTypeChoiceBox.getValue()
                        + " not implemented");
                return;
        }
    }

    private void setupInvestmentTransactionDialog(InvestmentTransaction investType) {
        final BigDecimal investAmountSign;
        switch (investType) {
            case BUY:
                mTransferAccountLabel.setText("Use Cash From:");
                mTotalLabel.setText("Total Cost:");
                investAmountSign = BigDecimal.ONE;
                break;
            case SELL:
                mTransferAccountLabel.setText("Put Cash Into:");
                mTotalLabel.setText("Total Cost:");
                investAmountSign = BigDecimal.ONE.negate();
                break;
            default:
                System.err.println("InvestmentTransaction " + investType + " not implemented yet.");
                return;
        }

        ObjectBinding<BigDecimal> amount = new ObjectBinding<BigDecimal>() {
            { super.bind(mTransaction.getPriceProperty(), mTransaction.getQuantityProperty(),
                    mTransaction.getCommissionProperty()); }
            @Override
            protected BigDecimal computeValue() {
                if (mTransaction.getPriceProperty().get() == null
                        || mTransaction.getQuantityProperty().get() == null
                        || mTransaction.getCommissionProperty().get() == null) {
                    return null;
                }
                return mTransaction.getQuantity().multiply(mTransaction.getPrice())
                        .add(mTransaction.getCommission().multiply(investAmountSign));
            }
        };

        if (mTransaction.getAmountProperty().isBound())
            mTransaction.getAmountProperty().unbind();
        mTransaction.getAmountProperty().bind(amount);

        // mapCategory return negative account id or positive category id
        Account transferAccount = mMainApp.getAccountByWrapedName(mTransaction.getCategory());
        if (transferAccount == null)
            transferAccount = mAccount;
        mTransaction.getCategoryProperty().unbindBidirectional(mTransferAccountChoiceBox.valueProperty());
        Bindings.bindBidirectional(mTransaction.getCategoryProperty(),
                mTransferAccountChoiceBox.valueProperty(), new AccountCategoryConverter());
        mTransferAccountChoiceBox.getSelectionModel().select(transferAccount);

        Security currentSecurity = mMainApp.getSecurityByName(mTransaction.getSecurityName());
        mTransaction.getSecurityNameProperty().unbindBidirectional(mSecurityChoiceBox.valueProperty());
        Bindings.bindBidirectional(mTransaction.getSecurityNameProperty(),
                mSecurityChoiceBox.valueProperty(), mSecurityChoiceBox.getConverter());
        mSecurityChoiceBox.getSelectionModel().select(currentSecurity);

        mSharesTextField.textProperty().unbindBidirectional(mTransaction.getQuantityProperty());
        mSharesTextField.textProperty().bindBidirectional(mTransaction.getQuantityProperty(),
                new BigDecimalStringConverter());

        mPriceTextField.textProperty().unbindBidirectional(mTransaction.getPriceProperty());
        mPriceTextField.textProperty().bindBidirectional(mTransaction.getPriceProperty(),
                new BigDecimalStringConverter());

        mCommissionTextField.textProperty().unbindBidirectional(mTransaction.getCommissionProperty());
        mCommissionTextField.textProperty().bindBidirectional(mTransaction.getCommissionProperty(),
                new BigDecimalStringConverter());

        mTotalTextField.textProperty().unbindBidirectional(mTransaction.getAmountProperty());
        mTotalTextField.textProperty().bindBidirectional(mTransaction.getAmountProperty(),
                new BigDecimalStringConverter());
    }
}
