### Sonar-Delphi Integration Plan

This document tracks the progress of integrating the `sonar-delphi` parser into the IntelliJ Pascal plugin. The goal is to replace manual text scanning with a robust AST-based approach while transitioning the codebase to Kotlin.

#### Current Status
- [x] Pre-requisites: Added Kotlin and `sonar-delphi` (v1.18.3) dependencies to `build.gradle.kts`.
- [x] Milestone 1: Kotlin Infrastructure & Parser Bridge (Completed: successfully integrated sonar-delphi 1.18.3 API, added unit tests)
- [x] Milestone 2: Basic File Structure (Unit / Program / Library) (Completed: mapped Unit/Program/Library declarations and Interface/Implementation sections)
- [x] Milestone 3: Type Definitions (Classes, Records, Interfaces) (Completed: mapped TypeDeclarationNode to TYPE_DEFINITION)
- [x] Milestone 4: Variable & Field Recognition (Completed: mapped NameDeclarationNode to VARIABLE_DEFINITION within appropriate contexts, added Variable/Type sections)
- [x] Milestone 5: Scope & Reference Resolution (Completed: refactored VariableDefinition/TypeDefinition to use PSI tree, improved visibility and generic support)
- [x] Milestone 6: Cleanup & Optimization (Completed: removed legacy manual parser files, updated parser definition)

---

#### Milestone 1: Kotlin Infrastructure & Parser Bridge
*Goal: Successfully invoke the sonar-delphi parser within the IntelliJ PSI building process.*
- **Action:** Create `nl.akiar.pascal.parser.PascalSonarParser.kt`.
- **Implementation:** Implement `PsiParser` interface. In the `parse` method, convert the `PsiBuilder` content to a string, pass it to the `DelphiParser` (from sonar-delphi), and receive an SSLR `AstNode`.
- **Verification:** Set a breakpoint in the parser or add a `LOG.info` to confirm the sonar-delphi parser is receiving the text and producing an AST.

#### Milestone 2: Basic File Structure (Unit / Program / Library)
*Goal: Use the AST to identify the main components of a Pascal file.*
- **Action:** Map sonar-delphi `compilationUnit`, `unitId`, and section nodes (interface, implementation) to PSI nodes.
- **Implementation:** Walk the sonar-delphi AST and use `builder.mark()` / `marker.done()` to wrap the relevant token ranges.
- **Verification:** Open a `.pas` file and check if the "Structure" tool window (Cmd+7) or "File Structure" popup (Cmd+F12) correctly displays the Unit name.

#### Milestone 3: Type Definitions (Classes, Records, Interfaces)
*Goal: Replace the fragile `isTypeDefinitionStart` logic with reliable AST mapping.*
- **Action:** Identify `typeDeclaration` nodes in the sonar-delphi AST.
- **Implementation:** Map these to `PascalElementTypes.TYPE_DEFINITION`. Use the AST to precisely locate the identifier and the type body (class/record).
- **Verification:** Verify that classes and records are correctly indexed. The "Go to Symbol" (Cmd+Alt+O) should reliably find these types even in complex files.

#### Milestone 4: Variable & Field Recognition
*Goal: Robust identification of variables across all scopes without backward scanning.*
- **Action:** Map `variableDeclaration`, `fieldDeclaration`, and `parameterDeclaration` nodes.
- **Implementation:**
    - Wrap these in `PascalElementTypes.VARIABLE_DEFINITION`.
    - Use the AST's parent hierarchy to determine the `VariableKind` (e.g., if the parent is a class body, it's a `FIELD`; if it's inside a procedure's `var` section, it's `LOCAL`).
- **Verification:** Check that local variables and class fields appear correctly in the index and have the correct icons/metadata in the UI.

#### Milestone 5: Scope & Reference Resolution
*Goal: Leverage the full AST to resolve identifiers.*
- **Action:** Implement scope-aware logic in `PascalVariableDefinitionImpl.getKind()` using the AST position.
- **Implementation:** Instead of the current `backward scan` logic in `PascalVariableDefinitionImpl.java`, the new Kotlin implementation will simply query its position in the mapped PSI tree.
- **Verification:** "Find Usages" and "Go to Declaration" (Cmd+Click) for local variables should work reliably, even if there are variables with the same name in different scopes.

#### Milestone 6: Cleanup & Optimization
*Goal: Remove legacy code and optimize performance.*
- **Action:** Delete the old `PascalStructuredParser.java` and `PascalParser.java`.
- **Implementation:** Ensure the bridge efficiently walks the SSLR AST. Implement a caching mechanism if necessary for large files.
- **Verification:** Run the plugin against a large Delphi codebase (e.g., the VCL source) to ensure no performance regressions or memory leaks.
