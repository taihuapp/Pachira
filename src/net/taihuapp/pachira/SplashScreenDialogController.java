/*
 * Copyright (C) 2017.  Guangliang He.  All Rights Reserved.
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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;

public class SplashScreenDialogController {
    private Stage mStage;
    private MainApp mMainApp;
    private boolean mFirstTime;

    @FXML
    private Label mApplicationNameLabel;
    @FXML
    private Label mApplicationVersionLabel;
    @FXML
    private TextArea mShortTextArea;
    @FXML
    private TextArea mGPLv3TextArea;
    @FXML
    private TextArea mThirdPartyTextArea;
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

        try {
            mShortTextArea.setText(readResourceTextFile2String("/Disclaimer"));
            mGPLv3TextArea.setText(readResourceTextFile2String("/COPYING"));
            mThirdPartyTextArea.setText(readResourceTextFile2String("/ThirdPartyLicense"));
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.err.println("Failed to read license file, probably corrupt installation, stop.");
            handleStop();
        }
    }

    @FXML
    private void handleContinue() {
        mMainApp.putAcknowledgeTimeStamp(LocalDateTime.now());
        mStage.close();
    }

    void handleClose() {
        if (mFirstTime)
            handleStop();
        else
            mStage.close();
    }

    @FXML
    void handleStop() {
        // this means user has NOT given a acknowledgement.  Stop everything
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void initialize() {
        mContinueButton.disableProperty().bind(mAgreeCheckBox.selectedProperty().not());
        mStopButton.disableProperty().bind(mAgreeCheckBox.selectedProperty());

        mApplicationNameLabel.setText(System.getProperty("Application.Name"));
        mApplicationVersionLabel.setText(System.getProperty("Application.Version"));
    }

    private String readResourceTextFile2String(String fileName) throws IOException {
        InputStream inputStream = getClass().getResourceAsStream(fileName);
        if (inputStream == null)
            throw new IOException("Unable to open resource file " + fileName);

        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = bufferedReader.readLine()) != null)
            stringBuilder.append(line).append("\n");

        return stringBuilder.toString();
    }
}