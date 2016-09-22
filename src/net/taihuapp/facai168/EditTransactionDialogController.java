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
import java.math.RoundingMode;
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

    private class CategoryConverter extends StringConverter<Category> {
        public Category fromString(String categoryName) { return mMainApp.getCategoryByName(categoryName); }
        public String toString(Category category) { return (category == null ? null : category.getName()); }
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

    // todo maybe should get rid of this mapping business.  Using tradeAction directly
    private InvestmentTransaction mapTradeAction(Transaction.TradeAction ta) {
        // todo need to complete all cases
        switch (ta) {
            case BUY:
            case BUYX:
                return InvestmentTransaction.BUY;
            case SELL:
            case SELLX:
                return InvestmentTransaction.SELL;
            case SHTSELL:
            case SHTSELLX:
                return InvestmentTransaction.SHTSELL;
            case SHRSIN:
                return InvestmentTransaction.SHRSIN;
            case SHRSOUT:
                return InvestmentTransaction.SHRSOUT;
            case DIV:
            case DIVX:
                return InvestmentTransaction.DIV;
            case INTINC:
            case INTINCX:
                return InvestmentTransaction.INTINC;
            case CGLONG:
            case CGLONGX:
                return InvestmentTransaction.CGLONG;
            case CGMID:
            case CGMIDX:
                return InvestmentTransaction.CGMID;
            case CGSHORT:
            case CGSHORTX:
                return InvestmentTransaction.CGSHORT;
            case REINVDIV:
                return InvestmentTransaction.REINVDIV;
            case REINVINT:
                return InvestmentTransaction.REINVINT;
            case REINVLG:
                return InvestmentTransaction.REINVLG;
            case REINVMD:
                return InvestmentTransaction.REINVMD;
            case REINVSH:
                return InvestmentTransaction.REINVSH;
            case XIN:
                return InvestmentTransaction.XIN;
            case XOUT:
                return InvestmentTransaction.XOUT;
            case DEPOSIT:
//            case DEPOSITX:
                return InvestmentTransaction.DEPOSIT;
            case WITHDRAW:
//            case WITHDRWX:
                return InvestmentTransaction.WITHDRAW;
            case STKSPLIT:
                return InvestmentTransaction.STKSPLIT;
            default:
                // more work is needed to added new cases
                return null;
        }
    }

    private final ObservableList<String> mInvestmentTransactionList = FXCollections.observableArrayList(
            InvestmentTransaction.names()
            //"Buy - Shares Bought", "Sell - Shares Sold"
    );

    @FXML
    private ChoiceBox<String> mTransactionChoiceBox;
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
    private ComboBox<Category> mCategoryComboBox;
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

    private MainApp mMainApp;
    private Account mAccount;
    private Stage mDialogStage;

    private Transaction mTransaction = null;
    private List<SecurityHolding.MatchInfo> mMatchInfoList = null;  // lot match list

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
                    Transaction.TradeAction.BUY, MainApp.getWrappedAccountName(mAccount));
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

        Transaction.TradeAction ta = Transaction.TradeAction.valueOf(mTransaction.getTradeAction());
        if ((ta != Transaction.TradeAction.SELL &&
                ta != Transaction.TradeAction.SELLX &&
                ta != Transaction.TradeAction.CVTSHRT &&
                ta != Transaction.TradeAction.CVTSHRTX)) {
            // only SELL or CVTSHORT needs the MatchInfoList
            mMatchInfoList.clear();
        }

        // setup transfer Transaction if needed
        Transaction.TradeAction xferTA = null;
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
            case DEPOSIT:
            case WITHDRAW:
            case STKSPLIT:
                // no transfer, do nothing
                break;
            case BUYX:
            case CVTSHRTX:
            case XIN:
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
                xferTA = Transaction.TradeAction.XIN;
                break;
            default:
                System.err.println("enterTransaction: Trade Action " + ta + " not implemented yet.");
                return false;
        }

        int xferTID = mTransaction.getMatchID();
        String wrappedTransferAccountName = mTransaction.getCategory();
        Account xferAccount = mMainApp.getAccountByWrappedName(wrappedTransferAccountName);
        BigDecimal xferAmount = mTransaction.getAmount();

        Transaction linkedTransaction = null;
        if (xferAccount != null && xferTA != null && xferAccount.getID() != mAccount.getID()
                && xferAmount.compareTo(BigDecimal.ZERO) != 0) {
            // we need a transfer transaction
            linkedTransaction = new Transaction(xferAccount.getID(), mTransaction.getTDate(), xferTA,
                    MainApp.getWrappedAccountName(mAccount));
            linkedTransaction.setID(xferTID);
            linkedTransaction.getAmountProperty().set(xferAmount);
            linkedTransaction.setMemo(mTransaction.getMemo());
        }

        // update database for the main transaction and update account balance
        int tid = mMainApp.insertUpDateTransactionToDB(mTransaction);
        if (tid == 0) {
            // insertion/updating failed
            System.err.println("Failed insert/update transaction, ID = " + mTransaction.getID());
            return false;
        }

        mMainApp.putMatchInfoList(mMatchInfoList);
        if (linkedTransaction != null) {
            linkedTransaction.setMatchID(tid, 0);
            mTransaction.setMatchID(mMainApp.insertUpDateTransactionToDB(linkedTransaction), 0);
        } else
            mMainApp.deleteTransactionFromDB(xferTID);  // delete the orphan matching transaction

        mMainApp.updateAccountBalance(mTransaction.getAccountID());
        if (xferAccount != null)
            mMainApp.updateAccountBalance(xferAccount.getID());

        return true;
    }

    // return true if the transaction is validated, false otherwise
    private boolean validateTransaction() {
        Security security = mSecurityComboBox.getValue();
        if (security != null && security.getID() > 0)  // has a valid security
            return true;

        // empty securiy here
        // for cash related transaction, return true
        switch (Transaction.TradeAction.valueOf(mTransaction.getTradeAction())) {
            case DIV:
            case DIVX:
            case INTINC:
            case INTINCX:
            case XIN:
            case XOUT:
            case DEPOSIT:
            case WITHDRAW:
                return true;
            default:
                // return false for all other transactions without security
                showWarningDialog("Warning", "Empty Security", "Please select a valid security");
                return false;
        }
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

        mTransactionChoiceBox.getItems().setAll(mInvestmentTransactionList);

        addEventFilter(mSharesTextField);
        addEventFilter(mOldSharesTextField);
        addEventFilter(mPriceTextField);
        addEventFilter(mCommissionTextField);
        addEventFilter(mTotalTextField);

        mTDatePicker.valueProperty().bindBidirectional(mTransaction.getTDateProperty());

        mADatePicker.valueProperty().bindBidirectional(mTransaction.getADateProperty());
        mAccountNameTextField.setText(mAccount.getName());

        mMemoTextField.textProperty().unbindBidirectional(mTransaction.getMemoProperty());
        mMemoTextField.textProperty().bindBidirectional(mTransaction.getMemoProperty());

        mPayeeTextField.textProperty().unbindBidirectional(mTransaction.getPayeeProperty());
        mPayeeTextField.textProperty().bindBidirectional(mTransaction.getPayeeProperty());

        mTransactionChoiceBox.getSelectionModel().selectedItemProperty()
                .addListener((observable1, oldValue, newValue)
                        -> setupInvestmentTransactionDialog(InvestmentTransaction.fromString(newValue)));

        InvestmentTransaction tc = mapTradeAction(Transaction.TradeAction.valueOf(mTransaction.getTradeAction()));
        // todo:  tc == null???
        // select Investment or Cash according to mTransaction
        mTransactionChoiceBox.getSelectionModel().select(tc.toString());
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

    private void setupInvestmentTransactionDialog(InvestmentTransaction investType) {
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
        for (Account account : mMainApp.getAccountList(null, null)) {
            if (account.getID() != mAccount.getID() || !isCashTransfer)
                mTransferAccountComboBox.getItems().add(account);
        }
        mTransferAccountComboBox.getSelectionModel().select(isCashTransfer ? new Account() : mAccount);

        mCategoryComboBox.setConverter(new CategoryConverter());
        mCategoryComboBox.getItems().clear();
        mCategoryComboBox.getItems().add(new Category());
        for (Category c : mMainApp.getCategoryList())
            mCategoryComboBox.getItems().add(c);

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
                                || mTransaction.getQuantity().signum() == 0 || mTransaction.getCommission() == null)
                            return null;

                        return mTransaction.getAmount().subtract(mTransaction.getCommission())
                                .divide(mTransaction.getQuantity(), 6, RoundingMode.HALF_UP);
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

        mTransaction.getCategoryProperty().unbindBidirectional(mTransferAccountComboBox.valueProperty());
        mTransaction.getCategoryProperty().unbindBidirectional(mCategoryComboBox.valueProperty());
        if (mTransaction.getTradeAction().equals(Transaction.TradeAction.DEPOSIT.name())
                || mTransaction.getTradeAction().equals(Transaction.TradeAction.DEPOSIT.name())) {
            Bindings.bindBidirectional(mTransaction.getCategoryProperty(),
                    mCategoryComboBox.valueProperty(), new CategoryConverter());
        } else {
            Account transferAccount = mMainApp.getAccountByWrappedName(mTransaction.getCategory());
            Bindings.bindBidirectional(mTransaction.getCategoryProperty(),
                    mTransferAccountComboBox.valueProperty(), new AccountCategoryConverter());
            mTransferAccountComboBox.getSelectionModel().select(transferAccount);
        }

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
