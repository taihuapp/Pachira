/*
 * Copyright (C) 2018-2021.  Guangliang He.  All Rights Reserved.
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

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class CategoryListDialogController {

    private MainModel mainModel;

    @FXML
    private TableView<Category> mCategoryTableView;
    @FXML
    private TableColumn<Category, String> mCategoryNameColumn;
    @FXML
    private TableColumn<Category, String> mCategoryDescriptionColumn;
    @FXML
    private Button mEditButton;
    @FXML
    private Button mDeleteButton;

    void setMainModel(MainModel mainModel) {
        this.mainModel = mainModel;
        mCategoryTableView.setItems(mainModel.getCategoryList());
    }

    void close() { ((Stage) mCategoryTableView.getScene().getWindow()).close(); }

    private void showEditCategoryDialog(Category category) {
        Stage stage = (Stage) mCategoryTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditCategoryDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Category");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));

            EditCategoryDialogController controller = loader.getController();
            controller.setMainModel(mainModel, category);
            dialogStage.showAndWait();
        } catch (IOException e) {
            MainApp.showExceptionDialog(stage,"Exception", "IOException", "Edit Category Dialog IO Exception", e);
        } catch (NullPointerException e) {
            MainApp.showExceptionDialog(stage,"Exception", "Null Pointer Exception",
                    "Edit Category Dialog Null Pointer Exception", e);
        }
    }

    @FXML
    private void handleNew() { showEditCategoryDialog(new Category()); }

    @FXML
    private void handleEdit() {
        showEditCategoryDialog(new Category(mCategoryTableView.getSelectionModel().getSelectedItem()));
    }

    @FXML
    private void handleDelete() {
        Stage stage = (Stage) mCategoryTableView.getScene().getWindow();
        DialogUtil.showExceptionDialog(stage,"Exception", "Action Not Implemented",
                "Delete category action not implemented", null);
    }

    @FXML
    private void handleClose() { close(); }

    @FXML
    private void initialize() {
        mCategoryNameColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        mCategoryDescriptionColumn.setCellValueFactory(cd -> cd.getValue().getDescriptionProperty());

        mEditButton.disableProperty().bind(mCategoryTableView.getSelectionModel().selectedItemProperty().isNull());
        mDeleteButton.disableProperty().bind(mCategoryTableView.getSelectionModel().selectedItemProperty().isNull());
    }
}
