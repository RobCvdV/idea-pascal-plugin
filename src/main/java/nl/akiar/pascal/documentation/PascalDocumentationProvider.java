package nl.akiar.pascal.documentation;

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import nl.akiar.pascal.PascalLanguage;
import nl.akiar.pascal.PascalSyntaxHighlighter;
import nl.akiar.pascal.PascalTokenType;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.*;
import nl.akiar.pascal.resolution.DelphiBuiltIns;
import nl.akiar.pascal.resolution.MemberChainResolver;
import nl.akiar.pascal.stubs.PascalPropertyIndex;
import nl.akiar.pascal.stubs.PascalRoutineIndex;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.stubs.PascalVariableIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Documentation provider for Pascal language elements.
 * Uses IntelliJ's DocumentationMarkup for consistent styling and
 * provides clickable type links for in-popup navigation.
 */
public class PascalDocumentationProvider extends AbstractDocumentationProvider {
    private static final Logger LOG = Logger.getInstance(PascalDocumentationProvider.class);

    /** Key for storing a generic type argument substitution map on an element during doc generation. */
    private static final com.intellij.openapi.util.Key<Map<String, String>> TYPE_ARG_MAP_KEY =
            com.intellij.openapi.util.Key.create("pascal.doc.typeArgMap");

    // Link prefixes for different element types
    private static final String LINK_TYPE = "type:";
    private static final String LINK_ROUTINE = "routine:";
    private static final String LINK_UNIT = "unit:";

    // Patterns for parsing doc comments
    private static final Pattern PARAM_PATTERN = Pattern.compile("@param\\s+(\\w+)\\s+(.+?)(?=@|$)", Pattern.DOTALL);
    private static final Pattern RETURNS_PATTERN = Pattern.compile("@returns?\\s+(.+?)(?=@|$)", Pattern.DOTALL);

    // XMLDoc patterns
    private static final Pattern XMLDOC_DETECT_PATTERN = Pattern.compile("<(summary|remarks|param|returns|value|exception|example|code|see |see>|c>)");
    private static final Pattern XMLDOC_SUMMARY_PATTERN = Pattern.compile("<summary>(.*?)</summary>", Pattern.DOTALL);
    private static final Pattern XMLDOC_REMARKS_PATTERN = Pattern.compile("<remarks>(.*?)</remarks>", Pattern.DOTALL);
    private static final Pattern XMLDOC_PARAM_PATTERN = Pattern.compile("<param\\s+name=\"(\\w+)\">(.*?)</param>", Pattern.DOTALL);
    private static final Pattern XMLDOC_RETURNS_PATTERN = Pattern.compile("<returns>(.*?)</returns>", Pattern.DOTALL);
    private static final Pattern XMLDOC_VALUE_PATTERN = Pattern.compile("<value>(.*?)</value>", Pattern.DOTALL);
    private static final Pattern XMLDOC_EXCEPTION_PATTERN = Pattern.compile("<exception\\s+cref=\"([^\"]+)\">(.*?)</exception>", Pattern.DOTALL);
    private static final Pattern XMLDOC_EXAMPLE_PATTERN = Pattern.compile("<example>(.*?)</example>", Pattern.DOTALL);
    private static final Pattern XMLDOC_CODE_PATTERN = Pattern.compile("<code>(.*?)</code>", Pattern.DOTALL);
    private static final Pattern XMLDOC_INLINE_CODE_PATTERN = Pattern.compile("<c>(.*?)</c>", Pattern.DOTALL);
    private static final Pattern XMLDOC_SEE_SELF_CLOSING_PATTERN = Pattern.compile("<see\\s+cref=\"([^\"]+)\"\\s*/>", Pattern.DOTALL);
    private static final Pattern XMLDOC_SEE_WITH_TEXT_PATTERN = Pattern.compile("<see\\s+cref=\"([^\"]+)\">(.*?)</see>", Pattern.DOTALL);

    /**
     * Find the best type definition from a list, preferring non-forward declarations.
     * Forward declarations like "TMyClass = class;" should be skipped in favor of the actual declaration.
     */
    @Nullable
    private static PascalTypeDefinition findBestTypeDefinition(List<PascalTypeDefinition> types) {
        if (types == null || types.isEmpty()) return null;

        // First pass: look for a non-forward declaration
        for (PascalTypeDefinition typeDef : types) {
            if (!typeDef.isForwardDeclaration()) {
                return typeDef;
            }
        }

        // Fallback: return the first one (even if it's a forward declaration)
        return types.get(0);
    }

    /**
     * Resolve a member name preceded by 'inherited' to the ancestor class's member.
     */
    @Nullable
    private PsiElement resolveInheritedMember(String memberName, PsiElement contextElement) {
        PascalTypeDefinition containingClass = MemberChainResolver.findContainingClass(contextElement);
        if (containingClass == null) return null;
        PascalTypeDefinition superClass = containingClass.getSuperClass();
        if (superClass == null) return null;
        for (PsiElement member : superClass.getMembers(true)) {
            if (member instanceof PsiNameIdentifierOwner) {
                String name = ((PsiNameIdentifierOwner) member).getName();
                if (memberName.equalsIgnoreCase(name)) {
                    return member;
                }
            }
        }
        return null;
    }

