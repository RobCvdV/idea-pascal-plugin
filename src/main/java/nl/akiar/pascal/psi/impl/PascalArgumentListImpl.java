package nl.akiar.pascal.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import nl.akiar.pascal.psi.PascalArgumentList;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.psi.PascalExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of PascalArgumentList PSI element.
 */
public class PascalArgumentListImpl extends ASTWrapperPsiElement implements PascalArgumentList {

    public PascalArgumentListImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public List<PascalExpression> getArguments() {
        List<PascalExpression> args = new ArrayList<>();

        // Find all ARGUMENT children, then get their expression content
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode childNode = child.getNode();
            if (childNode != null && childNode.getElementType() == PascalElementTypes.ARGUMENT) {
                // Each ARGUMENT contains an expression
                for (PsiElement argChild = child.getFirstChild(); argChild != null; argChild = argChild.getNextSibling()) {
                    if (argChild instanceof PascalExpression) {
                        args.add((PascalExpression) argChild);
                        break;  // One expression per argument
                    }
                }
            } else if (child instanceof PascalExpression) {
                // Direct expression children (if ARGUMENT nodes are not present)
                args.add((PascalExpression) child);
            }
        }

        return args;
    }

    @Override
    public int getArgumentCount() {
        return getArguments().size();
    }

    @Override
    public String toString() {
        return "PascalArgumentList(" + getArgumentCount() + " args)";
    }
}
