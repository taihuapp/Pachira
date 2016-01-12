package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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

    enum TransactionClass {
        INVESTMENT("Investment Transactions"), CASH("Cash Transactions");

        private final String mDesc;
        TransactionClass(String d) { mDesc = d; }
        @Override
        public String toString() { return mDesc; }
        public static TransactionClass fromString(String s) {
            if (s != null) {
                for (TransactionClass tt : TransactionClass.values()) {
                    if (s.equals(tt.toString()))
                        return tt;
                }
            }
            return null;
        }
        public static List<String> names() {
            List<String> names = new ArrayList<>();
            TransactionClass[] tts = values();
            for (TransactionClass tt : tts) {
                names.add(tt.toString());
            }
            return names;
        }
    }

    // Investment Transaction Types
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

    // Cash Transaction Types
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

    class TransactionTypeCombo {
        InvestmentTransaction mIT;
        CashTransaction mCT;

        public TransactionTypeCombo(InvestmentTransaction it) {
            mIT = it;
            mCT = null;
        }

        public TransactionTypeCombo(CashTransaction ct) {
            mIT = null;
            mCT = ct;
        }

        public TransactionClass getTransactionType() {
            if (mIT != null)
                return TransactionClass.INVESTMENT;
            if (mCT != null)
                return TransactionClass.CASH;
            return null;
        }

        public InvestmentTransaction getIT() { return mIT; }
        public CashTransaction getCT() { return mCT; }
    }

    TransactionTypeCombo mapTradeAction(Transaction.TradeAction ta) {
        // todo need to complete all cases
        switch (ta) {
            case BUY:
            case BUYX:
                return new TransactionTypeCombo(InvestmentTransaction.BUY);
            case SELL:
            case SELLX:
                return new TransactionTypeCombo((InvestmentTransaction.SELL));
            case CASH:
                return new TransactionTypeCombo(CashTransaction.OTHER);
            default:
                // more work is needed to added new cases
                return null;
        }
    }

    private final ObservableList<String> mTransactionClassList = FXCollections.observableArrayList(
            TransactionClass.names()
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
    private ChoiceBox<String> mClassChoiceBox;
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
    private Button mSpecifyLotButton;
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
    private List<SecurityHolding.MatchInfo> mMatchInfoList = null;

    // used for mSharesTextField, mPriceTextField, mCommissionTextField
    private void addEventFilter(TextField tf) {
        // add an event filter so only numerical values are permitted
        tf.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!"0123456789.".contains(event.getCharacter()))
                event.consume();
        });
    }

    // transaction can either be null, or a copy of an existing transaction in the list
    public void setMainApp(MainApp mainApp, Transaction transaction, Stage stage) {
        mDialogStage = stage;
        mMainApp = mainApp;
        mAccount = mMainApp.getCurrentAccount();

        if (transaction == null) {
            mTransaction = new Transaction(mAccount.getID(), LocalDate.now());
        } else {
            mTransaction = transaction;
        }
        mMatchInfoList = mMainApp.getMatchInfoList(mTransaction.getID());

        setupTransactionDialog();
/*
        mSecurityChoiceBox.setConverter(new SecurityConverter());
        mSecurityChoiceBox.setItems(mMainApp.getSecurityList());

        mTransferAccountChoiceBox.setConverter(new AccountConverter());
        mTransferAccountChoiceBox.setItems(mMainApp.getAccountList());
        mTransferAccountChoiceBox.getSelectionModel().select(mAccount);

        mClassChoiceBox.getItems().addAll(mTransactionClassList);
        mClassChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            switch (TransactionClass.fromString(newValue)) {
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

        mTransactionChoiceBox.getSelectionModel().selectedItemProperty()
                .addListener((observable1, oldValue, newValue) -> {
                    setupTransactionDialog(newValue);
                });

        addEventFilter(mSharesTextField);
        addEventFilter(mPriceTextField);
        addEventFilter(mCommissionTextField);
*/
    }

    // return true if enter worked
    // false if something is not quite right
    private boolean enterTransaction() {
        System.out.println("enterTransaction: only handling InvestmentTransaction for now");
        if (validateTransaction()) {
            mMainApp.insertUpDateTransactionToDB(mTransaction);
            if (mMatchInfoList.size() > 0) {
                mMainApp.putMatchInfoList(mMatchInfoList);
            }
            Transaction.TradeAction ta = Transaction.TradeAction.valueOf(mTransaction.getTradeAction());
            BigDecimal transferAmount;
            switch (ta) {
                case BUY:
                case SELL:
                    transferAmount = BigDecimal.ZERO;
                    break;
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
            if (transferAmount.compareTo(BigDecimal.ZERO) == 0)
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
            return true;
        } else {
            return false;
        }
    }

    private boolean validateTransaction() {
        if (TransactionClass.fromString(mClassChoiceBox.getValue()) == TransactionClass.CASH) {
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
    private void handleSpecifyLots() {
        mMainApp.showSpecifyLotsDialog(mTransaction, mMatchInfoList);
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
        if (enterTransaction()) {
            mMainApp.initTransactionList(mAccount);
            mDialogStage.close();
        }
    }

    @FXML
    private void handleEnterNew() {
        if (enterTransaction())
            mMainApp.initTransactionList(mAccount);
    }

    @FXML
    private void initialize() {
    }

    private void setupTransactionDialog() {

        mSecurityChoiceBox.setConverter(new SecurityConverter());
        mSecurityChoiceBox.setItems(mMainApp.getSecurityList());

        mTransferAccountChoiceBox.setConverter(new AccountConverter());
        mTransferAccountChoiceBox.setItems(mMainApp.getAccountList());
        mTransferAccountChoiceBox.getSelectionModel().select(mAccount);

        mClassChoiceBox.getItems().setAll(mTransactionClassList);
        mClassChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            switch (TransactionClass.fromString(newValue)) {
                case INVESTMENT:
                    mTransactionChoiceBox.getItems().setAll(mInvestmentTransactionList);
                    break;
                case CASH:
                    mTransactionChoiceBox.getItems().setAll(mCashTransactionList);
                    break;
                default:
                    System.err.println("Unknown choice " + newValue);
            }
        });

        addEventFilter(mSharesTextField);
        addEventFilter(mPriceTextField);
        addEventFilter(mCommissionTextField);

        //mDatePicker.setValue(LocalDate.now());
        mDatePicker.valueProperty().bindBidirectional(mTransaction.getDateProperty());
        mAccountNameTextField.setText(mAccount.getName());

        mMemoTextField.textProperty().unbindBidirectional(mTransaction.getMemoProperty());
        mMemoTextField.textProperty().bindBidirectional(mTransaction.getMemoProperty());

        mTransactionChoiceBox.getSelectionModel().selectedItemProperty()
                .addListener((observable1, oldValue, newValue) -> {
                    switch (TransactionClass.fromString(mClassChoiceBox.getSelectionModel().getSelectedItem())) {
                        case INVESTMENT:
                            setupInvestmentTransactionDialog(InvestmentTransaction.fromString(newValue));
                            break;
                        case CASH:
                            setupCashTransactionDialog(CashTransaction.fromString(newValue));
                            break;
                        default:
                            System.err.println("mTransactionChoiceBox listener: Unimplemented case: " + newValue);
                    }
                });

        TransactionTypeCombo tc = mapTradeAction(Transaction.TradeAction.valueOf(mTransaction.getTradeAction()));
        // select Investment or Cash according to mTransaction
        mClassChoiceBox.getSelectionModel().select(tc.getTransactionType().toString());
        switch (tc.getTransactionType()) {
            case INVESTMENT:
                mTransactionChoiceBox.getSelectionModel().select(tc.getIT().toString());
                break;
            case CASH:
                mTransactionChoiceBox.getSelectionModel().select(tc.getCT().toString());
                break;
            default:
                System.err.println("setupTransactionDialog: TransactionType " + tc
                        + " not implemented");
                return;
        }

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

        /*
        switch (TransactionType.fromString(mClassChoiceBox.getValue())) {
            case INVESTMENT:
                //setupInvestmentTransactionDialog(InvestmentTransaction.fromString(tValue));
                setupInvestmentTransactionDialog(mTransaction.getTradeAction());
                return;
            case CASH:
                setupCashTransactionDialog(tValue);
                return;
            default:
                System.err.println("setupTransactionDialog: " + mClassChoiceBox.getValue()
                        + " not implemented");
                return;
        }
*/
    }

    private void setupCashTransactionDialog(CashTransaction cashType) {
        System.err.println("CashTransactionDialog not implemented yet");
    }

    private void setupInvestmentTransactionDialog(InvestmentTransaction investType) {
        mTransactionLabel.setText(investType.name());

        final BigDecimal investAmountSign;
        switch (investType) {
            case BUY:
                mSpecifyLotButton.setVisible(false);
                mTransferAccountLabel.setText("Use Cash From:");
                mTotalLabel.setText("Total Cost:");
                investAmountSign = BigDecimal.ONE;
                break;
            case SELL:
                mSpecifyLotButton.setVisible(true);
                mTransferAccountLabel.setText("Put Cash Into:");
                mTotalLabel.setText("Total Sale:");
                investAmountSign = BigDecimal.ONE.negate();
                break;
            default:
                mSpecifyLotButton.setVisible(false);
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
