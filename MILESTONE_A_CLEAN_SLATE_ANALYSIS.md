# Milestone A - Clean Slate Results

## Test Results Summary

### Baseline (HEAD commit, before Milestone A)
- **Tests**: 191
- **Failures**: 27
- **Success Rate**: 86%

### After Milestone A Changes (with stub version bump)
- **Tests**: 191  
- **Failures**: 32
- **Success Rate**: 83%

### **NET IMPACT: +5 new failures introduced by Milestone A**

## Analysis

The stub enrichments introduced 5 new test failures. These are likely caused by:

1. **Null handling in new stub fields** - visibility/section/ownerTypeName may be null in edge cases
2. **Index key mismatches** - composite keys for scoped indexes may not match expected format
3. **Serialization order** - even with version bump, there may be compatibility issues

## Failing Tests Breakdown

### Baseline Failures (27 - pre-existing)
- MapReduceIndexMappingException with NPE: 14 tests
- StackOverflowError: 8 tests  
- Other: 5 tests

### New Failures from Milestone A (5 additional)
**Need to investigate which specific 5 tests regressed**

## Action Plan to Get Clean Slate

### Option 1: Fix the 5 Regressions  
Identify and fix the specific tests that broke due to our stub changes.

### Option 2: Revert Milestone A Completely
Go back to baseline and start fresh with a more conservative approach.

### Option 3: Minimal Stub Changes Only
Keep only the scoped index infrastructure but don't change existing stub serialization yet.

## Recommendation

**Option 1 is preferred** - We should fix the 5 regressions because:
- The stub enrichments are necessary for Milestone B
- Version bump forces clean reindex
- 5 failures is manageable to debug and fix

Next steps:
1. Identify which 5 tests are new failures (compare to baseline list)
2. Review their failure causes
3. Add null guards and defensive checks
4. Re-run to confirm we match baseline

---
**Status**: Milestone A has 5 regressions that need fixing before proceeding
**Baseline to match**: 191 tests, 27 failures

