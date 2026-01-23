package nl.akiar.pascal.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import nl.akiar.pascal.dpr.DprProjectService;
import nl.akiar.pascal.reference.PascalReferenceContributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PascalProjectInitializationActivity implements ProjectActivity {
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // Initialize services
        PascalProjectService.getInstance(project).rescan();
        DprProjectService.getInstance(project).rescan();
        
        return Unit.INSTANCE;
    }
}
