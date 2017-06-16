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

/**
 *
 * Created by ghe on 6/15/17.
 */
public class CategoryListDialogController {
    private MainApp mMainApp;
    private Stage mDialogStage;

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

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        mCategoryTableView.setItems(mainApp.getCategoryList());
        mCategoryNameColumn.setCellValueFactory(cd -> cd.getValue().getNameProperty());
        mCategoryDescriptionColumn.setCellValueFactory(cd -> cd.getValue().getDescriptionProperty());

        mEditButton.disableProperty().bind(mCategoryTableView.getSelectionModel().selectedItemProperty().isNull());
        mDeleteButton.disableProperty().bind(mCategoryTableView.getSelectionModel().selectedItemProperty().isNull());
    }

    void close() { mDialogStage.close(); }

    private void showEditCategoryDialog(Category category) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("EditCategoryDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Category");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mDialogStage);
            dialogStage.setScene(new Scene(loader.load()));

            EditCategoryDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, category, dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            mMainApp.showExceptionDialog("Exception", "IOException", "Edit Category Dialog IO Exception", e);
        } catch (NullPointerException e) {
            mMainApp.showExceptionDialog("Exception", "Null Pointer Exception",
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
        mMainApp.showExceptionDialog("Exception", "Action Not Implemented",
                "Delete category action not implemented", null);
    }

    @FXML
    private void handleClose() { mDialogStage.close(); }
}
