package dev.openin.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import dev.openin.launch.ApplicationLauncher;
import dev.openin.model.AppEntry;
import dev.openin.model.LaunchTarget;
import dev.openin.model.LaunchType;
import dev.openin.settings.OpenInSettingsService;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Edits independent copies; only Apply publishes configuration to the service. */
public final class OpenInConfigurable implements Configurable {
    private final List<AppEntry> drafts = new ArrayList<>();
    private List<AppEntry> saved = List.of();
    private JPanel panel;
    private JBTable table;
    private EntriesTableModel model;
    private JButton edit;
    private JButton delete;
    private JButton moveUp;
    private JButton moveDown;
    private JButton refresh;
    private JButton test;
    private JBLabel feedback;
    private boolean checking;
    private long refreshGeneration;

    public OpenInConfigurable() {}

    @Override
    public String getDisplayName() {
        return "Open In";
    }

    @Override
    public JComponent createComponent() {
        if (panel != null) return panel;
        panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        model = new EntriesTableModel();
        table = new JBTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                String text = column == 0 ? "Enable " + drafts.get(row).name : String.valueOf(getValueAt(row, column));
                if (component instanceof JComponent cell) {
                    cell.setToolTipText(text);
                    cell.getAccessibleContext().setAccessibleName(text);
                }
                return component;
            }

