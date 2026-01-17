package nl.akiar.pascal.uses;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import nl.akiar.pascal.PascalTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class for parsing and analyzing Pascal uses clauses.
 * Handles both interface and implementation uses clauses.
 */
public class PascalUsesClauseUtil {
    private static final Logger LOG = Logger.getInstance(PascalUsesClauseUtil.class);

    /**
     * Result of parsing uses clauses from a Pascal file.
     */
    public static class UsesClauseInfo {
        private final Set<String> interfaceUses;
        private final Set<String> implementationUses;
        private final int interfaceSectionStart;
        private final int implementationSectionStart;

        public UsesClauseInfo(Set<String> interfaceUses, Set<String> implementationUses,
                             int interfaceSectionStart, int implementationSectionStart) {
            this.interfaceUses = Collections.unmodifiableSet(interfaceUses);
            this.implementationUses = Collections.unmodifiableSet(implementationUses);
            this.interfaceSectionStart = interfaceSectionStart;
            this.implementationSectionStart = implementationSectionStart;
        }

        /** Units in the interface uses clause */
        public Set<String> getInterfaceUses() {
            return interfaceUses;
        }

        /** Units in the implementation uses clause */
        public Set<String> getImplementationUses() {
            return implementationUses;
        }

        /** All units from both uses clauses */
        public Set<String> getAllUses() {
            Set<String> all = new HashSet<>(interfaceUses);
            all.addAll(implementationUses);
            return all;
        }

        /** Offset where interface section starts (-1 if not found) */
        public int getInterfaceSectionStart() {
            return interfaceSectionStart;
        }

        /** Offset where implementation section starts (-1 if not found) */
        public int getImplementationSectionStart() {
            return implementationSectionStart;
        }

        /** Check if a unit is available at a given offset */
        public boolean isUnitAvailable(String unitName, int offset) {
            String lowerUnit = unitName.toLowerCase();
            // If in implementation section, both uses clauses are available
            if (implementationSectionStart >= 0 && offset >= implementationSectionStart) {
                return interfaceUses.contains(lowerUnit) || implementationUses.contains(lowerUnit);
            }
            // If in interface section, only interface uses are available
            return interfaceUses.contains(lowerUnit);
        }

        /** Check if an offset is in the interface section */
        public boolean isInInterfaceSection(int offset) {
            if (interfaceSectionStart < 0) return false;
            if (implementationSectionStart < 0) return offset >= interfaceSectionStart;
            return offset >= interfaceSectionStart && offset < implementationSectionStart;
        }

        /** Check if an offset is in the implementation section */
        public boolean isInImplementationSection(int offset) {
            return implementationSectionStart >= 0 && offset >= implementationSectionStart;
        }
    }

    /**
     * Parse uses clauses from a Pascal file.
     */
    @NotNull
    public static UsesClauseInfo parseUsesClause(@NotNull PsiFile file) {
        Set<String> interfaceUses = new LinkedHashSet<>();
        Set<String> implementationUses = new LinkedHashSet<>();
        int interfaceSectionStart = -1;
        int implementationSectionStart = -1;

        ASTNode fileNode = file.getNode();
        if (fileNode == null) {
            return new UsesClauseInfo(interfaceUses, implementationUses, interfaceSectionStart, implementationSectionStart);
        }

        // Track current section
        boolean inInterfaceSection = false;
        boolean inImplementationSection = false;

        ASTNode child = fileNode.getFirstChildNode();
        while (child != null) {
            IElementType type = child.getElementType();

            if (type == PascalTokenTypes.KW_INTERFACE) {
                inInterfaceSection = true;
                inImplementationSection = false;
                interfaceSectionStart = child.getStartOffset();
            } else if (type == PascalTokenTypes.KW_IMPLEMENTATION) {
                inInterfaceSection = false;
                inImplementationSection = true;
                implementationSectionStart = child.getStartOffset();
            } else if (type == PascalTokenTypes.KW_USES) {
                // Parse the uses clause
                Set<String> targetSet = inImplementationSection ? implementationUses : interfaceUses;
                parseUsesClauseContent(child, targetSet);
            }

            child = child.getTreeNext();
        }

        // For program files (no interface/implementation), treat all uses as "interface" uses
        // and make them available everywhere
        if (interfaceSectionStart < 0 && implementationSectionStart < 0 && !interfaceUses.isEmpty()) {
            // File has uses but no sections - it's likely a program file
            interfaceSectionStart = 0;
        }

        LOG.info("[PascalUses] Parsed file " + file.getName() +
                 ": interface uses=" + interfaceUses +
                 ", implementation uses=" + implementationUses);

        return new UsesClauseInfo(interfaceUses, implementationUses, interfaceSectionStart, implementationSectionStart);
    }

