package net.taihuapp.facai168;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

        private LocalDate mDate;
        private double mTAmount;
        private double mUAmount;  // not sure what's the difference between T and U amounts
        private int mCleared;  // 0 for not present, 1 for *, 2 for X
        private String mCheckNumber; // check number of ref, such as ATM, etc, so string is used
        private String mPayee;
        private String mMemo;
        private List<String> mAddressList; // QIF says up to 6 lines.
        private String mCategoryOrTransfer;
        private List<SplitBT> mSplitList;

        // default constructor
        public BankTransaction() {
            mCleared = 0;
            mAddressList = new ArrayList<>();
            mSplitList = new ArrayList<>();
        }
        // setters
        public void setDate(LocalDate d) { mDate = d; }
        public void setTAmount(double t) { mTAmount = t; }
        public void setUAmount(double u) { mUAmount = u; }
        public void setCleared(char c) {
            switch (c) {
                case '*':
                    mCleared = 1;
                    break;
                case 'X':
                    mCleared = 2;
                    break;
                default:
                    mCleared = 0;
                    break;
            }
        }
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


    private List<Account> mAccountList;
    private List<Category> mCategoryList;
    private List<Security> mSecurityList;
    private List<BankTransaction> mBankTransactionList;

    // public constructor
    public QIFParser() {
        mAccountList = new ArrayList<>();
        mCategoryList = new ArrayList<>();
        mSecurityList = new ArrayList<>();
        mBankTransactionList = new ArrayList<>();
    }

    // parse QIF formated date
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
                                System.out.println("# of Category = " + mCategoryList.size());
                                category = null;
                                nRecords++;
                            } else {
                                System.err.println("Bad formatted Category text: "
                                        + allLines.subList(i, j).toString());
                            }
                            i = j;
                            break;
                        case ACCOUNT:
                            account = Account.fromQIFLines(allLines.subList(i, j));
                            if (account != null) {
                                if (autoSwitch) {
                                    mAccountList.add(account);
                                    System.out.println("# of Account = " + mAccountList.size());
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
                                mBankTransactionList.add(bt);
                                System.out.println("# of BT = " + mBankTransactionList.size());
                            } else {
                                System.err.println("Bad formatted BankTransaction record: "
                                        + allLines.subList(i,j).toString());
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
