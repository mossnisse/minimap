package app;

import gis.layers.EditableTableLayer;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Resolves all unsaved work with one Save / Discard / Cancel decision. */
public final class CloseCoordinator {
    private CloseCoordinator() {}

    public static boolean resolve(Component parent,
                                  Collection<ProjectCloseParticipant> participants,
                                  Collection<EditableTableLayer> editableLayers) {
        List<ProjectCloseParticipant> dirtyParticipants = participants.stream()
                .filter(ProjectCloseParticipant::isDirty).toList();
        List<EditableTableLayer> dirtyLayers = editableLayers.stream()
                .filter(EditableTableLayer::isDirty).toList();

        if (!dirtyParticipants.isEmpty() || !dirtyLayers.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (ProjectCloseParticipant p : dirtyParticipants) names.add(p.description());
            for (EditableTableLayer layer : dirtyLayers) names.add(layer.getName());
            Object[] options = {"Save", "Discard", "Cancel"};
            int choice = JOptionPane.showOptionDialog(parent,
                    "There is unsaved work in:\n• " + String.join("\n• ", names),
                    "Unsaved work", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            if (choice != 0 && choice != 1) return false;

            if (choice == 0) {
                for (ProjectCloseParticipant p : dirtyParticipants) {
                    if (!p.save()) return false;
                }
                for (EditableTableLayer layer : dirtyLayers) {
                    try {
                        layer.save();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(parent,
                                "Could not save " + layer.getName() + ":\n" + e.getMessage(),
                                "Save failed", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                }
            } else {
                for (ProjectCloseParticipant p : dirtyParticipants) p.discard();
            }
        }

        for (ProjectCloseParticipant p : participants) p.close();
        for (EditableTableLayer layer : editableLayers) {
            Window editor = layer.getOpenEditor();
            if (editor != null) editor.dispose();
        }
        return true;
    }
}
