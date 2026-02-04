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
public interface PascalTypeDefinition extends PsiNameIdentifierOwner, StubBasedPsiElement<PascalTypeStub>, PascalAttributable {
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

    /**
     * Get the name of the direct superclass (for classes) or parent interface.
     * Returns null if no superclass/parent is specified.
     * This method uses the stub when available for fast access.
     */
    @Nullable
    String getSuperClassName();

    /**
     * Get the resolved superclass PSI element.
     * This may require type resolution if crossing unit boundaries.
     */
    @Nullable
    PascalTypeDefinition getSuperClass();

    @NotNull
    List<PsiElement> getMembers(boolean includeAncestors);

    @NotNull
    String getUnitName();

    /**
     * Get the GUID for an interface type.
     * In Delphi, interfaces can have a GUID attribute like:
     * <pre>
     *   IMyInterface = interface
     *     ['{285DEA8A-B865-11D1-AAA7-00C04FB17A72}']
     *   end;
     * </pre>
     *
     * @return The GUID string (without quotes or brackets), or null if not an interface or no GUID specified
     */
    @Nullable
    String getGUID();
}
