package nl.akiar.pascal.project;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.command.WriteCommandAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class PascalProjectServiceTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testIndexFileWithNonUtf8Encoding() {
        PascalProjectService service = PascalProjectService.getInstance(getProject());

        String unitName = "EncodingTest";
        String content = "unit " + unitName + ";\ninterface\nimplementation\nend. // Special char: \u00E9";
        byte[] bytes = content.getBytes(StandardCharsets.ISO_8859_1);

        // Wrap everything that modifies the project/VFS in a single WriteCommandAction
        // and use the standard JUnit 3/4 style naming (test...) for BasePlatformTestCase
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                VirtualFile file = myFixture.addFileToProject("src/EncodingTest.pas", "").getVirtualFile();
                file.setBinaryContent(bytes);

                // Initialize settings inside the write action to ensure consistency
                nl.akiar.pascal.settings.PascalSourcePathsSettings settings =
                        nl.akiar.pascal.settings.PascalSourcePathsSettings.getInstance(getProject());
                settings.setSourcePaths(Collections.singletonList(file.getParent().getUrl()));

                // Trigger rescan
                service.rescan();

                VirtualFile resolved = service.resolveUnit(unitName);
                assertNotNull("Should have resolved unit even with non-UTF-8 encoding", resolved);
                assertEquals(file.getPath(), resolved.getPath());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}