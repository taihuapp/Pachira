package net.taihuapp.facai168;

import javafx.beans.binding.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import java.text.DecimalFormat;
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
            return account.getName();
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

    static enum TransactionType {
        INVESTMENT("Investment Transactions"), CASH("Cash Transactions");

        private final String mDesc;
        private TransactionType(String d) { mDesc = d; }
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

    static enum InvestmentTransaction {
        BUY("Buy - Shares Bought"), SELL("Sell - Shares Sold");

        private final String mDesc;
        private InvestmentTransaction(String d) { mDesc = d; }
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

    static enum CashTransaction {
        CHECK("Write Check"), DEP("Deposit"), WITHDRAW("Withdraw"),
        ONLINE("Online Payment"), OTHER("Other Cash Transaction");

        private final String mDesc;
        private CashTransaction(String d) { mDesc = d; }
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
    private void addEventFilterAndChangeListener(TextField tf) {
        // add an event filter so only numerical values are permitted
        tf.addEventFilter(KeyEvent.KEY_TYPED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (!"0123456789.".contains(event.getCharacter()))
                    event.consume();
            }
        });

        // add a change listener to calculate total
/*
        tf.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                setTotal();
            }
        });
*/
    }

/*
    public void setTransaction(Transaction t) {
        if (t == null) {
            mTransaction = new Transaction(mAccount.getID(), LocalDate.now());
            System.out.println("New Transaction");
        } else {
            mTransaction = t;
            // need to setup the dialog according to the transaction
            System.out.println("Edit Transaction " + mTransaction.getID());
        }
    }
*/

    public void setDialogStage(Stage stage) {
        mDialogStage = stage;
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


        addEventFilterAndChangeListener(mSharesTextField);
        addEventFilterAndChangeListener(mPriceTextField);
        addEventFilterAndChangeListener(mCommissionTextField);
    }

    // return true if enter worked
    // false if something is not quite right
    private boolean enterTransaction() {
        System.out.println("enterTransaction: only handling InvestmentTransaction for now");
        boolean status = enterInvestmentTransaction();
        if (status)
            mMainApp.insertUpDateTransactionToDB(mTransaction);
        return status;
    }

    private boolean enterCashTransaction() {
        System.out.println("enterCashTransaction not implemented yet");
        return false;
    }

    private boolean enterInvestmentTransaction() {
        Security security = mSecurityChoiceBox.getValue();
        if (security == null) {
            showWarningDialog("Warning", "Empty Security", "Please select a valid security");
            return false;
        }

        // todo how to handle input data inconsistency

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

    private void setupInvestmentTransactionDialog(String tValue) {
        switch (InvestmentTransaction.fromString(tValue)) {
            case BUY:
                setupBuy();
                break;
            case SELL:
                setupSell();
                break;
            default:
                System.err.println("Unknown case " + tValue);
                return;
        }
    }

    private void setupCashTransactionDialog(String tValue) {
        System.err.println("CashTransactionDialog not implemented yet");
    }

    private void setupTransactionDialog(String tValue) {

        mDatePicker.setValue(LocalDate.now());
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
                setupInvestmentTransactionDialog(tValue);
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

    private void setupBuy() {
        mTransferAccountLabel.setText("Use Cash From:");
        if (mTransaction.getQuantity() == null) {
            mTransaction.setQuantity(BigDecimal.ZERO);
        }
        if (mTransaction.getPrice() == null) {
            mTransaction.setPrice(BigDecimal.ZERO);
        }
        if (mTransaction.getCommission() == null) {
            mTransaction.setCommission(BigDecimal.ZERO);
        }
        if (mTransaction.getSecurityName() == null) {
            mTransaction.setSecurityName("");
        }

        Bindings.bindBidirectional(mTransaction.getSecurityNameProperty(),
                mSecurityChoiceBox.valueProperty(), mSecurityChoiceBox.getConverter());
        mSharesTextField.textProperty().bindBidirectional(mTransaction.getQuantityProperty(),
                new BigDecimalStringConverter());
        mPriceTextField.textProperty().bindBidirectional(mTransaction.getPriceProperty(),
                new BigDecimalStringConverter());
        mCommissionTextField.textProperty().bindBidirectional(mTransaction.getCommissionProperty(),
                new BigDecimalStringConverter());
        mTotalTextField.textProperty().bindBidirectional(mTransaction.getInvestAmountProperty(),
                new BigDecimalStringConverter());

        ObjectBinding<BigDecimal> investAmount = new ObjectBinding<BigDecimal>() {
            { super.bind(mTransaction.getPriceProperty(), mTransaction.getQuantityProperty(),
                    mTransaction.getCommissionProperty()); }
            @Override
            protected BigDecimal computeValue() {
                if (mTransaction.getPriceProperty().get() == null
                        || mTransaction.getQuantityProperty().get() == null
                        || mTransaction.getCommissionProperty().get() == null) {
                    return null;
                }
                return mTransaction.getQuantity().multiply(mTransaction.getPrice().add(mTransaction.getCommission()));
            }
        };
        mTransaction.getInvestAmountProperty().bind(investAmount);
    }

    private void setupSell() {
        mTransferAccountLabel.setText("Put Cash Into:");
        setTotal(); // make sure total is consistent
    }

    private BigDecimal getBigDecimalFromTextField(TextField tf) {
        String s = tf.getText();
        if (s.length() == 0)
            return BigDecimal.ZERO;
        return new BigDecimal(s);
    }

    private void setTotal() {
        if (TransactionType.fromString(mTypeChoiceBox.getValue()) != TransactionType.INVESTMENT)
            return;

        System.out.println("setTotal");
//        BigDecimal nShares = getBigDecimalFromTextField(mSharesTextField);
//        BigDecimal price = getBigDecimalFromTextField(mPriceTextField);
//        BigDecimal commission = getBigDecimalFromTextField(mCommissionTextField);

//        BigDecimal total = nShares.multiply(price);

        System.out.println(mTransaction.getSecurityName());
        BigDecimal nShares = mTransaction.getQuantity();
        BigDecimal commission = mTransaction.getCommission();
        BigDecimal price = mTransaction.getPrice();
        BigDecimal total = nShares.multiply(price);
        switch (InvestmentTransaction.fromString(mTransactionChoiceBox.getValue())) {
            case BUY:
                total = total.add(commission);
                break;
            case SELL:
                total = total.subtract(commission);
                break;
            default:
                System.err.println("setTotal: " + mTransactionChoiceBox.getValue()
                        + " not implemented yet");
                return;
        }
        mTotalTextField.setText((new DecimalFormat("#0.00")).format(total));
    }
}
