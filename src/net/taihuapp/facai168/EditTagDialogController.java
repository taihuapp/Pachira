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
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class EditTagDialogController {
    private MainApp mMainApp;
    private Tag mTag;
    private Stage mDialogStage;

    @FXML
    private TextField mNameTextField;
    @FXML
    private TextField mDescriptionTextField;

    void setMainApp(MainApp mainApp, Tag tag, Stage stage) {
        mMainApp = mainApp;
        mTag = tag;
        mDialogStage = stage;

        mNameTextField.textProperty().bindBidirectional(mTag.getNameProperty());
        mDescriptionTextField.textProperty().bindBidirectional(mTag.getDescriptionProperty());
    }

    @FXML
    private void handleSave() {
        if (mMainApp.insertUpdateTagToDB(mTag)) {
            mMainApp.initTagList();
            mDialogStage.close();
        }
    }

    @FXML
    private void handleCancel() { mDialogStage.close(); }
}
