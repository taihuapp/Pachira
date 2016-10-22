package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static net.taihuapp.facai168.Transaction.TradeAction.*;

/**
 * Created by ghe on 7/10/15.
 * Controller for EditTransactionDialog
 */
public class EditTransactionDialogController {

    private class CategoryIDConverter extends StringConverter<Integer> {
        public Integer fromString(String categoryName) {
            Category c = mMainApp.getCategoryByName(categoryName);
            return c == null ? 0 : c.getID();
        }
        public String toString(Integer cid) {
            Category c = mMainApp.getCategoryByID(cid);
            return c == null ? "" : c.getName();
        }
    }

    private class AccountIDConverter extends StringConverter<Integer> {
        public Integer fromString(String accountName) {
            Account a = mMainApp.getAccountByName(accountName);
            return a == null ? 0 : -a.getID();
        }
        public String toString(Integer negAID) {
            Account a = mMainApp.getAccountByID(-negAID);
            return a == null ? "" : a.getName();
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

    @FXML
    private ChoiceBox<Transaction.TradeAction> mTransactionChoiceBox;
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
    private ComboBox<Integer> mTransferAccountComboBox;
    @FXML
    private Label mCategoryLabel;
    @FXML
    private ComboBox<Integer> mCategoryComboBox;
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
    private int mOldXferAccountID;
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
            mTransaction = new Transaction(mAccount.getID(), LocalDate.now(), BUY, 0);
        } else {
            mTransaction = transaction;
        }
        mMatchInfoList = mMainApp.getMatchInfoList(mTransaction.getID());

        mOldXferAccountID = -mTransaction.getCategoryID();

        setupTransactionDialog();
    }

    // return true if enter worked
    // false if something is not quite right
    private boolean enterTransaction() {
        if (!validateTransaction())
            return false;  // invalid transaction

        Transaction.TradeAction ta = mTransaction.getTradeAction();
        if ((ta != SELL && ta != Transaction.TradeAction.CVTSHRT)) {
            // only SELL or CVTSHRT needs the MatchInfoList
            mMatchInfoList.clear();
        }

        // transfer transaction id
        int xferTID = mTransaction.getMatchID();
        int xferAID = -mTransaction.getCategoryID();
        BigDecimal xferAmount = mTransaction.getAmount();

        // setup transfer Transaction if needed
        Transaction.TradeAction xferTA = null;
        switch (ta) {
            case BUY:
            case BUYBOND:
            case SHRSIN:
            case CGLONG:
            case CGMID:
            case CGSHORT:
            case CVTSHRT:
            case REINVDIV:
            case REINVINT:
            case REINVLG:
            case REINVMD:
            case REINVSH:
            case DEPOSIT:
            case WITHDRAW:
            case STKSPLIT:
            case XIN:
            case MARGINT:
                if (xferAID >= MainApp.MIN_ACCOUNT_ID || ta == Transaction.TradeAction.XIN)
                    xferTA = Transaction.TradeAction.XOUT;

                // no transfer, do nothing
                break;
            case DIV:
            case INTINC:
            case SELL:
            case SHTSELL:
            case XOUT:
                if (xferAID >= MainApp.MIN_ACCOUNT_ID || ta == Transaction.TradeAction.XOUT)
                    xferTA = Transaction.TradeAction.XIN;
                break;
            case SHRSOUT:
                // nothing to transfer
                break;
            default:
                System.err.println("enterTransaction: Trade Action " + ta + " not implemented yet.");
                return false;
        }

        Transaction linkedTransaction = null;
        if (xferTA != null && xferAID != mAccount.getID() && xferAmount.signum() != 0) {
            // we need a transfer transaction
            linkedTransaction = new Transaction(xferAID, mTransaction.getTDate(), xferTA,
                    -mAccount.getID());
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
            mMainApp.insertUpDateTransactionToDB(mTransaction);
        } else
            mMainApp.deleteTransactionFromDB(xferTID);  // delete the orphan matching transaction

        mMainApp.updateAccountBalance(mTransaction.getAccountID());

        if (xferAID > MainApp.MIN_ACCOUNT_ID)
            mMainApp.updateAccountBalance(xferAID);

        if (mOldXferAccountID >= MainApp.MIN_ACCOUNT_ID) {
            mMainApp.updateAccountBalance(mOldXferAccountID);
            mOldXferAccountID = 0;
        }

        if ((mTransaction.getTradeAction() == BUY || mTransaction.getTradeAction() == SELL
                || mTransaction.getTradeAction() == REINVDIV || mTransaction.getTradeAction() == REINVINT
                || mTransaction.getTradeAction() == REINVLG || mTransaction.getTradeAction() == REINVMD
                || mTransaction.getTradeAction() == REINVSH || mTransaction.getTradeAction() == SHTSELL
                || mTransaction.getTradeAction() == CVTSHRT || mTransaction.getTradeAction() == BUYBOND)
            && (mTransaction.getPrice().compareTo(BigDecimal.ZERO) != 0)) {
            Security security = mMainApp.getSecurityByName(mTransaction.getSecurityName());
            if (security != null) {
                int securityID = security.getID();
                LocalDate date = mTransaction.getTDate();
                // update price table
                mMainApp.insertUpdatePriceToDB(securityID, date, mTransaction.getPrice(), 0);
            }
        }
        return true;
    }

