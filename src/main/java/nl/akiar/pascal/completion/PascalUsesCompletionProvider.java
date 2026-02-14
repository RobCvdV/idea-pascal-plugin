package nl.akiar.pascal.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileBasedIndex;
import nl.akiar.pascal.index.PascalUnitIndex;
import nl.akiar.pascal.uses.PascalUsesClauseInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Provides completion inside uses clauses.
 * Suggests unit names from the project that are not already in the uses clause.
 */
public class PascalUsesCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();
        Project project = position.getProject();

        if (DumbService.isDumb(project)) return;

        // Get already-used units to filter them out
        PascalUsesClauseInfo usesInfo = PascalUsesClauseInfo.parse(file);
        Set<String> alreadyUsed = new HashSet<>();
        for (String unit : usesInfo.getAllUses()) {
            alreadyUsed.add(unit.toLowerCase());
        }

        // Get all unit names from the index
        Collection<String> allUnitNames = FileBasedIndex.getInstance()
                .getAllKeys(PascalUnitIndex.INDEX_ID, project);

        Set<String> addedNames = new HashSet<>();
        for (String unitName : allUnitNames) {
            if (unitName == null || unitName.isEmpty()) continue;
            String unitLower = unitName.toLowerCase();
            if (alreadyUsed.contains(unitLower)) continue;
            if (addedNames.contains(unitLower)) continue;
            addedNames.add(unitLower);

            LookupElementBuilder lookup = LookupElementBuilder.create(unitName)
                    .withIcon(AllIcons.Nodes.Module);
            result.addElement(lookup);
        }

        result.stopHere();
    }
}
