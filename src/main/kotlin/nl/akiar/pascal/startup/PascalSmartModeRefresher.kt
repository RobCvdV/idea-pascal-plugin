package nl.akiar.pascal.startup

import com.intellij.ProjectTopics
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.messages.MessageBusConnection
import nl.akiar.pascal.resolution.MemberChainResolver
import nl.akiar.pascal.resolution.PascalEnumValueIndex

/**
 * Listens for smart mode transitions and roots changes to clear resolution caches
 * so that highlighting and navigation recompute with complete indices.
 */
class PascalSmartModeRefresher : ProjectActivity {
    override suspend fun execute(project: Project) {
        val bus: MessageBusConnection = project.messageBus.connect()
        bus.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun exitDumbMode() {
                // Smart again: clear member-resolution caches (cheap to rebuild)
                // and restart daemon. Do NOT clear the enum-value index here:
                // during reindex storms exitDumbMode fires repeatedly and clearing
                // would force the slow enum scan to rerun every dumb→smart cycle.
                MemberChainResolver.clearCaches(project)
            }
        })
        bus.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                // Roots change is rare and means the index might now contain new
                // declarations — clear everything including the enum-value cache.
                MemberChainResolver.clearCaches(project)
                PascalEnumValueIndex.invalidate(project)
            }
        })
    }
}
