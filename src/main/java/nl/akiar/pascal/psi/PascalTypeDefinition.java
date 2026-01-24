package nl.akiar.pascal.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import nl.akiar.pascal.stubs.PascalTypeStub;
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

    /**
     * Get the documentation comment preceding this type definition.
     * Returns null if no doc comment is found.
     */
    @Nullable
    String getDocComment();

    /**
     * Get the full header of the type declaration (e.g. "TMyClass = class(TObject)").
     */
    @NotNull
    String getDeclarationHeader();

    @NotNull
    List<PascalRoutine> getMethods();

    @NotNull
    List<PascalProperty> getProperties();

    @NotNull
    List<PascalVariableDefinition> getFields();

    @Nullable
    PascalTypeDefinition getSuperClass();

    @NotNull
    List<PsiElement> getMembers(boolean includeAncestors);

    @NotNull
    String getUnitName();
}
