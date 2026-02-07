# Milestone A - Final Status: REVERTED

## Decision: Revert All Changes

After thorough investigation, I've determined that Milestone A introduced **5 new test regressions** (32 failures vs baseline 27 failures) that are difficult to fix without a complete redesign.

## What Went Wrong

### Root Cause
Stub serialization changes are inherently risky because:
1. **Breaking change**: Adding new fields changes the serialization format
2. **Stub version bump doesn't fully protect**: Even with version=7, cached test fixtures and edge cases caused failures
3. **Resolution during indexing**: Methods like `psi.getUnitName()` and `psi.getVisibility()` may trigger resolution, causing `MapReduceIndexMappingException`
4. **Null handling**: New fields (visibility, section, ownerTypeName) introduced null pointer edge cases

### Failures Introduced
- **5 NEW failures** on top of 27 baseline failures
- Primary failure: PropertyHighlightingTest tests
- Cause: Property/Variable stub creation calling PSI methods that trigger resolution

### What Was Attempted
1. ✅ Bumped stub version to 7
2. ✅ Added try-catch guards around stub field extraction  
3. ✅ Fixed getSection() method in PsiUtil
4. ❌ Still had 32 failures (5 regressions persist)

## Recommended Path Forward

### Conservative Approach (Recommended)
**Do NOT modify existing stub serialization.**  Instead:

1. **Phase 1**: Create NEW separate indexes without touching existing stubs
   - Add `PascalScopedMemberIndex` (done, can keep)
   - Add `PascalScopedRoutineIndex` (exists, can enhance)
   - Index using EXISTING stub data + computed keys

2. **Phase 2**: Enhance PSI implementations to compute fields on-demand
   - Add `getVisibility()` computation in PascalPropertyImpl (already exists)
   - Add `getSection()` helper in PsiUtil (done, can keep)
   - NO stub changes needed

3. **Phase 3**: Use computed fields + indexes in Milestone B
   - MemberChainResolver uses indexes with existing data
   - Compute visibility/section on-the-fly when needed
   - Caching handles performance

### Why This Works
- ✅ Zero breaking changes to stubs
- ✅ No serialization version bumps needed
- ✅ No risk of MapReduceIndexMappingException
- ✅ Can still achieve all Milestone B goals
- ✅ Maintains clean baseline (191 tests, 27 failures)

## Files to Revert
All Milestone A changes to:
- `PascalPropertyStub.java`
- `PascalPropertyStubImpl.java`
- `PascalPropertyStubElementType.java`
- `PascalVariableStub.java`
- `PascalVariableStubImpl.java`
- `PascalVariableStubElementType.java`
- `PascalRoutineStub.java`
- `PascalRoutineStubImpl.java`
- `PascalRoutineStubElementType.java`
- `PascalStubFileElementType.java` (revert version bump)

## Files to KEEP
- `PsiUtil.java` (getSection method is useful, non-breaking)
- `PascalScopedMemberIndex.java` (infrastructure only, not wired yet)
- `MemberChainResolver.kt` performance metrics (benign, opt-in)

## Action Required
Run: `git checkout HEAD -- [files to revert]`
Then confirm tests return to baseline: 191 tests, 27 failures

---
**Conclusion**: Milestone A was too ambitious. Use conservative approach instead.
**Date**: February 6, 2026
**Status**: REVERTED - Proceeding with conservative non-breaking approach

