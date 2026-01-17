package com.mendrix.pascal.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Project-level settings for Pascal source paths.
 * Stores a list of directories containing Pascal source files to index.
 */
@State(
    name = "PascalSourcePaths",
    storages = @Storage("pascalSourcePaths.xml")
)
@Service(Service.Level.PROJECT)
public final class PascalSourcePathsSettings implements PersistentStateComponent<PascalSourcePathsSettings.State> {

    /**
     * Persistent state containing the list of source paths.
     */
    public static class State {
        public List<String> sourcePaths = new ArrayList<>();
    }

    private State state = new State();

    /**
     * Get the settings instance for a project.
     */
    public static PascalSourcePathsSettings getInstance(@NotNull Project project) {
        return project.getService(PascalSourcePathsSettings.class);
    }

    /**
     * Get the list of configured source paths.
     */
    @NotNull
    public List<String> getSourcePaths() {
        return new ArrayList<>(state.sourcePaths);
    }

    /**
     * Set the list of source paths.
     */
    public void setSourcePaths(@NotNull List<String> paths) {
        state.sourcePaths = new ArrayList<>(paths);
    }

    /**
     * Add a source path to the list.
     */
    public void addSourcePath(@NotNull String path) {
        if (!state.sourcePaths.contains(path)) {
            state.sourcePaths.add(path);
        }
    }

    /**
     * Remove a source path from the list.
     */
    public void removeSourcePath(@NotNull String path) {
        state.sourcePaths.remove(path);
    }

    @Override
    @Nullable
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}
