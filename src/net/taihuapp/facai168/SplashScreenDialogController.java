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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;

public class SplashScreenDialogController {
    private Stage mStage;
    private MainApp mMainApp;
    private boolean mFirstTime;

    @FXML
    private TextArea mShortTextArea;
    @FXML
    private TextArea mGPLv3TextArea;
    @FXML
    private CheckBox mAgreeCheckBox;
    @FXML
    private Button mContinueButton;
    @FXML
    private Button mStopButton;

    void setMainApp(MainApp mainApp, Stage stage, boolean firstTime) {
        mMainApp = mainApp;
        mStage = stage;
        mFirstTime = firstTime;

        if (!mFirstTime) {
            mAgreeCheckBox.setSelected(true);
            mAgreeCheckBox.setVisible(false);
            mStopButton.setVisible(false);
        }
    }

    @FXML
    private void handleContinue() {
        mMainApp.putAcknowledgeTimeStamp(LocalDateTime.now());
        mStage.close();
    }

    @FXML
    void handleStop() {
        if (mFirstTime) {
            // this means user has NOT given a acknowledgement.  Stop everything
            Platform.exit();
            System.exit(0);
        }

        // user has acknowledged already, simply close the window
        mStage.close();
    }

    @FXML
    private void initialize() {
        // this two paragraphs are copied from GPL.
        final String shortText = System.getProperty("Application.Name")
                + " Copyright (C) 2017  Guangliang He\n"
                + "\n"
                + "This program is free software: you can redistribute it and/or modify\n"
                + "it under the terms of the GNU General Public License as published by\n"
                + "the Free Software Foundation, either version 3 of the License, or\n"
                + "any later version.\n"
                + "\n"
                + "This program is distributed in the hope that it will be useful,\n"
                + "but WITHOUT ANY WARRANTY; without even the implied warranty of\n"
                + "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n"
                + "GNU General Public License below for more details.";
        mShortTextArea.setText(shortText);

        InputStream gplv3Stream = getClass().getResourceAsStream("/COPYING");
        if (gplv3Stream != null) {
            StringBuilder inputStringBuilder = new StringBuilder();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gplv3Stream));
            try {
                String line = bufferedReader.readLine();
                while (line != null) {
                    inputStringBuilder.append(line).append('\n');
                    line = bufferedReader.readLine();
                }
            } catch (Exception e) {
                System.out.println("exception");
            }
            mGPLv3TextArea.setText(inputStringBuilder.toString());
            mContinueButton.disableProperty().bind(mAgreeCheckBox.selectedProperty().not());
            mStopButton.disableProperty().bind(mAgreeCheckBox.selectedProperty());
        }
    }
}
