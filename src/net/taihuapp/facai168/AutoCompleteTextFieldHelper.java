package net.taihuapp.facai168;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;

import java.util.Collection;

/**
 * Created by ghe on 4/26/17.
 *
 */
public class AutoCompleteTextFieldHelper {
    private final TextField mTextField;
    private final FilteredList<MenuItem> mFilteredItems;
    private boolean mKeyEvent;

    AutoCompleteTextFieldHelper(final TextField textField, Collection<String> items) {
        mTextField = textField;
        ObservableList<MenuItem> allItems = FXCollections.observableArrayList();
        for (String s : items) {
            MenuItem mi = new MenuItem(s);
            mi.setOnAction(eh -> mTextField.setText(s));
            allItems.add(mi);
        }
        mFilteredItems = new FilteredList<>(allItems);
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().setAll(mFilteredItems);
        mTextField.setContextMenu(contextMenu);

        mTextField.setOnKeyPressed(e -> mKeyEvent = true);
        mTextField.setOnKeyReleased(e -> mKeyEvent = false);
        mTextField.textProperty().addListener((obs, ov, nv) -> {
            if (!mKeyEvent)
                return; // if change is not due to keyevent, do nothing

            if (nv == null || nv.isEmpty()) {
                mFilteredItems.setPredicate(item -> true);
                return;
            }

            mFilteredItems.setPredicate(item -> item.getText().toLowerCase().contains(nv.toLowerCase()));
            mTextField.getContextMenu().getItems().setAll(mFilteredItems);
        });
    }
}
