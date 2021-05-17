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
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import net.taihuapp.pachira.dao.DaoException;

public class EditTagDialogController {
    private MainModel mainModel;
    private Tag tag;

    @FXML
    private TextField nameTextField;
    @FXML
    private TextField descriptionTextField;

    void setMainModel(MainModel mainModel, Tag tag) {
        this.mainModel = mainModel;
        this.tag = tag;
        nameTextField.textProperty().bindBidirectional(this.tag.getNameProperty());
        descriptionTextField.textProperty().bindBidirectional(this.tag.getDescriptionProperty());
    }

    @FXML
    private void handleSave() {
        try {
            if (tag.getID() <= 0)
                mainModel.insertTag(tag);
            else
                mainModel.updateTag(tag);

            ((Stage) nameTextField.getScene().getWindow()).close();
        } catch (DaoException e) {
            Stage stage = (Stage) nameTextField.getScene().getWindow();
            DialogUtil.showExceptionDialog(stage, "Database Error", "Saving Tag error: " + e.getErrorCode(),
                    "", e);
        }
    }

    @FXML
    private void handleCancel() {
        ((Stage) nameTextField.getScene().getWindow()).close();
    }
}
