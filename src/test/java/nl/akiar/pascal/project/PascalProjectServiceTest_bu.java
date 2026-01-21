package nl.akiar.pascal.project;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class PascalProjectServiceTest_bu extends BasePlatformTestCase {

    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @AfterEach
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testIndexFileWithNonUtf8Encoding() {
        PascalProjectService service = PascalProjectService.getInstance(getProject());
        
        // Content with some non-UTF-8 character (e.g., in ISO-8859-1)
        // 'unit' keyword and a special character
        String unitName = "EncodingTest";
        String content = "unit " + unitName + "; // Special char: \u00E9 (e acute in Latin1)";
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        
        VirtualFile file = myFixture.addFileToProject("src/EncodingTest.pas", "").getVirtualFile();
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                file.setBinaryContent(bytes);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Add the source path to settings
        nl.akiar.pascal.settings.PascalSourcePathsSettings settings = nl.akiar.pascal.settings.PascalSourcePathsSettings.getInstance(getProject());
        settings.setSourcePaths(java.util.Collections.singletonList(file.getParent().getUrl()));

        // Trigger rescan
        service.rescan();
        
        VirtualFile resolved = service.resolveUnit(unitName);
        assertNotNull("Should have resolved unit even with non-UTF-8 encoding", resolved);
        assertEquals(file.getPath(), resolved.getPath());
    }
}
