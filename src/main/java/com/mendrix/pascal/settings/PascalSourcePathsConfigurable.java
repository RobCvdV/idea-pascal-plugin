package com.mendrix.pascal.settings;

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
    private DefaultListModel<String> listModel;

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
        mainPanel = new JPanel(new BorderLayout());

        // Create list model and list
        listModel = new DefaultListModel<>();
        pathList = new JBList<>(listModel);
        pathList.setEmptyText("No source paths configured. Click + to add paths.");

        // Create toolbar with add/remove buttons
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(pathList)
                .setAddAction(new AnActionButtonRunnable() {
                    @Override
                    public void run(AnActionButton button) {
                        addPath();
                    }
                })
                .setRemoveAction(new AnActionButtonRunnable() {
                    @Override
                    public void run(AnActionButton button) {
                        removePath();
                    }
                })
                .disableUpDownActions();

        JPanel listPanel = decorator.createPanel();

        // Add description label
        JLabel descriptionLabel = new JLabel(
                "<html>Add directories containing Pascal source files (.pas, .dpr, .dpk).<br>" +
                "Types defined in these directories will be available for semantic highlighting.</html>"
        );
        descriptionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        mainPanel.add(descriptionLabel, BorderLayout.NORTH);
        mainPanel.add(listPanel, BorderLayout.CENTER);

        // Load current settings
        reset();

        return mainPanel;
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
                listModel.addElement(path);
            }
        }
    }

    private void removePath() {
        int selectedIndex = pathList.getSelectedIndex();
        if (selectedIndex >= 0) {
            listModel.remove(selectedIndex);
        }
    }

    private boolean containsPath(String path) {
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).equals(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isModified() {
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(project);
        List<String> currentPaths = settings.getSourcePaths();
        List<String> uiPaths = getPathsFromUI();
        return !currentPaths.equals(uiPaths);
    }

    @Override
    public void apply() throws ConfigurationException {
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(project);
        settings.setSourcePaths(getPathsFromUI());
    }

    @Override
    public void reset() {
        listModel.clear();
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(project);
        for (String path : settings.getSourcePaths()) {
            listModel.addElement(path);
        }
    }

    private List<String> getPathsFromUI() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            paths.add(listModel.get(i));
        }
        return paths;
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        pathList = null;
        listModel = null;
    }
}
