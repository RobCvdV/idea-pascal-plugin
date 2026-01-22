package nl.akiar.pascal.settings;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings page for configuring Pascal source paths.
 * Allows users to add directories containing Pascal source files
 * that should be indexed for type resolution.
 */
public class PascalSourcePathsConfigurable implements Configurable {

    private final Project project;
    private JPanel mainPanel;
    private JBList<String> pathList;
    private DefaultListModel<String> pathListModel;
    private JBList<String> scopeList;
    private DefaultListModel<String> scopeListModel;

    public PascalSourcePathsConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Pascal Source Paths";
    }

    @Override
    @Nullable
    public JComponent createComponent() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        // --- Source Paths Section ---
        gbc.gridy = 0;
        gbc.weighty = 0.0;
        JLabel pathLabel = new JLabel("<html><b>Pascal Source Paths</b><br>" +
                "Add directories containing Pascal source files (.pas, .dpr, .dpk).<br>" +
                "Types defined in these directories will be available for semantic highlighting.</html>");
        pathLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        mainPanel.add(pathLabel, gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.5;
        pathListModel = new DefaultListModel<>();
        pathList = new JBList<>(pathListModel);
        pathList.setEmptyText("No source paths configured. Click + to add paths.");
        
        JPanel pathPanel = ToolbarDecorator.createDecorator(pathList)
                .setAddAction(button -> addPath())
                .setRemoveAction(button -> removePath())
                .disableUpDownActions()
                .createPanel();
        mainPanel.add(pathPanel, gbc);

        // --- Unit Scope Names Section ---
        gbc.gridy = 2;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(10, 0, 0, 0);
        JLabel scopeLabel = new JLabel("<html><b>Unit Scope Names</b><br>" +
                "Delphi unit scope names (e.g., 'System'). If configured, 'SysUtils' in uses clause<br>" +
                "can be recognized as 'System.SysUtils'. Using short names is considered bad practice.</html>");
        scopeLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        mainPanel.add(scopeLabel, gbc);

        gbc.gridy = 3;
        gbc.weighty = 0.5;
        gbc.insets = new Insets(0, 0, 0, 0);
        scopeListModel = new DefaultListModel<>();
        scopeList = new JBList<>(scopeListModel);
        scopeList.setEmptyText("No unit scope names configured. Click + to add scopes.");

        JPanel scopePanel = ToolbarDecorator.createDecorator(scopeList)
                .setAddAction(button -> addScope())
                .setRemoveAction(button -> removeScope())
                .disableUpDownActions()
                .createPanel();
        mainPanel.add(scopePanel, gbc);

        reset();
        return mainPanel;
    }

    private void addScope() {
        String scope = JOptionPane.showInputDialog(mainPanel, "Enter unit scope name (e.g. System):", "Add Unit Scope", JOptionPane.PLAIN_MESSAGE);
        if (scope != null && !scope.trim().isEmpty()) {
            scope = scope.trim();
            if (!containsScope(scope)) {
                scopeListModel.addElement(scope);
            }
        }
    }

    private void removeScope() {
        int selectedIndex = scopeList.getSelectedIndex();
        if (selectedIndex >= 0) {
            scopeListModel.remove(selectedIndex);
        }
    }

    private boolean containsScope(String scope) {
        for (int i = 0; i < scopeListModel.size(); i++) {
            if (scopeListModel.get(i).equalsIgnoreCase(scope)) {
                return true;
            }
        }
        return false;
    }

    private void addPath() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(
                false,  // chooseFiles
                true,   // chooseFolders
                false,  // chooseJars
                false,  // chooseJarsAsFiles
                false,  // chooseJarContents
                true    // chooseMultiple
        );
        descriptor.setTitle("Select Pascal Source Directory");
        descriptor.setDescription("Select a directory containing Pascal source files");

        VirtualFile[] files = FileChooser.chooseFiles(descriptor, project, null);
        for (VirtualFile file : files) {
            String path = file.getPath();
            if (!containsPath(path)) {
                pathListModel.addElement(path);
            }
        }
    }

    private void removePath() {
        int selectedIndex = pathList.getSelectedIndex();
        if (selectedIndex >= 0) {
            pathListModel.remove(selectedIndex);
        }
    }

    private boolean containsPath(String path) {
        for (int i = 0; i < pathListModel.size(); i++) {
            if (pathListModel.get(i).equals(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isModified() {
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(project);
        return !settings.getSourcePaths().equals(getPathsFromUI()) ||
               !settings.getUnitScopeNames().equals(getScopesFromUI());
    }

    @Override
    public void apply() throws ConfigurationException {
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(project);
        List<String> newPaths = getPathsFromUI();
        List<String> newScopes = getScopesFromUI();
        settings.setSourcePaths(newPaths);
        settings.setUnitScopeNames(newScopes);
    }

    @Override
    public void reset() {
        pathListModel.clear();
        scopeListModel.clear();
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(project);
        for (String path : settings.getSourcePaths()) {
            pathListModel.addElement(path);
        }
        for (String scope : settings.getUnitScopeNames()) {
            scopeListModel.addElement(scope);
        }
    }

    private List<String> getPathsFromUI() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < pathListModel.size(); i++) {
            paths.add(pathListModel.get(i));
        }
        return paths;
    }

    private List<String> getScopesFromUI() {
        List<String> scopes = new ArrayList<>();
        for (int i = 0; i < scopeListModel.size(); i++) {
            scopes.add(scopeListModel.get(i));
        }
        return scopes;
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        pathList = null;
        pathListModel = null;
        scopeList = null;
        scopeListModel = null;
    }
}
