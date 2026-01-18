package nl.akiar.pascal.uses;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import nl.akiar.pascal.PascalTokenTypes;
import nl.akiar.pascal.psi.PascalElementTypes;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class for parsing and analyzing Pascal uses clauses.
 * Handles both interface and implementation uses clauses.
 */
public class PascalUsesClauseUtil {
    private static final Logger LOG = Logger.getInstance(PascalUsesClauseUtil.class);
    private static final Key<CachedValue<UsesClauseInfo>> USES_CLAUSE_CACHE_KEY = Key.create("PASCAL_USES_CLAUSE_CACHE");

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

        /**
         * Check if a unit is available at a given offset, considering unit scope names.
         * @param unitName The unit name to check (e.g. "SysUtils")
         * @param offset The offset in the file
         * @param scopes Optional list of scope names (e.g. ["System"])
         * @return Name found in uses clause if the unit (or unit with scope) is in the uses clause, null otherwise
         */
        @Nullable
        public String findUnitInUses(String unitName, int offset, @Nullable List<String> scopes) {
            String lowerUnit = unitName.toLowerCase();

            // 1. Implicit inclusion: if unitName itself is a scope name (e.g., "System"),
            // it is considered to be in uses clause of EVERY file always.
            if (scopes != null) {
                for (String scope : scopes) {
                    if (lowerUnit.equalsIgnoreCase(scope)) {
                        return unitName;
                    }
                }
            }

            Set<String> availableUnits = getAvailableUnits(offset);

            // 2. Direct match in uses clause
            if (availableUnits.contains(lowerUnit)) {
                return unitName;
            }

            if (scopes != null) {
                for (String scope : scopes) {
                    String lowerScope = scope.toLowerCase();

                    // 3. Scoped match (e.g., "System.SysUtils" in uses clause allows "SysUtils")
                    String scopedName = (lowerScope + "." + lowerUnit);
                    if (availableUnits.contains(scopedName)) {
                        // Return the actual scoped name from uses if possible
                        for (String unit : availableUnits) {
                            if (unit.equals(scopedName)) return unit;
                        }
                        return scope + "." + unitName;
                    }

                    // 4. Reverse scoped match (e.g., "SysUtils" in uses clause allows "System.SysUtils" unit)
                    // If targetUnit is "System.SysUtils" and "System" is a scope, and "SysUtils" is in uses clause
                    if (lowerUnit.startsWith(lowerScope + ".")) {
                        String shortUnitName = lowerUnit.substring(lowerScope.length() + 1);
                        if (availableUnits.contains(shortUnitName)) {
                            // Return the short unit name that was found in uses
                            for (String unit : availableUnits) {
                                if (unit.equals(shortUnitName)) return unit;
                            }
                            return shortUnitName;
                        }
                    }
                }
            }

            return null;
        }

        /** Check if a unit is available at a given offset */
        public boolean isUnitAvailable(String unitName, int offset) {
            return isUnitAvailable(unitName, offset, null);
        }

        /**
         * Check if a unit is available at a given offset, considering unit scope names.
         * @param unitName The unit name to check (e.g. "SysUtils")
         * @param offset The offset in the file
         * @param scopes Optional list of scope names (e.g. ["System"])
         * @return true if the unit (or unit with scope) is in the uses clause
         */
        public boolean isUnitAvailable(String unitName, int offset, @Nullable List<String> scopes) {
            return findUnitInUses(unitName, offset, scopes) != null;
        }

        private Set<String> getAvailableUnits(int offset) {
            // If in implementation section, both uses clauses are available
            if (implementationSectionStart >= 0 && offset >= implementationSectionStart) {
                Set<String> all = new HashSet<>(interfaceUses);
                all.addAll(implementationUses);
                return all;
            }
            // If in interface section, only interface uses are available
            return interfaceUses;
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
        return CachedValuesManager.getManager(file.getProject()).getCachedValue(file, USES_CLAUSE_CACHE_KEY, () -> {
            Set<String> interfaceUses = new LinkedHashSet<>();
            Set<String> implementationUses = new LinkedHashSet<>();
            int interfaceSectionStart = -1;
            int implementationSectionStart = -1;

            ASTNode fileNode = file.getNode();
            if (fileNode == null) {
                return CachedValueProvider.Result.create(
                        new UsesClauseInfo(interfaceUses, implementationUses, interfaceSectionStart, implementationSectionStart),
                        file);
            }

            // Use a recursive search for sections and uses clauses
            ParseState state = new ParseState();
            findSectionsAndUses(fileNode, state, interfaceUses, implementationUses);

            // For program files (no interface/implementation), treat all uses as "interface" uses
            // and make them available everywhere
            if (state.interfaceSectionStart < 0 && state.implementationSectionStart < 0 && !interfaceUses.isEmpty()) {
                // File has uses but no sections - it's likely a program or project file
                state.interfaceSectionStart = 0;
            }

            // If it's a project/program file, we might have uses without sections, 
            // but we still want to mark it as being in "interface" scope so errors are reported correctly
            if (state.interfaceSectionStart < 0 && (file.getName().endsWith(".dpr") || file.getName().endsWith(".lpr"))) {
                state.interfaceSectionStart = 0;
            }

            // LOG.info("[PascalUses] Parsed file " + file.getName() +
            //          ": interface uses=" + interfaceUses +
            //          ", implementation uses=" + implementationUses +
            //          ", interfaceStart=" + state.interfaceSectionStart +
            //          ", implementationStart=" + state.implementationSectionStart);

            return CachedValueProvider.Result.create(
                    new UsesClauseInfo(interfaceUses, implementationUses, state.interfaceSectionStart, state.implementationSectionStart),
                    file);
        }, false);
    }

