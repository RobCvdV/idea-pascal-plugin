package nl.akiar.pascal.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.*;
import nl.akiar.pascal.resolution.MemberChainResolver;
import nl.akiar.pascal.stubs.PascalRoutineIndex;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.stubs.PascalVariableIndex;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Provides completion after DOT for member access chains.
 * Resolves the qualifier to its type, then offers all accessible members.
 */
public class PascalMemberCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();

        if (DumbService.isDumb(position.getProject())) return;

        // Find the DOT before the current position
        PsiElement dot = PsiTreeUtil.prevLeaf(position);
        while (dot != null && dot.getNode().getElementType() == PascalTokenTypes.WHITE_SPACE) {
            dot = PsiTreeUtil.prevLeaf(dot);
        }
        if (dot == null || dot.getNode().getElementType() != PascalTokenTypes.DOT) return;

        // Find the qualifier before the DOT
        PsiElement qualifier = PsiTreeUtil.prevLeaf(dot);
        while (qualifier != null && qualifier.getNode().getElementType() == PascalTokenTypes.WHITE_SPACE) {
            qualifier = PsiTreeUtil.prevLeaf(qualifier);
        }
        if (qualifier == null) return;

        // Resolve the qualifier chain to get its type
        PascalTypeDefinition typeDef = resolveQualifierType(qualifier, file);
        if (typeDef == null) return;

        // Get all members (including inherited)
        List<PsiElement> members = typeDef.getMembers(true);
        for (PsiElement member : members) {
            if (!(member instanceof PsiNameIdentifierOwner named)) continue;
            String name = named.getName();
            if (name == null || name.isEmpty()) continue;

            // Visibility check
            if (!isAccessible(member, file)) continue;

            LookupElementBuilder lookup = createMemberLookup(member, name);
            if (lookup != null) {
                result.addElement(PrioritizedLookupElement.withPriority(lookup, getPriority(member)));
            }
        }

        result.stopHere();
    }

    private PascalTypeDefinition resolveQualifierType(PsiElement qualifier, PsiFile originFile) {
        // Use the chain resolver to resolve the qualifier
        MemberChainResolver.ChainResolutionResult chainResult = MemberChainResolver.resolveChain(qualifier);
        PsiElement lastResolved = chainResult.getLastResolved();

        if (lastResolved == null) {
            // Try direct lookup as variable/type/routine
            return resolveSimpleQualifier(qualifier, originFile);
        }

        return getTypeOf(lastResolved, originFile);
    }

    private PascalTypeDefinition resolveSimpleQualifier(PsiElement qualifier, PsiFile file) {
        String name = qualifier.getText();
        int offset = qualifier.getTextOffset();

        // Try as variable
        PascalVariableDefinition varDef = PascalVariableIndex.findVariableAtPosition(name, file, offset);
        if (varDef != null) return getTypeOf(varDef, file);

        // Try as type (for static access like TClass.Method)
        PascalTypeIndex.TypeLookupResult typeResult = PascalTypeIndex.findTypesWithUsesValidation(name, file, offset);
        if (!typeResult.getInScopeTypes().isEmpty()) {
            return typeResult.getInScopeTypes().get(0);
        }

        // Try as routine (for function return type access)
        PascalRoutineIndex.RoutineLookupResult routineResult = PascalRoutineIndex.findRoutinesWithUsesValidation(name, file, offset);
        if (!routineResult.getInScopeRoutines().isEmpty()) {
            PascalRoutine routine = routineResult.getInScopeRoutines().get(0);
            return getTypeOf(routine, file);
        }

        return null;
    }

    private PascalTypeDefinition getTypeOf(PsiElement element, PsiFile originFile) {
        String typeName = null;
        if (element instanceof PascalVariableDefinition varDef) {
            typeName = varDef.getTypeName();
            if (typeName == null || typeName.isBlank()) {
                // Try inferred type
                PascalTypeDefinition inferred = MemberChainResolver.getInferredTypeOf(varDef, originFile);
                if (inferred != null) return inferred;
            }
        } else if (element instanceof PascalProperty prop) {
            typeName = prop.getTypeName();
        } else if (element instanceof PascalRoutine routine) {
            typeName = routine.getReturnTypeName();
        } else if (element instanceof PascalTypeDefinition td) {
            return td; // Direct type access (e.g., TClass.Create)
        }

        if (typeName == null || typeName.isBlank()) return null;

        PascalTypeIndex.TypeLookupResult result = PascalTypeIndex.findTypeWithTransitiveDeps(
                typeName, originFile, 0);
        if (!result.getInScopeTypes().isEmpty()) {
            return result.getInScopeTypes().get(0);
        }
        return null;
    }

    private boolean isAccessible(PsiElement member, PsiFile callSiteFile) {
        String visibility = getVisibility(member);
        if (visibility == null || visibility.equalsIgnoreCase("public") || visibility.equalsIgnoreCase("published")) {
            return true;
        }

        // Same file: private/protected visible (Delphi unit-level visibility)
        PsiFile memberFile = member.getContainingFile();
        if (memberFile != null && memberFile.equals(callSiteFile)) {
            return true;
        }

        // Different file: private is hidden
        if (visibility.contains("private")) return false;

        // Protected: hidden across units (simplified, doesn't check subclass)
        if (visibility.contains("protected")) return false;

        return true;
    }

    private String getVisibility(PsiElement member) {
        if (member instanceof PascalRoutine r) return r.getVisibility();
        if (member instanceof PascalProperty p) return p.getVisibility();
        if (member instanceof PascalVariableDefinition v) return v.getVisibility();
        return null;
    }

    private LookupElementBuilder createMemberLookup(PsiElement member, String name) {
        if (member instanceof PascalRoutine routine) {
            String returnType = routine.getReturnTypeName();
            LookupElementBuilder builder = LookupElementBuilder.create(name)
                    .withIcon(getIcon(member))
                    .withTailText("()", true)
                    .withInsertHandler((ctx, item) -> {
                        ctx.getDocument().insertString(ctx.getTailOffset(), "()");
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                    });
            if (returnType != null) {
                builder = builder.withTypeText(returnType);
            }
            return builder;
        } else if (member instanceof PascalProperty prop) {
            LookupElementBuilder builder = LookupElementBuilder.create(name)
                    .withIcon(getIcon(member));
            String typeName = prop.getTypeName();
            if (typeName != null) {
                builder = builder.withTypeText(typeName);
            }
            return builder;
        } else if (member instanceof PascalVariableDefinition varDef) {
            LookupElementBuilder builder = LookupElementBuilder.create(name)
                    .withIcon(getIcon(member));
            String typeName = varDef.getTypeName();
            if (typeName != null) {
                builder = builder.withTypeText(typeName);
            }
            return builder;
        }
        return LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Variable);
    }

    private Icon getIcon(PsiElement member) {
        if (member instanceof PascalRoutine) return AllIcons.Nodes.Method;
        if (member instanceof PascalProperty) return AllIcons.Nodes.Property;
        if (member instanceof PascalVariableDefinition) return AllIcons.Nodes.Field;
        return AllIcons.Nodes.Variable;
    }

    private double getPriority(PsiElement member) {
        if (member instanceof PascalProperty) return 200;
        if (member instanceof PascalRoutine) return 150;
        if (member instanceof PascalVariableDefinition) return 100;
        return 50;
    }
}