    /**
     * Parse the unit names from a uses clause starting at the 'uses' keyword.
     * Handles dotted unit names like "System.Generics.Collections" or "Next.Core.Struct".
     */
    private static void parseUsesClauseContent(ASTNode usesNode, Set<String> targetSet) {
        ASTNode current = usesNode.getTreeNext();

        while (current != null) {
            IElementType type = current.getElementType();

            if (type == PascalTokenTypes.SEMI) {
                // End of uses clause
                break;
            } else if (type == PascalTokenTypes.IDENTIFIER) {
                // Build the full dotted unit name: Next.Core.Struct
                StringBuilder unitName = new StringBuilder(current.getText());
                current = current.getTreeNext();

                // Skip whitespace
                while (current != null && current.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                    current = current.getTreeNext();
                }

                // Check for dots and more identifiers
                while (current != null && current.getElementType() == PascalTokenTypes.DOT) {
                    unitName.append(".");
                    current = current.getTreeNext();

                    // Skip whitespace after dot
                    while (current != null && current.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                        current = current.getTreeNext();
                    }

                    // Get next identifier
                    if (current != null && current.getElementType() == PascalTokenTypes.IDENTIFIER) {
                        unitName.append(current.getText());
                        current = current.getTreeNext();

                        // Skip whitespace
                        while (current != null && current.getElementType() == PascalTokenTypes.WHITE_SPACE) {
                            current = current.getTreeNext();
                        }
                    } else {
                        break;
                    }
                }

                targetSet.add(unitName.toString().toLowerCase());
                continue; // Don't advance again, we already moved past the unit name
            } else if (type == PascalTokenTypes.KW_IN) {
                // Skip "in 'path'" part - just advance past the string
                current = current.getTreeNext();
                while (current != null &&
                       current.getElementType() != PascalTokenTypes.COMMA &&
                       current.getElementType() != PascalTokenTypes.SEMI) {
                    current = current.getTreeNext();
                }
                if (current == null) break;
                continue; // Don't advance again
            }
            // Skip whitespace, comments, commas
            current = current.getTreeNext();
        }
    }

    /**
     * Get the unit name from a file (file name without extension).
     */
    @NotNull
    public static String getUnitName(@NotNull PsiFile file) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex).toLowerCase();
        }
        return fileName.toLowerCase();
    }

    /**
     * Check if a type reference at a given offset has the required unit in scope.
     *
     * @param referenceFile The file containing the reference
     * @param targetFile The file containing the type definition
     * @param referenceOffset The offset of the reference in referenceFile
     * @return null if OK, or an error message if not in scope
     */
    @Nullable
    public static String validateTypeReference(@NotNull PsiFile referenceFile,
                                               @NotNull PsiFile targetFile,
                                               int referenceOffset) {
        // Same file - always OK
        if (referenceFile.equals(targetFile)) {
            return null;
        }

        UsesClauseInfo usesInfo = parseUsesClause(referenceFile);
        String targetUnit = getUnitName(targetFile);

        if (usesInfo.isUnitAvailable(targetUnit, referenceOffset)) {
            return null; // OK
        }

        // Determine which uses clause is missing
        if (usesInfo.isInInterfaceSection(referenceOffset)) {
            if (usesInfo.getImplementationUses().contains(targetUnit.toLowerCase())) {
                return "Unit '" + targetUnit + "' is only in implementation uses, but referenced in interface section";
            }
            return "Unit '" + targetUnit + "' is not in uses clause";
        } else if (usesInfo.isInImplementationSection(referenceOffset)) {
            return "Unit '" + targetUnit + "' is not in uses clause";
        }

        // Program file or other
        return "Unit '" + targetUnit + "' is not in uses clause";
    }
}
