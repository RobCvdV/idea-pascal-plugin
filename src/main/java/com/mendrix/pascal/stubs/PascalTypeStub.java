package com.mendrix.pascal.stubs;

import com.intellij.psi.stubs.StubElement;
import com.mendrix.pascal.psi.PascalTypeDefinition;
import com.mendrix.pascal.psi.TypeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stub interface for Pascal type definitions.
 * Stores the minimal information needed for indexing without parsing the full PSI.
 */
public interface PascalTypeStub extends StubElement<PascalTypeDefinition> {
    @Nullable
    String getName();

    @NotNull
    TypeKind getTypeKind();
}
