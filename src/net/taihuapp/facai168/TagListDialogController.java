/*
 * Copyright (C) 2017.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of FaCai168.
 *
 * FaCai168 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * FaCai168 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.facai168;

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
    private MainApp mMainApp;
    private Stage mDialogStage;

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

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        mTagTableView.setItems(mainApp.getTagList());
        mTagNameColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        mTagDescriptionColumn.setCellValueFactory(cd -> cd.getValue().getDescriptionProperty());

        mEditButton.disableProperty().bind(mTagTableView.getSelectionModel().selectedItemProperty().isNull());
        mDeleteButton.disableProperty().bind(mTagTableView.getSelectionModel().selectedItemProperty().isNull());
    }

    void close() { mDialogStage.close(); }

    private void showEditTagDialog(Tag tag) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("EditTagDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Tag");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mDialogStage);
            dialogStage.setScene(new Scene(loader.load()));

            EditTagDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, tag, dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            mMainApp.showExceptionDialog("Exception", "IOException", "Edit Tag Dialog IO Exception", e);
        } catch (NullPointerException e) {
            mMainApp.showExceptionDialog("Exception", "Null Pointer Exception",
                    "Edit Tag Dialog Null Pointer Exception", e);
        }
    }

    @FXML
    private void handleNew() { showEditTagDialog(new Tag()); }

    @FXML
    private void handleEdit() {
        showEditTagDialog(new Tag(mTagTableView.getSelectionModel().getSelectedItem()));
    }

    @FXML
    private void handleDelete() {
        mMainApp.showExceptionDialog("Exception", "Action Not Implemented",
                "Delete tag action not implemented", null);
    }

    @FXML
    private void handleClose() { mDialogStage.close(); }
}
