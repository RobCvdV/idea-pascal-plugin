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
        // Iterate through all open projects to see if any project considers this file "active"
        // This is necessary because indexing is global but activity is per-project.
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed()) continue;
            PascalDependencyService dependencyService = PascalDependencyService.getInstance(project);
            if (dependencyService != null && dependencyService.isActive(file)) {
                return true;
            }
        }
        
        // If we can't find a project that considers it active, we might still want to index it if it's small?
        // No, let's be strict for now to save memory/CPU as requested.
        return false;
    }

    @Override
    public int getStubVersion() {
        return super.getStubVersion() + 1; // Increment version since we changed the filtering logic
    }
}
