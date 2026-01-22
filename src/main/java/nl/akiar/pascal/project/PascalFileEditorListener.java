package nl.akiar.pascal.project;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class PascalFileEditorListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if ("pas".equalsIgnoreCase(file.getExtension())) {
            PascalDependencyService dependencyService = PascalDependencyService.getInstance(source.getProject());
            if (dependencyService != null) {
                dependencyService.markActive(file);
            }
        }
    }
}
