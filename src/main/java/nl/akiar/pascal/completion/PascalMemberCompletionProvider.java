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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // Find the qualifier before the DOT, skipping past brackets and parens
        PsiElement qualifier = PsiTreeUtil.prevLeaf(dot);
        while (qualifier != null && qualifier.getNode().getElementType() == PascalTokenTypes.WHITE_SPACE) {
            qualifier = PsiTreeUtil.prevLeaf(qualifier);
        }
        // Skip past bracket/paren expressions to find the actual identifier
        qualifier = skipBackwardToIdentifier(qualifier);
        if (qualifier == null) return;

        // Resolve the full qualifier chain (including generic substitution)
        PsiElement lastResolved = null;
        Map<String, String> typeArgMap = new HashMap<>();
        try {
            MemberChainResolver.ChainResolutionResult chainResult = MemberChainResolver.resolveChain(qualifier);
            lastResolved = chainResult.getLastResolved();
            typeArgMap = chainResult.getTypeArgMap();
        } catch (Exception e) {
            // Log and fall through to resolveSimpleQualifier
            com.intellij.openapi.diagnostic.Logger.getInstance(PascalMemberCompletionProvider.class)
                .debug("Chain resolution failed for completion: " + e.getMessage());
        }

        // Check if qualifier resolves to a unit (PsiFile) for unit-qualified completion
        if (lastResolved instanceof PsiFile) {
            addUnitGlobals((PsiFile) lastResolved, file, result);
            result.stopHere();
            return;
        }

        // Resolve the qualifier to its type
        PascalTypeDefinition typeDef = resolveQualifierType(lastResolved, qualifier, file, typeArgMap);
        if (typeDef == null) return;

        // Build the type arg map for this type context if not already set
        if (typeArgMap.isEmpty() && lastResolved != null) {
            String rawTypeName = getMemberTypeName(lastResolved);
            if (rawTypeName != null) {
                typeArgMap = buildTypeArgMap(typeDef, rawTypeName);
            }
        }

        // Get all members (including inherited)
        List<PsiElement> members = typeDef.getMembers(true);
        for (PsiElement member : members) {
            String name;
            if (member instanceof PsiNameIdentifierOwner named) {
                name = named.getName();
            } else if (member.getNode() != null &&
                       member.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT) {
                name = getEnumElementName(member);
            } else {
                continue;
            }
            if (name == null || name.isEmpty()) continue;

            // Visibility check (enum values are always public)
            if (member instanceof PsiNameIdentifierOwner && !isAccessible(member, file)) continue;

            LookupElementBuilder lookup = createMemberLookup(member, name, typeArgMap);
            if (lookup != null) {
                result.addElement(PrioritizedLookupElement.withPriority(lookup, getPriority(member)));
            }
        }

        result.stopHere();
    }

    /**
     * Resolve qualifier to its type definition, using the chain result's typeArgMap
     * for generic type parameter substitution.
     */
    private PascalTypeDefinition resolveQualifierType(PsiElement lastResolved, PsiElement qualifier,
                                                       PsiFile originFile, Map<String, String> typeArgMap) {
        if (lastResolved == null) {
            // Try direct lookup as variable/type/routine
            return resolveSimpleQualifier(qualifier, originFile);
        }

        // Get the raw type name of the last resolved element
        String rawTypeName = getMemberTypeName(lastResolved);

        // Apply generic substitution if the type name is a type parameter
        if (rawTypeName != null && typeArgMap.containsKey(rawTypeName)) {
            String substituted = typeArgMap.get(rawTypeName);
            return getTypeOf(lastResolved, originFile, substituted);
        }

        return getTypeOf(lastResolved, originFile);
    }

    /**
     * Get the type name from a resolved element (variable, property, routine, or type definition).
     */
    private String getMemberTypeName(PsiElement element) {
        if (element instanceof PascalVariableDefinition varDef) return varDef.getTypeName();
        if (element instanceof PascalProperty prop) return prop.getTypeName();
        if (element instanceof PascalRoutine routine) return routine.getReturnTypeName();
        if (element instanceof PascalTypeDefinition td) return td.getName();
        return null;
    }

    /**
     * Build a type argument substitution map from a type definition and the raw type name
     * that references it (which may contain generic arguments).
     * E.g., for TEntityList definition with type param T, and raw name "TEntityList<TRide>",
     * returns {T → TRide}.
     */
    private Map<String, String> buildTypeArgMap(PascalTypeDefinition typeDef, String rawTypeName) {
        Map<String, String> map = new HashMap<>();
        if (rawTypeName == null || typeDef == null) return map;
        int ltIdx = rawTypeName.indexOf('<');
        if (ltIdx <= 0) return map;
        int gtIdx = rawTypeName.lastIndexOf('>');
        if (gtIdx <= ltIdx) return map;
        String argsStr = rawTypeName.substring(ltIdx + 1, gtIdx);
        List<String> typeArgs = splitGenericArgs(argsStr);
        List<String> typeParams = typeDef.getTypeParameters();
        for (int i = 0; i < typeArgs.size() && i < typeParams.size(); i++) {
            map.put(typeParams.get(i), typeArgs.get(i));
        }
        return map;
    }

    /**
     * Split generic arguments at depth-0 commas.
     * "String, TList<Integer>" → ["String", "TList<Integer>"]
     */
    private List<String> splitGenericArgs(String argsStr) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                args.add(argsStr.substring(start, i).trim());
                start = i + 1;
            }
        }
        String last = argsStr.substring(start).trim();
        if (!last.isEmpty()) args.add(last);
        return args;
    }

    /**
     * Skip backward past bracket/paren expressions to find the actual identifier.
     * For "ItemsById[0]", if we start at "]", skip to "ItemsById".
     * For "GetItem(0)", if we start at ")", skip to "GetItem".
     */
    private PsiElement skipBackwardToIdentifier(PsiElement element) {
        if (element == null) return null;

        while (element != null) {
            var elemType = element.getNode().getElementType();
            if (elemType == PascalTokenTypes.RPAREN) {
                element = skipMatchedBackward(element, PascalTokenTypes.LPAREN, PascalTokenTypes.RPAREN);
                if (element == null) return null;
                element = PsiTreeUtil.prevLeaf(element);
                while (element != null && element.getNode().getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    element = PsiTreeUtil.prevLeaf(element);
                }
            } else if (elemType == PascalTokenTypes.RBRACKET) {
                element = skipMatchedBackward(element, PascalTokenTypes.LBRACKET, PascalTokenTypes.RBRACKET);
                if (element == null) return null;
                element = PsiTreeUtil.prevLeaf(element);
                while (element != null && element.getNode().getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    element = PsiTreeUtil.prevLeaf(element);
                }
            } else if (elemType == PascalTokenTypes.GT) {
                element = skipMatchedBackward(element, PascalTokenTypes.LT, PascalTokenTypes.GT);
                if (element == null) return null;
                element = PsiTreeUtil.prevLeaf(element);
                while (element != null && element.getNode().getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    element = PsiTreeUtil.prevLeaf(element);
                }
            } else {
                break;
            }
        }
        return element;
    }

    private PsiElement skipMatchedBackward(PsiElement close,
                                            com.intellij.psi.tree.IElementType openType,
                                            com.intellij.psi.tree.IElementType closeType) {
        int depth = 1;
        PsiElement cur = PsiTreeUtil.prevLeaf(close);
        while (cur != null && depth > 0) {
            if (cur.getNode().getElementType() == closeType) depth++;
            else if (cur.getNode().getElementType() == openType) depth--;
            if (depth == 0) return cur;
            cur = PsiTreeUtil.prevLeaf(cur);
        }
        return null;
    }

    private PascalTypeDefinition resolveSimpleQualifier(PsiElement qualifier, PsiFile file) {
        // Handle Self keyword
        if (qualifier.getNode().getElementType() == PascalTokenTypes.KW_SELF) {
            return MemberChainResolver.findContainingClass(qualifier);
        }

        // Handle Result keyword — resolve to the return type of the enclosing function
        if (qualifier.getNode().getElementType() == PascalTokenTypes.KW_RESULT) {
            PascalRoutine routine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(qualifier, PascalRoutine.class);
            while (routine != null) {
                if (routine.getReturnTypeName() != null) return getTypeOf(routine, file);
                routine = com.intellij.psi.util.PsiTreeUtil.getParentOfType(routine, PascalRoutine.class);
            }
            return null;
        }

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

    /**
     * Add completion items for all public globals (types, variables, routines) in a unit.
     * Uses PSI tree traversal on the unit file to find all top-level declarations.
     */
    private void addUnitGlobals(PsiFile unitFile, PsiFile originFile, CompletionResultSet result) {
        java.util.Set<String> seen = new java.util.HashSet<>();

        // Add types from the unit
        for (PascalTypeDefinition typeDef : PsiTreeUtil.findChildrenOfType(unitFile, PascalTypeDefinition.class)) {
            String name = typeDef.getName();
            if (name == null || name.isEmpty() || !seen.add(name.toLowerCase())) continue;
            LookupElementBuilder lookup = LookupElementBuilder.create(name)
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Class)
                    .withTypeText(typeDef.getTypeKind() != null ? typeDef.getTypeKind().name().toLowerCase() : "type");
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 200));
        }

        // Add global variables from the unit (no containing class)
        for (PascalVariableDefinition varDef : PsiTreeUtil.findChildrenOfType(unitFile, PascalVariableDefinition.class)) {
            if (varDef.getContainingClassName() != null && !varDef.getContainingClassName().isEmpty()) continue;
            String name = varDef.getName();
            if (name == null || name.isEmpty() || !seen.add(name.toLowerCase())) continue;
            LookupElementBuilder lookup = LookupElementBuilder.create(name)
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Variable);
            String typeName = varDef.getTypeName();
            if (typeName != null) lookup = lookup.withTypeText(typeName);
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 150));
        }

        // Add global routines from the unit (no containing class)
        for (PascalRoutine routine : PsiTreeUtil.findChildrenOfType(unitFile, PascalRoutine.class)) {
            if (routine.getContainingClassName() != null && !routine.getContainingClassName().isEmpty()) continue;
            if (routine.isImplementation()) continue;
            String name = routine.getName();
            if (name == null || name.isEmpty() || !seen.add(name.toLowerCase())) continue;
            LookupElementBuilder lookup = LookupElementBuilder.create(name)
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Function)
                    .withTailText("()", true);
            String returnType = routine.getReturnTypeName();
            if (returnType != null) lookup = lookup.withTypeText(returnType);
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 100));
        }
    }

    private PascalTypeDefinition getTypeOf(PsiElement element, PsiFile originFile) {
        return getTypeOf(element, originFile, null);
    }

    private PascalTypeDefinition getTypeOf(PsiElement element, PsiFile originFile, String typeNameOverride) {
        String typeName = typeNameOverride;
        if (typeName == null) {
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
        }

        if (typeName == null || typeName.isBlank()) return null;

        // Strip generic arguments: "TEntityList<TRide>" -> "TEntityList"
        int ltIdx = typeName.indexOf('<');
        if (ltIdx > 0) {
            typeName = typeName.substring(0, ltIdx);
        }

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

    private LookupElementBuilder createMemberLookup(PsiElement member, String name, Map<String, String> typeArgMap) {
        if (member instanceof PascalRoutine routine) {
            String returnType = routine.getReturnTypeName();
            // Apply generic substitution to return type
            if (returnType != null && typeArgMap.containsKey(returnType)) {
                returnType = typeArgMap.get(returnType);
            }
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
            // Apply generic substitution
            if (typeName != null && typeArgMap.containsKey(typeName)) {
                typeName = typeArgMap.get(typeName);
            }
            if (typeName != null) {
                builder = builder.withTypeText(typeName);
            }
            return builder;
        } else if (member instanceof PascalVariableDefinition varDef) {
            LookupElementBuilder builder = LookupElementBuilder.create(name)
                    .withIcon(getIcon(member));
            String typeName = varDef.getTypeName();
            // Apply generic substitution
            if (typeName != null && typeArgMap.containsKey(typeName)) {
                typeName = typeArgMap.get(typeName);
            }
            if (typeName != null) {
                builder = builder.withTypeText(typeName);
            }
            return builder;
        }
        // Enum element
        if (member.getNode() != null &&
            member.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT) {
            PascalTypeDefinition enumType = com.intellij.psi.util.PsiTreeUtil.getParentOfType(member, PascalTypeDefinition.class);
            String typeName = enumType != null ? enumType.getName() : "enum";
            return LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Enum)
                    .withTypeText(typeName);
        }
        return LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Variable);
    }

    private String getEnumElementName(PsiElement enumElement) {
        // ENUM_ELEMENT nodes may be leaf nodes with no children; use getText() directly
        for (PsiElement child : enumElement.getChildren()) {
            if (child.getNode() != null &&
                child.getNode().getElementType() == nl.akiar.pascal.PascalTokenTypes.IDENTIFIER) {
                return child.getText();
            }
        }
        // Strip ordinal assignment: "askForMileageMode_Always = 2" → "askForMileageMode_Always"
        String text = enumElement.getText();
        int eqIdx = text.indexOf('=');
        return eqIdx > 0 ? text.substring(0, eqIdx).trim() : text.trim();
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
        // Enum values should have reasonable priority
        if (member.getNode() != null &&
            member.getNode().getElementType() == nl.akiar.pascal.psi.PascalElementTypes.ENUM_ELEMENT) return 100;
        return 50;
    }
}
