package nl.akiar.pascal.project;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.command.WriteCommandAction;
import nl.akiar.pascal.settings.PascalSourcePathsSettings;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.Set;

public class PascalDependencyServiceTest extends BasePlatformTestCase {
    
    public void testTransitiveDependencyCrawling() throws Exception {
        PascalDependencyService service = PascalDependencyService.getInstance(getProject());
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(getProject());
        
        // Create a chain of units: Unit1 -> Unit2 -> Unit3
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                VirtualFile dir = myFixture.getTempDirFixture().findOrCreateDir("lib");
                settings.setSourcePaths(Collections.singletonList(dir.getUrl()));
                
                VirtualFile file3 = dir.createChildData(this, "Unit3.pas");
                com.intellij.openapi.vfs.VfsUtil.saveText(file3, "unit Unit3;\ninterface\nimplementation\nend.");
                
                VirtualFile file2 = dir.createChildData(this, "Unit2.pas");
                com.intellij.openapi.vfs.VfsUtil.saveText(file2, "unit Unit2;\ninterface\nuses Unit3;\nimplementation\nend.");
                
                VirtualFile file1 = myFixture.addFileToProject("Unit1.pas", 
                    "unit Unit1;\ninterface\nuses Unit2;\nimplementation\nend.").getVirtualFile();
                
                // Trigger initial scan
                service.markActive(file1);
                
                // Wait for background scan (AppExecutorUtil)
                // In tests we might need to wait or trigger it manually
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // Wait up to 5 seconds for background scan
        long start = System.currentTimeMillis();
        boolean found = false;
        while (System.currentTimeMillis() - start < 5000) {
            VirtualFile file3 = myFixture.getTempDirFixture().getFile("lib/Unit3.pas");
            if (file3 != null && service.isActive(file3)) {
                found = true;
                break;
            }
            Thread.sleep(100);
        }
        
        assertTrue("Unit3.pas should be active via transitive dependency from Unit1 -> Unit2 -> Unit3", found);
        
        VirtualFile file2 = myFixture.getTempDirFixture().getFile("lib/Unit2.pas");
        assertTrue("Unit2.pas should be active", service.isActive(file2));
    }

    public void testNonActiveFileNotIndexed() throws Exception {
        PascalDependencyService service = PascalDependencyService.getInstance(getProject());
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(getProject());

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                // In Light tests, everything under the temp root is often considered "project"
                // Let's try to simulate a library by using a path that we know is NOT in the project
                // but configured in settings.
                
                // For this test to work reliably, we need a file that isActive() returns false for.
                // isActive() returns true for anything under guessProjectDir().
                
                VirtualFile projectDir = ProjectUtil.guessProjectDir(getProject());
                VirtualFile tempRoot = projectDir.getParent();
                VirtualFile libDir = tempRoot.createChildDirectory(this, "external_library");
                
                settings.setSourcePaths(Collections.singletonList(libDir.getUrl()));

                VirtualFile file = libDir.createChildData(this, "UnusedUnit.pas");
                com.intellij.openapi.vfs.VfsUtil.saveText(file,
                    "unit UnusedUnit;\ninterface\nvar GUnused: Integer;\nimplementation\nend.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // We need to find the file we just created
        VirtualFile projectDir = ProjectUtil.guessProjectDir(getProject());
        VirtualFile tempRoot = projectDir.getParent();
        VirtualFile unusedFile = tempRoot.findFileByRelativePath("external_library/UnusedUnit.pas");
        
        assertNotNull(unusedFile);
        assertFalse("UnusedUnit.pas should NOT be active as it is outside project dir", service.isActive(unusedFile));

        // Try to find the variable GUnused via index
        java.util.Collection<nl.akiar.pascal.psi.PascalVariableDefinition> vars =
            nl.akiar.pascal.stubs.PascalVariableIndex.findVariables("GUnused", getProject());

        assertTrue("Variable in non-active file should NOT be indexed", vars.isEmpty());
    }

    public void testFileOpenedBecomesActive() throws Exception {
        PascalDependencyService service = PascalDependencyService.getInstance(getProject());
        PascalSourcePathsSettings settings = PascalSourcePathsSettings.getInstance(getProject());

        final VirtualFile[] filePtr = new VirtualFile[1];
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                VirtualFile projectDir = ProjectUtil.guessProjectDir(getProject());
                VirtualFile tempRoot = projectDir.getParent();
                VirtualFile libDir = tempRoot.createChildDirectory(this, "active_lib");
                settings.setSourcePaths(Collections.singletonList(libDir.getUrl()));

                filePtr[0] = libDir.createChildData(this, "OpenedUnit.pas");
                com.intellij.openapi.vfs.VfsUtil.saveText(filePtr[0],
                    "unit OpenedUnit;\ninterface\nimplementation\nend.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        VirtualFile openedFile = filePtr[0];
        assertFalse("Should not be active yet", service.isActive(openedFile));

        // Simulate opening the file
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).openFile(openedFile, true);

        // Should become active immediately or after a short delay
        assertTrue("File should become active when opened", service.isActive(openedFile));
    }
}
