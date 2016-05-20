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

    private class AccountConverter extends StringConverter<Account> {
        public Account fromString(String accountName) {
            return mMainApp.getAccountByName(accountName);
        }
        public String toString(Account account) {
            if (account == null)
                return null;
            return account.getName();
        }
    }

    private class AccountCategoryConverter extends StringConverter<Account> {
        public Account fromString(String wrapedAccountName) {
            return mMainApp.getAccountByWrappedName(wrapedAccountName);
        }
        public String toString(Account account) {
            return MainApp.getWrappedAccountName(account);
        }
    }

    private class SecurityConverter extends StringConverter<Security> {
        public Security fromString(String s) {
            return mMainApp.getSecurityByName(s);
        }
        public String toString(Security security) {
            if (security == null)
                return null;
            return security.getName();
        }
    }

    private enum TransactionClass {
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
    private enum InvestmentTransaction {
        BUY("Buy - Shares Bought"), SELL("Sell - Shares Sold"),
        SHTSELL("Short Sell"), CVTSHRT("Cover Short Sale"), SHRSIN("Shares Added"), SHRSOUT("Shares Removed"),
        DIV("Dividend"), INTINC("Interest"), CGLONG("Long-term Cap Gain"),
        CGMID("Mid-term Cap Gain"), CGSHORT("Short-term Cap Gain"),
        REINVDIV("Reinvest Dividend"), REINVINT("Reinvest Interest"),  REINVLG("Reinvest Long-term Cap Gain"),
        REINVMD("Reinvest Mid-term Cap Gain"), REINVSH("Reinvest Short-term Cap Gain"),
        STKSPLIT("Stock Split"),
        XIN("Cash Transferred In"), XOUT("Cash Transferred Out"),
        DEPOSIT("Deposit Money"), WITHDRAW("Withdraw Money");

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
    private enum CashTransaction {
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

    private class TransactionTypeCombo {
        InvestmentTransaction mIT;
        CashTransaction mCT;

        TransactionTypeCombo(InvestmentTransaction it) {
            mIT = it;
            mCT = null;
        }

        TransactionTypeCombo(CashTransaction ct) {
            mIT = null;
            mCT = ct;
        }

        TransactionClass getTransactionType() {
            if (mIT != null)
                return TransactionClass.INVESTMENT;
            if (mCT != null)
                return TransactionClass.CASH;
            return null;
        }

        InvestmentTransaction getIT() { return mIT; }
        CashTransaction getCT() { return mCT; }
    }

    private TransactionTypeCombo mapTradeAction(Transaction.TradeAction ta) {
        // todo need to complete all cases
        switch (ta) {
            case BUY:
            case BUYX:
                return new TransactionTypeCombo(InvestmentTransaction.BUY);
            case SELL:
            case SELLX:
                return new TransactionTypeCombo((InvestmentTransaction.SELL));
            case SHTSELL:
            case SHTSELLX:
                return new TransactionTypeCombo((InvestmentTransaction.SHTSELL));
            case SHRSIN:
                return new TransactionTypeCombo(InvestmentTransaction.SHRSIN);
            case SHRSOUT:
                return new TransactionTypeCombo(InvestmentTransaction.SHRSOUT);
            case DIV:
            case DIVX:
                return new TransactionTypeCombo(InvestmentTransaction.DIV);
            case INTINC:
            case INTINCX:
                return new TransactionTypeCombo(InvestmentTransaction.INTINC);
            case CGLONG:
            case CGLONGX:
                return new TransactionTypeCombo(InvestmentTransaction.CGLONG);
            case CGMID:
            case CGMIDX:
                return new TransactionTypeCombo(InvestmentTransaction.CGMID);
            case CGSHORT:
            case CGSHORTX:
                return new TransactionTypeCombo(InvestmentTransaction.CGSHORT);
            case REINVDIV:
                return new TransactionTypeCombo(InvestmentTransaction.REINVDIV);
            case REINVINT:
                return new TransactionTypeCombo(InvestmentTransaction.REINVINT);
            case REINVLG:
                return new TransactionTypeCombo(InvestmentTransaction.REINVLG);
            case REINVMD:
                return new TransactionTypeCombo(InvestmentTransaction.REINVMD);
            case REINVSH:
                return new TransactionTypeCombo(InvestmentTransaction.REINVSH);
            case XIN:
                return new TransactionTypeCombo(InvestmentTransaction.XIN);
            case XOUT:
                return new TransactionTypeCombo(InvestmentTransaction.XOUT);
            case DEPOSIT:
                return new TransactionTypeCombo(InvestmentTransaction.DEPOSIT);
            case WITHDRAW:
                return new TransactionTypeCombo(InvestmentTransaction.WITHDRAW);
            case STKSPLIT:
                return new TransactionTypeCombo(InvestmentTransaction.STKSPLIT);
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
    private DatePicker mTDatePicker;
    @FXML
    private Label mADatePickerLabel;
    @FXML
    private DatePicker mADatePicker;
    @FXML
    private TextField mAccountNameTextField;
    @FXML
    private Label mTransferAccountLabel;
    @FXML
    private ComboBox<Account> mTransferAccountComboBox;
    @FXML
    private Label mCategoryLabel;
    @FXML
    private ComboBox mCategoryComboBox;
    @FXML
    private TextField mMemoTextField;
    @FXML
    private Label mSecurityNameLabel;
    @FXML
    private ComboBox<Security> mSecurityComboBox;
    @FXML
    private Label mPayeeLabel;
    @FXML
    private TextField mPayeeTextField;
    @FXML
    private Label mIncomeLabel;
    @FXML
    private TextField mIncomeTextField;
    @FXML
    private Label mSharesLabel;
    @FXML
    private TextField mSharesTextField;
    @FXML
    private Label mOldSharesLabel;
    @FXML
    private TextField mOldSharesTextField;
    @FXML
    private Label mPriceLabel;
    @FXML
    private TextField mPriceTextField;
    @FXML
    private Label mCommissionLabel;
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

    // used for mSharesTextField, mPriceTextField, mCommissionTextField, mOldSharesTextField
    private void addEventFilter(TextField tf) {
        // add an event filter so only numerical values are permitted
        tf.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!"0123456789.".contains(event.getCharacter()))
                event.consume();
        });
    }

    // transaction can either be null, or a copy of an existing transaction in the list
    void setMainApp(MainApp mainApp, Transaction transaction, Stage stage) {
        mDialogStage = stage;
        mMainApp = mainApp;
        mAccount = mMainApp.getCurrentAccount();

        if (transaction == null) {
            mTransaction = new Transaction(mAccount.getID(), LocalDate.now(),
                    Transaction.TradeAction.BUY);
        } else {
            mTransaction = transaction;
        }
        mMatchInfoList = mMainApp.getMatchInfoList(mTransaction.getID());

        setupTransactionDialog();
    }

    // return true if enter worked
    // false if something is not quite right
    private boolean enterTransaction() {
        if (!validateTransaction())
            return false;  // invalid transaction

        // update database for the main transaction and update account balance
        mMainApp.insertUpDateTransactionToDB(mTransaction);
        mMainApp.updateAccountBalance(mTransaction.getAccountID());

        Transaction.TradeAction ta = Transaction.TradeAction.valueOf(mTransaction.getTradeAction());
        if ((ta != Transaction.TradeAction.SELL &&
                ta != Transaction.TradeAction.SELLX &&
                ta != Transaction.TradeAction.CVTSHRT &&
                ta != Transaction.TradeAction.CVTSHRTX)) {
            // only SELL or CVTSHORT needs the MatchInfoList
            mMatchInfoList.clear();
        }
        mMainApp.putMatchInfoList(mMatchInfoList);
        Transaction.TradeAction xferTA = null;
        BigDecimal transferAmount;
        switch (ta) {
            case BUY:
            case SELL:
            case SHTSELL:
            case SHRSIN:
            case DIV:
            case INTINC:
            case CGLONG:
            case CGMID:
            case CGSHORT:
            case REINVDIV:
            case REINVINT:
            case REINVLG:
            case REINVMD:
            case REINVSH:
            case DEPOSIT:  // deposit and withdraw are not transfered from known account
            case WITHDRAW:
            case STKSPLIT:
                transferAmount = BigDecimal.ZERO;
                break;
            case BUYX:
            case XIN:
                transferAmount = mTransaction.getAmount();
                xferTA = Transaction.TradeAction.XOUT;
                break;
            case SELLX:
            case SHTSELLX:
            case DIVX:
            case INTINCX:
            case CGLONGX:
            case CGMIDX:
            case CGSHORTX:
            case XOUT:
                transferAmount = mTransaction.getAmount();
                xferTA = Transaction.TradeAction.XIN;
                break;
            default:
                System.err.println("enterTransaction: Trade Action " + ta + " not implemented yet.");
                return false;
        }

        if (xferTA == null) {
            System.err.println("Null xferTA??");
            return false;
        }

        int tID = mTransaction.getMatchID();
        String wrappedTransferAccountName = mTransaction.getCategory();
        Account xferAccount = mMainApp.getAccountByWrappedName(wrappedTransferAccountName);
        if (xferAccount == null) {
            // blank account name, probably transfer from an unknown account
            // no need to have a match transaction
            if (tID > 0)
                mMainApp.deleteTransactionFromDB(tID);  // delete the orphan matching transaction

            return true;
        }

        if (transferAmount.compareTo(BigDecimal.ZERO) == 0) {
            // no transfer
            if (tID > 0) {
                mMainApp.deleteTransactionFromDB(tID);
                mMainApp.updateAccountBalance(xferAccount.getID());
            }
            return true;
        }

        Transaction linkedTransaction = new Transaction(tID, mTransaction.getTDate(), xferTA);
        linkedTransaction.setMemo(mTransaction.getMemo());
        linkedTransaction.setMatchID(mTransaction.getID(), 0);
        if (xferAccount.getType() == Account.Type.INVESTING) {
            linkedTransaction.getAmountProperty().set(transferAmount);
        } else {
            linkedTransaction.getTradeActionProperty().set(null);
            linkedTransaction.getAmountProperty().set(transferAmount.negate());
        }
        mMainApp.insertUpDateTransactionToDB(linkedTransaction);
        if (mTransaction.getMatchID() != linkedTransaction.getID()) {
            mTransaction.setMatchID(linkedTransaction.getID(), 0);
            mMainApp.insertUpDateTransactionToDB(mTransaction);
        }
        mMainApp.updateAccountBalance(xferAccount.getID());
        return true;
    }

    // return true if the transaction is validated, false otherwise
    private boolean validateTransaction() {
        if (TransactionClass.fromString(mClassChoiceBox.getValue()) == TransactionClass.CASH) {
            System.err.println("CASH Transaction not implemented yet");
            return false;
        }

        Security security = mSecurityComboBox.getValue();
        if (security != null && security.getID() > 0)  // has a valid security
            return true;

        // empty securiy here
        // for cash related transaction, return true
        if (mTransaction.getTradeAction().equals("DIV") || mTransaction.getTradeAction().equals("DIVX")
                || mTransaction.getTradeAction().equals("INTINC") || mTransaction.getTradeAction().equals("INTINCX")
                || mTransaction.getTradeAction().equals("XIN") || mTransaction.getTradeAction().equals("XOUT")
                || mTransaction.getTradeAction().equals("DEPOSIT") || mTransaction.getTradeAction().equals("WITHDRAW")
                || mTransaction.getTradeAction().equals("CASH")) {
            return true;
        }

        // return false for all other transactions without security
        showWarningDialog("Warning", "Empty Security", "Please select a valid security");
        return false;
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
            mAccount.setTransactionList(mMainApp.loadAccountTransactions(mAccount.getID()));
            mDialogStage.close();
        }
    }

    @FXML
    private void handleEnterNew() {
        if (enterTransaction()) {
            mTransaction.setID(0); // a new transaction
            mAccount.setTransactionList(mMainApp.loadAccountTransactions(mAccount.getID()));
        }
    }

    private void setupTransactionDialog() {

        mSecurityComboBox.setConverter(new SecurityConverter());
        mSecurityComboBox.getItems().add(new Security());  // add a Blank Security
        mSecurityComboBox.getItems().addAll(mMainApp.getSecurityList());

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
        addEventFilter(mOldSharesTextField);
        addEventFilter(mPriceTextField);
        addEventFilter(mCommissionTextField);

        mTDatePicker.valueProperty().bindBidirectional(mTransaction.getTDateProperty());

        mADatePicker.valueProperty().bindBidirectional(mTransaction.getADateProperty());
        mAccountNameTextField.setText(mAccount.getName());

        mMemoTextField.textProperty().unbindBidirectional(mTransaction.getMemoProperty());
        mMemoTextField.textProperty().bindBidirectional(mTransaction.getMemoProperty());

        mPayeeTextField.textProperty().unbindBidirectional(mTransaction.getPayeeProperty());
        mPayeeTextField.textProperty().bindBidirectional(mTransaction.getPayeeProperty());

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
        // todo:  tc == null???
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

        mTransaction.getTradeActionProperty().unbind();
        mTransaction.getTradeActionProperty().bind(new StringBinding() {
            { super.bind(mTransferAccountComboBox.valueProperty(),
                    mTransactionChoiceBox.valueProperty()); }
            @Override
            protected String computeValue() {
                InvestmentTransaction it
                        = InvestmentTransaction.fromString(mTransactionChoiceBox.valueProperty().get());
                Account xferAccount = mTransferAccountComboBox.valueProperty().get();
                if (xferAccount != null && xferAccount.getID() == mAccount.getID())
                    return it.name();

                switch (it) {
                    case SHRSIN:
                    case SHRSOUT:
                    case REINVDIV:
                    case REINVINT:
                    case REINVLG:
                    case REINVMD:
                    case REINVSH:
                    case XIN:
                    case XOUT:
                    case DEPOSIT:
                    case WITHDRAW:
                    case STKSPLIT:
                        return it.name();
                    case BUY:
                    case SELL:
                    case SHTSELL:
                    case CVTSHRT:
                    case DIV:
                    case INTINC:
                    case CGLONG:
                    case CGMID:
                    case CGSHORT:
                        return it.name() + "X";
                    default:
                        System.err.println("Unhandled InvestmentTransaction type " + it.name());
                        return null;
                }
            }
        });
    }

    private void setupCashTransactionDialog(CashTransaction cashType) {
        System.err.println("CashTransactionDialog not implemented yet, Transaction type = " + cashType.name());
    }

    private void setupInvestmentTransactionDialog(InvestmentTransaction investType) {
        mTransactionLabel.setText(investType.name());

        final BigDecimal investAmountSign;
        boolean isIncome = false;
        boolean isReinvest = false;
        boolean isCashTransfer = false;
        switch (investType) {
            case STKSPLIT:
                isCashTransfer = false;
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
                mSharesLabel.setText("New Shares");
                mSharesLabel.setVisible(true);
                mSharesTextField.setVisible(true);
                mOldSharesLabel.setVisible(true);
                mOldSharesTextField.setVisible(true);
                mPriceLabel.setVisible(false);
                mPriceTextField.setVisible(false);
                mCommissionLabel.setVisible(false);
                mCommissionTextField.setVisible(false);
                mSpecifyLotButton.setVisible(false);
                mTransferAccountLabel.setVisible(false);
                mTransferAccountComboBox.setVisible(false);
                mADatePickerLabel.setVisible(false);
                mADatePicker.setVisible(false);
                mIncomeLabel.setVisible(false);
                mIncomeTextField.setVisible(false);
                mTotalLabel.setVisible(false);
                mTotalTextField.setVisible(false);
                investAmountSign = BigDecimal.ONE;
                break;
            case DEPOSIT:
            case WITHDRAW:
                isCashTransfer = true;
                mCategoryLabel.setVisible(true);
                mCategoryComboBox.setVisible(true);
                mPayeeLabel.setVisible(true);
                mPayeeTextField.setVisible(true);
                mSecurityNameLabel.setVisible(false);
                mSecurityComboBox.setVisible(false);
                mSharesLabel.setVisible(false);
                mSharesTextField.setVisible(false);
                mOldSharesLabel.setVisible(false);
                mOldSharesTextField.setVisible(false);
                mPriceLabel.setVisible(false);
                mPriceTextField.setVisible(false);
                mCommissionLabel.setVisible(false);
                mCommissionTextField.setVisible(false);
                mSpecifyLotButton.setVisible(false);
                mTransferAccountLabel.setVisible(false);
                mTransferAccountComboBox.setVisible(false);
                mADatePickerLabel.setVisible(false);
                mADatePicker.setVisible(false);
                mIncomeLabel.setVisible(false);
                mIncomeTextField.setVisible(false);
                mTotalLabel.setVisible(true);
                mTotalTextField.setVisible(true);
                mTotalLabel.setText("Amount:");
                mTotalTextField.setEditable(true);
                investAmountSign = BigDecimal.ONE;
                break;
            case XIN:
            case XOUT:
                isCashTransfer = true;
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(false);
                mSecurityComboBox.setVisible(false);
                mSharesLabel.setVisible(false);
                mSharesTextField.setVisible(false);
                mOldSharesLabel.setVisible(false);
                mOldSharesTextField.setVisible(false);
                mPriceLabel.setVisible(false);
                mPriceTextField.setVisible(false);
                mCommissionLabel.setVisible(false);
                mCommissionTextField.setVisible(false);
                mSpecifyLotButton.setVisible(false);
                mTransferAccountLabel.setVisible(true);
                mTransferAccountComboBox.setVisible(true);
                mADatePickerLabel.setVisible(false);
                mADatePicker.setVisible(false);
                mIncomeLabel.setVisible(false);
                mIncomeTextField.setVisible(false);
                if (investType == InvestmentTransaction.XIN)
                    mTransferAccountLabel.setText("Transfer Cash From:");
                else
                    mTransferAccountLabel.setText("Transfer Cash To:");
                mTotalLabel.setVisible(true);
                mTotalTextField.setVisible(true);
                mTotalLabel.setText("Transfer Amount:");
                mTotalTextField.setEditable(true);
                investAmountSign = BigDecimal.ONE;
                break;
            case BUY:
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
                mSharesLabel.setText("Number of Shares");
                mSharesLabel.setVisible(true);
                mSharesTextField.setVisible(true);
                mOldSharesLabel.setVisible(false);
                mOldSharesTextField.setVisible(false);
                mPriceLabel.setVisible(true);
                mPriceTextField.setVisible(true);
                mCommissionLabel.setVisible(true);
                mCommissionTextField.setVisible(true);
                mSpecifyLotButton.setVisible(false);
                mTransferAccountLabel.setVisible(true);
                mTransferAccountComboBox.setVisible(true);
                mADatePickerLabel.setVisible(false);
                mADatePicker.setVisible(false);
                mIncomeLabel.setVisible(false);
                mIncomeTextField.setVisible(false);
                mTransferAccountLabel.setText("Use Cash From:");
                mTotalLabel.setVisible(true);
                mTotalTextField.setVisible(true);
                mTotalLabel.setText("Total Cost:");
                mTotalTextField.setEditable(false);
                investAmountSign = BigDecimal.ONE;
                break;
            case SELL:
            case SHTSELL:
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
                mSharesLabel.setText("Number of Shares");
                mSharesLabel.setVisible(true);
                mSharesTextField.setVisible(true);
                mOldSharesLabel.setVisible(false);
                mOldSharesTextField.setVisible(false);
                mPriceLabel.setVisible(true);
                mPriceTextField.setVisible(true);
                mCommissionLabel.setVisible(true);
                mCommissionTextField.setVisible(true);
                mSpecifyLotButton.setVisible(investType == InvestmentTransaction.SELL);
                mTransferAccountLabel.setVisible(true);
                mTransferAccountComboBox.setVisible(true);
                mADatePickerLabel.setVisible(false);
                mADatePicker.setVisible(false);
                mIncomeLabel.setVisible(false);
                mIncomeTextField.setVisible(false);
                mTransferAccountLabel.setText("Put Cash Into:");
                mTotalLabel.setVisible(true);
                mTotalTextField.setVisible(true);
                mTotalLabel.setText("Total Sale:");
                mTotalTextField.setEditable(false);
                investAmountSign = BigDecimal.ONE.negate();
                break;
            case SHRSIN:
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
                mSharesLabel.setText("Number of Shares");
                mSharesLabel.setVisible(true);
                mSharesTextField.setVisible(true);
                mOldSharesLabel.setVisible(false);
                mOldSharesTextField.setVisible(false);
                mPriceLabel.setVisible(true);
                mPriceTextField.setVisible(true);
                mCommissionLabel.setVisible(true);
                mCommissionTextField.setVisible(true);
                mSpecifyLotButton.setVisible(false);
                mTransferAccountLabel.setVisible(false);
                mTransferAccountComboBox.setVisible(false);
                mADatePickerLabel.setVisible(true);
                mADatePicker.setVisible(true);
                mIncomeLabel.setVisible(false);
                mIncomeTextField.setVisible(false);
                mTotalLabel.setVisible(true);
                mTotalTextField.setVisible(true);
                mTotalLabel.setText("Total Cost:");
                mTotalTextField.setEditable(false);
                investAmountSign = BigDecimal.ONE;
                break;
            case REINVDIV:
            case REINVINT:
            case REINVLG:
            case REINVMD:
            case REINVSH:
                isReinvest = true;
                // fall through here
            case DIV:
            case INTINC:
            case CGMID:
            case CGSHORT:
                isIncome = true;
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
                mSharesLabel.setText("Number of Shares");
                mSharesLabel.setVisible(isReinvest);
                mSharesTextField.setVisible(isReinvest);
                mOldSharesLabel.setVisible(false);
                mOldSharesTextField.setVisible(false);
                mPriceLabel.setVisible(isReinvest);
                mPriceTextField.setVisible(isReinvest);
                mPriceTextField.setEditable(!isReinvest);  // calculated price when Reinvest
                mCommissionLabel.setVisible(isReinvest);
                mCommissionTextField.setVisible(isReinvest);
                mSpecifyLotButton.setVisible(false);
                mTransferAccountLabel.setVisible(!isReinvest);
                mTransferAccountComboBox.setVisible(!isReinvest);
                mADatePickerLabel.setVisible(false);
                mADatePicker.setVisible(false);
                mIncomeLabel.setVisible(true);
                mIncomeTextField.setVisible(true);
                mTotalLabel.setVisible(true);
                mTotalTextField.setVisible(true);
                mTotalLabel.setText("Total Income");
                mTotalTextField.setEditable(false);
                mIncomeLabel.setText(investType.toString());
                investAmountSign = BigDecimal.ONE;
                break;
            default:
                System.err.println("InvestmentTransaction " + investType + " not implemented yet.");
                return;
        }

        mTransferAccountComboBox.setConverter(new AccountConverter());
        mTransferAccountComboBox.getItems().clear();
        mTransferAccountComboBox.getItems().add(new Account()); // a blank account
        System.out.println("setupInvestmentTransactionDialog");
        for (Account account : mMainApp.getAccountList()) {
            if (account.getID() != mAccount.getID() || !isCashTransfer)
                mTransferAccountComboBox.getItems().add(account);
        }
        mTransferAccountComboBox.getSelectionModel().select(isCashTransfer ? new Account() : mAccount);

        // make sure it is not bind
        mTransaction.getAmountProperty().unbind();
        mTransaction.getPriceProperty().unbind();

        if (isIncome) {
            mIncomeTextField.textProperty().bindBidirectional(mTransaction.getAmountProperty(),
                    new BigDecimalStringConverter());
            if (isReinvest) {
                ObjectBinding<BigDecimal> price = new ObjectBinding<BigDecimal>() {
                    {
                        super.bind(mTransaction.getAmountProperty(), mTransaction.getQuantityProperty(),
                                mTransaction.getCommissionProperty());
                    }

                    @Override
                    protected BigDecimal computeValue() {
                        if (mTransaction.getAmount() == null || mTransaction.getQuantity() == null
                                || mTransaction.getCommission() == null)
                            return null;

                        return mTransaction.getAmount().subtract(mTransaction.getCommission())
                                .divide(mTransaction.getQuantity(), 6, BigDecimal.ROUND_HALF_UP);
                    }
                };
                mTransaction.getPriceProperty().bind(price);
            }
        } else if (!isCashTransfer) {
            ObjectBinding<BigDecimal> amount = new ObjectBinding<BigDecimal>() {
                {
                    super.bind(mTransaction.getPriceProperty(), mTransaction.getQuantityProperty(),
                            mTransaction.getCommissionProperty());
                }

                @Override
                protected BigDecimal computeValue() {
                    if (mTransaction.getPriceProperty().get() == null
                            || mTransaction.getQuantityProperty().get() == null
                            || mTransaction.getCommissionProperty().get() == null)
                        return null;

                    return mTransaction.getQuantity().multiply(mTransaction.getPrice())
                            .add(mTransaction.getCommission().multiply(investAmountSign));
                }
            };
            mTransaction.getAmountProperty().bind(amount);
        }

        // mapCategory return negative account id or positive category id
        Account transferAccount = mMainApp.getAccountByWrappedName(mTransaction.getCategory());

        mTransaction.getCategoryProperty().unbindBidirectional(mTransferAccountComboBox.valueProperty());
        Bindings.bindBidirectional(mTransaction.getCategoryProperty(),
                mTransferAccountComboBox.valueProperty(), new AccountCategoryConverter());
        mTransferAccountComboBox.getSelectionModel().select(transferAccount);

        Security currentSecurity = mMainApp.getSecurityByName(mTransaction.getSecurityName());
        mTransaction.getSecurityNameProperty().unbindBidirectional(mSecurityComboBox.valueProperty());
        Bindings.bindBidirectional(mTransaction.getSecurityNameProperty(),
                mSecurityComboBox.valueProperty(), mSecurityComboBox.getConverter());
        mSecurityComboBox.getSelectionModel().select(currentSecurity);

        mSharesTextField.textProperty().unbindBidirectional(mTransaction.getQuantityProperty());
        mSharesTextField.textProperty().bindBidirectional(mTransaction.getQuantityProperty(),
                new BigDecimalStringConverter());

        mOldSharesTextField.textProperty().unbindBidirectional(mTransaction.getOldQuantityProperty());
        mOldSharesTextField.textProperty().bindBidirectional(mTransaction.getOldQuantityProperty(),
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
