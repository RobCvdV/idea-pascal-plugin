package nl.akiar.pascal.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import nl.akiar.pascal.stubs.PascalPropertyStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI element interface for Pascal property definitions.
 */
public interface PascalProperty extends PsiNameIdentifierOwner, StubBasedPsiElement<PascalPropertyStub>, PascalAttributable {

    @Override
    @Nullable
    String getName();

    @Nullable
    String getTypeName();

    @Nullable
    String getContainingClassName();

    @Nullable
    String getReadSpecifier();

    @Nullable
    String getWriteSpecifier();

    @NotNull
    String getUnitName();

    @Nullable
    String getDocComment();

    @Nullable
    String getVisibility();

    /**
     * Returns the class that contains this property.
     */
    @Nullable
    PascalTypeDefinition getContainingClass();
}