            @Override
            public Component prepareEditor(TableCellEditor editor, int row, int column) {
                Component component = super.prepareEditor(editor, row, column);
                if (column == 0 && component instanceof JComponent cell) {
                    cell.getAccessibleContext().setAccessibleName("Enable " + drafts.get(row).name);
                }
                return component;
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowGrid(false);
        table.setVisibleRowCount(9);
        table.setDefaultEditor(Boolean.class, JBTable.createBooleanEditor());
        JBTable.setupCheckboxShortcut(table, 0);
        table.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(72));
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(90));
        table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(180));
        table.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(90));
        table.getColumnModel().getColumn(3).setPreferredWidth(JBUI.scale(300));
        table.getAccessibleContext().setAccessibleName("Open In applications");
        table.getAccessibleContext().setAccessibleDescription("Entries appear in menu order. Toggle Enabled independently. Select a row to edit, delete a user entry, move it, or test it.");
        table.getEmptyText().setText("Add an application or command to show it in Open In.");
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && table.rowAtPoint(event.getPoint()) >= 0
                        && table.columnAtPoint(event.getPoint()) > 0) editEntry();
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openin.edit");
        table.getActionMap().put("openin.edit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                editEntry();
            }
        });
        JBLabel explanation = new JBLabel("Enabled entries appear in Project view → Open In, in the order below. Built-in entries can be edited or disabled, but not deleted.")
                .setAllowAutoWrapping(true);
        explanation.setLabelFor(table);
        panel.add(explanation, BorderLayout.NORTH);
        panel.add(new JBScrollPane(table), BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        JPanel entryButtons = new JPanel(new FlowLayout(FlowLayout.LEADING, JBUI.scale(6), 0));
        entryButtons.add(button("Add…", 'A', "Add a macOS application or command-line entry", this::addEntry));
        edit = button("Edit…", 'E', "Edit the selected entry without saving other settings", this::editEntry);
        delete = button("Delete", 'D', "Delete the selected user entry; built-in entries cannot be deleted", this::deleteEntry);
        moveUp = button("Move Up", 'U', "Move the selected entry earlier in the Open In menu", () -> moveEntry(-1));
        moveDown = button("Move Down", 'W', "Move the selected entry later in the Open In menu", () -> moveEntry(1));
        entryButtons.add(edit);
        entryButtons.add(delete);
        entryButtons.add(moveUp);
        entryButtons.add(moveDown);
        controls.add(entryButtons, BorderLayout.NORTH);
        JPanel statusButtons = new JPanel(new FlowLayout(FlowLayout.LEADING, JBUI.scale(6), 0));
        refresh = button("Refresh Installed Status", 'R', "Check application and executable availability in the background", this::refreshStatus);
        test = button("Test with Directory…", 'T', "Launch the selected draft with a directory you choose; this does not save changes", this::testEntry);
        statusButtons.add(refresh);
        statusButtons.add(test);
        controls.add(statusButtons, BorderLayout.CENTER);
        feedback = new JBLabel().setAllowAutoWrapping(true);
        feedback.getAccessibleContext().setAccessibleName("Open In settings status");
        controls.add(feedback, BorderLayout.SOUTH);
        panel.add(controls, BorderLayout.SOUTH);
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        commitTableEdit();
        return !drafts.equals(saved);
    }

    @Override
    public void apply() throws ConfigurationException {
        commitTableEdit();
        for (int row = 0; row < drafts.size(); row++) {
            EntryValidation.Problem problem = EntryValidation.validate(drafts.get(row));
            if (problem != null) {
                select(row);
                String message = "Entry “" + drafts.get(row).name + "”: " + problem.message() + " Select Edit to correct it.";
                if (feedback != null) feedback.setText(message);
                throw new ConfigurationException(message, "Invalid Open In Entry");
            }
        }
        OpenInSettingsService service = OpenInSettingsService.getInstance();
        service.replaceEntries(copyEntries(drafts));
        saved = service.entries();
        drafts.clear();
        drafts.addAll(copyEntries(saved));
        if (model != null) {
            int selected = table.getSelectedRow();
            model.fireTableDataChanged();
            select(Math.min(selected, drafts.size() - 1));
            feedback.setText("Settings saved. Menu changes take effect immediately; no restart is required.");
            refreshStatus();
        }
    }

    @Override
    public void reset() {
        if (table != null && table.isEditing()) table.getCellEditor().cancelCellEditing();
        saved = OpenInSettingsService.getInstance().entries();
        drafts.clear();
        drafts.addAll(copyEntries(saved));
        if (model != null) {
            model.fireTableDataChanged();
            select(drafts.isEmpty() ? -1 : 0);
            feedback.setText("Missing applications may remain enabled and saved. Refresh checks installation; Test launches without saving.");
            refreshStatus();
        }
    }

    @Override
    public void disposeUIResources() {
        refreshGeneration++;
        checking = false;
        panel = null;
        table = null;
        model = null;
        edit = null;
        delete = null;
        moveUp = null;
        moveDown = null;
        refresh = null;
        test = null;
        feedback = null;
        drafts.clear();
        saved = List.of();
    }

    private void addEntry() {
        commitTableEdit();
        AppEntry entry = new AppEntry();
        entry.id = "user." + UUID.randomUUID();
        AppEditorDialog dialog = new AppEditorDialog(panel, entry, true);
        if (!dialog.showAndGet()) return;
        drafts.add(dialog.entry());
        model.fireTableRowsInserted(drafts.size() - 1, drafts.size() - 1);
        select(drafts.size() - 1);
        feedback.setText("Entry added to the draft. Select Apply or OK to save it.");
        refreshStatus();
    }

    private void editEntry() {
        commitTableEdit();
        int selected = table.getSelectedRow();
        if (selected < 0) return;
        AppEditorDialog dialog = new AppEditorDialog(panel, drafts.get(selected), false);
        if (!dialog.showAndGet()) return;
        drafts.set(selected, dialog.entry());
        model.fireTableRowsUpdated(selected, selected);
        select(selected);
        feedback.setText("Entry updated in the draft. Select Apply or OK to save it.");
        refreshStatus();
    }

    private void deleteEntry() {
        commitTableEdit();
        int selected = table.getSelectedRow();
        if (selected < 0 || drafts.get(selected).isBuiltin()) return;
        drafts.remove(selected);
        model.fireTableRowsDeleted(selected, selected);
        select(Math.min(selected, drafts.size() - 1));
        feedback.setText("Entry deleted from the draft. Select Apply or OK to save, or Reset to restore saved entries.");
    }

    private void moveEntry(int direction) {
        commitTableEdit();
        int selected = table.getSelectedRow();
        int destination = selected + direction;
        if (selected < 0 || destination < 0 || destination >= drafts.size()) return;
        Collections.swap(drafts, selected, destination);
        model.fireTableRowsUpdated(Math.min(selected, destination), Math.max(selected, destination));
        select(destination);
        feedback.setText("Menu order updated in the draft. Select Apply or OK to save it.");
    }

    private void testEntry() {
        commitTableEdit();
        int selected = table.getSelectedRow();
        if (selected < 0) return;
        AppEntry entry = drafts.get(selected).copy();
        EntryValidation.Problem problem = EntryValidation.validate(entry);
        if (problem != null) {
            Messages.showErrorDialog(panel, problem.message() + " Select Edit to correct the entry.", "Cannot Test Open In Entry");
            return;
        }
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.singleDir()
                .withTitle("Choose Test Directory")
                .withDescription("This directory is used as the selected path, containing directory, and project root. Unsaved settings are used.");
        VirtualFile file = FileChooser.chooseFile(descriptor, panel, null, null);
        if (file == null) return;
        Path path = Path.of(file.getPath());
        ApplicationLauncher.launch(entry, new LaunchTarget(path, path, path), null);
        feedback.setText("Launch requested for " + entry.name + ". Any launch error appears in Open In notifications. Changes are not saved.");
    }

    private void refreshStatus() {
        if (model == null) return;
        long generation = ++refreshGeneration;
        checking = true;
        refresh.setText("Checking Installed Status…");
        updateButtons();
        model.fireTableRowsUpdated(0, Math.max(0, drafts.size() - 1));
        ApplicationLauncher.refreshAvailability(copyEntries(drafts), () -> {
            if (model == null || generation != refreshGeneration) return;
            checking = false;
            refresh.setText("Refresh Installed Status");
            model.fireTableRowsUpdated(0, Math.max(0, drafts.size() - 1));
            updateButtons();
        });
    }

    private void updateButtons() {
        if (table == null || edit == null) return;
        int selected = table.getSelectedRow();
        boolean hasSelection = selected >= 0 && selected < drafts.size();
        edit.setEnabled(hasSelection);
        delete.setEnabled(hasSelection && !drafts.get(selected).isBuiltin());
        moveUp.setEnabled(hasSelection && selected > 0);
        moveDown.setEnabled(hasSelection && selected < drafts.size() - 1);
        test.setEnabled(hasSelection);
        refresh.setEnabled(!checking);
    }

    private void select(int row) {
        if (table == null) return;
        if (row < 0 || row >= drafts.size()) table.clearSelection();
        else {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
        updateButtons();
    }

    private void commitTableEdit() {
        if (table != null && table.isEditing()) table.getCellEditor().stopCellEditing();
    }

    private static JButton button(String title, int mnemonic, String description, Runnable action) {
        JButton button = new JButton(title);
        button.setMnemonic(mnemonic);
        button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleDescription(description);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static List<AppEntry> copyEntries(List<AppEntry> entries) {
        return entries.stream().map(AppEntry::copy).toList();
    }

    private final class EntriesTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Enabled", "Name", "Source", "Installed status"};

        @Override
        public int getRowCount() {
            return drafts.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Object getValueAt(int row, int column) {
            AppEntry entry = drafts.get(row);
            return switch (column) {
                case 0 -> entry.enabled;
                case 1 -> entry.name;
                case 2 -> entry.isBuiltin() ? "Built-in" : "User";
                case 3 -> {
                    if (checking) yield "Checking…";
                    String status = ApplicationLauncher.availability(entry);
                    yield status.isEmpty() ? (entry.launchType == LaunchType.COMMAND ? "Available" : "Installed") : status;
                }
                default -> throw new IndexOutOfBoundsException("Unknown table column " + column);
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 0 || !(value instanceof Boolean enabled)) return;
            drafts.get(row).enabled = enabled;
            fireTableCellUpdated(row, column);
            feedback.setText("Enabled state changed in the draft. Select Apply or OK to update the menu.");
        }
    }
}
