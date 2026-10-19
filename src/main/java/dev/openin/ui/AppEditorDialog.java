package dev.openin.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import dev.openin.launch.ApplicationLauncher;
import dev.openin.model.AppEntry;
import dev.openin.model.LaunchTarget;
import dev.openin.model.LaunchType;
import dev.openin.model.TargetMode;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

final class AppEditorDialog extends DialogWrapper {
    private final AppEntry original;
    private final boolean builtin;
    private final JBTextField name = new JBTextField();
    private final JBCheckBox enabled = new JBCheckBox("Enabled in the Open In menu");
    private final ComboBox<LaunchType> mode = new ComboBox<>(new LaunchType[]{LaunchType.APPLICATION, LaunchType.COMMAND});
    private final JBTextField applicationPath = new JBTextField();
    private final JBTextField bundleId = new JBTextField();
    private final JBTextField executable = new JBTextField();
    private final JBTextArea arguments = new JBTextArea(5, 42);
    private final ComboBox<TargetMode> target;
    private final CardLayout locationLayout = new CardLayout();
    private final JPanel locationCards = new JPanel(locationLayout);
    private final JBLabel argumentHelp = new JBLabel();
    private final JBLabel testFeedback = new JBLabel();

    AppEditorDialog(Component parent, AppEntry entry, boolean adding) {
        super(parent, true);
        original = entry.copy();
        builtin = original.isBuiltin();
        target = new ComboBox<>(builtin
                ? new TargetMode[]{TargetMode.DIRECTORY, TargetMode.PROJECT_ROOT}
                : TargetMode.values());
        name.setText(original.name);
        enabled.setSelected(original.enabled);
        applicationPath.setText(original.applicationPath);
        bundleId.setText(original.bundleId);
        executable.setText(original.executable);
        String argumentText = String.join("\n", original.arguments);
        if (!original.arguments.isEmpty() && original.arguments.getLast().isEmpty()) argumentText += "\n";
        arguments.setText(argumentText);
        target.setSelectedItem(original.targetMode);
        mode.setRenderer(new ChoiceRenderer());
        target.setRenderer(new ChoiceRenderer());
        if (!builtin) mode.setSelectedItem(original.launchType);
        enabled.getAccessibleContext().setAccessibleName("Enabled in the Open In menu");
        arguments.getAccessibleContext().setAccessibleDescription("One argument per line. Spaces are preserved; do not add shell quotes. Blank lines pass empty arguments. A final newline ends a line; an empty field passes no arguments.");
        setTitle(adding ? "Add Open In Entry" : "Edit Open In Entry");
        setOKButtonText(adding ? "Add" : "Save");
        setResizable(true);
        init();
        mode.addActionListener(event -> updateMode());
        updateMode();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel form = new ScrollableForm();
        int row = 0;
        addField(form, row++, "Name:", name, 'N');
        addWide(form, row++, enabled);
        if (builtin) {
            addField(form, row++, "Launcher:", new JBLabel(launcherName(original.launchType)), 0);
        } else {
            addField(form, row++, "Mode:", mode, 'M');
        }

        JPanel application = new JPanel(new GridBagLayout());
        addField(application, 0, "Application:", pathControl(applicationPath, false), 'A', applicationPath);
        addField(application, 1, "Bundle identifier:", bundleId, 'B');
        addWide(application, 2, help("Choose an .app path or enter its bundle identifier. Missing applications can be saved."));
        if (builtin) {
            addWide(application, 3, help("Leave the location empty to use the built-in application's default location."));
        }
        JPanel command = new JPanel(new GridBagLayout());
        addField(command, 0, "Executable:", pathControl(executable, true), 'E', executable);
        addWide(command, 1, help("Use an absolute executable path. The command runs directly, without a shell."));
        locationCards.add(application, "application");
        locationCards.add(command, "command");
        addWide(form, row++, locationCards);
        addField(form, row++, "Target:", target, 'T');
        addWide(form, row++, help("Containing directory: selected folder or a selected file's parent.<br>Project root requires an open project."));
        if (builtin) {
            addWide(form, row++, help("Always opens a new terminal window; existing sessions are not reused."));
        } else {
            JBScrollPane argumentPane = new JBScrollPane(arguments);
            argumentPane.setMinimumSize(JBUI.size(200, 100));
            argumentPane.setPreferredSize(JBUI.size(420, 100));
            addField(form, row++, "Arguments (one per line):", argumentPane, 'R', arguments);
            argumentHelp.setAllowAutoWrapping(true);
            addWide(form, row++, argumentHelp);
        }
        JButton test = new JButton("Test with Directory…");
        test.setMnemonic('D');
        test.getAccessibleContext().setAccessibleDescription("Launch this unsaved configuration with a directory you choose.");
        test.addActionListener(event -> testDraft());
        addWide(form, row++, test);
        testFeedback.setAllowAutoWrapping(true);
        addWide(form, row++, testFeedback);
        GridBagConstraints filler = constraints(row);
        filler.gridwidth = 2;
        filler.weighty = 1;
        form.add(new JPanel(), filler);
        JBScrollPane scroll = new JBScrollPane(form, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setPreferredSize(JBUI.size(600, builtin ? 360 : 560));
        return scroll;
    }

    private JPanel pathControl(JBTextField field, boolean command) {
        JPanel panel = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        JButton browse = new JButton("Browse…");
        browse.getAccessibleContext().setAccessibleName(command ? "Browse for executable" : "Browse for application");
        browse.addActionListener(event -> {
            FileChooserDescriptor descriptor = (command ? FileChooserDescriptorFactory.singleFile()
                    : FileChooserDescriptorFactory.singleFileOrAppBundle())
                    .withTitle(command ? "Choose Executable" : "Choose macOS Application")
                    .withDescription(command ? "Choose the executable file to run directly."
                            : "Choose an application bundle ending in .app.");
            VirtualFile file = FileChooser.chooseFile(descriptor, panel, null, null);
            if (file != null) field.setText(file.getPath());
        });
        panel.add(field, BorderLayout.CENTER);
        panel.add(browse, BorderLayout.EAST);
        return panel;
    }

    private void updateMode() {
        boolean command = !builtin && mode.getSelectedItem() == LaunchType.COMMAND;
        locationLayout.show(locationCards, command ? "command" : "application");
        argumentHelp.setText("<html>" + (command
                ? "One exact argument per line; no shell quoting or expansion."
                : "The chosen target is opened by the application; optional arguments follow.")
                + "<br>{path}: path chosen by Target; {directory}: selected folder or file's parent."
                + "<br>{projectRoot}: actual project root; an absent root reports an error."
                + "<br>Spaces are preserved. Blank lines pass empty arguments."
                + "<br>A final newline ends a line; an empty field passes no arguments.</html>");
        locationCards.revalidate();
        locationCards.repaint();
    }

    AppEntry entry() {
        AppEntry result = original.copy();
        result.name = name.getText().trim();
        result.enabled = enabled.isSelected();
        result.launchType = builtin ? original.launchType : (LaunchType) mode.getSelectedItem();
        result.targetMode = (TargetMode) target.getSelectedItem();
        boolean command = result.launchType == LaunchType.COMMAND;
        result.applicationPath = command || applicationPath.getText().isBlank() ? "" : applicationPath.getText();
        result.bundleId = command ? "" : bundleId.getText().trim();
        result.executable = command ? executable.getText() : "";
        String text = arguments.getText();
        result.arguments = new ArrayList<>();
        if (!builtin && !text.isEmpty()) {
            String[] lines = text.split("\\R", -1);
            int count = lines.length;
            if (lines[count - 1].isEmpty()) count--;
            result.arguments.addAll(Arrays.asList(lines).subList(0, count));
        }
        return result;
    }

    @Override
    protected ValidationInfo doValidate() {
        EntryValidation.Problem problem = EntryValidation.validate(entry());
        return problem == null ? null : new ValidationInfo(problem.message(), componentFor(problem.field()));
    }

    private JComponent componentFor(String field) {
        return switch (field) {
            case "name" -> name;
            case "mode" -> mode;
            case "application" -> applicationPath;
            case "bundle" -> bundleId;
            case "executable" -> executable;
            case "arguments" -> arguments;
            case "target" -> target;
            default -> name;
        };
    }

    private void testDraft() {
        ValidationInfo problem = doValidate();
        if (problem != null) {
            setErrorText(problem.message, problem.component);
            problem.component.requestFocusInWindow();
            return;
        }
        setErrorText(null);
        AppEntry draft = entry();
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.singleDir()
                .withTitle("Choose Test Directory")
                .withDescription("Test uses this directory for the selected path, containing directory, and project root. It does not save changes.");
        VirtualFile file = FileChooser.chooseFile(descriptor, getContentPanel(), null, null);
        if (file == null) return;
        Path path = Path.of(file.getPath());
        ApplicationLauncher.launch(draft, new LaunchTarget(path, path, path), null);
        testFeedback.setText("Launch requested for " + draft.name + ". Any launch error appears in Open In notifications. Changes are not saved.");
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return name;
    }

    private static JBLabel help(String text) {
        return new JBLabel("<html>" + text + "</html>").setAllowAutoWrapping(true);
    }

    private static void addField(JPanel panel, int row, String title, JComponent component, int mnemonic) {
        addField(panel, row, title, component, mnemonic, component);
    }

    private static void addField(JPanel panel, int row, String title, JComponent component, int mnemonic, JComponent labelled) {
        JBLabel label = new JBLabel(title);
        label.setLabelFor(labelled);
        if (mnemonic != 0) label.setDisplayedMnemonic(mnemonic);
        labelled.getAccessibleContext().setAccessibleName(title.replace(":", ""));
        GridBagConstraints left = constraints(row);
        left.anchor = GridBagConstraints.NORTHWEST;
        panel.add(label, left);
        GridBagConstraints right = constraints(row);
        right.gridx = 1;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, right);
    }

