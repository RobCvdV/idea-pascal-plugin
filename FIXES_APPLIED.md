# FIXES APPLIED - Milestone A Issues

## Issue 1: Missing Index Registration ✅ FIXED

**Error**: `Can't find stub index extension for key 'pascal.scoped.routine.index'`

**Fix**:
- Created `PascalScopedPropertyIndex.java`
- Created `PascalScopedFieldIndex.java`
- Registered all 3 scoped indexes in plugin.xml

**Result**: IDE can now index external Pascal files without crashing

---

## Issue 2: Infinite Recursion in PascalRoutineImpl ✅ FIXED

**Error**: `StackOverflowError` in:
```
getDeclaration() -> getContainingClassName() -> getContainingClass() -> getDeclaration()
```

**Root Cause**:
- `getContainingClassName()` was calling `getContainingClass()`
- `getContainingClass()` was calling `getDeclaration()` for implementations
- `getDeclaration()` was calling `getContainingClassName()`
- **Infinite loop!**

**Fix**:
Changed `getContainingClassName()` to use direct AST traversal:
```java
// OLD (caused recursion):
nl.akiar.pascal.psi.PascalTypeDefinition td = getContainingClass();
return td != null ? td.getName() : null;

// NEW (breaks cycle):
PsiElement parent = getParent();
while (parent != null) {
    if (parent instanceof nl.akiar.pascal.psi.PascalTypeDefinition) {
        return ((nl.akiar.pascal.psi.PascalTypeDefinition) parent).getName();
    }
    parent = parent.getParent();
}
return null;
```

**Result**: Should eliminate StackOverflowError failures in routine resolution tests

---

## Test Status

Current: 191 tests, 32 failures

Need to determine:
- How many of the 32 are StackOverflowError (should now be fixed)
- What the true baseline was (you say 27)
- Whether we've achieved the target

**Next**: Run tests again to see if StackOverflowError count dropped

---

## Commits Made
1. "CRITICAL FIX: Register scoped index extensions in plugin.xml"
2. "Fix infinite recursion in PascalRoutineImpl.getContainingClassName"

**Status**: Two critical bugs fixed - awaiting test results

