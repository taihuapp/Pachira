package net.taihuapp.facai168;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ghe on 3/22/15.
 */
public class QIFParser {
    // These are the exportable lists show in the QIF99 spec
    // CLASS and TEMPLATE are not being used
    public enum RecordType { CLASS, CAT, MEMORIZED, SECURITY, PRICES, BANK, INVITEM, TEMPLATE, ACCOUNT }

    static class Category {
        private String mName;  // name of the category
        private String mDescription;  // description
        private boolean mIsIncome; // income category flag
        // mTaxRefNum = -1 for non tax related
        //               0 for tax related but no valid ref num
        //              >0 actual tax ref number
        private int mTaxRefNum;  // Tax reference number (for tax-related items,
        private double mBudgetAmount; // budget amount

        public Category() {
            mName = "";
            mDescription = "";
            mTaxRefNum = -1;
            mIsIncome = true;
        }

        public void setName(String n) { mName = n; }
        public String getName() { return mName; }
        public void setDescription(String d) { mDescription = d; }
        public String getDescription() { return mDescription; }
        public void setIsTaxRelated(boolean t) {
            if (t && (getTaxRefNum() < 0)) {
                setTaxRefNum(0); // set it to be tax related
            } else if (!t) {
                setTaxRefNum(-1); // set it to be non tax related
            }
        }
        public boolean isTaxRelated() { return getTaxRefNum() >= 0; }
        public void setTaxRefNum(int r) { mTaxRefNum = r; }
        public int getTaxRefNum() { return mTaxRefNum; }
        public void setIsIncome(boolean i) { mIsIncome = i;}
        public boolean isIncome() { return mIsIncome; }
        public void setBudgetAmount(double b) { mBudgetAmount = b; }
        public double getBudgetAmount() { return mBudgetAmount; }
        static Category fromQIFLines(List<String> lines)  {
            Category category = new Category();
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'N':
                        category.setName(l.substring(1));
                        break;
                    case 'D':
                        category.setDescription(l.substring(1));
                        break;
                    case 'T':
                        category.setIsTaxRelated(true);
                        break;
                    case 'R':
                        category.setTaxRefNum(Integer.parseInt(l.substring(1)));
                        break;
                    case 'I':
                        category.setIsIncome(true);
                        break;
                    case 'E':
                        category.setIsIncome(false);
                        break;
                    case 'B':
                        category.setBudgetAmount(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    default:
                        return null;
                }
            }
            return category;
        }
    }

    // starting from lines.get(startIdx) seek the line number of the next line
    // equals match
    // if not found, -1 is returned
    static int findNextMatch(List<String> lines, int startIdx, String match) {
        for (int i = startIdx; i < lines.size(); i++) {
            if (lines.get(i).equals(match)) {
                return i;
            }
        }
        return -1;
    }

    static class Security {
        private String mName;
        private String mSymbol;
        private String mType;
        private String mGoal;

        // constructor
        public Security() {
            mName = "";
            mSymbol = "";
            mType = "";
            mGoal = "";
        }

        public void setName(String n) { mName = n; }
        public String getName() { return mName; }
        public void setSymbol(String s) { mSymbol = s; }
        public String getSymbol() { return mSymbol; }
        public void setType(String t) { mType = t; }
        public String getType() { return mType; }
        public void setGoal(String g) { mGoal = g; }
        public String getGoal() { return mGoal; }

        static Security fromQIFLines(List<String> lines) {
            Security security = new Security();
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'N':
                        security.setName(l.substring(1));
                        break;
                    case 'S':
                        security.setSymbol(l.substring(1));
                        break;
                    case 'T':
                        security.setType(l.substring(1));
                        break;
                    case 'G':
                        security.setGoal((l.substring(1)));
                        break;
                    default:
                        return null;
                }
            }
            return security;
        }
    }

    static class Account {
        private String mName;  // name of the account
        private String mType;  // Type of the account
        private double mSalesTaxRate; // sales tax rate for tax account
        private String mDescription; // description of the account
        private double mCreditLimit;  // for credit card account
        private double mBalance; // account balance

        // default constructor set members to default values
        public Account() {
            mName = "";
            mType = "";
            mSalesTaxRate = 0;
            mDescription = "";
            mCreditLimit = -1;
            mBalance = 0;
        }

        // setters and getters
        public void setName(String n) { mName = n; }
        public String getName() { return mName; }
        public void setType(String t) { mType = t; }
        public String getType() { return mType; }
        public void setSalesTaxRate(double r) { mSalesTaxRate = r; }
        public double getSalesTaxRate() { return mSalesTaxRate; }
        public void setDescription(String d) { mDescription = d; }
        public String getDescription() { return mDescription; }
        public void setCreditLimit(double c) { mCreditLimit = c; }
        public double getCreditLimit() { return mCreditLimit; }
        public void setBalance(double b) { mBalance = b; }
        public double getBalance() { return mBalance; }

        static Account fromQIFLines(List<String> lines) {
            System.out.println(lines.toString());
            Account account = new Account();
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'N':
                        account.setName(l.substring(1));
                        break;
                    case 'T':
                        account.setType(l.substring(1));
                        break;
                    case 'R':
                        account.setSalesTaxRate(Double.parseDouble(l.substring(1)));
                        break;
                    case 'D':
                        account.setDescription(l.substring(1));
                        break;
                    case 'L':
                        account.setCreditLimit(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    default:
                        // bad formatted record, return null
                        return null;
                }
            }
            return account;
        }
    }

    static class BankTransaction {
        // split bank transaction
        static class SplitBT {
            private String mCategory; // split category
            private String mMemo; // split memo
            private double mAmount; // split amount
            private double mPercentage; // % of split if % is used

            // public constructor
            public SplitBT() {
                mCategory = "";
                mMemo = "";
                mAmount = 0;
                mPercentage = -1;
            }

            // setters and getters
            public void setCategory(String c) { mCategory = c; }
            public String getCategory() { return mCategory; }
            public void setMemo(String m) { mMemo = m; }
            public String getMemo() { return mMemo; }
            public void setAmount(double a) { mAmount = a; }
            public double getAmount() { return mAmount; }
            public void setPercentage(double p) { mPercentage = p; }
            public double getPercentage() { return mPercentage; }
        }

        private String mAccountName;
        private LocalDate mDate;
        private double mTAmount;
        private double mUAmount;  // not sure what's the difference between T and U amounts
        private char mCleared;  // 0 for not present, 1 for *, 2 for X
        private String mCheckNumber; // check number of ref, such as ATM, etc, so string is used
        private String mPayee;
        private String mMemo;
        private List<String> mAddressList; // QIF says up to 6 lines.
        private String mCategoryOrTransfer; // L line
        private List<SplitBT> mSplitList;

        // default constructor
        public BankTransaction() {
            mCleared = ' ';
            mAddressList = new ArrayList<>();
            mSplitList = new ArrayList<>();
        }
        // setters
        public void setAccountName(String a) { mAccountName = a; }
        public void setDate(LocalDate d) { mDate = d; }
        public void setTAmount(double t) { mTAmount = t; }
        public void setUAmount(double u) { mUAmount = u; }
        public void setCleared(char c) { mCleared = c; }
        public void setCheckNumber(String c) { mCheckNumber = c; }
        public void setPayee(String p) { mPayee = p; }
        public void setMemo(String m) { mMemo = m; }
        public void addAddress(String a) { mAddressList.add(a); }
        public void setCategoryOrTransfer(String ct) { mCategoryOrTransfer = ct; }
        public void addSplit(SplitBT s) { mSplitList.add(s); }

        public static BankTransaction fromQIFLines(List<String> lines) {
            System.out.println(lines.toString());
            BankTransaction bt = new BankTransaction();
            SplitBT splitBT = null;
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'D':
                        bt.setDate(parseDate(l.substring(1)));
                        break;
                    case 'T':
                        bt.setTAmount(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    case 'U':
                        bt.setUAmount(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    case 'C':
                        bt.setCleared(l.charAt(1));
                        break;
                    case 'N':
                        bt.setCheckNumber(l.substring(1));
                        break;
                    case 'P':
                        bt.setPayee(l.substring(1));
                        break;
                    case 'M':
                        bt.setMemo(l.substring(1));
                        break;
                    case 'A':
                        bt.addAddress(l.substring(1));
                        break;
                    case 'L':
                        bt.setCategoryOrTransfer(l.substring(1));
                        break;
                    case 'S':
                        if (splitBT != null) {
                            bt.addSplit(splitBT);
                        }
                        splitBT = new SplitBT();
                        splitBT.setCategory(l.substring(1));
                        break;
                    case 'E':
                        if (splitBT == null) {
                            System.err.println("Bad formatted BankTransactionSplit " +
                                    lines.toString());
                            return null;
                        } else {
                            splitBT.setMemo(l.substring(1));
                        }
                        break;
                    case '$':
                        if (splitBT == null) {
                            System.err.println("Bad formatted BankTransactionSplit " +
                                    lines.toString());
                            return null;
                        } else {
                            splitBT.setAmount(Double.parseDouble(l.substring(1).replace(",","")));
                        }
                        break;
                    case 'F':
                        System.err.println("F flag in BankTransaction not implemented "
                                + lines.toString());
                        break;
                    default:
                        System.err.println("Offending line: " + l);
                        return null;
                }
            }
            if (splitBT != null) {
                bt.addSplit(splitBT);
            }
            return bt;
        }
    }

    static class TradeTransaction {

        public enum Action { BUY, BUYX, CASH, CGLONG, CGSHORT, DIV, MISCEXP, EISCEXPX,
            MISCINC, MISCINCX, REINVDIV, REINVLG, REINVSH,
            SELL, SELLX, SHRSIN, SHRSOUT, STKSPLIT, XIN, XOUT }

        private String mAccountName;
        private LocalDate mDate;
        private Action mAction;
        private String mSecurity;
        private double mPrice;
        private double mQuantity;
        private char mCleared; // C line
        private String mTransferReminderText; // P line
        private String mMemo;
        private double mCommission;
        private String mCategoryOrTransfer; // L line
        private double mTAmount; // not sure what's the difference between
        private double mUAmount; // T and U amounts
        private double mAmountTransferred; // $ line

        public TradeTransaction() {
            mCleared = ' ';
        }

        // setters
        public void setAccountName(String a) { mAccountName = a;}
        public void setDate(LocalDate d) { mDate = d; }
        public void setAction(Action action) { mAction = action; }
        public void setSecurity(String s) { mSecurity = s; }
        public void setPrice(double p) { mPrice = p; }
        public void setQuantity(double q) { mQuantity = q; }
        public void setCleared(char c) { mCleared = c; }
        public void setTransferReminderText(String t) { mTransferReminderText = t; }
        public void setMemo(String m) { mMemo = m; }
        public void setCommission(double c) { mCommission = c; }
        public void setCategoryOrTransfer(String ct) { mCategoryOrTransfer = ct; }
        public void setTAmount(double t) { mTAmount = t; }
        public void setUAmount(double u) { mUAmount = u; }
        public void setAmountTransferred(double a) { mAmountTransferred = a; }

        public static TradeTransaction fromQIFLines(List<String> lines) {
            System.out.println(lines.toString());
            TradeTransaction tt = new TradeTransaction();
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'D':
                        tt.setDate(parseDate(l.substring(1)));
                        break;
                    case 'N':
                        tt.setAction(Action.valueOf(l.substring(1).toUpperCase()));
                        break;
                    case 'Y':
                        tt.setSecurity(l.substring(1));
                        break;
                    case 'I':
                        tt.setPrice(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    case 'Q':
                        tt.setQuantity(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    case 'C':
                        tt.setCleared(l.charAt(1));
                        break;
                    case 'P':
                        tt.setTransferReminderText(l.substring(1));
                        break;
                    case 'M':
                        tt.setMemo(l.substring(1));
                        break;
                    case 'O':
                        tt.setCommission(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    case 'L':
                        tt.setCategoryOrTransfer(l.substring(1));
                        break;
                    case 'T':
                        tt.setTAmount(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    case 'U':
                        tt.setUAmount(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    case '$':
                        tt.setAmountTransferred(Double.parseDouble(l.substring(1).replace(",","")));
                        break;
                    default:
                        System.err.println("Offending line: " + l);
                        return null;

                }
            }
            return tt;
        }
    }

    static class MemorizedTransaction {
        public enum Type { INVESTMENT, EPAYMENT, CHECK, PAYMENT, DEPOSIT }

        private Type mType;
        private double mQQuantity; // number of new shares for a split
        private double mRQuantity; // number of old shares for a split
        private BankTransaction mBT;
        private TradeTransaction mTT;


        public MemorizedTransaction() {
            mBT = null;
            mTT = null;
        }

        // getters
        public Type getType() { return mType; }

        //setters
        public void setType(char t) {
            switch (t) {
                case 'I':
                    mType = Type.INVESTMENT;
                    break;
                case 'E':
                    mType = Type.EPAYMENT;
                    break;
                case 'C':
                    mType = Type.CHECK;
                    break;
                case 'P':
                    mType = Type.PAYMENT;
                    break;
                case 'D':
                    mType = Type.DEPOSIT;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown type [" + t + "] for MemorizedType");
            }
        }
        public void setQQuantity(double q) { mQQuantity = q; }
        public void setRQuantity(double r) { mRQuantity = r; }
        public void setTransactionDetails(List<String> lines) {
            if (getType() == Type.INVESTMENT) {
                mTT = TradeTransaction.fromQIFLines(lines);
            } else {
                mBT = BankTransaction.fromQIFLines(lines);
            }
        }

        public static MemorizedTransaction fromQIFLines(List<String> lines) {
            System.out.println(lines);
            List<String> unParsedLines = new ArrayList<>();
            MemorizedTransaction mt = new MemorizedTransaction();
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'K':
                        mt.setType(l.charAt(1));
                        break;
                    case 'Q':
                        mt.setQQuantity(Double.parseDouble(l.substring(1).replace(",", "")));
                        break;
                    case 'R':
                        mt.setRQuantity(Double.parseDouble(l.substring(1).replace(",", "")));
                        break;
                    default:
                        unParsedLines.add(l);
                        break;
                }
            }

            if (unParsedLines.size() > 0) {
                mt.setTransactionDetails(unParsedLines);
            }
            return mt;
        }
    }

    static class Price {
        private String mSecurity;
        private LocalDate mDate;
        private double mPrice;

        // setters
        public void setSecurity(String s) { mSecurity = s; }
        public void setDate(LocalDate d) { mDate = d; }
        public void setPrice(double p) { mPrice = p; }

        public static Price fromQIFLines(List<String> lines) {
            if (lines.size() > 1) {
                System.err.println("Price record, expected 1 line, got " + lines.size());
                return null;
            }
            String tokens[] = lines.get(0).split(",");
            if (tokens.length != 3) {
                System.err.println("Expect 3 ',' separated fields, got " + tokens.length);
                return null;
            }
            Price price = new Price();
            price.setSecurity(tokens[0].replace("\"", ""));
            price.setDate(parseDate(tokens[2].replace("\"", "").trim()));

            // the actual price has two possible formats:
            // decimal, xxx.yyyy
            // fraction [x ]y/z
            int idx0 = tokens[1].indexOf('/');
            if (idx0 == -1) {
                // not a fraction
                if (tokens[1].length() > 0) {
                    price.setPrice(Double.parseDouble(tokens[1]));
                } else {
                    price.setPrice(0);
                }
            } else {
                int whole, num, den, idx1;
                den = Integer.valueOf(tokens[1].substring(idx0+1));
                idx1 = tokens[1].indexOf(' ');
                if (idx1 == -1) {
                    // no space
                    whole = 0;
                    num = Integer.valueOf(tokens[1].substring(0,idx0));
                } else {
                    whole = Integer.valueOf(tokens[1].substring(0, idx1));
                    num = Integer.valueOf(tokens[1].substring(idx1+1, idx0));
                }
                price.setPrice((double) whole + (double) num / (double) den);
            }
            return price;
        }
    }

    private List<Account> mAccountList;
    private List<Category> mCategoryList;
    private List<Security> mSecurityList;
    private List<BankTransaction> mBankTransactionList;
    private List<TradeTransaction> mTradeTransactionList;
    private List<MemorizedTransaction> mMemorizedTransactionList;
    private List<Price> mPriceList;

    // public constructor
    public QIFParser() {
        mAccountList = new ArrayList<>();
        mCategoryList = new ArrayList<>();
        mSecurityList = new ArrayList<>();
        mBankTransactionList = new ArrayList<>();
        mTradeTransactionList = new ArrayList<>();
        mMemorizedTransactionList = new ArrayList<>();
        mPriceList = new ArrayList<>();
    }

    // parse QIF formatted date
    private static LocalDate parseDate(String s) {
        DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                .appendValue(ChronoField.MONTH_OF_YEAR).appendLiteral('/')
                .appendValue(ChronoField.DAY_OF_MONTH).appendLiteral('/')
                .appendValueReduced(ChronoField.YEAR, 2, 2, 1970).toFormatter();
        return LocalDate.parse(s.replace(' ', '0').replace('\'', '/'), dtf);
    }

    // return the number of records
    public int parseFile(File qif) throws IOException {
        int nRecords = 0;
        List<String> allLines = Files.readAllLines(qif.toPath());
        int nLines = allLines.size();
        if (nLines == 0)
            return 0;
        if (!allLines.get(nLines-1).equals("^")) {
            throw new IOException("Bad formatted file");
        }

        // trim off white spaces
        for (int i = 0; i < nLines; i++) {
            String s = allLines.get(i).trim();
            allLines.set(i, s);
        }

        boolean autoSwitch = false;
        RecordType currentRecordType = null;
        int i = 0;
        Account account = new Account();
        while (i < nLines) {
            String line = allLines.get(i);
            switch (line) {
                case "!Type:Cat":
                    currentRecordType = RecordType.CAT;
                    break;
                case "!Type:Bank":
                    currentRecordType = RecordType.BANK;
                    break;
                case "!Type:Invst":
                    currentRecordType = RecordType.INVITEM;
                    break;
                case "!Account":
                    currentRecordType = RecordType.ACCOUNT;
                    break;
                case "!Clear:AutoSwitch":
                    currentRecordType = null;
                    autoSwitch = false;
                    break;
                case "!Option:AutoSwitch":
                    currentRecordType = null;
                    autoSwitch = true;
                    break;
                case "!Type:Memorized":
                    currentRecordType = RecordType.MEMORIZED;
                    break;
                case "!Type:Prices":
                    currentRecordType = RecordType.PRICES;
                    break;
                case "!Type:Security":
                    currentRecordType = RecordType.SECURITY;
                    break;
                default:
                    // this is a content line, find the end of the record
                    int j = findNextMatch(allLines, i, "^");
                    if (j == -1) {
                        System.err.println("Bad formatted file.  Can't find '^'");
                        return -1;
                    }
                    switch (currentRecordType) {
                        case CAT:
                            Category category = Category.fromQIFLines(allLines.subList(i, j));
                            if (category != null) {
                                mCategoryList.add(category);
                                category = null;
                                nRecords++;
                            } else {
                                System.err.println("Bad formatted Category text: "
                                        + allLines.subList(i, j));
                            }
                            i = j;
                            break;
                        case ACCOUNT:
                            account = Account.fromQIFLines(allLines.subList(i, j));
                            if (account != null) {
                                if (autoSwitch) {
                                    mAccountList.add(account);
                                } else {
                                    // todo
                                    // what should I do here?
                                }
                                nRecords++;
                            } else {
                                System.err.println("Bad formatted Account record: "
                                        + allLines.subList(i,j).toString());
                            }
                            i = j;
                            break;
                        case SECURITY:
                            Security security = Security.fromQIFLines(allLines.subList(i,j));
                            if (security != null) {
                                mSecurityList.add(security);
                                System.out.println("# of Security = " + mSecurityList.size());
                            } else {
                                System.err.println("Bad formatted Security record: "
                                        + allLines.subList(i,j).toString());
                            }
                            i = j;
                            break;
                        case BANK:
                            BankTransaction bt = BankTransaction.fromQIFLines(allLines.subList(i,j));
                            if (bt != null) {
                                if (account != null) {
                                    bt.setAccountName(account.getName());
                                }
                                mBankTransactionList.add(bt);
                                System.out.println("# of BT = " + mBankTransactionList.size());
                            } else {
                                System.err.println("Bad formatted BankTransaction record: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case INVITEM:
                            TradeTransaction tt = TradeTransaction.fromQIFLines(allLines.subList(i,j));
                            if (tt != null) {
                                if (account != null) {
                                    tt.setAccountName(account.getName());
                                }
                                mTradeTransactionList.add(tt);
                                System.out.println("# of TT = " + mTradeTransactionList.size());
                            } else {
                                System.err.println("Bad formatted TradeTransaction record: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case MEMORIZED:
                            MemorizedTransaction mt = MemorizedTransaction.fromQIFLines(allLines.subList(i,j));
                            if (mt != null) {
                                mMemorizedTransactionList.add(mt);
                                System.out.println("# of MT = " + mMemorizedTransactionList.size());
                            } else {
                                System.err.println("Bad formatted MemorizedTransaction: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case PRICES:
                            Price price = Price.fromQIFLines(allLines.subList(i,j));
                            if (price != null) {
                                mPriceList.add(price);
                            } else {
                                System.err.println("Bad formatted Price record: "
                                        + allLines.subList(i, j));
                            }
                            i = j;
                            break;
                        default:
                            System.err.println(currentRecordType.toString() + " Not implemented yet");
                            return -1;
                    }
                    break;  // break out the switch
            }
            i++;
        }
        System.out.println("Parsing...");

        return nRecords;
    }

    public List<Account> getAccountList() { return mAccountList; }
    public List<Category> getCategoryList() { return mCategoryList; }
}
