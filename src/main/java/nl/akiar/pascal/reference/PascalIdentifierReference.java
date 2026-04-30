package nl.akiar.pascal.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.psi.PascalVariableDefinition;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.stubs.PascalVariableIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference for Pascal identifiers that can be either types or variables/constants.
 * Routine calls are handled by {@link PascalRoutineCallReference}.
 */
public class PascalIdentifierReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    private static final Logger LOG = Logger.getInstance(PascalIdentifierReference.class);
    private final String name;

    public PascalIdentifierReference(@NotNull PsiElement element, TextRange textRange) {
        super(element, textRange);
        name = element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        // 1. Variables (local > field > global) — early return if found
        PascalVariableDefinition var = PascalVariableIndex.findVariableAtPosition(
            name, myElement.getContainingFile(), myElement.getTextOffset());
        if (var != null) {
            return new ResolveResult[]{new PsiElementResolveResult(var)};
        }

        // 2. Types (with uses-clause validation, in-scope only)
        List<ResolveResult> results = new ArrayList<>();
        PascalTypeIndex.TypeLookupResult typeResult =
                PascalTypeIndex.findTypesWithUsesValidation(name, myElement.getContainingFile(), myElement.getTextOffset());

        List<PascalTypeDefinition> inScopeTypes = typeResult.getInScopeTypes();
        // Filter out forward declarations, preferring full definitions
        List<PascalTypeDefinition> fullDefs = new ArrayList<>();
        for (PascalTypeDefinition type : inScopeTypes) {
            if (!type.isForwardDeclaration()) fullDefs.add(type);
        }
        for (PascalTypeDefinition type : (fullDefs.isEmpty() ? inScopeTypes : fullDefs)) {
            results.add(new PsiElementResolveResult(type));
        }

        // 3. Routines (with uses-clause validation, in-scope only) — needed for Pascal procedure calls without parens
        if (results.isEmpty()) {
            nl.akiar.pascal.stubs.PascalRoutineIndex.RoutineLookupResult routineResult =
                    nl.akiar.pascal.stubs.PascalRoutineIndex.findRoutinesWithUsesValidation(
                        name, myElement.getContainingFile(), myElement.getTextOffset());
            for (nl.akiar.pascal.psi.PascalRoutine routine : routineResult.getInScopeRoutines()) {
                results.add(new PsiElementResolveResult(routine));
            }
        }

        // 4. Enum values — search in-scope enum types for a matching value
        if (results.isEmpty()) {
            PsiElement enumElement = findEnumValueInScope(name);
            if (enumElement != null) {
                results.add(new PsiElementResolveResult(enumElement));
            }
        }

        return results.toArray(new ResolveResult[0]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length > 0 ? resolveResults[0].getElement() : null;
    }

    /**
     * Search for an enum value with the given name across all in-scope enum types.
     * In Delphi, enum values can be used without the type prefix (e.g., taLeftJustify instead of TAlignment.taLeftJustify).
     */
    @Nullable
    private PsiElement findEnumValueInScope(String valueName) {
        PsiFile file = myElement.getContainingFile();
        if (file == null) return null;

        // Search all types in the stub index for enum types that contain this value
        java.util.Collection<String> allTypeKeys = com.intellij.psi.stubs.StubIndex.getInstance()
                .getAllKeys(PascalTypeIndex.KEY, myElement.getProject());

        for (String key : allTypeKeys) {
            java.util.Collection<PascalTypeDefinition> types = com.intellij.psi.stubs.StubIndex.getElements(
                    PascalTypeIndex.KEY, key, myElement.getProject(),
                    com.intellij.psi.search.GlobalSearchScope.allScope(myElement.getProject()),
                    PascalTypeDefinition.class);

            for (PascalTypeDefinition typeDef : types) {
                if (typeDef.getTypeKind() != nl.akiar.pascal.psi.TypeKind.ENUM) continue;

                // Check if this enum type is in scope
                PascalTypeIndex.TypeLookupResult scopeCheck =
                        PascalTypeIndex.findTypesWithUsesValidation(
                                typeDef.getName(), file, myElement.getTextOffset());
                if (scopeCheck.getInScopeTypes().isEmpty()) continue;

                // Search enum values — ENUM_ELEMENT nodes are nested inside ENUM_TYPE, not direct children
                java.util.List<PsiElement> enumElements = new ArrayList<>();
                com.intellij.psi.util.PsiTreeUtil.processElements(typeDef, element -> {
                    if (element.getNode() != null &&
                        element.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT) {
                        enumElements.add(element);
                    }
                    return true;
                });
                for (PsiElement enumEl : enumElements) {
                    // ENUM_ELEMENT may be a leaf node with no children; compare getText() directly
                    String enumName = null;
                    for (PsiElement idChild : enumEl.getChildren()) {
                        if (idChild.getNode() != null &&
                            idChild.getNode().getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                            enumName = idChild.getText();
                            break;
                        }
                    }
                    if (enumName == null) {
                        // Strip ordinal assignment: "askForMileageMode_Always = 2" → "askForMileageMode_Always"
                        String rawText = enumEl.getText();
                        int eqIdx = rawText.indexOf('=');
                        enumName = eqIdx > 0 ? rawText.substring(0, eqIdx).trim() : rawText.trim();
                    }
                    if (valueName.equalsIgnoreCase(enumName)) {
                        return enumEl; // Return the ENUM_ELEMENT node
                    }
                }
            }
        }
        return null;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) {
        return nl.akiar.pascal.psi.PascalPsiFactory.INSTANCE.replaceIdentifier(myElement, newElementName);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return new Object[0];
    }
}
