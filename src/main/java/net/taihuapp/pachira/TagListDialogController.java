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

public class TagListDialogController {

    private MainModel mainModel;

    @FXML
    private TableView<Tag> mTagTableView;
    @FXML
    private TableColumn<Tag, String> mTagNameColumn;
    @FXML
    private TableColumn<Tag, String> mTagDescriptionColumn;
    @FXML
    private Button mEditButton;
    @FXML
    private Button mDeleteButton;

    void setMainModel(MainModel mainModel) {
        this.mainModel = mainModel;
        mTagTableView.setItems(mainModel.getTagList());
    }

    void close() {
        ((Stage) mTagTableView.getScene().getWindow()).close();
    }

    private void showEditTagDialog(Tag tag) {
        Stage stage = (Stage) mTagTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditTagDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Tag");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));

            EditTagDialogController controller = loader.getController();
            controller.setMainModel(mainModel, tag);
            dialogStage.showAndWait();
        } catch (IOException e) {
            MainApp.showExceptionDialog(stage, "Exception", "IOException", "Edit Tag Dialog IO Exception", e);
        } catch (NullPointerException e) {
            MainApp.showExceptionDialog(stage, "Exception", "Null Pointer Exception",
                    "Edit Tag Dialog Null Pointer Exception", e);
        }
    }

    @FXML
    private void handleNew() { showEditTagDialog(new Tag()); }

    @FXML
    private void handleEdit() {
        // edit a copy of selectedItem
        showEditTagDialog(new Tag(mTagTableView.getSelectionModel().getSelectedItem()));
    }

    @FXML
    private void handleDelete() {
        Stage stage = (Stage) mTagTableView.getScene().getWindow();
        MainApp.showExceptionDialog(stage, "Exception", "Action Not Implemented",
                "Delete tag action not implemented", null);
    }

    @FXML
    private void handleClose() { close(); }

    @FXML
    private void initialize() {
        mTagNameColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        mTagDescriptionColumn.setCellValueFactory(cd -> cd.getValue().getDescriptionProperty());

        mEditButton.disableProperty().bind(mTagTableView.getSelectionModel().selectedItemProperty().isNull());
        mDeleteButton.disableProperty().bind(mTagTableView.getSelectionModel().selectedItemProperty().isNull());
    }
}
