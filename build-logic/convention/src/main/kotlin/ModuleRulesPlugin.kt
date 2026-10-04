import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Enforces the module graph from the migration plan (§03). Applied to the root project; run with
 * `./gradlew checkModuleRules` (CI does). Test configurations may also use `:core:testing`.
 */
class ModuleRulesPlugin : Plugin<Project> {

    private val sources = setOf(":core:database", ":core:datastore", ":core:network", ":core:firebase", ":core:location")

    /** Project dependencies each module may have in production code; null means any. */
    private fun allowed(path: String): Set<String>? = when {
        path == ":core:common" -> emptySet()
        path == ":core:domain" -> setOf(":core:common")
        path == ":core:testing" -> setOf(":core:common", ":core:domain")
        path == ":core:ml" -> setOf(":core:common", ":core:domain")
        path == ":core:designsystem" -> setOf(":core:common")
        path in sources -> setOf(":core:common")
        path == ":core:data" -> setOf(":core:common", ":core:domain", ":core:ml") + sources
        path.startsWith(":feature:") -> setOf(":core:common", ":core:domain", ":core:designsystem", ":core:maps")
        else -> null
    }

    /** Modules that must stay pure Kotlin: only the Kotlin standard library and coroutines. */
    private val pure = setOf(":core:common", ":core:domain")
    private val pureGroups = setOf("org.jetbrains.kotlin", "org.jetbrains.kotlinx")

    override fun apply(target: Project) {
        val violations = mutableListOf<String>()
        target.gradle.projectsEvaluated {
            target.subprojects.forEach { project -> violations += check(project) }
        }
        target.tasks.register("checkModuleRules", CheckModuleRulesTask::class.java) {
            group = "verification"
            description = "Checks module dependencies against the architecture rules."
            this.violations.set(target.provider { violations })
        }
    }

    private fun check(project: Project): List<String> {
        val found = mutableListOf<String>()
        val allowed = allowed(project.path)
        project.configurations
            .filter { c -> listOf("Implementation", "Api", "CompileOnly").any { c.name.endsWith(it, ignoreCase = true) } }
            .forEach { configuration ->
                val isTest = configuration.name.contains("test", ignoreCase = true)
                configuration.dependencies.forEach { dependency ->
                    val where = "${project.path} (${configuration.name})"
                    when (dependency) {
                        is ProjectDependency -> {
                            val to = dependency.path
                            when {
                                to == ":core:testing" && !isTest && project.path != ":core:testing" ->
                                    found += "$where → $to: core:testing is for tests only"
                                isTest && to == ":core:testing" -> Unit
                                allowed != null && to !in allowed -> found += "$where → $to is not allowed"
                            }
                        }
                        is ExternalModuleDependency ->
                            if (project.path in pure && !isTest && dependency.group !in pureGroups) {
                                found += "$where → ${dependency.group}:${dependency.name}: must stay pure Kotlin"
                            }
                    }
                }
            }
        return found
    }
}

abstract class CheckModuleRulesTask : DefaultTask() {
    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun check() {
        val found = violations.get()
        if (found.isNotEmpty()) throw GradleException("Module rules broken:\n" + found.joinToString("\n") { "  $it" })
        logger.lifecycle("Module rules: OK")
    }
}
