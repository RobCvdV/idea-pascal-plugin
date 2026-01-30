package nl.akiar.pascal.startup

import com.intellij.ProjectTopics
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.messages.MessageBusConnection
import nl.akiar.pascal.resolution.MemberChainResolver

/**
 * Listens for smart mode transitions and roots changes to clear resolution caches
 * so that highlighting and navigation recompute with complete indices.
 */
class PascalSmartModeRefresher : ProjectActivity {
    override suspend fun execute(project: Project) {
        val bus: MessageBusConnection = project.messageBus.connect()
        bus.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun exitDumbMode() {
                // Smart again: clear caches and restart daemon
                MemberChainResolver.clearCaches(project)
            }
        })
        bus.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                MemberChainResolver.clearCaches(project)
            }
        })
    }
}
