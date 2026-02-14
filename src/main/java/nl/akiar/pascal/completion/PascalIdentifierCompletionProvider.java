package nl.akiar.pascal.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.*;
import nl.akiar.pascal.resolution.DelphiBuiltIns;
import nl.akiar.pascal.stubs.PascalRoutineIndex;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.stubs.PascalVariableIndex;
import nl.akiar.pascal.uses.PascalUsesClauseInfo;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Provides general identifier completion in code contexts (not after DOT,
 * not in TYPE_REFERENCE, not in USES_SECTION).
 * Includes local variables, class members, types, routines, built-ins, and keywords.
 *
 * Uses a two-tier approach:
 * - Tier 1 (always): PSI tree walk for locals/parameters, class members, built-ins, keywords
 * - Tier 2 (deferred): stub index queries for types, routines, global variables
 *   Only runs on explicit invocation (Ctrl+Space) or when prefix >= 2 chars
 */
public class PascalIdentifierCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();
        Project project = position.getProject();

        if (DumbService.isDumb(project)) return;

        // Skip if after DOT (handled by member provider)
        PsiElement prevLeaf = PsiTreeUtil.prevLeaf(position);
        while (prevLeaf != null && prevLeaf.getNode().getElementType() == PascalTokenTypes.WHITE_SPACE) {
            prevLeaf = PsiTreeUtil.prevLeaf(prevLeaf);
        }
        if (prevLeaf != null && prevLeaf.getNode().getElementType() == PascalTokenTypes.DOT) return;

        // Skip if inside TYPE_REFERENCE (handled by type provider)
        if (isInsideElementType(position, PascalElementTypes.TYPE_REFERENCE)) return;

        // Skip if inside USES_SECTION or UNIT_REFERENCE (handled by uses provider)
        if (isInsideElementType(position, PascalElementTypes.USES_SECTION)) return;
        if (isInsideElementType(position, PascalElementTypes.UNIT_REFERENCE)) return;

        int offset = parameters.getOffset();
        Set<String> addedNames = new HashSet<>();

        int invocationCount = parameters.getInvocationCount();
        boolean isAutopopup = invocationCount == 0;
        String prefix = result.getPrefixMatcher().getPrefix();

        // === Tier 1: Always run (PSI-local, instant) ===

        // 1. Local variables and parameters via PSI tree walk (no index queries)
        addLocalVariablesPsi(position, file, offset, addedNames, result);

        // 2. Class members (if inside a method implementation)
        addClassMembers(position, file, invocationCount, addedNames, result);

        // 3. Built-in functions
        addBuiltInFunctions(addedNames, result);

        // 4. Built-in types
        addBuiltInTypes(addedNames, result);

        // 5. Keywords (context-dependent)
        addKeywords(position, addedNames, result);

        // === Tier 2: Only when user has typed 2+ chars or explicitly invoked ===
        if (isAutopopup && prefix.length() < 2) return;

        String prefixLower = prefix.toLowerCase();

        PascalUsesClauseInfo usesInfo = PascalUsesClauseInfo.parse(file);
        List<String> availableUnits = usesInfo.getAvailableUnits(offset);
        Set<String> availableUnitsLower = new HashSet<>();
        for (String unit : availableUnits) {
            availableUnitsLower.add(unit.toLowerCase());
        }
        List<String> scopes = PascalSourcePathsSettings.getInstance(project).getUnitScopeNames();

        // 6. Types in scope (prefix-filtered)
        addTypesInScope(project, file, offset, availableUnitsLower, scopes, prefixLower, addedNames, result);

        // 7. Routines in scope (prefix-filtered)
        addRoutinesInScope(project, file, offset, availableUnitsLower, scopes, prefixLower, addedNames, result);

        // 8. Global variables/constants in scope (prefix-filtered)
        addGlobalVariables(project, file, offset, availableUnitsLower, scopes, prefixLower, addedNames, result);
    }

    /**
     * Walk the PSI tree from caret position upward to collect local variables and parameters.
     * This is O(local scope size) instead of O(all project variables).
     */
    private void addLocalVariablesPsi(PsiElement position, PsiFile file, int offset,
                                      Set<String> addedNames, CompletionResultSet result) {
        PsiElement scope = file.findElementAt(offset);
        if (scope == null) return;

        // Walk upward through parent scopes
        while (scope != null && !(scope instanceof PsiFile)) {
            // Collect variable definitions at this scope level
            for (PascalVariableDefinition varDef : PsiTreeUtil.findChildrenOfType(scope, PascalVariableDefinition.class)) {
                String name = varDef.getName();
                if (name == null || name.isEmpty()) continue;
                if (addedNames.contains(name.toLowerCase())) continue;

                VariableKind kind = varDef.getVariableKind();

                // Only include locals, parameters, loop vars, and same-file globals/constants
                if (kind == VariableKind.FIELD) continue; // Handled by class members

                if (kind == VariableKind.LOCAL || kind == VariableKind.PARAMETER || kind == VariableKind.LOOP_VAR) {
                    // Don't offer variables declared after the caret
                    if (varDef.getTextOffset() > offset) continue;
                }

                addedNames.add(name.toLowerCase());
                String typeName = varDef.getTypeName();
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withIcon(kind == VariableKind.CONSTANT ? AllIcons.Nodes.Constant : AllIcons.Nodes.Variable)
                        .withTypeText(typeName != null ? typeName : "", true);

                double priority = (kind == VariableKind.LOCAL || kind == VariableKind.PARAMETER
                        || kind == VariableKind.LOOP_VAR) ? 200 : 100;
                result.addElement(PrioritizedLookupElement.withPriority(lookup, priority));
            }

            scope = scope.getParent();
        }
    }

    private void addClassMembers(PsiElement position, PsiFile file, int invocationCount,
                                 Set<String> addedNames, CompletionResultSet result) {
        int offset = position.getTextOffset();
        PsiElement elementAtOffset = file.findElementAt(offset);

        // Find containing routine and its class
        PascalRoutine containingRoutine = PsiTreeUtil.getParentOfType(elementAtOffset, PascalRoutine.class);
        PascalTypeDefinition containingClass = null;

        // Walk up through anonymous routines to find the owning named method
        PascalRoutine r = containingRoutine;
        while (r != null) {
            containingClass = r.getContainingClass();
            if (containingClass != null) break;
            r = PsiTreeUtil.getParentOfType(r, PascalRoutine.class);
        }

        if (containingClass == null) return;

        // On autopopup/first invocation: direct members only; on second+: include inherited
        boolean includeInherited = invocationCount >= 2;
        List<PsiElement> members = containingClass.getMembers(includeInherited);
        for (PsiElement member : members) {
            if (!(member instanceof com.intellij.psi.PsiNameIdentifierOwner named)) continue;
            String name = named.getName();
            if (name == null || name.isEmpty()) continue;
            if (addedNames.contains(name.toLowerCase())) continue;

            addedNames.add(name.toLowerCase());
            LookupElementBuilder lookup;
            if (member instanceof PascalRoutine routine) {
                String returnType = routine.getReturnTypeName();
                lookup = LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Method)
                        .withTailText("()", true)
                        .withTypeText(returnType != null ? returnType : "", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getDocument().insertString(ctx.getTailOffset(), "()");
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        });
            } else if (member instanceof PascalProperty prop) {
                lookup = LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Property)
                        .withTypeText(prop.getTypeName() != null ? prop.getTypeName() : "", true);
            } else if (member instanceof PascalVariableDefinition varDef) {
                lookup = LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Field)
                        .withTypeText(varDef.getTypeName() != null ? varDef.getTypeName() : "", true);
            } else {
                continue;
            }
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 150));
        }
    }

    private void addTypesInScope(Project project, PsiFile file, int offset,
                                 Set<String> availableUnitsLower, List<String> scopes,
                                 String prefixLower,
                                 Set<String> addedNames, CompletionResultSet result) {
        Collection<String> allKeys = StubIndex.getInstance().getAllKeys(PascalTypeIndex.KEY, project);
        for (String key : allKeys) {
            // Prefix filter: skip keys that don't match what the user typed
            if (!prefixLower.isEmpty() && !key.toLowerCase().startsWith(prefixLower)) continue;

            Collection<PascalTypeDefinition> types = StubIndex.getElements(
                    PascalTypeIndex.KEY, key, project,
                    GlobalSearchScope.allScope(project), PascalTypeDefinition.class);

            for (PascalTypeDefinition typeDef : types) {
                String name = typeDef.getName();
                if (name == null || name.isEmpty()) continue;
                if (typeDef.isForwardDeclaration()) continue;
                if (addedNames.contains(name.toLowerCase())) continue;

                if (!isInScope(typeDef.getContainingFile(), typeDef.getUnitName(),
                        file, availableUnitsLower, scopes)) continue;

                addedNames.add(name.toLowerCase());
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Class)
                        .withTypeText(typeDef.getUnitName(), true);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 80));
            }
        }
    }

    private void addRoutinesInScope(Project project, PsiFile file, int offset,
                                    Set<String> availableUnitsLower, List<String> scopes,
                                    String prefixLower,
                                    Set<String> addedNames, CompletionResultSet result) {
        Collection<String> allKeys = StubIndex.getInstance().getAllKeys(PascalRoutineIndex.KEY, project);
        for (String key : allKeys) {
            // Prefix filter: skip keys that don't match what the user typed
            if (!prefixLower.isEmpty() && !key.toLowerCase().startsWith(prefixLower)) continue;

            Collection<PascalRoutine> routines = StubIndex.getElements(
                    PascalRoutineIndex.KEY, key, project,
                    GlobalSearchScope.allScope(project), PascalRoutine.class);

            for (PascalRoutine routine : routines) {
                // Skip methods (they're accessed through member completion)
                if (routine.isMethod()) continue;
                // Skip implementations (prefer declarations)
                if (routine.isImplementation() && routine.getDeclaration() != null) continue;

                String name = routine.getName();
                if (name == null || name.isEmpty()) continue;
                if (addedNames.contains(name.toLowerCase())) continue;

                if (!isInScope(routine.getContainingFile(), routine.getUnitName(),
                        file, availableUnitsLower, scopes)) continue;

                addedNames.add(name.toLowerCase());
                String returnType = routine.getReturnTypeName();
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Function)
                        .withTailText("()", true)
                        .withTypeText(returnType != null ? returnType : "", true)
                        .withInsertHandler((ctx, item) -> {
                            ctx.getDocument().insertString(ctx.getTailOffset(), "()");
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                        });
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 80));
            }
        }
    }

    private void addGlobalVariables(Project project, PsiFile file, int offset,
                                    Set<String> availableUnitsLower, List<String> scopes,
                                    String prefixLower,
                                    Set<String> addedNames, CompletionResultSet result) {
        Collection<String> allKeys = StubIndex.getInstance().getAllKeys(PascalVariableIndex.KEY, project);
        for (String key : allKeys) {
            // Prefix filter: skip keys that don't match what the user typed
            if (!prefixLower.isEmpty() && !key.toLowerCase().startsWith(prefixLower)) continue;

            Collection<PascalVariableDefinition> vars = StubIndex.getElements(
                    PascalVariableIndex.KEY, key, project,
                    GlobalSearchScope.allScope(project), PascalVariableDefinition.class);

            for (PascalVariableDefinition varDef : vars) {
                VariableKind kind = varDef.getVariableKind();
                if (kind != VariableKind.GLOBAL && kind != VariableKind.CONSTANT && kind != VariableKind.THREADVAR) continue;

                String name = varDef.getName();
                if (name == null || name.isEmpty()) continue;
                if (addedNames.contains(name.toLowerCase())) continue;

                // Same file already handled by local variables PSI walk
                if (file.equals(varDef.getContainingFile())) continue;

                if (!isInScope(varDef.getContainingFile(), varDef.getUnitName(),
                        file, availableUnitsLower, scopes)) continue;

                addedNames.add(name.toLowerCase());
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withIcon(kind == VariableKind.CONSTANT ? AllIcons.Nodes.Constant : AllIcons.Nodes.Variable)
                        .withTypeText(varDef.getTypeName() != null ? varDef.getTypeName() : "", true);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 60));
            }
        }
    }

    private void addBuiltInFunctions(Set<String> addedNames, CompletionResultSet result) {
        String[] builtInFunctions = {
                "Assigned", "Addr", "SizeOf", "TypeOf", "TypeInfo",
                "GetMem", "FreeMem", "ReallocMem", "New", "Dispose", "FillChar", "Move",
                "Ord", "Chr", "Succ", "Pred", "Inc", "Dec", "Low", "High", "Odd",
                "Abs", "Sqr", "Sqrt", "Round", "Trunc",
                "Length", "SetLength", "Copy", "Concat", "Pos", "Delete", "Insert",
                "Str", "Val", "IntToStr", "StrToInt", "FloatToStr", "StrToFloat", "Format",
                "Write", "WriteLn", "Read", "ReadLn",
                "Exit", "Halt", "Break", "Continue",
                "Raise", "Abort",
                "FreeAndNil", "Assert",
                "Exclude", "Include",
                "Now", "Date", "Time", "Sleep",
                "FileExists", "DirectoryExists", "ExtractFilePath", "ExtractFileName"
        };

        for (String name : builtInFunctions) {
            if (addedNames.contains(name.toLowerCase())) continue;
            addedNames.add(name.toLowerCase());
            LookupElementBuilder lookup = LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Function)
                    .withTailText("()", true)
                    .withTypeText("built-in", true)
                    .withInsertHandler((ctx, item) -> {
                        ctx.getDocument().insertString(ctx.getTailOffset(), "()");
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                    });
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 40));
        }
    }

    private void addBuiltInTypes(Set<String> addedNames, CompletionResultSet result) {
        String[] builtInTypes = {
                "Integer", "Int64", "Cardinal", "Byte", "Word", "ShortInt", "SmallInt",
                "LongWord", "LongInt", "NativeInt", "NativeUInt",
                "Single", "Double", "Extended", "Real", "Currency",
                "Boolean", "ByteBool", "WordBool", "LongBool",
                "String", "AnsiString", "WideString", "UnicodeString", "ShortString",
                "Char", "AnsiChar", "WideChar",
                "Pointer", "PChar", "PWideChar",
                "Variant", "OleVariant",
                "TDateTime", "TGuid"
        };

        for (String name : builtInTypes) {
            if (addedNames.contains(name.toLowerCase())) continue;
            addedNames.add(name.toLowerCase());
            LookupElementBuilder lookup = LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Type)
                    .withTypeText("built-in", true)
                    .bold();
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 30));
        }
    }

    private void addKeywords(PsiElement position, Set<String> addedNames, CompletionResultSet result) {
        // Determine context
        boolean inRoutineBody = isInsideElementType(position, PascalElementTypes.ROUTINE_BODY)
                || isInsideElementType(position, PascalElementTypes.COMPOUND_STATEMENT);
        boolean inClassBody = isInsideElementType(position, PascalElementTypes.CLASS_BODY)
                || isInsideElementType(position, PascalElementTypes.RECORD_BODY);

        String[] keywords;
        if (inRoutineBody) {
            keywords = new String[]{
                    "begin", "end", "if", "then", "else",
                    "for", "to", "downto", "do", "while", "repeat", "until",
                    "try", "except", "finally", "raise",
                    "case", "of", "with",
                    "var", "const",
                    "exit", "break", "continue",
                    "not", "and", "or", "xor",
                    "nil", "true", "false",
                    "inherited", "self", "Result"
            };
        } else if (inClassBody) {
            keywords = new String[]{
                    "procedure", "function", "constructor", "destructor",
                    "property", "class",
                    "private", "protected", "public", "published", "strict",
                    "virtual", "override", "abstract", "dynamic",
                    "overload", "reintroduce", "static"
            };
        } else {
            keywords = new String[]{
                    "procedure", "function", "type", "var", "const",
                    "uses", "begin", "end",
                    "class", "record", "interface",
                    "implementation", "initialization", "finalization"
            };
        }

        for (String kw : keywords) {
            if (addedNames.contains(kw.toLowerCase())) continue;
            addedNames.add(kw.toLowerCase());
            LookupElementBuilder lookup = LookupElementBuilder.create(kw)
                    .bold();
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 20));
        }
    }

    private boolean isInScope(PsiFile memberFile, String unitName,
                              PsiFile fromFile, Set<String> availableUnitsLower, List<String> scopes) {
        if (fromFile.equals(memberFile)) return true;
        if (unitName == null) return false;
        String unitLower = unitName.toLowerCase();

        // Implicit System availability
        if (unitLower.equals("system") || unitLower.startsWith("system.")) return true;

        if (availableUnitsLower.contains(unitLower)) return true;

        for (String scope : scopes) {
            String scopeLower = scope.toLowerCase();
            if (unitLower.startsWith(scopeLower + ".")) {
                String shortName = unitLower.substring(scopeLower.length() + 1);
                if (availableUnitsLower.contains(shortName)) return true;
            }
            String scopedName = scopeLower + "." + unitLower;
            if (availableUnitsLower.contains(scopedName)) return true;
        }
        return false;
    }

    private static boolean isInsideElementType(PsiElement element, com.intellij.psi.tree.IElementType type) {
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (parent.getNode() != null && parent.getNode().getElementType() == type) return true;
            parent = parent.getParent();
        }
        return false;
    }
}
