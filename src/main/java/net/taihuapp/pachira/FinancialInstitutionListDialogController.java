/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of Pachira.
 *
 * Pachira is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * Pachira is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.pachira;

import com.webcohesion.ofx4j.client.FinancialInstitutionData;
import com.webcohesion.ofx4j.client.impl.LocalResourceFIDataStore;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class FinancialInstitutionListDialogController {

    private static final Logger mLogger = Logger.getLogger(FinancialInstitutionListDialogController.class);

    private MainApp mMainApp;
    private Stage mDialogStage;

    @FXML
    private TableView<DirectConnection.FIData> mImportedFIDataTableView;
    @FXML
    private TableColumn<DirectConnection.FIData, String> mImportedFIDataNameTableColumn;
    @FXML
    private TableColumn<DirectConnection.FIData, String> mImportedFIDataFIIDTableColumn;
    @FXML
    private TableColumn<DirectConnection.FIData, String> mImportedFIDataFISubIDTableColumn;
    @FXML
    private TableColumn<DirectConnection.FIData, String> mImportedFIDataORGTableColumn;
    @FXML
    private TableColumn<DirectConnection.FIData, String> mImportedFIDataURLTableColumn;


    @FXML
    private TableView<FinancialInstitutionData> mOFXFIDataTableView;
    @FXML
    private TableColumn<FinancialInstitutionData, String> mOFXFIDataNameTableColumn;
    @FXML
    private TableColumn<FinancialInstitutionData, String> mOFXFIDataFIIDTableColumn;
    @FXML
    private TableColumn<FinancialInstitutionData, String> mOFXFIDataIDTableColumn;
    @FXML
    private TableColumn<FinancialInstitutionData, String> mOFXFIDataORGTableColumn;
    @FXML
    private TableColumn<FinancialInstitutionData, String> mOFXFIDataURLTableColumn;

    @FXML
    FilteredList<FinancialInstitutionData> mFilteredOFXFIDataList = null;

    @FXML
    private Button mEditButton;
    @FXML
    private Button mDeleteButton;
    @FXML
    private Button mImportButton;
    @FXML
    private TextField mFilterTextField;

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        // todo clean up here
        try {
            FilteredList<DirectConnection.FIData> filteredFIDataList =
                    new FilteredList<>(mMainApp.getFIDataList());
            SortedList<DirectConnection.FIData> sortedFIDataList = new SortedList<>(filteredFIDataList);
            sortedFIDataList.comparatorProperty().bind(mImportedFIDataTableView.comparatorProperty());
            mImportedFIDataTableView.setItems(sortedFIDataList);

            mFilteredOFXFIDataList = new FilteredList<>(FXCollections.observableArrayList(
                            (new LocalResourceFIDataStore()).getInstitutionDataList()));
            SortedList<FinancialInstitutionData> sortedOFXFIDataList = new SortedList<>(mFilteredOFXFIDataList);

            sortedOFXFIDataList.comparatorProperty().bind(mOFXFIDataTableView.comparatorProperty());
            mOFXFIDataTableView.setItems(sortedOFXFIDataList);
            /*for (FinancialInstitutionData fiData : sortedOFXFIDataList) {
                mLogger.info("Name = " + fiData.getName() + ", FIID = " + fiData.getFinancialInstitutionId()
                        + ", ID = " + fiData.getId());
            }*/
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    private void showEditFIDataDialog(DirectConnection.FIData fiData) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditFIDataDialog.fxml"));

            Stage stage = new Stage();
            stage.setTitle("Edit Financial Institution Data:");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mDialogStage);
            stage.setScene(new Scene(loader.load()));

            EditFIDataDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, fiData, stage);
            stage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            mMainApp.showExceptionDialog("IOException", "showEditFIDataDialog failed",
                    e.getMessage(), e);
        }
    }

    @FXML
    private void handleNew() {
        showEditFIDataDialog(new DirectConnection.FIData());
    }

    @FXML
    private void handleEdit() {
        showEditFIDataDialog(new DirectConnection.FIData(mImportedFIDataTableView.getSelectionModel().getSelectedItem()));
    }

    @FXML
    private void handleDelete() {
        // check usage
        DirectConnection.FIData fiData = mImportedFIDataTableView.getSelectionModel().getSelectedItem();
        int fiDataID = fiData.getID();
        FilteredList<DirectConnection> usageList = new FilteredList<>(mMainApp.getDCInfoList(),
                dc -> dc.getFIID() == fiDataID);
        if (usageList.size() > 0) {
            StringBuilder contentSB = new StringBuilder("It is used by following Direct Connection(s):\n");
            for (DirectConnection dc : usageList) {
                contentSB.append(" ").append(dc.getName()).append("\n");
            }
            contentSB.append("It can't be deleted.");
            MainApp.showWarningDialog("Warning", "Financial Institution can't be deleted.",
                    contentSB.toString());
            return;
        }
        try {
            mMainApp.deleteFIDataFromDB(fiDataID);
            mMainApp.initFIDataList();
        } catch (SQLException e) {
            mLogger.error(MainApp.SQLExceptionToString(e), e);
            mMainApp.showExceptionDialog("Exception", "Database Exception",
                    MainApp.SQLExceptionToString(e), e);
        }
    }

    @FXML
    private void handleImport() {
        FinancialInstitutionData fiData = mOFXFIDataTableView.getSelectionModel().getSelectedItem();
        System.out.println("Importing: " + fiData.getName() + " " + fiData.getId());
    }

    @FXML
    private void initialize() {
        mImportedFIDataNameTableColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        mImportedFIDataFIIDTableColumn.setCellValueFactory(cd -> cd.getValue().getFIIDProperty());
        mImportedFIDataFISubIDTableColumn.setCellValueFactory(cd -> cd.getValue().getSubIDProperty());
        mImportedFIDataORGTableColumn.setCellValueFactory(cd -> cd.getValue().getORGProperty());
        mImportedFIDataURLTableColumn.setCellValueFactory(cd -> cd.getValue().getURLProperty());

        mOFXFIDataNameTableColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getName()));
        mOFXFIDataFIIDTableColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getFinancialInstitutionId()));
        mOFXFIDataIDTableColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getId()));
        mOFXFIDataORGTableColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getOrganization()));
        mOFXFIDataURLTableColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getOFXURL().toString()));

        mEditButton.disableProperty().bind(mImportedFIDataTableView.getSelectionModel().selectedItemProperty().isNull());
        mDeleteButton.disableProperty().bind(mImportedFIDataTableView.getSelectionModel().selectedItemProperty().isNull());
        mImportButton.disableProperty().bind(mOFXFIDataTableView.getSelectionModel().selectedItemProperty().isNull());
        mFilterTextField.textProperty().addListener((obs, ov, nv) -> {
            if (mFilteredOFXFIDataList != null) {
                String nvLower = nv.toLowerCase();
                mFilteredOFXFIDataList.setPredicate(fiData -> nv.isEmpty()
                        || (fiData.getName() != null && fiData.getName().toLowerCase().contains(nvLower))
                        || (fiData.getFinancialInstitutionId() != null &&
                        fiData.getFinancialInstitutionId().toLowerCase().contains(nvLower))
                        || (fiData.getId() != null && fiData.getId().toLowerCase().contains(nvLower))
                        || (fiData.getOrganization() != null &&
                        fiData.getOrganization().toLowerCase().contains(nvLower))
                        || (fiData.getOFXURL() != null &&
                        fiData.getOFXURL().toString().toLowerCase().contains(nvLower)));
            }
        });
    }
}