    // return true if the transaction is validated, false otherwise
    private boolean validateTransaction() {
        Security security = mSecurityComboBox.getValue();
        if (security != null && security.getID() > 0)  // has a valid security
            return true;

        // empty security here
        // for cash related transaction, return true
        switch (mTransaction.getTradeAction()) {
            case DIV:
            case INTINC:
            case XIN:
            case XOUT:
            case DEPOSIT:
            case WITHDRAW:
            case MARGINT:
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
        mIncomeTextField.setText("0.00");
        mSharesTextField.setText("0.00");
        mCommissionTextField.setText("0.00");
    }

    @FXML
    private void handleEnterDone() {
        if (enterTransaction())
            mDialogStage.close();
    }

    @FXML
    private void handleEnterNew() {
        if (enterTransaction()) {
            mTransaction.setID(0); // a new transaction
            handleClear();
        }
    }

    private void setupTransactionDialog() {

        mSecurityComboBox.setConverter(new SecurityConverter());
        mSecurityComboBox.getItems().add(new Security());  // add a Blank Security
        mSecurityComboBox.getItems().addAll(mMainApp.getSecurityList());

        mTransactionChoiceBox.getItems().setAll(Transaction.TradeAction.values());

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
                        -> setupInvestmentTransactionDialog(newValue));

        mTransactionChoiceBox.getSelectionModel().select(mTransaction.getTradeAction());
        mTransactionChoiceBox.valueProperty().unbindBidirectional(mTransaction.getTradeActionProperty());
        mTransactionChoiceBox.valueProperty().bindBidirectional(mTransaction.getTradeActionProperty());
    }