    private static class ParseState {
        boolean inInterfaceSection = false;
        boolean inImplementationSection = false;
        int interfaceSectionStart = -1;
        int implementationSectionStart = -1;
    }

    private static void findSectionsAndUses(ASTNode node, ParseState state, Set<String> interfaceUses, Set<String> implementationUses) {
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            IElementType type = child.getElementType();

            if (type == PascalTokenTypes.KW_INTERFACE) {
                state.inInterfaceSection = true;
                state.inImplementationSection = false;
                if (state.interfaceSectionStart < 0) {
                    state.interfaceSectionStart = child.getStartOffset();
                }
            } else if (type == PascalTokenTypes.KW_IMPLEMENTATION) {
                state.inInterfaceSection = false;
                state.inImplementationSection = true;
                if (state.implementationSectionStart < 0) {
                    state.implementationSectionStart = child.getStartOffset();
                }
            } else if (type == PascalTokenTypes.KW_USES) {
                Set<String> targetSet = state.inImplementationSection ? implementationUses : interfaceUses;
                parseUsesClauseContent(child, targetSet);
                // After parsing KW_USES, we skip its immediate siblings handled by parseUsesClauseContent
                // but findSectionsAndUses will still visit them as siblings of KW_USES in the next iteration.
            }
            // Recurse into children
            if (type != PascalTokenTypes.KW_USES) { // Optimization: KW_USES is a leaf in current structured parser, no need to recurse
                findSectionsAndUses(child, state, interfaceUses, implementationUses);
            }

            child = child.getTreeNext();
        }
    }

    /**
     * Parse the unit names from a uses clause starting at the 'uses' keyword.
     * Handles dotted unit names like "System.Generics.Collections" or "Next.Core.Struct".
     */
    private static void parseUsesClauseContent(ASTNode usesNode, Set<String> targetSet) {
        // The children of 'uses' node might contain the unit references if it's a structured node
        if (usesNode.getFirstChildNode() != null) {
            ASTNode child = usesNode.getFirstChildNode();
            while (child != null) {
                if (child.getElementType() == PascalElementTypes.UNIT_REFERENCE) {
                    targetSet.add(child.getText().toLowerCase());
                } else if (child.getElementType() == PascalTokenTypes.IDENTIFIER) {
                    // Fallback for simple identifiers if UNIT_REFERENCE is not used
                    targetSet.add(child.getText().toLowerCase());
                }
                child = child.getTreeNext();
            }
        }

        ASTNode current = usesNode.getTreeNext();
        // Skip whitespace and comments immediately after 'uses'
        current = skipIgnorableTokens(current);

        while (current != null) {
            IElementType type = current.getElementType();

            if (type == PascalTokenTypes.SEMI) {
                // End of uses clause
                break;
            } else if (type == PascalElementTypes.UNIT_REFERENCE) {
                targetSet.add(current.getText().toLowerCase());
                current = current.getTreeNext();
                current = skipIgnorableTokens(current);
                continue;
            } else if (type == PascalTokenTypes.IDENTIFIER) {
                // Build the full dotted unit name: Next.Core.Struct
                StringBuilder unitName = new StringBuilder(current.getText());
                current = current.getTreeNext();

                // Skip whitespace and comments
                current = skipIgnorableTokens(current);

                // Check for dots and more identifiers
                while (current != null && current.getElementType() == PascalTokenTypes.DOT) {
                    unitName.append(".");
                    current = current.getTreeNext();

                    // Skip whitespace and comments after dot
                    current = skipIgnorableTokens(current);

                    // Get next identifier
                    if (current != null && current.getElementType() == PascalTokenTypes.IDENTIFIER) {
                        unitName.append(current.getText());
                        current = current.getTreeNext();

                        // Skip whitespace and comments
                        current = skipIgnorableTokens(current);
                    } else {
                        break;
                    }
                }

                targetSet.add(unitName.toString().toLowerCase());
                // We are now past the unit name (and dots), and skipIgnorableTokens was called.
                // Next token could be COMMA, SEMI, or KW_IN.
                continue; 
            } else if (type == PascalTokenTypes.KW_IN) {
                // Skip "in 'path'" part
                current = current.getTreeNext();
                while (current != null) {
                    IElementType currentType = current.getElementType();
                    if (currentType == PascalTokenTypes.COMMA || currentType == PascalTokenTypes.SEMI) {
                        break;
                    }
                    current = current.getTreeNext();
                }
                if (current == null) break;
                // We are at COMMA or SEMI, the loop will handle it (skip COMMA or break SEMI)
            } else if (type == PascalTokenTypes.COMMA) {
                current = current.getTreeNext();
                current = skipIgnorableTokens(current);
                continue;
            } else {
                // Unexpected token, skip it
                current = current.getTreeNext();
                current = skipIgnorableTokens(current);
            }
        }
    }

    /**
     * Skip whitespace, comments and compiler directives.
     */
    private static ASTNode skipIgnorableTokens(ASTNode node) {
        while (node != null) {
            IElementType type = node.getElementType();
            if (type == PascalTokenTypes.WHITE_SPACE ||
                type == PascalTokenTypes.LINE_COMMENT ||
                type == PascalTokenTypes.BLOCK_COMMENT ||
                type == PascalTokenTypes.COMPILER_DIRECTIVE) {
                node = node.getTreeNext();
            } else {
                break;
            }
        }
        return node;
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

        List<String> scopes = PascalSourcePathsSettings.getInstance(referenceFile.getProject()).getUnitScopeNames();

        if (usesInfo.isUnitAvailable(targetUnit, referenceOffset, scopes)) {
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
