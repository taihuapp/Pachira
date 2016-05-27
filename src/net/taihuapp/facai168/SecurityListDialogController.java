package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;

/**
 * Created by ghe on 5/24/16.
 *
 */
public class SecurityListDialogController {
    private MainApp mMainApp = null;

    private Security mSavedCopy = null;
    @FXML
    private TableView<Security> mSecurityTableView;
    @FXML
    private TableColumn<Security, String> mSecurityNameColumn;
    @FXML
    private TableColumn<Security, String> mSecurityTickerColumn;
    @FXML
    private TableColumn<Security, Security.Type> mSecurityTypeColumn;

    void setMainApp(MainApp mainApp) {
        mMainApp = mainApp;

        mSecurityTableView.setEditable(true);
        mSecurityTableView.setItems(mainApp.getSecurityList());

        mSecurityNameColumn.setCellValueFactory(cellData->cellData.getValue().getNameProperty());
        mSecurityTickerColumn.setCellValueFactory(cellData->cellData.getValue().getTickerProperty());
        mSecurityTypeColumn.setCellValueFactory(cellData->cellData.getValue().getTypeProperty());

        mSecurityNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        mSecurityNameColumn.setOnEditCommit(e -> {
            if (mSavedCopy == null) mSavedCopy = new Security(e.getRowValue()); // save a backup copy
            e.getRowValue().setName(e.getNewValue());
        });

        mSecurityTickerColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        mSecurityTickerColumn.setOnEditCommit(e -> {
            if (mSavedCopy == null) mSavedCopy = new Security(e.getRowValue()); // save a backup copy
            e.getRowValue().setTicker(e.getNewValue());
        });

        mSecurityTypeColumn.setCellFactory(ChoiceBoxTableCell.forTableColumn(Security.Type.values()));
        mSecurityTypeColumn.setOnEditCommit(e -> {
            if (mSavedCopy == null) mSavedCopy = new Security(e.getRowValue()); // save a backup copy
            e.getRowValue().setType(e.getNewValue());
        });

        mSecurityTableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> syncEditedEntry(oldValue));
    }

    private boolean syncEditedEntry(Security security) {
        if (mSavedCopy == null)
            return false;  // no saved copy, there is no editing.

        System.out.println("Old Row Index = " + mSecurityTableView.getItems().indexOf(security));
        // check confirmation
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Save Changes?");
        alert.setHeaderText("You have unsaved changed in Security:" + "\n" + security.getName());
        alert.setContentText("Do want to save the change?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK)
                mMainApp.insertUpdateSecurityToDB(security);
            else {
                mSavedCopy.setName(mSavedCopy.getName() + " Copy");
                int index = mSecurityTableView.getItems().indexOf(security);
                // for some reason, use set(index, mSavedCopy) causes an indexOutOfBoundsException
                // but use separate remove and add works.
                mSecurityTableView.getItems().remove(index);
                mSecurityTableView.getItems().add(index, mSavedCopy);
            }
        });
        mSavedCopy = null;
        return true;
    }

    void close() { syncEditedEntry(mSecurityTableView.getSelectionModel().getSelectedItem()); }
}