    private void setupInvestmentTransactionDialog(Transaction.TradeAction tradeAction) {
        final BigDecimal investAmountSign;
        boolean isIncome = false;
        boolean isReinvest = false;
        boolean isCashTransfer = false;
        switch (tradeAction) {
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
                mTransferAccountLabel.setText(tradeAction == XOUT ? "Transfer Cash To:" : "Transfer Cash From:");
                mTotalLabel.setVisible(true);
                mTotalTextField.setVisible(true);
                mTotalLabel.setText(tradeAction == MARGINT ? "Amount" : "Transfer Amount:");
                mTotalTextField.setEditable(true);
                investAmountSign = BigDecimal.ONE;
                break;
            case BUY:
            case CVTSHRT:
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
                mPriceTextField.setEditable(true);
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
            case SHRSOUT:
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
                mPriceLabel.setVisible(tradeAction != SHRSOUT);
                mPriceTextField.setVisible(tradeAction != SHRSOUT);
                mPriceTextField.setEditable(true);
                mCommissionLabel.setVisible(tradeAction != SHRSOUT);
                mCommissionTextField.setVisible(tradeAction != SHRSOUT);
                mSpecifyLotButton.setVisible(tradeAction == SELL || tradeAction == SHRSOUT);
                mTransferAccountLabel.setVisible(tradeAction != SHRSOUT);
                mTransferAccountComboBox.setVisible(tradeAction != SHRSOUT);
                mADatePickerLabel.setVisible(false);
                mADatePicker.setVisible(false);
                mIncomeLabel.setVisible(false);
                mIncomeTextField.setVisible(false);
                mTransferAccountLabel.setText("Put Cash Into:");
                mTotalLabel.setVisible(tradeAction != SHRSOUT);
                mTotalTextField.setVisible(tradeAction != SHRSOUT);
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
                mPriceTextField.setEditable(true);
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
            case CGLONG:
            case MARGINT:
            case MISCINC:
            case MISCEXP:
            case RTRNCAP:
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
                mTransferAccountLabel.setText("Put Cash Into:");
                mTransferAccountComboBox.setVisible(!isReinvest);
                mADatePickerLabel.setVisible(false);
                mADatePicker.setVisible(false);
                mIncomeLabel.setVisible(true);
                mIncomeLabel.setText(tradeAction.name());
                mIncomeTextField.setVisible(true);
                mTotalLabel.setVisible(true);
                mTotalTextField.setVisible(true);
                mTotalLabel.setText("Amount");
                mTotalTextField.setEditable(false);
                investAmountSign = BigDecimal.ONE;
                break;
            case XFRSHRS:
            case BUYBOND:
            default:
                System.err.println("TradeAction " + tradeAction + " not implemented yet.");
                return;
        }

        mTransferAccountComboBox.setConverter(new AccountIDConverter());
        mTransferAccountComboBox.getItems().clear();
        mTransferAccountComboBox.getItems().add(0); // a blank account
        System.out.println("setupInvestmentTransactionDialog");
        for (Account account : mMainApp.getAccountList(null, null, true)) {
            if (account.getID() != mAccount.getID() || !isCashTransfer)
                mTransferAccountComboBox.getItems().add(-account.getID());
        }
        mTransferAccountComboBox.getSelectionModel().select(0);

        mCategoryComboBox.setConverter(new CategoryIDConverter());
        mCategoryComboBox.getItems().clear();
        mCategoryComboBox.getItems().add(0);
        for (Category c : mMainApp.getCategoryList())
            mCategoryComboBox.getItems().add(c.getID());

        // make sure it is not bind
        mTransaction.getAmountProperty().unbind();
        mTransaction.getPriceProperty().unbind();
        mTransaction.getCommissionProperty().unbind();
        mTransaction.getQuantityProperty().unbind();

        mTotalTextField.textProperty().unbindBidirectional(mTransaction.getAmountProperty());
        mIncomeTextField.textProperty().unbindBidirectional(mTransaction.getAmountProperty());
        mSharesTextField.textProperty().unbindBidirectional(mTransaction.getQuantityProperty());
        mOldSharesTextField.textProperty().unbindBidirectional(mTransaction.getOldQuantityProperty());
        mPriceTextField.textProperty().unbindBidirectional(mTransaction.getPriceProperty());
        mCommissionTextField.textProperty().unbindBidirectional(mTransaction.getCommissionProperty());

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

        Bindings.unbindBidirectional(mTransferAccountComboBox.valueProperty(), mTransaction.getCategoryIDProperty());
        Bindings.unbindBidirectional(mCategoryComboBox.valueProperty(), mTransaction.getCategoryIDProperty());
        if (mTransaction.getTradeAction() == Transaction.TradeAction.DEPOSIT
                || mTransaction.getTradeAction() == Transaction.TradeAction.DEPOSIT) {
            Bindings.bindBidirectional(mCategoryComboBox.valueProperty(),
                    mTransaction.getCategoryIDProperty().asObject());
        } else {
            Bindings.bindBidirectional(mTransferAccountComboBox.valueProperty(),
                    mTransaction.getCategoryIDProperty().asObject());
        }

        Security currentSecurity = mMainApp.getSecurityByName(mTransaction.getSecurityName());
        mTransaction.getSecurityNameProperty().unbindBidirectional(mSecurityComboBox.valueProperty());
        Bindings.bindBidirectional(mTransaction.getSecurityNameProperty(),
                mSecurityComboBox.valueProperty(), mSecurityComboBox.getConverter());
        mSecurityComboBox.getSelectionModel().select(currentSecurity);

        mSharesTextField.textProperty().bindBidirectional(mTransaction.getQuantityProperty(),
                new BigDecimalStringConverter());

        mOldSharesTextField.textProperty().bindBidirectional(mTransaction.getOldQuantityProperty(),
                new BigDecimalStringConverter());

        mPriceTextField.textProperty().bindBidirectional(mTransaction.getPriceProperty(),
                new BigDecimalStringConverter());

        mCommissionTextField.textProperty().bindBidirectional(mTransaction.getCommissionProperty(),
                new BigDecimalStringConverter());

        mTotalTextField.textProperty().bindBidirectional(mTransaction.getAmountProperty(),
                new BigDecimalStringConverter());
    }
}