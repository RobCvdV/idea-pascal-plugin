package nl.akiar.pascal.project;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import nl.akiar.pascal.dpr.DprProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PascalProjectInitializationActivity implements ProjectActivity {
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // Safety check: ensure platform is fully loaded
        if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
            return Unit.INSTANCE;
        }

        if (project.isDisposed()) {
            return Unit.INSTANCE;
        }

        // Wait for smart mode before initializing services that use indexes
        DumbService.getInstance(project).runWhenSmart(() -> {
            if (!project.isDisposed()) {
                // Initialize services on pooled thread to avoid blocking EDT
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    if (!project.isDisposed()) {
                        PascalProjectService.getInstance(project).rescan();
                        DprProjectService.getInstance(project).rescan();
                    }
                });
            }
        });

        return Unit.INSTANCE;
    }
}
