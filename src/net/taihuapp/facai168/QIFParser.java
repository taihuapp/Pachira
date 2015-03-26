package net.taihuapp.facai168;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by ghe on 3/22/15.
 */
public class QIFParser {
    // These are the exportable lists show in the QIF99 spec
    // ACCOUNT list is not among the exportable lists, it uses !Option:AutoSwitch and !Clear:AutoSwitch
    // to indicate
    public enum RecordType { CLASS, CAT, MEMORIZED, SECURITY, PRICES, BUDGET, INVITEM, TEMPLATE, ACCOUNT }

    static class Category extends Object {
        private String mName;  // name of the category
        private String mDescription;  // description
        private boolean mIsTaxRelated;
        private int mTaxRefNum;  // Tax reference number (for tax-related items
        private boolean mIsIncome; // income category flag
//38689943
        public Category() {
            mName = "";
            mDescription = "";
            mIsTaxRelated = false;
            mTaxRefNum = -1;
            mIsIncome = true;
        }

        public void setName(String n) { mName = n; }
        public String getName() { return mName; }
        public void setDescription(String d) { mDescription = d; }
        public String getDescription() { return mDescription; }
        public void setIsTaxRelated(boolean t) { mIsTaxRelated = t; }
        public boolean isTaxRelated() { return mIsTaxRelated; }
        public void setTaxRefNum(int r) { mTaxRefNum = r; }
        public int getTaxRefNum() { return mTaxRefNum; }
        public void setIsIncome(boolean i) { mIsIncome = i;}
        public boolean isIncome() { return mIsIncome; }

        static Category fromQIFLines(List<String> lines) throws IOException {
            System.out.println(lines.toString());
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
                    default:
                        return null;
                }
            }
            return category;
        }
    }

    static class Account {
        private List<Transaction> mTransactionList;

        public List<Transaction> getTransactionList() { return mTransactionList; }
    }
    static class Transaction {

    }

    private List<Account> mAccountList;
    private List<Category> mCategoryList;

    // public constructor
    public QIFParser() {
        mAccountList = new ArrayList<>();
        mCategoryList = new ArrayList<>();
    }

    // return the number of records
    public int parseFile(File qif) throws IOException {
        int nRecords = 0;
        List<String> allLines = Files.readAllLines(qif.toPath());
        int nLines = allLines.size();
        if (nLines == 0)
            return 0;
        if (!allLines.get(nLines-1).equals("^")) {
            throw new IOException("Bad formated file");
        }

        // trim off white spaces
        for (int i = 0; i < nLines; i++) {
            String s = allLines.get(i).trim();
            allLines.set(i, s);
        }

        RecordType currentRecordType = null;
        boolean endRecord = true;
        int i = 0;
        Category category = null;
        while (i < nLines) {
            String line = allLines.get(i);
            switch (line) {
                case "!Type:Cat":
                    currentRecordType = RecordType.CAT;
                    break;
                case "!Account":
                case "!Clear:AutoSwitch":
                case "!Option:AutoSwitch":
                case "!Type:Bank":
                case "!Type:Invst":
                case "!Type:Memorized":
                case "!Type:Prices":
                case "!Type:Security":
                    System.err.println(line + " Not implemented yet.");
                    return -1;
                default:
                    for (int j = i; j < nLines; j++) {
                        if (allLines.get(j).equals("^")) {
                            switch (currentRecordType) {
                                case CAT:
                                    category = Category.fromQIFLines(allLines.subList(i, j));
                                    if (category != null) {
                                        mCategoryList.add(category);
                                        category = null;
                                        nRecords++;
                                    } else {
                                        System.err.println("Bad formated Category text: "
                                                + allLines.subList(i, j).toString());
                                    }
                                    i = j;
                                    break;
                                default:
                                    System.err.println("Not implemented yet");
                                    return -1;
                            }
                            break;  // break out the for loop
                        }
                    }
            }
            i++;
        }
        System.out.println("Parsing...");

        return nRecords;
    }

    public List<Account> getAccountList() { return mAccountList; }
    public List<Category> getCategoryList() { return mCategoryList; }
}
