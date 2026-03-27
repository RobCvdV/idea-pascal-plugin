package nl.akiar.pascal.navigation;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import nl.akiar.pascal.psi.*;
import nl.akiar.pascal.psi.impl.PascalRoutineImpl;
import nl.akiar.pascal.stubs.PascalImplementorsIndex;
import nl.akiar.pascal.stubs.PascalTypeIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provides "Go to Implementation" (Cmd+Opt+B) for Pascal interface types,
 * interface methods, and interface properties.
 */
public class PascalImplementationSearcher
        extends QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters> {
    private static final Logger LOG = Logger.getInstance(PascalImplementationSearcher.class);

    public PascalImplementationSearcher() {
        super(true); // require read action
    }

    @Override
    public void processQuery(
            DefinitionsScopedSearch.@NotNull SearchParameters params,
            @NotNull Processor<? super PsiElement> consumer) {

        PsiElement element = params.getElement();
        LOG.debug("[GoToImpl] processQuery called with: " + element.getClass().getSimpleName()
                + " text='" + element.getText().substring(0, Math.min(element.getText().length(), 50)) + "'");

        // Case A: Interface TYPE → find implementing classes
        if (element instanceof PascalTypeDefinition td && td.getTypeKind() == TypeKind.INTERFACE) {
            LOG.debug("[GoToImpl] Case A: interface type '" + td.getName() + "'");
            processInterfaceType(td, consumer);
            return;
        }

        // Case B: Method on interface → find implementing methods
        if (element instanceof PascalRoutine routine) {
            PascalTypeDefinition containingClass = routine.getContainingClass();
            LOG.debug("[GoToImpl] Case B check: routine '" + routine.getName()
                    + "' containingClass=" + (containingClass != null ? containingClass.getName() + "(" + containingClass.getTypeKind() + ")" : "null"));
            if (containingClass != null && containingClass.getTypeKind() == TypeKind.INTERFACE) {
                processInterfaceMethod(routine, containingClass, consumer);
                return;
            }

            // Case B2: Method on a CLASS that implements an interface →
            // trace back to the interface method and find all implementations
            if (containingClass != null && containingClass.getTypeKind() == TypeKind.CLASS) {
                PascalRoutine interfaceMethod = findInterfaceMethodFor(routine, containingClass);
                if (interfaceMethod != null) {
                    PascalTypeDefinition interfaceType = interfaceMethod.getContainingClass();
                    if (interfaceType != null) {
                        LOG.debug("[GoToImpl] Case B2: class method '" + routine.getName()
                                + "' → interface '" + interfaceType.getName() + "'");
                        processInterfaceMethod(interfaceMethod, interfaceType, consumer);
                        return;
                    }
                }
            }
        }

        // Case C: Property on interface → find implementing properties
        if (element instanceof PascalProperty property) {
            PascalTypeDefinition containingClass = property.getContainingClass();
            LOG.debug("[GoToImpl] Case C check: property '" + property.getName()
                    + "' containingClass=" + (containingClass != null ? containingClass.getName() + "(" + containingClass.getTypeKind() + ")" : "null"));
            if (containingClass != null && containingClass.getTypeKind() == TypeKind.INTERFACE) {
                processInterfaceProperty(property, containingClass, consumer);
            }

            // Case C2: Property on a CLASS that implements an interface
            if (containingClass != null && containingClass.getTypeKind() == TypeKind.CLASS) {
                PascalProperty interfaceProp = findInterfacePropertyFor(property, containingClass);
                if (interfaceProp != null) {
                    PascalTypeDefinition interfaceType = interfaceProp.getContainingClass();
                    if (interfaceType != null) {
                        LOG.debug("[GoToImpl] Case C2: class property '" + property.getName()
                                + "' → interface '" + interfaceType.getName() + "'");
                        processInterfaceProperty(interfaceProp, interfaceType, consumer);
                    }
                }
            }
        }

        // If element is not one of our PSI types, check if it's a leaf identifier
        // whose parent might be relevant (IntelliJ may pass the raw identifier token)
        if (!(element instanceof PascalTypeDefinition) && !(element instanceof PascalRoutine) && !(element instanceof PascalProperty)) {
            PascalTypeDefinition parentType = PsiTreeUtil.getParentOfType(element, PascalTypeDefinition.class);
            if (parentType != null && parentType.getTypeKind() == TypeKind.INTERFACE) {
                PsiElement nameId = parentType.getNameIdentifier();
                if (nameId != null && (nameId.equals(element) || nameId.getTextRange().contains(element.getTextRange()))) {
                    LOG.debug("[GoToImpl] Leaf identifier fallback → interface type '" + parentType.getName() + "'");
                    processInterfaceType(parentType, consumer);
                    return;
                }
            }
            PascalRoutine parentRoutine = PsiTreeUtil.getParentOfType(element, PascalRoutine.class);
            if (parentRoutine != null) {
                PascalTypeDefinition routineContainer = parentRoutine.getContainingClass();
                if (routineContainer != null && routineContainer.getTypeKind() == TypeKind.INTERFACE) {
                    LOG.debug("[GoToImpl] Leaf identifier fallback → interface method '" + parentRoutine.getName() + "'");
                    processInterfaceMethod(parentRoutine, routineContainer, consumer);
                    return;
                }
            }
            PascalProperty parentProp = PsiTreeUtil.getParentOfType(element, PascalProperty.class);
            if (parentProp != null) {
                PascalTypeDefinition propContainer = parentProp.getContainingClass();
                if (propContainer != null && propContainer.getTypeKind() == TypeKind.INTERFACE) {
                    LOG.debug("[GoToImpl] Leaf identifier fallback → interface property '" + parentProp.getName() + "'");
                    processInterfaceProperty(parentProp, propContainer, consumer);
                    return;
                }
            }
        }
    }

    /**
     * Find all classes that implement the given interface.
     */
    private void processInterfaceType(
            @NotNull PascalTypeDefinition interfaceType,
            @NotNull Processor<? super PsiElement> consumer) {

        String name = interfaceType.getName();
        if (name == null) return;

        Project project = interfaceType.getProject();
        Collection<PascalTypeDefinition> implementors = PascalImplementorsIndex.findImplementors(name, project);
        LOG.debug("[GoToImpl] findImplementors('" + name + "') returned " + implementors.size() + " results");

        int fed = 0;
        for (PascalTypeDefinition impl : implementors) {
            if (impl.getTypeKind() != TypeKind.CLASS) continue;
            if (!ancestorListContains(impl, name)) continue;
            fed++;
            if (!consumer.process(impl)) return;
        }
        LOG.debug("[GoToImpl] Fed " + fed + " implementing classes to consumer");
    }

    /**
     * Find implementations of an interface method in implementing classes.
     * Navigates to the implementation body (not the forward declaration).
     * When the interface method's signature can be determined, filters to
     * matching overloads; otherwise includes all overloads with the same name.
     */
    private void processInterfaceMethod(
            @NotNull PascalRoutine interfaceMethod,
            @NotNull PascalTypeDefinition interfaceType,
            @NotNull Processor<? super PsiElement> consumer) {

        String interfaceName = interfaceType.getName();
        String methodName = interfaceMethod.getName();
        if (interfaceName == null || methodName == null) return;

        // Get the interface method's signature for overload matching
        String interfaceSig = null;
        if (interfaceMethod instanceof PascalRoutineImpl ri) {
            interfaceSig = ri.getSignatureHash();
        }

        Project project = interfaceType.getProject();
        Collection<PascalTypeDefinition> implementors = PascalImplementorsIndex.findImplementors(interfaceName, project);

        List<PascalRoutine> allMatches = new ArrayList<>();
        List<PascalRoutine> sigMatches = new ArrayList<>();

        for (PascalTypeDefinition impl : implementors) {
            if (impl.getTypeKind() != TypeKind.CLASS) continue;
            if (!ancestorListContains(impl, interfaceName)) continue;

            for (PascalRoutine method : impl.getMethods()) {
                if (methodName.equalsIgnoreCase(method.getName())) {
                    // Compare signature against the CLASS FORWARD DECLARATION (not
                    // implementation body) — both have only their own parameters.
                    boolean sigMatch = false;
                    if (interfaceSig != null && method instanceof PascalRoutineImpl mi) {
                        String classSig = mi.getSignatureHash();
                        LOG.debug("[GoToImpl]   comparing sigs: interface='" + interfaceSig +
                                "' class='" + classSig + "' for " + impl.getName() + "." + method.getName());
                        sigMatch = interfaceSig.equalsIgnoreCase(classSig);
                    }

                    PascalRoutine target = method.getImplementation();
                    if (target == null) target = method;
                    allMatches.add(target);
                    if (sigMatch) sigMatches.add(target);
                }
            }
        }

        // Prefer signature-matched overloads; fall back to all name matches
        List<PascalRoutine> results = sigMatches.isEmpty() ? allMatches : sigMatches;
        LOG.debug("[GoToImpl] processInterfaceMethod '" + methodName + "': " +
                allMatches.size() + " name matches, " + sigMatches.size() + " sig matches");
        for (PascalRoutine r : results) {
            if (!consumer.process(r)) return;
        }
    }

    /**
     * Find implementations of an interface property in implementing classes.
     */
    private void processInterfaceProperty(
            @NotNull PascalProperty interfaceProperty,
            @NotNull PascalTypeDefinition interfaceType,
            @NotNull Processor<? super PsiElement> consumer) {

        String interfaceName = interfaceType.getName();
        String propertyName = interfaceProperty.getName();
        if (interfaceName == null || propertyName == null) return;

        Project project = interfaceType.getProject();
        Collection<PascalTypeDefinition> implementors = PascalImplementorsIndex.findImplementors(interfaceName, project);

        for (PascalTypeDefinition impl : implementors) {
            if (impl.getTypeKind() != TypeKind.CLASS) continue;
            if (!ancestorListContains(impl, interfaceName)) continue;

            for (PascalProperty prop : impl.getProperties()) {
                if (propertyName.equalsIgnoreCase(prop.getName())) {
                    if (!consumer.process(prop)) return;
                }
            }
        }
    }

    /**
     * For a method on a CLASS, find the matching method declaration on an ancestor INTERFACE.
     * This allows Cmd+Opt+B on a class method (e.g. from a call site) to find
     * sibling implementations via the interface.
     */
    @Nullable
    private PascalRoutine findInterfaceMethodFor(@NotNull PascalRoutine classMethod,
                                                  @NotNull PascalTypeDefinition classType) {
        String methodName = classMethod.getName();
        if (methodName == null) return null;

        Project project = classType.getProject();
        for (String ancestor : classType.getAllAncestorNames()) {
            String simpleName = stripQualifiers(ancestor);
            Collection<PascalTypeDefinition> types = PascalTypeIndex.findTypes(simpleName, project);
            for (PascalTypeDefinition type : types) {
                if (type.getTypeKind() != TypeKind.INTERFACE) continue;
                for (PascalRoutine method : type.getMethods()) {
                    if (methodName.equalsIgnoreCase(method.getName())) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    /**
     * For a property on a CLASS, find the matching property on an ancestor INTERFACE.
     */
    @Nullable
    private PascalProperty findInterfacePropertyFor(@NotNull PascalProperty classProp,
                                                     @NotNull PascalTypeDefinition classType) {
        String propName = classProp.getName();
        if (propName == null) return null;

        Project project = classType.getProject();
        for (String ancestor : classType.getAllAncestorNames()) {
            String simpleName = stripQualifiers(ancestor);
            Collection<PascalTypeDefinition> types = PascalTypeIndex.findTypes(simpleName, project);
            for (PascalTypeDefinition type : types) {
                if (type.getTypeKind() != TypeKind.INTERFACE) continue;
                for (PascalProperty prop : type.getProperties()) {
                    if (propName.equalsIgnoreCase(prop.getName())) {
                        return prop;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Strip generic params and unit prefix from an ancestor name.
     */
    private String stripQualifiers(String name) {
        int ltIdx = name.indexOf('<');
        if (ltIdx > 0) name = name.substring(0, ltIdx);
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx >= 0) name = name.substring(dotIdx + 1);
        return name;
    }

    /**
     * Check if the type's ancestor list contains the given simple name (case-insensitive).
     */
    private boolean ancestorListContains(@NotNull PascalTypeDefinition type, @NotNull String simpleName) {
        for (String ancestor : type.getAllAncestorNames()) {
            String stripped = stripQualifiers(ancestor);
            if (simpleName.equalsIgnoreCase(stripped)) return true;
        }
        return false;
    }
}
