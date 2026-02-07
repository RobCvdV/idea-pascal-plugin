package nl.akiar.pascal;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.tree.IStubFileElementType;
import nl.akiar.pascal.project.PascalDependencyService;
import org.jetbrains.annotations.NotNull;

public class PascalStubFileElementType extends IStubFileElementType<PsiFileStub<PascalFile>> {
    public PascalStubFileElementType() {
        super("PASCAL_FILE", PascalLanguage.INSTANCE);
    }

    @Override
    public boolean shouldBuildStubFor(@NotNull VirtualFile file) {
        // To avoid "Stub index points to a file without indexed stub tree" errors,
        // we must return true for all Pascal files that can appear in stub indexes.
        // Keep an opt-in flag to restore gating if needed.
        if (Boolean.getBoolean("pascal.stubs.gate")) {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()) continue;
                PascalDependencyService dependencyService = PascalDependencyService.getInstance(project);
                if (dependencyService != null && dependencyService.isActive(file)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public int getStubVersion() {
        // Bump to force reindex after adding visibility/section/ownerTypeName fields (Milestone A)
        return super.getStubVersion() + 7;
    }
}
