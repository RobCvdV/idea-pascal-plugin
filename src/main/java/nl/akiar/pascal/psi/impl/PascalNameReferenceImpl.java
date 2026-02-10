package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalNameReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of PascalNameReference PSI element.
 * Represents a name reference which can be simple (X) or qualified (A.B.C).
 */
public class PascalNameReferenceImpl extends ASTWrapperPsiElement implements PascalNameReference {

    public PascalNameReferenceImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public List<PsiElement> getNameParts() {
        List<PsiElement> parts = new ArrayList<>();
        // Iterate through AST nodes to find all IDENTIFIER tokens
        for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == PascalTokenTypes.IDENTIFIER) {
                parts.add(child.getPsi());
            }
        }
        return parts;
    }

    @Override
    @Nullable
    public PsiElement getQualifier() {
        List<PsiElement> parts = getNameParts();
        if (parts.size() <= 1) {
            return null;
        }
        // Return all except the last part as the qualifier
        // For now, just return the first n-1 parts' parent element if they share one,
        // or create a text range covering them
        // Simpler approach: return the element covering from start to before the last dot
        String text = getText();
        int lastDot = text.lastIndexOf('.');
        if (lastDot <= 0) return null;

        // Find the PsiElement that covers the qualifier portion
        int qualifierEnd = getTextOffset() + lastDot;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            int childEnd = child.getTextOffset() + child.getTextLength();
            if (childEnd >= qualifierEnd && child.getTextOffset() <= getTextOffset()) {
                // This child might be the qualifier or contain it
            }
        }

        // Simplified: return the parent of all identifier parts except the last
        // This is a simplification - for full accuracy, would need more complex logic
        return parts.size() > 1 ? parts.get(0) : null;
    }

    @Override
    @NotNull
    public String getReferenceName() {
        List<PsiElement> parts = getNameParts();
        if (parts.isEmpty()) {
            // Fallback to text parsing
            String text = getText().trim();
            int lastDot = text.lastIndexOf('.');
            return lastDot >= 0 ? text.substring(lastDot + 1).trim() : text;
        }
        return parts.get(parts.size() - 1).getText();
    }

    @Override
    public boolean isQualified() {
        return getNameParts().size() > 1 || getText().contains(".");
    }

    @Override
    public String toString() {
        return "PascalNameReference(" + getText() + ")";
    }
}