    private String getClassContextForElement(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PascalTypeDefinition) return ((PascalTypeDefinition) current).getName();
            if (current instanceof PascalRoutine) return ((PascalRoutine) current).getContainingClassName();
            current = current.getParent();
        }
        return null;
    }

    @NotNull
    private PsiElement pickBestRoutine(List<PascalRoutine> routines, @Nullable String classContext) {
        if (routines.size() == 1 || classContext == null) return routines.get(0);
        // Filter by class context
        List<PascalRoutine> filtered = new java.util.ArrayList<>();
        for (PascalRoutine r : routines) {
            if (classContext.equalsIgnoreCase(r.getContainingClassName())) filtered.add(r);
        }
        List<PascalRoutine> candidates = filtered.isEmpty() ? routines : filtered;
        // Prefer declarations over implementations
        for (PascalRoutine r : candidates) {
            if (!r.isImplementation()) return r;
        }
        return candidates.get(0);
    }

    @Override
    @Nullable
    public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement, int targetOffset) {
        LOG.debug("[PascalDoc] getCustomDocumentationElement element='" + (contextElement != null ? contextElement.getText() : "<null>") + "' file='" + file.getName() + "'");
        // Handle Self keyword -> resolve to containing class for documentation
        if (contextElement != null && contextElement.getNode().getElementType() == PascalTokenTypes.KW_SELF) {
            PascalTypeDefinition containingClass = MemberChainResolver.findContainingClass(contextElement);
            if (containingClass != null) return containingClass;
        }
        if (contextElement != null && contextElement.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            String name = contextElement.getText();

            // Check if UNIT_REFERENCE
            PsiElement parent = contextElement.getParent();
            if (parent != null && parent.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
                LOG.debug("[PascalDoc] Unit reference detected: " + parent.getText());
                return parent;
            }

            // Check if this is a member access (after a DOT)
            boolean isMemberAccess = false;
            PsiElement prevLeaf = com.intellij.psi.util.PsiTreeUtil.prevLeaf(contextElement);
            while (prevLeaf != null && (prevLeaf instanceof PsiWhiteSpace || prevLeaf instanceof com.intellij.psi.PsiComment)) {
                prevLeaf = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prevLeaf);
            }
            if (prevLeaf != null && prevLeaf.getNode() != null &&
                prevLeaf.getNode().getElementType() == PascalTokenTypes.DOT) {
                isMemberAccess = true;
                LOG.debug("[PascalDoc] Member access detected for: " + name);
            }

            // For member access, prioritize chain resolution (handles generic substitution)
            if (isMemberAccess) {
                LOG.info("[GenericChain][Doc] member access; attempting chain resolve for: " + name);
                MemberChainResolver.ChainResolutionResult chainResult = MemberChainResolver.resolveChain(contextElement);
                LOG.info("[GenericChain][Doc] chain collected: " + chainResult.getChainElements().size() + " elements = " +
                    chainResult.getChainElements().stream().map(e -> e.getText()).collect(java.util.stream.Collectors.joining(", ")));
                int myIndex = -1;
                for (int idx = 0; idx < chainResult.getChainElements().size(); idx++) {
                    if (chainResult.getChainElements().get(idx).getTextRange().equals(contextElement.getTextRange())) {
                        myIndex = idx;
                        break;
                    }
                }
                if (myIndex < 0) myIndex = chainResult.getChainElements().size() - 1;
                PsiElement chainResolved = myIndex < chainResult.getResolvedElements().size()
                        ? chainResult.getResolvedElements().get(myIndex) : null;
                LOG.info("[GenericChain][Doc] myIndex=" + myIndex + " chainResolved=" +
                    (chainResolved != null ? chainResolved.getClass().getSimpleName() : "<null>") +
                    " allResolved=[" + chainResult.getResolvedElements().stream()
                        .map(e -> e != null ? e.getClass().getSimpleName() : "<null>")
                        .collect(java.util.stream.Collectors.joining(", ")) + "]");
                if (chainResolved != null) {
                    // Get the typeArgMap that was active when resolving this element
                    // (i.e., the owner's generic substitution context)
                    Map<String, String> typeArgMap = chainResult.getTypeArgMapForIndex(myIndex);
                    if (!typeArgMap.isEmpty()) {
                        chainResolved.putUserData(TYPE_ARG_MAP_KEY, typeArgMap);
                        LOG.info("[GenericChain][Doc] stored typeArgMap on resolved: " + typeArgMap);
                    }
                    return redirectForwardDeclaration(chainResolved);
                }
                // Chain resolution failed — fall through to reference/index resolution
                LOG.info("[GenericChain][Doc] chain resolution FAILED for member: " + name + " — falling through to index lookups");
            }

            // Handle 'inherited Method' — resolve to ancestor's method
            if (!isMemberAccess) {
                PsiElement inheritedCheck = com.intellij.psi.util.PsiTreeUtil.prevLeaf(contextElement);
                while (inheritedCheck instanceof PsiWhiteSpace || inheritedCheck instanceof com.intellij.psi.PsiComment) {
                    inheritedCheck = com.intellij.psi.util.PsiTreeUtil.prevLeaf(inheritedCheck);
                }
                if (inheritedCheck != null && inheritedCheck.getNode().getElementType() == PascalTokenTypes.KW_INHERITED) {
                    PsiElement resolved = resolveInheritedMember(name, contextElement);
                    if (resolved != null) {
                        return redirectForwardDeclaration(resolved);
                    }
                }
            }

            // Check if the context element is a name identifier of a declaration
            PsiNameIdentifierOwner owner = com.intellij.psi.util.PsiTreeUtil.getParentOfType(contextElement, PsiNameIdentifierOwner.class);
            if (owner != null && owner.getNameIdentifier() == contextElement) {
                return redirectForwardDeclaration((PsiElement) owner);
            }

            // Check if it resolves via reference
            PsiReference[] refs = contextElement.getReferences();
            for (PsiReference ref : refs) {
                PsiElement resolved = ref.resolve();
                if (resolved != null) {
                    return redirectForwardDeclaration(resolved);
                }
            }

            // Fallback to index lookups
            PascalTypeIndex.TypeLookupResult typeResult =
                    PascalTypeIndex.findTypesWithUsesValidation(name, contextElement.getContainingFile(), contextElement.getTextOffset());
            PascalTypeDefinition bestType = findBestTypeDefinition(typeResult.getInScopeTypes());
            if (bestType != null) {
                return bestType;
            }
            
            // ... (rest of the method follows)

            // For member access, skip global index lookups (they return unrelated matches
            // from any class). Instead, return the raw identifier so generateDoc can show
            // "member not found" instead of IntelliJ's default parent-walking behavior.
            if (isMemberAccess) {
                return contextElement;
            }

            PsiFile currentFile = contextElement.getContainingFile();
            int offset = contextElement.getTextOffset();
            PascalVariableDefinition foundVar = PascalVariableIndex.findVariableAtPosition(name, currentFile, offset);
            if (foundVar != null) {
                return foundVar;
            }

            PascalPropertyIndex.PropertyLookupResult propResult =
                    PascalPropertyIndex.findPropertiesWithUsesValidation(name, contextElement.getContainingFile(), contextElement.getTextOffset());
            if (!propResult.getInScopeProperties().isEmpty()) {
                return propResult.getInScopeProperties().get(0);
            }

            PascalRoutineIndex.RoutineLookupResult routineResult =
                    PascalRoutineIndex.findRoutinesWithUsesValidation(name, contextElement.getContainingFile(), contextElement.getTextOffset());
            if (!routineResult.getInScopeRoutines().isEmpty()) {
                String classCtx = getClassContextForElement(contextElement);
                return pickBestRoutine(routineResult.getInScopeRoutines(), classCtx);
            }

            // Last resort: out-of-scope matches
            PascalTypeDefinition outOfScopeType = findBestTypeDefinition(typeResult.getOutOfScopeTypes());
            if (outOfScopeType != null) {
                return outOfScopeType;
            }
            if (!routineResult.getOutOfScopeRoutines().isEmpty()) {
                String classCtx = getClassContextForElement(contextElement);
                return pickBestRoutine(routineResult.getOutOfScopeRoutines(), classCtx);
            }

            // Final fallback: built-in identifier (only if no source resolution succeeded)
            if (DelphiBuiltIns.isBuiltIn(name)) {
                LOG.debug("[PascalDoc] Built-in identifier (fallback): " + name);
                return contextElement;
            }
        }
        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset);
    }

    @Override
    @Nullable
    public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
        if (link == null || context == null) return null;

        Project project = context.getProject();
        PsiFile contextFile = context.getContainingFile();
        int offset = context.getTextOffset();

        if (link.startsWith(LINK_TYPE)) {
            String typeName = link.substring(LINK_TYPE.length());
            PascalTypeIndex.TypeLookupResult result =
                    PascalTypeIndex.findTypesWithUsesValidation(typeName, contextFile, offset);
            PascalTypeDefinition bestInScope = findBestTypeDefinition(result.getInScopeTypes());
            if (bestInScope != null) {
                return bestInScope;
            }
            PascalTypeDefinition bestOutOfScope = findBestTypeDefinition(result.getOutOfScopeTypes());
            if (bestOutOfScope != null) {
                return bestOutOfScope;
            }
            // Try built-in type documentation
            if (DelphiBuiltIns.isBuiltInType(typeName)) {
                return context; // Will generate built-in doc
            }
        } else if (link.startsWith(LINK_ROUTINE)) {
            String routineName = link.substring(LINK_ROUTINE.length());
            PascalRoutineIndex.RoutineLookupResult result =
                    PascalRoutineIndex.findRoutinesWithUsesValidation(routineName, contextFile, offset);
            if (!result.getInScopeRoutines().isEmpty()) {
                return result.getInScopeRoutines().get(0);
            }
            if (!result.getOutOfScopeRoutines().isEmpty()) {
                return result.getOutOfScopeRoutines().get(0);
            }
        } else if (link.startsWith(LINK_UNIT)) {
            // Unit navigation - find the unit file
            String unitName = link.substring(LINK_UNIT.length());
            // TODO: Navigate to unit file
        }

        return null;
    }

    @Override
    @Nullable
    public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        LOG.debug("[PascalDoc] generateDoc called for element: " + element + " class: " + (element != null ? element.getClass().getName() : "null"));

        if (element == null) return null;

        // Unit reference documentation
        if (element.getNode() != null && element.getNode().getElementType() == PascalElementTypes.UNIT_REFERENCE) {
            return generateUnitDoc(element.getText(), element);
        }

        // Built-in or unresolved identifier
        if (element.getNode() != null && element.getNode().getElementType() == PascalTokenTypes.IDENTIFIER) {
            String name = element.getText();
            if (DelphiBuiltIns.isBuiltIn(name)) {
                // Try to resolve via references first — if a source definition exists, use that
                PsiReference[] refs = element.getReferences();
                for (PsiReference ref : refs) {
                    PsiElement resolved = ref.resolve();
                    if (resolved != null && resolved != element) {
                        String sourceDoc = generateDoc(resolved, element);
                        if (sourceDoc != null) return sourceDoc;
                    }
                }
                // Also try contributed references
                PsiReference[] contributed = com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry.getReferencesFromProviders(element);
                for (PsiReference ref : contributed) {
                    PsiElement resolved = ref.resolve();
                    if (resolved != null && resolved != element) {
                        String sourceDoc = generateDoc(resolved, element);
                        if (sourceDoc != null) return sourceDoc;
                    }
                }
                // No source resolution — fall back to built-in doc
                return generateBuiltInDoc(name, element);
            }

            // Check for unresolved member access
            PsiElement prevLeaf = com.intellij.psi.util.PsiTreeUtil.prevLeaf(element);
            while (prevLeaf != null && (prevLeaf instanceof PsiWhiteSpace || prevLeaf instanceof com.intellij.psi.PsiComment)) {
                prevLeaf = com.intellij.psi.util.PsiTreeUtil.prevLeaf(prevLeaf);
            }
            if (prevLeaf != null && prevLeaf.getNode() != null &&
                prevLeaf.getNode().getElementType() == PascalTokenTypes.DOT) {
                return generateUnresolvedMemberDoc(name, prevLeaf, element);
            }
        }

        // Type definition documentation
        if (element instanceof PascalTypeDefinition) {
            return generateTypeDoc((PascalTypeDefinition) element);
        }

        // Retrieve generic type argument substitution map if set by chain resolution
        Map<String, String> typeArgMap = element.getUserData(TYPE_ARG_MAP_KEY);
        // Clean up UserData after reading to avoid stale data
        if (typeArgMap != null) {
            element.putUserData(TYPE_ARG_MAP_KEY, null);
        }

        // Variable definition documentation
        if (element instanceof PascalVariableDefinition) {
            return generateVariableDoc((PascalVariableDefinition) element, typeArgMap);
        }

        // Property documentation
        if (element instanceof PascalProperty) {
            return generatePropertyDoc((PascalProperty) element, typeArgMap);
        }

        // Routine documentation
        if (element instanceof PascalRoutine) {
            return generateRoutineDoc((PascalRoutine) element, typeArgMap);
        }

        return null;
    }

    // ==================== Document Generation Methods ====================

    private String generateUnitDoc(String unitName, PsiElement element) {
        StringBuilder sb = new StringBuilder();

        // Definition section
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append("<b>unit</b> ").append(escapeHtml(unitName));
        sb.append(DocumentationMarkup.DEFINITION_END);

        // Source location
        appendSourceLocation(sb, element);

        return sb.toString();
    }

    private PsiElement redirectForwardDeclaration(PsiElement element) {
        if (element instanceof PascalTypeDefinition && ((PascalTypeDefinition) element).isForwardDeclaration()) {
            PascalTypeDefinition counterpart = findTypeCounterpart((PascalTypeDefinition) element);
            if (counterpart != null && !counterpart.isForwardDeclaration()) {
                LOG.debug("[PascalDoc] Redirecting from forward declaration to full definition: " + ((PascalTypeDefinition) element).getName());
                return counterpart;
            }
        }
        return element;
    }

    @Nullable
    private PascalTypeDefinition findTypeCounterpart(PascalTypeDefinition typeDef) {
        String name = typeDef.getName();
        if (name == null) return null;

        PsiFile file = typeDef.getContainingFile();
        if (file == null) return null;

        // Search for the same type name in the same file
        PascalTypeIndex.TypeLookupResult result =
                PascalTypeIndex.findTypesWithUsesValidation(name, file, typeDef.getTextOffset());

        PascalTypeDefinition candidateWithDoc = null;
        PascalTypeDefinition anyOther = null;
        for (PascalTypeDefinition other : result.getInScopeTypes()) {
            if (other != typeDef && other.getContainingFile() == file) {
                if (anyOther == null) anyOther = other;
                String doc = other.getDocComment();
                if (doc != null && !doc.isEmpty()) {
                    candidateWithDoc = other;
                    break;
                }
            }
        }
        return candidateWithDoc != null ? candidateWithDoc : anyOther;
    }

    private String generateTypeDoc(PascalTypeDefinition typeDef) {
        StringBuilder sb = new StringBuilder();
        Project project = typeDef.getProject();

        // Definition section with syntax highlighting
        sb.append(DocumentationMarkup.DEFINITION_START);
        appendHighlightedTypeSignature(sb, typeDef, project);
        sb.append(DocumentationMarkup.DEFINITION_END);

        // Content section (doc comment)
        String docComment = typeDef.getDocComment();
        if (docComment == null || docComment.isEmpty()) {
            // Fallback to the counterpart (full declaration if this is forward, or vice versa)
            PascalTypeDefinition counterpart = findTypeCounterpart(typeDef);
            if (counterpart != null) {
                docComment = counterpart.getDocComment();
            }
        }

        if (docComment != null && !docComment.isEmpty()) {
            sb.append(DocumentationMarkup.CONTENT_START);
            sb.append(formatDocComment(docComment));
            sb.append(DocumentationMarkup.CONTENT_END);
        }

        // Sections (metadata)
        sb.append(DocumentationMarkup.SECTIONS_START);

        // Generic parameters
        List<String> typeParams = typeDef.getTypeParameters();
        if (!typeParams.isEmpty()) {
            appendSectionRaw(sb, "Generic:", "<p>&lt;" + escapeHtml(String.join(", ", typeParams)) + "&gt;</p>");
        }

        // Visibility (if any)
        // Type kind
        TypeKind kind = typeDef.getTypeKind();
        if (kind != null) {
            appendSection(sb, "Kind:", kind.name().toLowerCase());
        }

        sb.append(DocumentationMarkup.SECTIONS_END);

        // Source location at bottom
        appendSourceLocation(sb, typeDef);

        return sb.toString();
    }

    private String generateVariableDoc(PascalVariableDefinition varDef) {
        return generateVariableDoc(varDef, null);
    }

    private String generateVariableDoc(PascalVariableDefinition varDef, @Nullable Map<String, String> typeArgMap) {
        StringBuilder sb = new StringBuilder();
        Project project = varDef.getProject();

        // Definition section
        sb.append(DocumentationMarkup.DEFINITION_START);
        appendVariableSignature(sb, varDef, project, typeArgMap);
        sb.append(DocumentationMarkup.DEFINITION_END);

        // Content section (doc comment)
        String docComment = varDef.getDocComment();
        if (docComment != null && !docComment.isEmpty()) {
            sb.append(DocumentationMarkup.CONTENT_START);
            sb.append(formatDocComment(docComment));
            sb.append(DocumentationMarkup.CONTENT_END);
        }

        // Sections
        sb.append(DocumentationMarkup.SECTIONS_START);

        VariableKind kind = varDef.getVariableKind();
        String visibility = varDef.getVisibility();
        String kindLabel = getVariableKindLabel(kind);

        if (visibility != null && !visibility.isEmpty()) {
            appendSection(sb, "Kind:", visibility + " " + kindLabel.toLowerCase());
        } else {
            appendSection(sb, "Kind:", kindLabel);
        }

        // Owner information
        switch (kind) {
            case FIELD:
                String className = varDef.getContainingClassName();
                if (className != null && !className.isEmpty()) {
                    appendSectionWithLink(sb, "Class:", className, LINK_TYPE + className);
                }
                break;
            case LOCAL:
            case LOOP_VAR:
                String funcName = varDef.getContainingFunctionName();
                if (funcName != null && !funcName.isEmpty()) {
                    appendSection(sb, "Function:", funcName);
                }
                break;
            case PARAMETER:
                String paramFunc = varDef.getContainingScopeName();
                if (paramFunc != null && !paramFunc.isEmpty()) {
                    appendSection(sb, "Parameter of:", paramFunc);
                }
                break;
            default:
                break;
        }

        sb.append(DocumentationMarkup.SECTIONS_END);

        // Source location
        appendSourceLocation(sb, varDef);

        return sb.toString();
    }

    private String generatePropertyDoc(PascalProperty prop) {
        return generatePropertyDoc(prop, null);
    }

    private String generatePropertyDoc(PascalProperty prop, @Nullable Map<String, String> typeArgMap) {
        StringBuilder sb = new StringBuilder();
        Project project = prop.getProject();

        // Definition section
        sb.append(DocumentationMarkup.DEFINITION_START);
        appendPropertySignature(sb, prop, project, typeArgMap);
        sb.append(DocumentationMarkup.DEFINITION_END);

        // Content section (doc comment)
        String doc = prop.getDocComment();
        if (doc != null && !doc.isEmpty()) {
            sb.append(DocumentationMarkup.CONTENT_START);
            sb.append(formatDocComment(doc));
            sb.append(DocumentationMarkup.CONTENT_END);
        }

        // Sections
        sb.append(DocumentationMarkup.SECTIONS_START);

        String visibility = prop.getVisibility();
        if (visibility != null && !visibility.isEmpty()) {
            appendSection(sb, "Visibility:", visibility);
        }

        String className = prop.getContainingClassName();
        if (className != null) {
            appendSectionWithLink(sb, "Class:", className, LINK_TYPE + className);
        }

        sb.append(DocumentationMarkup.SECTIONS_END);

        // Source location
        appendSourceLocation(sb, prop);

        return sb.toString();
    }

    private String generateRoutineDoc(PascalRoutine routine) {
        return generateRoutineDoc(routine, null);
    }

    private String generateRoutineDoc(PascalRoutine routine, @Nullable Map<String, String> typeArgMap) {
        StringBuilder sb = new StringBuilder();
        Project project = routine.getProject();

        // Definition section with syntax highlighting
        sb.append(DocumentationMarkup.DEFINITION_START);
        appendHighlightedRoutineSignature(sb, routine, project, typeArgMap);
        sb.append(DocumentationMarkup.DEFINITION_END);

        // Get doc comment (check counterpart if needed)
        String doc = routine.getDocComment();
        if (doc == null) {
            PascalRoutine counterpart = routine.isImplementation() ? routine.getDeclaration() : routine.getImplementation();
            if (counterpart != null) {
                doc = counterpart.getDocComment();
            }
        }

        // Content section (description part of doc comment)
        if (doc != null && !doc.isEmpty()) {
            String description = extractDescription(doc);
            if (!description.isEmpty()) {
                sb.append(DocumentationMarkup.CONTENT_START);
                sb.append(formatDocComment(description));
                sb.append(DocumentationMarkup.CONTENT_END);
            }
        }

        // Sections
        sb.append(DocumentationMarkup.SECTIONS_START);

        // Parameters section
        if (doc != null) {
            Map<String, String> paramDocs = parseParamDocs(doc);
            if (!paramDocs.isEmpty()) {
                StringBuilder paramContent = new StringBuilder();
                for (Map.Entry<String, String> entry : paramDocs.entrySet()) {
                    paramContent.append("<p><code>").append(escapeHtml(entry.getKey())).append("</code>");
                    if (!entry.getValue().isEmpty()) {
                        paramContent.append(" &ndash; ").append(escapeHtml(entry.getValue().trim()));
                    }
                    paramContent.append("</p>");
                }
                appendSectionRaw(sb, "Params:", paramContent.toString());
            }

            // Returns section
            String returns = parseReturns(doc);
            if (returns != null && !returns.isEmpty()) {
                appendSection(sb, "Returns:", returns.trim());
            }
        }

        // Visibility
        String visibility = routine.getVisibility();
        if (visibility != null && !visibility.isEmpty()) {
            appendSection(sb, "Visibility:", visibility);
        }

        // Containing class
        PascalTypeDefinition containingClass = routine.getContainingClass();
        if (containingClass != null) {
            String className = containingClass.getName();
            appendSectionWithLink(sb, "Class:", className, LINK_TYPE + className);
        }

        sb.append(DocumentationMarkup.SECTIONS_END);

        // Source location
        appendSourceLocation(sb, routine);

        return sb.toString();
    }

    private String generateBuiltInDoc(String name, PsiElement context) {
        StringBuilder sb = new StringBuilder();

        // Definition section
        sb.append(DocumentationMarkup.DEFINITION_START);
        if (DelphiBuiltIns.isBuiltInFunction(name)) {
            sb.append("<b>function</b> ").append(escapeHtml(name));
        } else if (DelphiBuiltIns.isBuiltInType(name)) {
            sb.append("<b>type</b> ").append(escapeHtml(name));
        } else if (DelphiBuiltIns.isBuiltInConstant(name)) {
            sb.append("<b>const</b> ").append(escapeHtml(name));
        } else {
            sb.append(escapeHtml(name));
        }
        sb.append(DocumentationMarkup.DEFINITION_END);

        // Content section (description)
        String description = getBuiltInDescription(name);
        if (!description.isEmpty()) {
            sb.append(DocumentationMarkup.CONTENT_START);
            sb.append(description);
            sb.append(DocumentationMarkup.CONTENT_END);
        }

        // Sections
        sb.append(DocumentationMarkup.SECTIONS_START);
        if (DelphiBuiltIns.isBuiltInFunction(name)) {
            appendSection(sb, "Kind:", "Built-in Function");
        } else if (DelphiBuiltIns.isBuiltInType(name)) {
            appendSection(sb, "Kind:", "Built-in Type");
        } else if (DelphiBuiltIns.isBuiltInConstant(name)) {
            appendSection(sb, "Kind:", "Built-in Constant");
        } else {
            appendSection(sb, "Kind:", "Built-in");
        }
        appendSection(sb, "Unit:", "System (implicit)");
        sb.append(DocumentationMarkup.SECTIONS_END);

        return sb.toString();
    }

    private String generateUnresolvedMemberDoc(String memberName, PsiElement dotElement, PsiElement context) {
        StringBuilder sb = new StringBuilder();

        // Definition section
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append(escapeHtml(memberName));
        sb.append(DocumentationMarkup.DEFINITION_END);

        // Try to find qualifier type
        PsiElement qualifier = com.intellij.psi.util.PsiTreeUtil.prevLeaf(dotElement);
        while (qualifier != null && (qualifier instanceof PsiWhiteSpace || qualifier instanceof com.intellij.psi.PsiComment)) {
            qualifier = com.intellij.psi.util.PsiTreeUtil.prevLeaf(qualifier);
        }

        String qualifierTypeName = null;
        if (qualifier != null) {
            PsiReference[] refs = qualifier.getReferences();
            for (PsiReference ref : refs) {
                PsiElement resolved = ref.resolve();
                if (resolved instanceof PascalVariableDefinition) {
                    qualifierTypeName = ((PascalVariableDefinition) resolved).getTypeName();
                    break;
                } else if (resolved instanceof PascalProperty) {
                    qualifierTypeName = ((PascalProperty) resolved).getTypeName();
                    break;
                } else if (resolved instanceof PascalRoutine) {
                    qualifierTypeName = ((PascalRoutine) resolved).getReturnTypeName();
                    break;
                } else if (resolved instanceof PascalTypeDefinition) {
                    qualifierTypeName = ((PascalTypeDefinition) resolved).getName();
                    break;
                }
            }
        }

        // Content section
        sb.append(DocumentationMarkup.CONTENT_START);
        if (qualifierTypeName != null) {
            sb.append("Could not find member '<b>").append(escapeHtml(memberName)).append("</b>' ");
            sb.append("in type '");
            appendTypeLink(sb, qualifierTypeName);
            sb.append("' or its ancestors.");
            sb.append("<p>The type might not be indexed, or the member is defined in a base class that wasn't found.</p>");
        } else {
            sb.append("Could not resolve the qualifier expression.");
        }
        sb.append(DocumentationMarkup.CONTENT_END);

        // Sections
        sb.append(DocumentationMarkup.SECTIONS_START);
        appendSection(sb, "Kind:", "Member (unresolved)");
        if (qualifierTypeName != null) {
            appendSectionWithLink(sb, "Qualifier type:", qualifierTypeName, LINK_TYPE + qualifierTypeName);
        }
        sb.append(DocumentationMarkup.SECTIONS_END);

        return sb.toString();
    }

    // ==================== Signature Building Methods ====================

    private void appendHighlightedTypeSignature(StringBuilder sb, PascalTypeDefinition typeDef, Project project) {
        // Build the signature text
        String signature = buildTypeSignature(typeDef);

        // Try HtmlSyntaxInfoUtil for syntax highlighting
        try {
            HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                    sb, project, PascalLanguage.INSTANCE, signature, 1.0f);
        } catch (Exception e) {
            // Fallback to manual highlighting
            appendManuallyHighlightedType(sb, typeDef);
        }
    }

    private void appendHighlightedRoutineSignature(StringBuilder sb, PascalRoutine routine, Project project) {
        appendHighlightedRoutineSignature(sb, routine, project, null);
    }

    private void appendHighlightedRoutineSignature(StringBuilder sb, PascalRoutine routine, Project project, @Nullable Map<String, String> typeArgMap) {
        // Build the signature text
        String signature = buildRoutineSignature(routine);

        // Apply generic substitution to the return type in the signature
        if (typeArgMap != null && !typeArgMap.isEmpty()) {
            signature = applyGenericSubstitutionToSignature(signature, typeArgMap);
        }

        // Try HtmlSyntaxInfoUtil for syntax highlighting
        try {
            HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                    sb, project, PascalLanguage.INSTANCE, signature, 1.0f);
        } catch (Exception e) {
            // Fallback to manual highlighting
            appendManuallyHighlightedRoutine(sb, routine);
        }
    }

    /**
     * Apply generic type parameter substitution to a routine signature string.
     * Replaces type parameter names (e.g., "T") with their concrete types (e.g., "TRide")
     * in the return type position (after the last ":").
     */
    private String applyGenericSubstitutionToSignature(String signature, Map<String, String> typeArgMap) {
        // Find the return type portion: after the closing paren and last ":"
        // e.g., "function GetItem(Index: Integer): T;" → replace T with TRide
        int lastColon = signature.lastIndexOf(':');
        if (lastColon < 0) return signature;

        // Only substitute after the last colon if it comes after the closing paren
        int lastParen = signature.lastIndexOf(')');
        if (lastParen >= 0 && lastColon < lastParen) return signature;

        String beforeReturn = signature.substring(0, lastColon + 1);
        String returnPart = signature.substring(lastColon + 1);

        // Replace type parameter names in the return part
        for (Map.Entry<String, String> entry : typeArgMap.entrySet()) {
            // Use word boundary replacement to avoid partial matches
            returnPart = returnPart.replaceAll("\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b", entry.getValue());
        }

        return beforeReturn + returnPart;
    }

    private String buildTypeSignature(PascalTypeDefinition typeDef) {
        return typeDef.getDeclarationHeader();
    }

    private String buildRoutineSignature(PascalRoutine routine) {
        StringBuilder sig = new StringBuilder();
        ASTNode node = routine.getNode();
        ASTNode child = node.getFirstChildNode();

        while (child != null) {
            IElementType type = child.getElementType();
            String text = child.getText();

            if (type == PascalTokenTypes.SEMI) {
                sig.append(";");
                break;
            }

            // Stop at body start keywords
            if (type == PascalTokenTypes.KW_BEGIN ||
                type == PascalTokenTypes.KW_VAR ||
                type == PascalTokenTypes.KW_CONST ||
                type == PascalTokenTypes.KW_TYPE) {
                break;
            }

            sig.append(text);
            child = child.getTreeNext();
        }

        return sig.toString().trim();
    }

    private void appendManuallyHighlightedType(StringBuilder sb, PascalTypeDefinition typeDef) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        ASTNode node = typeDef.getNode();
        ASTNode child = node.getFirstChildNode();
        TypeKind kind = typeDef.getTypeKind();
        boolean foundKindKeyword = false;

        while (child != null) {
            IElementType type = child.getElementType();
            String text = child.getText();

            // Skip comments in signature
            if (type == PascalTokenTypes.LINE_COMMENT || type == PascalTokenTypes.BLOCK_COMMENT) {
                child = child.getTreeNext();
                continue;
            }

            if (type == PascalTokenTypes.SEMI) {
                sb.append(";");
                break;
            }

            if (kind == TypeKind.CLASS || kind == TypeKind.RECORD || kind == TypeKind.INTERFACE) {
                if (foundKindKeyword && isBodyStartKeyword(type)) {
                    break;
                }
                if (type == PascalTokenTypes.KW_CLASS || type == PascalTokenTypes.KW_RECORD || type == PascalTokenTypes.KW_INTERFACE) {
                    foundKindKeyword = true;
                }
            }

            if (isKeyword(type)) {
                appendStyled(sb, text, PascalSyntaxHighlighter.KEYWORD, scheme, true);
            } else if (type == PascalTokenTypes.IDENTIFIER) {
                if (text.equals(typeDef.getName())) {
                    TextAttributesKey colorKey = getColorForTypeKind(kind);
                    appendStyled(sb, text, colorKey != null ? colorKey : PascalSyntaxHighlighter.IDENTIFIER, scheme, true);
                } else {
                    // Type reference - make it clickable
                    appendTypeLink(sb, text);
                }
            } else if (type == PascalTokenTypes.STRING_LITERAL) {
                appendStyled(sb, text, PascalSyntaxHighlighter.STRING, scheme, false);
            } else {
                // Collapse multiple newlines (left over from skipped comments) into one
                String collapsed = text.replaceAll("\\n\\s*\\n+", "\n");
                sb.append(escapeHtml(collapsed));
            }

            child = child.getTreeNext();
        }
    }

    private void appendManuallyHighlightedRoutine(StringBuilder sb, PascalRoutine routine) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        ASTNode node = routine.getNode();
        ASTNode child = node.getFirstChildNode();
        boolean inReturnType = false;
        boolean inParams = false;

        while (child != null) {
            IElementType type = child.getElementType();
            String text = child.getText();

            if (type == PascalTokenTypes.SEMI) {
                sb.append(";");
                break;
            }

            if (type == PascalTokenTypes.LPAREN) {
                inParams = true;
                sb.append("(");
            } else if (type == PascalTokenTypes.RPAREN) {
                inParams = false;
                sb.append(")");
            } else if (type == PascalTokenTypes.COLON && !inParams) {
                inReturnType = true;
                sb.append(": ");
            } else if (isKeyword(type)) {
                appendStyled(sb, text, PascalSyntaxHighlighter.KEYWORD, scheme, true);
            } else if (type == PascalTokenTypes.IDENTIFIER) {
                if (text.equals(routine.getName())) {
                    appendStyled(sb, text, PascalSyntaxHighlighter.ROUTINE_DECLARATION, scheme, true);
                } else if (inReturnType || (inParams && isLikelyType(text))) {
                    // Type reference - make it clickable
                    appendTypeLink(sb, text);
                } else if (inParams) {
                    appendStyled(sb, text, PascalSyntaxHighlighter.VAR_PARAMETER, scheme, false);
                } else {
                    sb.append(escapeHtml(text));
                }
            } else if (type == PascalTokenTypes.STRING_LITERAL) {
                appendStyled(sb, text, PascalSyntaxHighlighter.STRING, scheme, false);
            } else {
                sb.append(escapeHtml(text));
            }

            child = child.getTreeNext();
        }
    }

    private void appendVariableSignature(StringBuilder sb, PascalVariableDefinition varDef, Project project) {
        appendVariableSignature(sb, varDef, project, null);
    }

    private void appendVariableSignature(StringBuilder sb, PascalVariableDefinition varDef, Project project, @Nullable Map<String, String> typeArgMap) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        VariableKind kind = varDef.getVariableKind();
        TextAttributesKey colorKey = getColorForVariableKind(kind);

        String name = varDef.getName();
        if (name != null) {
            appendStyled(sb, name, colorKey != null ? colorKey : PascalSyntaxHighlighter.IDENTIFIER, scheme, true);
        }

        String typeName = varDef.getTypeName();
        // Apply generic substitution if available
        if (typeName != null && typeArgMap != null && typeArgMap.containsKey(typeName)) {
            typeName = typeArgMap.get(typeName);
        }
        if (typeName != null && !typeName.isEmpty()) {
            sb.append(": ");
            appendTypeLink(sb, typeName);
        } else {
            // Try to infer type from initializer (inline var)
            PsiFile originFile = varDef.getContainingFile();
            if (originFile != null) {
                PascalTypeDefinition inferredType = MemberChainResolver.getInferredTypeOf(varDef, originFile);
                if (inferredType != null && inferredType.getName() != null) {
                    sb.append(": ");
                    appendTypeLink(sb, inferredType.getName());
                    sb.append(" <span class='").append(DocumentationMarkup.CLASS_GRAYED).append("'>(inferred)</span>");
                }
            }
        }
    }

    private void appendPropertySignature(StringBuilder sb, PascalProperty prop, Project project) {
        appendPropertySignature(sb, prop, project, null);
    }

    private void appendPropertySignature(StringBuilder sb, PascalProperty prop, Project project, @Nullable Map<String, String> typeArgMap) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();

        appendStyled(sb, "property ", PascalSyntaxHighlighter.KEYWORD, scheme, true);
        appendStyled(sb, prop.getName(), PascalSyntaxHighlighter.VAR_FIELD, scheme, true);

        String typeName = prop.getTypeName();
        // Apply generic substitution if available
        if (typeName != null && typeArgMap != null && typeArgMap.containsKey(typeName)) {
            typeName = typeArgMap.get(typeName);
        }
        if (typeName != null) {
            sb.append(": ");
            appendTypeLink(sb, typeName);
        }

        String read = prop.getReadSpecifier();
        if (read != null) {
            sb.append(" ");
            appendStyled(sb, "read ", PascalSyntaxHighlighter.KEYWORD, scheme, false);
            sb.append(escapeHtml(read));
        }

        String write = prop.getWriteSpecifier();
        if (write != null) {
            sb.append(" ");
            appendStyled(sb, "write ", PascalSyntaxHighlighter.KEYWORD, scheme, false);
            sb.append(escapeHtml(write));
        }
    }

    // ==================== Helper Methods ====================

    private void appendTypeLink(StringBuilder sb, String typeName) {
        if (typeName == null || typeName.isEmpty()) return;

        int ltIdx = typeName.indexOf('<');
        if (ltIdx > 0) {
            // Has generic arguments: "TEntityList<TRide>"
            String baseName = typeName.substring(0, ltIdx);
            String genericPart = typeName.substring(ltIdx); // "<TRide>"
            DocumentationManagerUtil.createHyperlink(sb, LINK_TYPE + baseName, baseName, false);
            sb.append(escapeHtml(genericPart));
        } else {
            DocumentationManagerUtil.createHyperlink(sb, LINK_TYPE + typeName, typeName, false);
        }
    }

    private void appendSourceLocation(StringBuilder sb, PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) return;

        String fileName = file.getName();
        String unitName = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            unitName = fileName.substring(0, dotIndex);
        }

        // Use CLASS_BOTTOM for the source location bar
        sb.append("<div class='").append(DocumentationMarkup.CLASS_BOTTOM).append("'>");

        // Unit name as clickable link
        sb.append("<a href='").append(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL);
        sb.append(LINK_UNIT).append(unitName).append("'>");
        sb.append(escapeHtml(unitName));
        sb.append("</a>");

        // File name in grayed style
        sb.append(" <span class='").append(DocumentationMarkup.CLASS_GRAYED).append("'>");
        sb.append("(").append(escapeHtml(fileName)).append(")");
        sb.append("</span>");

        sb.append("</div>");
    }

    private void appendSection(StringBuilder sb, String label, String value) {
        sb.append(DocumentationMarkup.SECTION_HEADER_START);
        sb.append(label);
        sb.append(DocumentationMarkup.SECTION_SEPARATOR);
        sb.append("<p>").append(escapeHtml(value)).append("</p>");
        sb.append(DocumentationMarkup.SECTION_END);
    }

    private void appendSectionRaw(StringBuilder sb, String label, String valueHtml) {
        sb.append(DocumentationMarkup.SECTION_HEADER_START);
        sb.append(label);
        sb.append(DocumentationMarkup.SECTION_SEPARATOR);
        sb.append(valueHtml);
        sb.append(DocumentationMarkup.SECTION_END);
    }

    private void appendSectionWithLink(StringBuilder sb, String label, String text, String link) {
        sb.append(DocumentationMarkup.SECTION_HEADER_START);
        sb.append(label);
        sb.append(DocumentationMarkup.SECTION_SEPARATOR);
        sb.append("<p>");
        DocumentationManagerUtil.createHyperlink(sb, link, text, false);
        sb.append("</p>");
        sb.append(DocumentationMarkup.SECTION_END);
    }

    private void appendStyled(StringBuilder sb, String text, TextAttributesKey key, EditorColorsScheme scheme, boolean bold) {
        TextAttributes attr = scheme.getAttributes(key);
        Color color = attr != null ? attr.getForegroundColor() : scheme.getDefaultForeground();
        if (color == null) color = Color.BLACK;

        sb.append("<span style='color: ").append(colorToHex(color)).append(";");
        if (bold || (attr != null && (attr.getFontType() & Font.BOLD) != 0)) {
            sb.append(" font-weight: bold;");
        }
        sb.append("'>").append(escapeHtml(text)).append("</span>");
    }

    // ==================== Doc Comment Parsing ====================

    private String extractDescription(String docComment) {
        if (docComment == null) return "";

        if (isXmlDoc(docComment)) {
            // Extract <summary> content
            Matcher summaryMatcher = XMLDOC_SUMMARY_PATTERN.matcher(docComment);
            if (summaryMatcher.find()) {
                return summaryMatcher.group(1).trim();
            }
            // Fallback: return text minus known XMLDoc tags
            return docComment
                    .replaceAll("</?(?:summary|remarks|param|returns|value|exception|example|code|see|c)[^>]*>", "")
                    .trim();
        }

        // Remove @param, @returns, @see sections
        String desc = docComment
                .replaceAll("@param\\s+\\w+\\s+[^@]*", "")
                .replaceAll("@returns?\\s+[^@]*", "")
                .replaceAll("@see\\s+[^@]*", "")
                .trim();
        return desc;
    }

    private Map<String, String> parseParamDocs(String docComment) {
        Map<String, String> params = new LinkedHashMap<>();
        if (docComment == null) return params;

        if (isXmlDoc(docComment)) {
            Matcher matcher = XMLDOC_PARAM_PATTERN.matcher(docComment);
            while (matcher.find()) {
                params.put(matcher.group(1), matcher.group(2).trim());
            }
            return params;
        }

        Matcher matcher = PARAM_PATTERN.matcher(docComment);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String paramDesc = matcher.group(2).trim();
            params.put(paramName, paramDesc);
        }
        return params;
    }

    private String parseReturns(String docComment) {
        if (docComment == null) return null;

        if (isXmlDoc(docComment)) {
            Matcher matcher = XMLDOC_RETURNS_PATTERN.matcher(docComment);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            return null;
        }

        Matcher matcher = RETURNS_PATTERN.matcher(docComment);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String formatDocComment(String docComment) {
        if (docComment == null) return "";
        if (isXmlDoc(docComment)) {
            return formatXmlDoc(docComment);
        }
        return escapeHtml(docComment).replace("\n", "<br/>");
    }

    private boolean isXmlDoc(String docComment) {
        return docComment != null && XMLDOC_DETECT_PATTERN.matcher(docComment).find();
    }

    private String formatXmlDoc(String docComment) {
        StringBuilder sb = new StringBuilder();

        // Summary
        Matcher summaryMatcher = XMLDOC_SUMMARY_PATTERN.matcher(docComment);
        if (summaryMatcher.find()) {
            sb.append("<p>").append(processInlineXmlDoc(summaryMatcher.group(1).trim())).append("</p>");
        }

        // Remarks
        Matcher remarksMatcher = XMLDOC_REMARKS_PATTERN.matcher(docComment);
        if (remarksMatcher.find()) {
            sb.append("<p><i>Remarks:</i> ").append(processInlineXmlDoc(remarksMatcher.group(1).trim())).append("</p>");
        }

        // Value (used for properties)
        Matcher valueMatcher = XMLDOC_VALUE_PATTERN.matcher(docComment);
        if (valueMatcher.find()) {
            sb.append("<p>").append(processInlineXmlDoc(valueMatcher.group(1).trim())).append("</p>");
        }

        // Exceptions
        Matcher exceptionMatcher = XMLDOC_EXCEPTION_PATTERN.matcher(docComment);
        boolean hasExceptions = false;
        while (exceptionMatcher.find()) {
            if (!hasExceptions) {
                sb.append("<p><b>Raises:</b></p>");
                hasExceptions = true;
            }
            String exType = exceptionMatcher.group(1);
            String exDesc = exceptionMatcher.group(2).trim();
            sb.append("<p>");
            appendTypeLink(sb, exType);
            if (!exDesc.isEmpty()) {
                sb.append(" &mdash; ").append(processInlineXmlDoc(exDesc));
            }
            sb.append("</p>");
        }

        // Example
        Matcher exampleMatcher = XMLDOC_EXAMPLE_PATTERN.matcher(docComment);
        if (exampleMatcher.find()) {
            String exampleContent = exampleMatcher.group(1).trim();
            sb.append("<p><b>Example:</b></p>");
            sb.append("<pre><code>").append(escapeHtml(exampleContent)).append("</code></pre>");
        }

        return sb.toString();
    }

    private String processInlineXmlDoc(String text) {
        // Process <c>...</c> -> inline code
        String result = XMLDOC_INLINE_CODE_PATTERN.matcher(text).replaceAll(m ->
                "<code>" + escapeHtml(m.group(1)) + "</code>");

        // Process <code>...</code> -> pre block
        result = XMLDOC_CODE_PATTERN.matcher(result).replaceAll(m ->
                "<pre><code>" + escapeHtml(m.group(1)) + "</code></pre>");

        // Process <see cref="X">text</see> -> type link with text
        result = XMLDOC_SEE_WITH_TEXT_PATTERN.matcher(result).replaceAll(m -> {
            StringBuilder linkSb = new StringBuilder();
            DocumentationManagerUtil.createHyperlink(linkSb, LINK_TYPE + m.group(1), m.group(2), false);
            return linkSb.toString();
        });

        // Process <see cref="X"/> -> type link
        result = XMLDOC_SEE_SELF_CLOSING_PATTERN.matcher(result).replaceAll(m -> {
            StringBuilder linkSb = new StringBuilder();
            DocumentationManagerUtil.createHyperlink(linkSb, LINK_TYPE + m.group(1), m.group(1), false);
            return linkSb.toString();
        });

        // Strip any remaining unrecognized XML tags
        result = result.replaceAll("</?[a-zA-Z][^>]*>", "");

        // Escape remaining plain text (but preserve already-processed HTML)
        // Since we've already inserted HTML tags, we can't bulk-escape here.
        // The input from XMLDoc comments should be safe — just trim up newlines.
        result = result.replace("\n", " ").replaceAll("\\s+", " ").trim();

        return result;
    }

    // ==================== Type/Kind Helpers ====================

    private boolean isLikelyType(String text) {
        if (text == null || text.isEmpty()) return false;
        // Common Pascal type conventions
        if (text.startsWith("T") && text.length() > 1 && Character.isUpperCase(text.charAt(1))) return true;
        if (text.startsWith("I") && text.length() > 1 && Character.isUpperCase(text.charAt(1))) return true;
        if (text.startsWith("E") && text.length() > 1 && Character.isUpperCase(text.charAt(1))) return true;
        if (text.startsWith("P") && text.length() > 1 && Character.isUpperCase(text.charAt(1))) return true;
        // Built-in types
        return isPrimitiveType(text) || DelphiBuiltIns.isBuiltInType(text);
    }

    private boolean isPrimitiveType(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.equals("integer") || lower.equals("string") || lower.equals("boolean") ||
               lower.equals("byte") || lower.equals("word") || lower.equals("cardinal") ||
               lower.equals("int64") || lower.equals("single") || lower.equals("double") ||
               lower.equals("extended") || lower.equals("char") || lower.equals("shortint") ||
               lower.equals("smallint") || lower.equals("longint") || lower.equals("longword") ||
               lower.equals("real") || lower.equals("pointer") || lower.equals("variant");
    }

    private String getVariableKindLabel(VariableKind kind) {
        if (kind == null) return "Variable";
        switch (kind) {
            case GLOBAL: return "Global Variable";
            case LOCAL: return "Local Variable";
            case PARAMETER: return "Parameter";
            case FIELD: return "Field";
            case CONSTANT: return "Constant";
            case THREADVAR: return "Thread Variable";
            case LOOP_VAR: return "Loop Variable";
            default: return "Variable";
        }
    }

    private TextAttributesKey getColorForVariableKind(VariableKind kind) {
        if (kind == null) return null;
        switch (kind) {
            case GLOBAL: return PascalSyntaxHighlighter.VAR_GLOBAL;
            case LOCAL: return PascalSyntaxHighlighter.VAR_LOCAL;
            case PARAMETER: return PascalSyntaxHighlighter.VAR_PARAMETER;
            case FIELD: return PascalSyntaxHighlighter.VAR_FIELD;
            case CONSTANT: return PascalSyntaxHighlighter.VAR_CONSTANT;
            case THREADVAR: return PascalSyntaxHighlighter.VAR_THREADVAR;
            case LOOP_VAR: return PascalSyntaxHighlighter.VAR_LOCAL;
            default: return null;
        }
    }

    private TextAttributesKey getColorForTypeKind(TypeKind kind) {
        if (kind == null) return null;
        switch (kind) {
            case CLASS: return PascalSyntaxHighlighter.TYPE_CLASS;
            case RECORD: return PascalSyntaxHighlighter.TYPE_RECORD;
            case INTERFACE: return PascalSyntaxHighlighter.TYPE_INTERFACE;
            case PROCEDURAL: return PascalSyntaxHighlighter.TYPE_PROCEDURAL;
            case ENUM: return PascalSyntaxHighlighter.TYPE_ENUM;
            case ALIAS: return PascalSyntaxHighlighter.TYPE_SIMPLE;
            default: return null;
        }
    }

    private String getBuiltInDescription(String name) {
        String lower = name.toLowerCase();

        // Functions
        switch (lower) {
            case "assigned": return "Tests whether a pointer or procedural variable is <code>nil</code>. Returns <code>True</code> if not <code>nil</code>.";
            case "length": return "Returns the number of elements in an array or characters in a string.";
            case "setlength": return "Sets the length of a dynamic array or string.";
            case "inc": return "Increments a variable by 1 or a specified amount.";
            case "dec": return "Decrements a variable by 1 or a specified amount.";
            case "ord": return "Returns the ordinal value of an ordinal-type expression.";
            case "chr": return "Returns the character for a specified ordinal value.";
            case "high": return "Returns the highest value in the range of an ordinal type or array.";
            case "low": return "Returns the lowest value in the range of an ordinal type or array.";
            case "sizeof": return "Returns the size in bytes of a type or variable.";
            case "copy": return "Returns a substring or a copy of part of an array.";
            case "pos": return "Searches for a substring in a string and returns its position.";
            case "inttostr": return "Converts an integer to its string representation.";
            case "strtoint": return "Converts a string to an integer.";
            case "format": return "Formats a string with the specified arguments.";
            case "freeandnil": return "Frees an object and sets its reference to <code>nil</code>.";
        }

        // Types
        switch (lower) {
            case "tobject": return "The ultimate ancestor class. All classes inherit from TObject.";
            case "exception": return "Base class for all exception types.";
            case "string": return "A managed string type (UnicodeString by default).";
            case "integer": return "A signed 32-bit integer type.";
            case "boolean": return "A Boolean type with values <code>True</code> and <code>False</code>.";
            case "tstrings": return "Abstract base class for string list classes.";
            case "tstringlist": return "A list of strings with sorting and searching capabilities.";
            case "tlist": return "A list of pointers.";
            case "tstream": return "Abstract base class for streaming data.";
            case "tcomponent": return "Base class for all components.";
            case "tpersistent": return "Base class for objects that have published properties.";
        }

        if (DelphiBuiltIns.isBuiltInFunction(name)) {
            return "Built-in function from the System unit.";
        } else if (DelphiBuiltIns.isBuiltInType(name)) {
            return "Built-in type from the System unit.";
        } else if (DelphiBuiltIns.isBuiltInConstant(name)) {
            return "Built-in constant from the System unit.";
        }
        return "";
    }

    // ==================== Token Helpers ====================

    private boolean isBodyStartKeyword(IElementType type) {
        if (type == null) return false;
        String s = type.toString();
        if (s.startsWith("PascalTokenType.")) {
            s = s.substring("PascalTokenType.".length());
        }
        return s.equals("PRIVATE") || s.equals("PROTECTED") || s.equals("PUBLIC") || s.equals("PUBLISHED") ||
               s.equals("STRICT") || s.equals("VAR") || s.equals("CONST") || s.equals("TYPE") ||
               s.equals("PROCEDURE") || s.equals("FUNCTION") || s.equals("CONSTRUCTOR") || s.equals("DESTRUCTOR") ||
               s.equals("PROPERTY") || s.equals("OPERATOR") || s.equals("BEGIN") ||
               s.equals("VISIBILITY_SECTION") || s.equals("CLASS_BODY") || s.equals("RECORD_BODY") || s.equals("INTERFACE_BODY");
    }

    private boolean isKeyword(IElementType type) {
        if (!(type instanceof PascalTokenType)) return false;
        if (type == PascalTokenTypes.IDENTIFIER) return false;
        if (type == PascalTokenTypes.STRING_LITERAL) return false;
        if (type == PascalTokenTypes.INTEGER_LITERAL) return false;
        if (type == PascalTokenTypes.FLOAT_LITERAL) return false;
        if (type == PascalTokenTypes.HEX_LITERAL) return false;
        if (type == PascalTokenTypes.CHAR_LITERAL) return false;
        if (type == PascalTokenTypes.LINE_COMMENT) return false;
        if (type == PascalTokenTypes.BLOCK_COMMENT) return false;
        if (type == PascalTokenTypes.COMPILER_DIRECTIVE) return false;

        String name = type.toString();
        if (name.equals("LPAREN") || name.equals("RPAREN") || name.equals("LBRACKET") || name.equals("RBRACKET") ||
            name.equals("COMMA") || name.equals("SEMI") || name.equals("DOT") || name.equals("COLON") ||
            name.equals("EQ") || name.equals("PLUS") || name.equals("MINUS") || name.equals("MULT") || name.equals("DIVIDE")) {
            return false;
        }

        return true;
    }

    // ==================== Utility Methods ====================

    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    @Override
    @Nullable
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        if (element instanceof PascalTypeDefinition) {
            PascalTypeDefinition typeDef = (PascalTypeDefinition) element;
            return typeDef.getTypeKind().name().toLowerCase() + " " + typeDef.getName();
        }
        if (element instanceof PascalVariableDefinition) {
            PascalVariableDefinition varDef = (PascalVariableDefinition) element;
            String typeName = varDef.getTypeName();
            String kindLabel = getVariableKindLabel(varDef.getVariableKind()).toLowerCase();
            if (typeName != null && !typeName.isEmpty()) {
                return kindLabel + " " + varDef.getName() + ": " + typeName;
            }
            return kindLabel + " " + varDef.getName();
        }
        if (element instanceof PascalProperty) {
            PascalProperty prop = (PascalProperty) element;
            return "property " + prop.getName() + ": " + prop.getTypeName();
        }
        if (element instanceof PascalRoutine) {
            PascalRoutine routine = (PascalRoutine) element;
            return routine.getText().split("begin|var|const|type|\\{|\\/\\/|\\(\\*", 2)[0].trim();
        }
        return null;
    }
}
