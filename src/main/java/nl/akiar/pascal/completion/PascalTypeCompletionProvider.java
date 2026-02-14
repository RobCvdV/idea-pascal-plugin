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
import com.intellij.util.ProcessingContext;
import nl.akiar.pascal.psi.PascalTypeDefinition;
import nl.akiar.pascal.resolution.DelphiBuiltIns;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import nl.akiar.pascal.uses.PascalUsesClauseInfo;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Provides completion for type positions (after colon in variable declarations,
 * parameter types, return types, inheritance).
 *
 * Uses prefix filtering and invocation-count gating to avoid full index scans
 * on autopopup with short prefixes.
 */
public class PascalTypeCompletionProvider extends CompletionProvider<CompletionParameters> {

    private static final String[] BUILT_IN_TYPE_NAMES = {
            // Numeric types
            "Integer", "Int64", "Cardinal", "Byte", "Word", "ShortInt", "SmallInt",
            "LongWord", "LongInt", "UInt64", "NativeInt", "NativeUInt",
            "Single", "Double", "Extended", "Real", "Currency", "Comp",
            // Boolean types
            "Boolean", "ByteBool", "WordBool", "LongBool",
            // String/Char types
            "String", "AnsiString", "WideString", "UnicodeString", "ShortString",
            "RawByteString", "UTF8String",
            "Char", "AnsiChar", "WideChar",
            // Pointer types
            "Pointer", "PChar", "PWideChar", "PAnsiChar", "PByte",
            // Other fundamental
            "Variant", "OleVariant",
            "TDateTime", "TDate", "TTime",
            "TGuid",
            "TextFile", "File",
            // Interface types
            "IUnknown", "IInterface", "IDispatch"
    };

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();
        Project project = position.getProject();

        if (DumbService.isDumb(project)) return;

        int offset = parameters.getOffset();
        Set<String> addedNames = new HashSet<>();

        int invocationCount = parameters.getInvocationCount();
        boolean isAutopopup = invocationCount == 0;
        String prefix = result.getPrefixMatcher().getPrefix();
        String prefixLower = prefix.toLowerCase();

        // Built-in types are always fast — add them first (Tier 1)
        for (String builtIn : BUILT_IN_TYPE_NAMES) {
            if (addedNames.contains(builtIn.toLowerCase())) continue;
            addedNames.add(builtIn.toLowerCase());
            LookupElementBuilder lookup = LookupElementBuilder.create(builtIn)
                    .withIcon(AllIcons.Nodes.Type)
                    .withTypeText("built-in", true)
                    .bold();
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 50));
        }

        // Keyword types are always fast
        String[] keywordTypes = {"string", "array", "set", "file"};
        for (String kw : keywordTypes) {
            if (addedNames.contains(kw)) continue;
            addedNames.add(kw);
            LookupElementBuilder lookup = LookupElementBuilder.create(kw)
                    .withIcon(AllIcons.Nodes.Type)
                    .bold();
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 25));
        }

        // Tier 2: Stub index queries — only when user has typed 2+ chars or explicitly invoked
        if (isAutopopup && prefixLower.length() < 2) return;

        // Types from stub index that are in scope (prefix-filtered)
        Collection<String> allKeys = StubIndex.getInstance().getAllKeys(PascalTypeIndex.KEY, project);
        PascalUsesClauseInfo usesInfo = PascalUsesClauseInfo.parse(file);
        List<String> availableUnits = usesInfo.getAvailableUnits(offset);
        Set<String> availableUnitsLower = new HashSet<>();
        for (String unit : availableUnits) {
            availableUnitsLower.add(unit.toLowerCase());
        }
        List<String> scopes = PascalSourcePathsSettings.getInstance(project).getUnitScopeNames();

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

                String nameLower = name.toLowerCase();
                if (addedNames.contains(nameLower)) continue;

                // Check if type is in scope
                if (!isTypeInScope(typeDef, file, availableUnitsLower, scopes)) continue;

                addedNames.add(nameLower);
                String unitName = typeDef.getUnitName();
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Class)
                        .withTypeText(unitName, true);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 100));
            }
        }
    }

    private boolean isTypeInScope(PascalTypeDefinition typeDef, PsiFile fromFile,
                                  Set<String> availableUnitsLower, List<String> scopes) {
        // Same file is always in scope
        if (fromFile.equals(typeDef.getContainingFile())) return true;

        String targetUnit = typeDef.getUnitName();
        if (targetUnit == null) return false;
        String targetLower = targetUnit.toLowerCase();

        // Implicit System availability
        if (targetLower.equals("system") || targetLower.startsWith("system.")) return true;

        // Direct match
        if (availableUnitsLower.contains(targetLower)) return true;

        // Scope-based matching
        for (String scope : scopes) {
            String scopeLower = scope.toLowerCase();
            // "SysUtils" in uses matches "System.SysUtils" unit
            if (targetLower.startsWith(scopeLower + ".")) {
                String shortName = targetLower.substring(scopeLower.length() + 1);
                if (availableUnitsLower.contains(shortName)) return true;
            }
            // "System.SysUtils" in uses matches "SysUtils" unit
            String scopedName = scopeLower + "." + targetLower;
            if (availableUnitsLower.contains(scopedName)) return true;
        }

        return false;
    }
}
