package com.mendrix.pascal.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.mendrix.pascal.stubs.PascalTypeStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PSI element interface for Pascal type definitions.
 * Represents: TMyClass = class, TMyRecord = record, IMyInterface = interface
 */
public interface PascalTypeDefinition extends PsiNameIdentifierOwner, StubBasedPsiElement<PascalTypeStub> {
    @Override
    @Nullable
    String getName();

    @NotNull
    TypeKind getTypeKind();

    /**
     * Get the list of generic type parameter names.
     */
    @NotNull
    List<String> getTypeParameters();
}
