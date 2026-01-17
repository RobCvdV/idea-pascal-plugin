package com.mendrix.pascal.stubs;

import com.intellij.psi.stubs.StubElement;
import com.mendrix.pascal.psi.PascalTypeDefinition;
import com.mendrix.pascal.psi.TypeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stub interface for Pascal type definitions.
 * Stores the minimal information needed for indexing without parsing the full PSI.
 */
public interface PascalTypeStub extends StubElement<PascalTypeDefinition> {
    /**
     * Get the base name of the type (without generic parameters).
     * For "TList<T>", returns "TList".
     */
    @Nullable
    String getName();

    @NotNull
    TypeKind getTypeKind();

    /**
     * Get the list of generic type parameter names.
     * For "TList<T>", returns ["T"].
     * For "TDictionary<TKey, TValue>", returns ["TKey", "TValue"].
     * For non-generic types, returns an empty list.
     */
    @NotNull
    List<String> getTypeParameters();
}
