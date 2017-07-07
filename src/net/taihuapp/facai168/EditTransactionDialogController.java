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
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.*;

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
            Category c = (cid == null) ? null : mMainApp.getCategoryByID(cid);
            return c == null ? "" : c.getName();
        }
    }

    private class MyBigDecimalStringConverter extends BigDecimalStringConverter {
        public String toString(BigDecimal b) {
            return (b == null) ? "" : MainApp.DOLLAR_CENT_FORMAT.format(b);
        }
        public BigDecimal fromString(String s) {
            BigDecimal b = BigDecimal.ZERO;
            if (s == null)
                return b;

            try {
                b = (BigDecimal) MainApp.DOLLAR_CENT_FORMAT.parse(s);
            } catch (ParseException e) {
                // do nothing, return BigDecimal.ZERO
            }
            return b;
        }
    }

    private class AccountIDConverter extends StringConverter<Integer> {
        public Integer fromString(String accountName) {
            Account a = mMainApp.getAccountByName(accountName);
            return a == null ? 0 : -a.getID();
        }
        public String toString(Integer negAID) {
            Account a = (negAID == null) ? null : mMainApp.getAccountByID(-negAID);
            return a == null ? "" : a.getName();
        }
    }

    private class AccountConverter extends StringConverter<Account> {
        public Account fromString(String accountName) {
            return mMainApp.getAccountByName(accountName);
        }
        public String toString(Account a) {
            return a.getName();
        }
    }

    private class SecurityConverter extends StringConverter<Security> {
        public Security fromString(String s) {
            return mMainApp.getSecurityByName(s);
        }
        public String toString(Security security) { return security == null ? "" : security.getName(); }
    }

    @FXML
    private ChoiceBox<Transaction.TradeAction> mTradeActionChoiceBox;
    @FXML
    private DatePicker mTDatePicker;
    @FXML
    private Label mADatePickerLabel;
    @FXML
    private DatePicker mADatePicker;
    @FXML
    private ComboBox<Account> mAccountComboBox;
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
    private Label mReferenceLabel;
    @FXML
    private TextField mReferenceTextField;
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
    private Button mEnterNewButton;
    @FXML
    private Button mEnterDoneButton;
    @FXML
    private Button mSpecifyLotButton;
    @FXML
    private Button mSplitTransactionButton;

    private MainApp mMainApp;
    private Stage mDialogStage;
    private Transaction mTransactionOrig = null;  // original copy of transaction
    private Transaction mTransaction = null; // working copy of transaction
    private boolean mSplitTransactionListChanged = false;
    private List<SecurityHolding.MatchInfo> mMatchInfoList = null;  // lot match list

    // used for mSharesTextField, mPriceTextField, mCommissionTextField, mOldSharesTextField
    private void addEventFilter(TextField tf) {
        // add an event filter so only numerical values are permitted
        tf.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!"0123456789.".contains(event.getCharacter()))
                event.consume();
        });
    }

    int getTransactionID() {
        if (mTransaction == null)
            return -1;
        return mTransaction.getID();
    }

    // transaction can either be null, or an existing transaction
    void setMainApp(MainApp mainApp, Transaction transaction, Stage stage,
                    List<Account> accountList, Account defaultAccount,
                    List<Transaction.TradeAction> taList) {
        mDialogStage = stage;
        mMainApp = mainApp;

        // when being forced to close, clear out mTransaction
        mDialogStage.setOnCloseRequest(eh -> mTransaction = null);

        mAccountComboBox.setConverter(new AccountConverter());
        mAccountComboBox.getItems().setAll(accountList);
        mAccountComboBox.getSelectionModel().select(defaultAccount);

        if (accountList.size() > 1) {
            mEnterDoneButton.setDefaultButton(true);
            mEnterNewButton.setDefaultButton(false);
            mEnterNewButton.setVisible(false);
        } else {
            mEnterDoneButton.setDefaultButton(false);
            mEnterNewButton.setDefaultButton(true);
            mEnterNewButton.setVisible(true);
        }

        mTradeActionChoiceBox.getItems().setAll(taList);

        if (transaction == null) {
            Account account = mAccountComboBox.getSelectionModel().getSelectedItem();
            mTransaction = new Transaction(account.getID(), LocalDate.now(),
                    account.getType() == Account.Type.INVESTING ? BUY : WITHDRAW, 0);
        } else {
            mTransaction = new Transaction(transaction);
            if (transaction.getID() > 0)
                mTransactionOrig = transaction;
        }
        mMatchInfoList = mMainApp.getMatchInfoList(mTransaction.getID());

        setupTransactionDialog();
    }

    // return true if enter worked
    // false if something is not quite right
    private boolean enterTransaction() {
        int accountID = mAccountComboBox.getSelectionModel().getSelectedItem().getID();
        mTransaction.setAccountID(accountID);
        if (!validateTransaction())
            return false;  // invalid transaction

        Transaction.TradeAction ta = mTransaction.getTradeAction();
        if ((ta != SELL && ta != Transaction.TradeAction.CVTSHRT)) {
            // only SELL or CVTSHRT needs the MatchInfoList
            mMatchInfoList.clear();
        }
        if (!Transaction.hasQuantity(ta))
            mTransaction.setQuantity(null);

        // setup transfer Transaction if needed
        Transaction.TradeAction xferTA = null;
        switch (ta) {
            case BUY:
            case SHRSIN:
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
                if (-mTransaction.getCategoryID() >= MainApp.MIN_ACCOUNT_ID || ta == Transaction.TradeAction.XIN)
                    xferTA = Transaction.TradeAction.XOUT;

                // no transfer, do nothing
                break;
            case DIV:
            case CGLONG:
            case CGMID:
            case CGSHORT:
            case INTINC:
            case SELL:
            case SHTSELL:
            case XOUT:
                if (-mTransaction.getCategoryID() >= MainApp.MIN_ACCOUNT_ID || ta == Transaction.TradeAction.XOUT)
                    xferTA = Transaction.TradeAction.XIN;
                break;
            case SHRSOUT:
                // nothing to transfer
                break;
            default:
                System.err.println("enterTransaction: Trade Action " + ta + " not implemented yet.");
                return false;
        }

        String payee;
        switch (ta) {
            case DEPOSIT:
            case WITHDRAW:
            case XIN:
            case XOUT:
                payee = mTransaction.getPayee();
                break;
            default:
                payee = mTransaction.getSecurityName();
                if (payee == null || payee.length() == 0)
                    payee = mTransaction.getPayee();
                break;
        }

        // we don't want to insert mTransaction into master transaction list in memory
        // make a copy of mTransaction
        Transaction dbCopyT = new Transaction(mTransaction);

        // this is a list of transactions to be updated in master list.
        List<Transaction> updateTList = new ArrayList<>();
        updateTList.add(dbCopyT);

        // this is a list of transaction id to be deleted in master list
        List<Integer> deleteList = new ArrayList<>();

        // this is the set of account ids which involved in this and all linked transactions
        Set<Integer> accountSet = new HashSet<>();
        accountSet.add(dbCopyT.getAccountID());

        Transaction linkedT = null;
        if (xferTA != null && -mTransaction.getCategoryID() != accountID && mTransaction.getAmount().signum() != 0) {
            // we need a transfer transaction
            linkedT = new Transaction(-mTransaction.getCategoryID(), dbCopyT.getTDate(), xferTA, -accountID);
            linkedT.setID(mTransaction.getMatchID());
            linkedT.getAmountProperty().set(mTransaction.getAmount());
            linkedT.setMemo(dbCopyT.getMemo());
            linkedT.setPayee(payee);

            updateTList.add(linkedT);
            accountSet.add(linkedT.getAccountID());
        }


        // new code here
        try {
            if (!mMainApp.setDBSavepoint()) {
                showWarningDialog("Unexpected situation", "Database savepoint already set?",
                        "Please restart application");
                return false;
            }
            mTransaction.setID(mMainApp.insertUpdateTransactionToDB(dbCopyT));

            // for new transactions, tid in elements of mMatchInfoList is -1, need to update
            for (SecurityHolding.MatchInfo mi : mMatchInfoList) {
                mi.setTransactionID(dbCopyT.getID());
            }
            mMainApp.putMatchInfoList(dbCopyT.getID(), mMatchInfoList);

            // handle linked transactions here
            if (linkedT != null) {
                linkedT.setMatchID(dbCopyT.getID(), 0);
                mMainApp.insertUpdateTransactionToDB(linkedT);
                dbCopyT.setMatchID(linkedT.getID(), 0);
                accountSet.add(linkedT.getAccountID());
            } else {
                dbCopyT.setMatchID(-1, -1);
            }

            for (SplitTransaction st : dbCopyT.getSplitTransactionList()) {
                if (st.getCategoryID() <= -MainApp.MIN_ACCOUNT_ID && st.getAmount().compareTo(BigDecimal.ZERO) != 0) {
                    // this is a transfer
                    Transaction stLinkedT = new Transaction(-st.getCategoryID(), dbCopyT.getTDate(),
                            st.getAmount().compareTo(BigDecimal.ZERO) > 0 ? XOUT : XIN, -dbCopyT.getAccountID());
                    stLinkedT.setID(st.getMatchID());
                    stLinkedT.setAmount(st.getAmount().abs());
                    stLinkedT.setPayee(st.getPayee());
                    stLinkedT.setMemo(st.getMemo());
                    stLinkedT.setPayee(dbCopyT.getPayee());
                    stLinkedT.setMatchID(dbCopyT.getID(), st.getID());

                    mMainApp.insertUpdateTransactionToDB(stLinkedT);
                    st.setMatchID(stLinkedT.getID());

                    updateTList.add(stLinkedT);
                    accountSet.add(stLinkedT.getAccountID());
                } else {
                    // not linked to any other transaction
                    st.setMatchID(0);
                }
            }

            // update MatchID info.
            mMainApp.insertUpdateTransactionToDB(dbCopyT);

            if (mTransactionOrig != null) {
                for (SplitTransaction st : mTransactionOrig.getSplitTransactionList()) {
                    if (st.getMatchID() > 0) {
                        // matchID was in
                        boolean delete = true;
                        for (Transaction t : updateTList) {
                            if (t.getID() == st.getMatchID())
                                delete = false;
                        }
                        Transaction t = mMainApp.getTransactionByID(st.getMatchID());
                        // the transaction might changed, let's update account balance.
                        accountSet.add(t.getAccountID());
                        if (delete) {
                            deleteList.add(st.getMatchID());
                            mMainApp.deleteTransactionFromDB(st.getMatchID());
                        }
                    }
                }

                if (mTransactionOrig.getCategoryID() <= -MainApp.MIN_ACCOUNT_ID)
                    accountSet.add(-mTransactionOrig.getCategoryID());

                if (mTransactionOrig.getMatchID() > 0 && mTransactionOrig.getMatchID() != dbCopyT.getMatchID()) {
                    Transaction t = mMainApp.getTransactionByID(mTransactionOrig.getMatchID());
                    if (t != null) {
                        accountSet.add(t.getAccountID());
                        deleteList.add(mTransactionOrig.getMatchID());
                        mMainApp.deleteTransactionFromDB(mTransactionOrig.getMatchID());
                    }
                }
            }

            mMainApp.commitDB();
            // end database work here

            // update master list here
            for (Integer deleteID : deleteList)
                mMainApp.deleteTransactionFromMasterList(deleteID);
            for (Transaction t : updateTList)
                mMainApp.insertUpdateTransactionToMasterList(t);
        } catch (SQLException e) {
            try {
                mMainApp.rollbackDB();
            } catch (SQLException e1) {
                mMainApp.showExceptionDialog("Database Error", "Unable to rollback to savepoint",
                        MainApp.SQLExceptionToString(e1), e1);
            }
        } finally {
            try {
                mMainApp.releaseDBSavepoint();
            } catch (SQLException e) {
                mMainApp.showExceptionDialog("Database Error",
                        "Unable to release savepoint and set DB autocommit",
                        MainApp.SQLExceptionToString(e), e);
            }
        }

        // new code end here

        // update price first
        Security security = mMainApp.getSecurityByName(dbCopyT.getSecurityName());
        if (Transaction.hasQuantity(dbCopyT.getTradeAction())
                && (security != null) && (dbCopyT.getPrice() != null)
                && (dbCopyT.getPrice().compareTo(BigDecimal.ZERO) != 0)) {
            int securityID = security.getID();
            LocalDate date = dbCopyT.getTDate();
            // update price table
            mMainApp.insertUpdatePriceToDB(securityID, date, dbCopyT.getPrice(), 0);
            // price changed for this security, let's update balances for all account has this security
            mMainApp.updateAccountBalance(security);
        }

        // This security might be new in this account, which means this account balance was
        // not updated in updateAccountBalance(security), let's make sure it is updated now
        for (Integer aid : accountSet) {
            mMainApp.updateAccountBalance(aid);
        }

        return true;
    }

    // return true if the transaction is validated, false otherwise
    private boolean validateTransaction() {
        // sometimes the selected cid/tid is not corrected reflected in mTransaction
        // make sure it is correct.
        Integer cid = mCategoryComboBox.getSelectionModel().getSelectedItem();
        Integer tid = mTransferAccountComboBox.getSelectionModel().getSelectedItem();
        if (cid == null && tid == null) {
            showWarningDialog("Warning", "Category ID is null?", "Is that right?");
            return false;
        } else if ((cid == null && !tid.equals(mTransaction.getCategoryID()))
                || (tid == null && !cid.equals(mTransaction.getCategoryID()))) {
            showWarningDialog("Warning", "GUI problem",
                    "CategoryID/Transfer AccountID not match selection");
            return false;
        }

        Security security = mSecurityComboBox.getValue();
        if (security != null && security.getID() <= 0) {
            //  invalid security
            showWarningDialog("Warning", "Invalid Security", "Please select a valid security.");
            return false;
        }

        if (!mTransaction.getSplitTransactionList().isEmpty()) {
            BigDecimal netAmount = mTransaction.getPayment().subtract(mTransaction.getDeposit());
            for (SplitTransaction st : mTransaction.getSplitTransactionList()) {
                netAmount = netAmount.add(st.getAmount());
            }
            if (netAmount.compareTo(BigDecimal.ZERO) != 0) {
                showWarningDialog("Warning", "Split transaction amount net matching with total amount.",
                        "Please check split transactions and total amount.");
                return false;
            }
        }

        int accountID = mTransaction.getAccountID();

        // check self transfer in splittransaction
        for (SplitTransaction st : mTransaction.getSplitTransactionList()) {
            if (st.getCategoryID() == -accountID) {
                showWarningDialog("Warning", "Self Transfer in Split Transactions",
                        "Please make sure account is different from transfer account.");
                return false;
            }
        }

        Transaction.TradeAction ta = mTransaction.getTradeAction();
        // check transfer account
        if (ta == XIN || ta == XOUT) {
            if (accountID == -mTransaction.getCategoryID()) {
                showWarningDialog("Warning", "Self Transfer",
                        "Please make sure account is different from transfer account.");
                return false;
            }

            if (mTransaction.getCategoryID() > -MainApp.MIN_ACCOUNT_ID) {
                showWarningDialog("Warning", "Invalid Transfer Account",
                        "Please select a valid account to transfer.");
                return false;
            }
        } else if ((ta == SELL || ta == CVTSHRT) && !mMatchInfoList.isEmpty()) {
            BigDecimal matchQ = BigDecimal.ZERO;
            for (SecurityHolding.MatchInfo mi : mMatchInfoList) {
                matchQ = matchQ.add(mi.getMatchQuantity());
            }
            if (matchQ.compareTo(mTransaction.getQuantity()) != 0) {
                showWarningDialog("Warning", "Mismatching Specify Lot Quantity",
                        "Please check specify lots.");
                return false;
            }
        }


        // empty security here
        // for cash related transaction, return true
        if (security == null) {
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
        mMainApp.showSpecifyLotsDialog(mDialogStage, mTransaction, mMatchInfoList);
    }

    @FXML
    private void handleSplitTransactions() {
        List<SplitTransaction> outputSplitTransactionList = mMainApp.showSplitTransactionsDialog(mDialogStage,
                mTransaction.getSplitTransactionList(),
                mTransaction.getPayment().subtract(mTransaction.getDeposit()));

        if (outputSplitTransactionList != null) {
            // splittransactionlist changed
            mSplitTransactionListChanged = true;
            mTransaction.setSplitTransactionList(outputSplitTransactionList);
        }
    }

    @FXML
    private void handleCancel() {
        if (mSplitTransactionListChanged) {
            // ask if user want to save changed splittransaction
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Split Transactions were changed.");
            alert.setHeaderText("Are you sure to discard the change?");
            alert.setContentText("OK to discard change, Cancel to go back.");
            Optional<ButtonType> result = alert.showAndWait();
            if (!(result.isPresent() && result.get() == ButtonType.OK))
                return; // do nothing
        }
        mTransaction = null;  // cancelled, clear out mTransaction
        mDialogStage.close();
    }

    @FXML
    private void handleClear() {
        mPayeeTextField.setText("");
        mReferenceTextField.setText("");
        mMemoTextField.setText("");
        mIncomeTextField.setText("0.00");
        mSharesTextField.setText("0.00");
        mCommissionTextField.setText("0.00");
        mTotalTextField.setText("0.00");
    }

    @FXML
    private void handleEnterDone() {
        if (enterTransaction())
            mDialogStage.close();
    }

    @FXML
    private void handleEnterNew() {
        if (enterTransaction()) {
            // new transaction, set transaction id, matchID, matchSplitID
            mTransaction.setID(0);
            mTransaction.setMatchID(-1, -1);
            mTransactionOrig = null;
            handleClear();
        }
    }

    private void setupTransactionDialog() {

        mSecurityComboBox.setConverter(new SecurityConverter());
        mSecurityComboBox.getItems().clear();
        mSecurityComboBox.getItems().add(new Security());  // add a Blank Security

        // add account current security list in the front, the list is sorted by Name
        TreeSet<Security> accountSecuritySet = new TreeSet<>(Comparator.comparing(Security::getName));
        accountSecuritySet.addAll(mAccountComboBox.getSelectionModel().getSelectedItem().getCurrentSecurityList());
        TreeSet<Security> allSecuritySet = new TreeSet<>(Comparator.comparing(Security::getName));
        allSecuritySet.addAll(mMainApp.getSecurityList());
        allSecuritySet.removeAll(accountSecuritySet);
        mSecurityComboBox.getItems().addAll(accountSecuritySet);
        mSecurityComboBox.getItems().addAll(allSecuritySet);
        new AutoCompleteComboBoxHelper<>(mSecurityComboBox);

        addEventFilter(mSharesTextField);
        addEventFilter(mOldSharesTextField);
        addEventFilter(mPriceTextField);
        addEventFilter(mCommissionTextField);
        addEventFilter(mTotalTextField);

        mTDatePicker.valueProperty().bindBidirectional(mTransaction.getTDateProperty());

        mADatePicker.valueProperty().bindBidirectional(mTransaction.getADateProperty());

        mMemoTextField.textProperty().unbindBidirectional(mTransaction.getMemoProperty());
        mMemoTextField.textProperty().bindBidirectional(mTransaction.getMemoProperty());

        mReferenceTextField.textProperty().unbindBidirectional(mTransaction.getReferenceProperty());
        mReferenceTextField.textProperty().bindBidirectional(mTransaction.getReferenceProperty());

        mPayeeTextField.textProperty().unbindBidirectional(mTransaction.getPayeeProperty());
        mPayeeTextField.textProperty().bindBidirectional(mTransaction.getPayeeProperty());
        new AutoCompleteTextFieldHelper(mPayeeTextField, mMainApp.getPayeeSet());

        // populate Category ComboBox
        mCategoryComboBox.setConverter(new CategoryIDConverter());
        mCategoryComboBox.getItems().clear(); // just to be safe
        mCategoryComboBox.getItems().add(0); // add an blank category
        for (Category c : mMainApp.getCategoryList())
            mCategoryComboBox.getItems().add(c.getID());
        new AutoCompleteComboBoxHelper<>(mCategoryComboBox);

        // populate TransferAccount Combobox
        mTransferAccountComboBox.setConverter(new AccountIDConverter());
        mTransferAccountComboBox.getItems().clear();
        mTransferAccountComboBox.getItems().add(0); // a blank account
        for (Account account : mMainApp.getAccountList(null, false, true)) {
            // get all types, non-hidden accounts, exclude deleted_account
            if (account.getID() != mAccountComboBox.getSelectionModel().getSelectedItem().getID())
                mTransferAccountComboBox.getItems().add(-account.getID());
        }

        mTradeActionChoiceBox.getSelectionModel().selectedItemProperty()
                .addListener((ob, o, n) -> { if (n != null) setupInvestmentTransactionDialog(n); });

        mTradeActionChoiceBox.getSelectionModel().select(mTransaction.getTradeAction());
        mTradeActionChoiceBox.valueProperty().unbindBidirectional(mTransaction.getTradeActionProperty());
        mTradeActionChoiceBox.valueProperty().bindBidirectional(mTransaction.getTradeActionProperty());
    }

    private void setupInvestmentTransactionDialog(Transaction.TradeAction tradeAction) {
        boolean isIncome = false;
        boolean isReinvest = false;
        boolean isCashTransfer = false;
        switch (tradeAction) {
            case STKSPLIT:
                isCashTransfer = false;
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mReferenceLabel.setVisible(false);
                mReferenceTextField.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
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
                break;
            case DEPOSIT:
            case WITHDRAW:
                isCashTransfer = true;
                mCategoryLabel.setVisible(true);
                mCategoryComboBox.setVisible(true);
                mReferenceLabel.setVisible(true);
                mReferenceTextField.setVisible(true);
                mPayeeLabel.setVisible(true);
                mPayeeTextField.setVisible(true);
                mSecurityNameLabel.setVisible(false);
                mSecurityComboBox.setVisible(false);
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
                break;
            case XIN:
            case XOUT:
                isCashTransfer = true;
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mReferenceLabel.setVisible(true);
                mReferenceTextField.setVisible(true);
                mPayeeLabel.setVisible(true);
                mPayeeTextField.setVisible(true);
                mSecurityNameLabel.setVisible(false);
                mSecurityComboBox.setVisible(false);
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
                break;
            case BUY:
            case CVTSHRT:
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mReferenceLabel.setVisible(false);
                mReferenceTextField.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
                mPriceLabel.setVisible(true);
                mPriceTextField.setVisible(true);
                mPriceTextField.setEditable(false);
                mCommissionLabel.setVisible(true);
                mCommissionTextField.setVisible(true);
                mSpecifyLotButton.setVisible(tradeAction == CVTSHRT);
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
                mTotalTextField.setEditable(true);
                break;
            case SELL:
            case SHTSELL:
            case SHRSOUT:
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mReferenceLabel.setVisible(false);
                mReferenceTextField.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
                mPriceLabel.setVisible(tradeAction != SHRSOUT);
                mPriceTextField.setVisible(tradeAction != SHRSOUT);
                mPriceTextField.setEditable(false);
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
                mTotalTextField.setEditable(true);
                break;
            case SHRSIN:
                mCategoryLabel.setVisible(false);
                mCategoryComboBox.setVisible(false);
                mReferenceLabel.setVisible(false);
                mReferenceTextField.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
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
                mReferenceLabel.setVisible(false);
                mReferenceTextField.setVisible(false);
                mPayeeLabel.setVisible(false);
                mPayeeTextField.setVisible(false);
                mSecurityNameLabel.setVisible(true);
                mSecurityComboBox.setVisible(true);
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
                break;
            case XFRSHRS:
            default:
                System.err.println("TradeAction " + tradeAction + " not implemented yet.");
                return;
        }

        mSharesLabel.setVisible(Transaction.hasQuantity(tradeAction));
        mSharesLabel.setText(tradeAction.equals(STKSPLIT) ? "New Shares" : "Number of Shares");
        mSharesTextField.setVisible(Transaction.hasQuantity(tradeAction));
        mOldSharesLabel.setVisible(tradeAction.equals(STKSPLIT));
        mOldSharesTextField.setVisible(tradeAction.equals(STKSPLIT));

        Bindings.unbindBidirectional(mTransferAccountComboBox.valueProperty(), mTransaction.getCategoryIDProperty());
        Bindings.unbindBidirectional(mCategoryComboBox.valueProperty(), mTransaction.getCategoryIDProperty());
        if (mTransferAccountComboBox.isVisible()) {
            Bindings.bindBidirectional(mTransferAccountComboBox.valueProperty(),
                    mTransaction.getCategoryIDProperty().asObject());
        } else {
            mTransferAccountComboBox.getSelectionModel().selectFirst(); // make sure it selects something not null
        }

        if (mCategoryComboBox.isVisible()) {
            int cid = mTransaction.getCategoryID();
            Bindings.bindBidirectional(mCategoryComboBox.valueProperty(),
                    mTransaction.getCategoryIDProperty().asObject());
            mCategoryComboBox.getSelectionModel().select(new Integer(cid));
        } else {
            mCategoryComboBox.getSelectionModel().selectFirst();  // make sure it selects something not null
        }

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

        if (isIncome)
            mIncomeTextField.textProperty().bindBidirectional(mTransaction.getAmountProperty(),
                    new BigDecimalStringConverter());

        if (isReinvest || (!isIncome && !isCashTransfer)) {
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

                    if (tradeAction == SELL || tradeAction == SHTSELL)
                        return mTransaction.getAmount().add(mTransaction.getCommission())
                                .divide(mTransaction.getQuantity(), MainApp.PRICE_FRACTION_LEN, RoundingMode.HALF_UP);
                    return mTransaction.getAmount().subtract(mTransaction.getCommission())
                            .divide(mTransaction.getQuantity(), MainApp.PRICE_FRACTION_LEN, RoundingMode.HALF_UP);
                }
            };
            mTransaction.getPriceProperty().bind(price);
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
                new MyBigDecimalStringConverter());
    }
}