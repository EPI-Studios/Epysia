package fr.epistudio.epysia.editor.selection;

@FunctionalInterface
public interface SelectionListener {
    void onSelectionChanged(Selection previous, Selection current);
}
