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
    public enum ListType { CLASS, CAT, MEMORIZED, SECURITY, PRICES, BUDGET, INVITEM, TEMPLATE, ACCOUNT }

    static class Account {
        private List<Transaction> mTransactionList;

        public List<Transaction> getTransactionList() { return mTransactionList; }
    }
    static class Transaction {

    }

    private List<Account> mAccountList;

    // public constructor
    public QIFParser() {
        mAccountList = new ArrayList<>();
    }

    // return the number of records
    public int parseFile(File qif) throws IOException {
        List<String> allLines = Files.readAllLines(qif.toPath());
        int nLines = allLines.size();
        if (nLines == 0)
            return 0;
        if (!allLines.get(nLines-1).equals("^")) {
            throw new IOException("Bad formated file");
        }

        HashMap<String, String> record = new HashMap<String, String>();
        List<HashMap<String, String>> recordList = new ArrayList<>();
        int i = 0;
        while (i < nLines) {
            if (allLines.get(i).equals("^")) {
                recordList.add(record);
                if (i < nLines-1) {
                    record = new HashMap<String, String>();
                    System.out.println(allLines.get(i+1));
                }
            }
            i++;
        }
        System.out.println("Parsing...");

        return recordList.size();
    }

    public List<Account> getAccountList() { return mAccountList; }
}
