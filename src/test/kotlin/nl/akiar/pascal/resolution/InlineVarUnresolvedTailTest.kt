package nl.akiar.pascal.resolution

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nl.akiar.pascal.psi.PascalVariableDefinition

/**
 * Inline `var X := some.chain` must yield an unknown (null) inferred type when
 * the final member of the chain fails to resolve. Previously the inference
 * fell back to the type of the last-resolved chain element, which leaked an
 * incorrect type (e.g. LOrder inferring to the type of `.Value` when
 * `.Value.ItemsById[...]` couldn't be resolved).
 */
class InlineVarUnresolvedTailTest : BasePlatformTestCase() {

    fun testInferredTypeIsUnknownWhenFinalMemberUnresolved() {
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TInner = class
              public
                Value: Integer;
              end;
              TOuter = class
              public
                property Inner: TInner read GetInner;
              end;
            implementation
            procedure DoTest;
            var
              LOuter: TOuter;
            begin
              // NoSuchMember does not exist on TInner; the chain final step fails.
              var LBad := LOuter.Inner.NoSuchMember;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lBad = vars.firstOrNull { it.name == "LBad" }
        assertNotNull("LBad inline var should be found", lBad)

        val inferred = MemberChainResolver.getInferredTypeName(lBad!!, file)
        assertNull("Inferred type must be unknown when final chain member fails to resolve, " +
            "got '$inferred' (leaking from prior step)", inferred)
    }

    fun testInferredTypeStillResolvesWhenChainFullyResolves() {
        // Sanity check: the policy change must not break the happy path.
        val file = myFixture.configureByText("Test.pas", """
            unit Test;
            interface
            type
              TInner = class
              public
                Value: Integer;
              end;
              TOuter = class
              public
                property Inner: TInner read GetInner;
              end;
            implementation
            procedure DoTest;
            var
              LOuter: TOuter;
            begin
              var LGood := LOuter.Inner;
            end;
            end.
        """.trimIndent())

        val vars = PsiTreeUtil.findChildrenOfType(file, PascalVariableDefinition::class.java)
        val lGood = vars.firstOrNull { it.name == "LGood" }
        assertNotNull(lGood)

        val inferred = MemberChainResolver.getInferredTypeName(lGood!!, file)
        assertEquals("TInner", inferred)
    }
}