    private static void addWide(JPanel panel, int row, JComponent component) {
        GridBagConstraints constraints = constraints(row);
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, constraints);
    }

    private static GridBagConstraints constraints(int row) {
        GridBagConstraints result = new GridBagConstraints();
        result.gridy = row;
        result.anchor = GridBagConstraints.WEST;
        result.insets = JBUI.insets(0, 0, 8, 8);
        return result;
    }

    private static String launcherName(LaunchType type) {
        return switch (type) {
            case TERMINAL -> "Terminal";
            case ITERM -> "iTerm2";
            case GHOSTTY -> "Ghostty";
            case APPLICATION -> "Application";
            case COMMAND -> "Command line";
        };
    }

    private static final class ScrollableForm extends JPanel implements Scrollable {
        private ScrollableForm() {
            super(new GridBagLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return JBUI.scale(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            int extent = orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
            return Math.max(JBUI.scale(16), extent - JBUI.scale(16));
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class ChoiceRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            String title = switch (value) {
                case LaunchType type -> launcherName(type);
                case TargetMode target -> switch (target) {
                    case SELECTED -> "Selected file or folder";
                    case DIRECTORY -> "Containing directory";
                    case PROJECT_ROOT -> "Project root";
                };
                case null -> "";
                default -> value.toString();
            };
            return super.getListCellRendererComponent(list, title, index, selected, focused);
        }
    }
}
