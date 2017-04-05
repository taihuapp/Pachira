package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by ghe on 3/30/17.
 *
 */
public class SplitTransactionsDialogController {

    private class CategoryTransferToStringConverter extends StringConverter<Integer> {
        public Integer fromString(String name) {
            return mMainApp.mapCategoryOrAccountNameToID(name);
        }
        public String toString(Integer cid) {
            if (cid >= MainApp.MIN_CATEGORY_ID) {
                Category c = mMainApp.getCategoryByID(cid);
                return c == null ? null : c.getName();
            }

            if (cid <= -MainApp.MIN_ACCOUNT_ID) {
                Account a = mMainApp.getAccountByID(-cid);
                return a == null ? null : MainApp.getWrappedAccountName(a);
            }
            return null;
        }
    }

    private MainApp mMainApp;
    private Stage mDialogStage;
    private BigDecimal mNetAmount;

    @FXML
    private TableView<SplitTransaction> mSplitTransactionsTableView;
    @FXML
    private TableColumn<SplitTransaction, Integer> mCategoryTableColumn;
    @FXML
    private TableColumn<SplitTransaction, String> mMemoTableColumn;
    @FXML
    private TableColumn<SplitTransaction, BigDecimal> mAmountTableColumn;
    @FXML
    private ComboBox<Integer> mCategoryIDComboBox;
    @FXML
    private TextField mMemoTextField;
    @FXML
    private TextField mAmountTextField;
    @FXML
    private Button mAddButton;
    @FXML
    private Button mEditButton;
    @FXML
    private Button mDeleteButton;

    // the content of stList is copied, the original content is unchanged.
    void setMainApp(MainApp mainApp, Stage stage, List<SplitTransaction> stList, BigDecimal netAmount) {
        mMainApp = mainApp;
        mDialogStage = stage;
        mNetAmount = netAmount;

        mCategoryIDComboBox.setConverter(new CategoryTransferToStringConverter());
        mCategoryIDComboBox.getItems().clear();
        mCategoryIDComboBox.getItems().add(null); // add a blank
        for (Category c : mMainApp.getCategoryList()) {
            mCategoryIDComboBox.getItems().add(c.getID());
        }
        for (Account a : mMainApp.getAccountList(null, null, true)) {
            mCategoryIDComboBox.getItems().add(-a.getID());
        }

        mSplitTransactionsTableView.setEditable(true);
        mSplitTransactionsTableView.getItems().clear();
        for (SplitTransaction st : stList) {
            mSplitTransactionsTableView.getItems().add(new SplitTransaction(st.getID(), st.getCategoryID(),
                    st.getMemo(), st.getAmount(), st.getMatchID()));
        }

        mCategoryTableColumn.setCellValueFactory(cd -> cd.getValue().getCategoryIDProperty().asObject());
        mCategoryTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(new CategoryTransferToStringConverter(),
                mCategoryIDComboBox.getItems()));

        mMemoTableColumn.setCellValueFactory(cd -> cd.getValue().getMemoProperty());
        mMemoTableColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        mMemoTableColumn.setOnEditCommit(e
                -> e.getTableView().getItems().get(e.getTablePosition().getRow()).setMemo(e.getNewValue()));

        mAmountTableColumn.setCellValueFactory(cd->cd.getValue().getAmountProperty());
        mAmountTableColumn.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        mAmountTableColumn.setOnEditCommit(e -> {
            e.getTableView().getItems().get(e.getTablePosition().getRow()).setAmount(e.getNewValue());
            updateRemainingAmount();
        });
        mAmountTableColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        updateRemainingAmount();
    }

    private void updateRemainingAmount() {
        BigDecimal remainingAmount = mNetAmount;
        for (SplitTransaction st : mSplitTransactionsTableView.getItems()) {
            remainingAmount = remainingAmount.add(st.getAmount());
        }
        remainingAmount = remainingAmount.negate();
        mAmountTextField.setText(remainingAmount.toPlainString());
    }

    @FXML
    private void initialize() {
        mCategoryIDComboBox.setPrefWidth(mCategoryTableColumn.getWidth());
        mMemoTextField.setPrefWidth(mMemoTableColumn.getWidth());
        mAmountTextField.setPrefWidth(mAmountTableColumn.getWidth());

        mCategoryTableColumn.widthProperty().addListener((ob, o, n) -> mCategoryIDComboBox.setPrefWidth(n.doubleValue()));
        mMemoTableColumn.widthProperty().addListener((ob, o, n) -> mMemoTextField.setPrefWidth(n.doubleValue()));
        mAmountTableColumn.widthProperty().addListener((ob, o, n) -> mAmountTextField.setPrefWidth(n.doubleValue()));
    }

    @FXML
    private void handleOK() {
        mDialogStage.setUserData(mSplitTransactionsTableView.getItems());
        mDialogStage.close();
    }

    @FXML
    private void handleCancel() {
        mDialogStage.setUserData(null);
        mDialogStage.close();
    }

    @FXML
    private void handleAdd() { System.out.println("Add"); }
    @FXML
    private void handleEdit() { System.out.println("Edit"); }
    @FXML
    private void handleDelete() { System.out.println("Delete"); }
}
