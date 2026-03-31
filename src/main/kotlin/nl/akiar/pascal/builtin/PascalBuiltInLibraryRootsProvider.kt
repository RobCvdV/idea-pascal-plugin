package nl.akiar.pascal.builtin

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import javax.swing.Icon

/**
 * Provides a bundled System.pas containing compiler-intrinsic declarations
 * (TObject, IInterface, etc.) as an additional library root so that stub
 * indexing picks them up automatically.
 */
class PascalBuiltInLibraryRootsProvider : AdditionalLibraryRootsProvider() {

    companion object {
        private val LOG = Logger.getInstance(PascalBuiltInLibraryRootsProvider::class.java)
        private const val BUILTIN_DIR = "pascal-builtins"
        private const val RESOURCE_PATH = "/builtin/System.pas"
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        if (project.isDefault || !project.isInitialized || project.isDisposed) {
            return emptyList()
        }

        val targetDir = File(PathManager.getSystemPath(), BUILTIN_DIR)
        val targetFile = File(targetDir, "System.pas")

        if (!targetFile.exists()) {
            try {
                targetDir.mkdirs()
                val resourceStream = javaClass.getResourceAsStream(RESOURCE_PATH)
                if (resourceStream == null) {
                    LOG.warn("[PascalBuiltIn] Resource $RESOURCE_PATH not found in JAR")
                    return emptyList()
                }
                resourceStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                LOG.info("[PascalBuiltIn] Extracted System.pas to ${targetFile.absolutePath}")
            } catch (e: Exception) {
                LOG.warn("[PascalBuiltIn] Failed to extract System.pas: ${e.message}")
                return emptyList()
            }
        }

        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir)
        if (vf == null || !vf.isValid || !vf.isDirectory) {
            LOG.warn("[PascalBuiltIn] Could not find virtual file for $targetDir")
            return emptyList()
        }

        return listOf(PascalBuiltInLibrary(listOf(vf)))
    }

    private class PascalBuiltInLibrary(private val roots: List<VirtualFile>) : SyntheticLibrary(), ItemPresentation {
        override fun getSourceRoots(): Collection<VirtualFile> = roots

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            return roots == (other as PascalBuiltInLibrary).roots
        }

        override fun hashCode(): Int = roots.hashCode()

        override fun getPresentableText(): String = "Pascal Built-in Types"
        override fun getLocationString(): String = "System.pas (compiler intrinsics)"
        override fun getIcon(unused: Boolean): Icon? = null
    }
}
