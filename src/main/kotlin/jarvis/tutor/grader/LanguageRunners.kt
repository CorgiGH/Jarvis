package jarvis.tutor.grader

import java.io.File

/**
 * Per-language subprocess runners for the execution grader (R-6-Q1, §0.9-C).
 *
 * Each [LanguageRunner] knows two things:
 *  - how to RESOLVE its toolchain binary ([probe]) — env override → PATH → known default
 *    install locations (the §0.9-C resolution order),
 *  - how to turn a student program + a sandbox temp dir into the ProcessBuilder commands the
 *    [ExecutionGrader] runs (write the source file; optional compile command; the run command).
 *
 * Languages: R (`Rscript main.R`), Python (`python main.py`, fallback `python3`),
 * C++ (`g++ -std=c++17 main.cpp -o main` then run), Alk (the verified wrapper script
 * `tools/alk/alki.cmd` / `alki.sh` — Task 0; never the raw classpath form).
 *
 * **bash / POSIX-C have NO runner** — they are routed to the rubric leg by [GraderRouting]
 * (R-6-Q8); asking for them here yields a `null` runner so the execution leg defers.
 */

/** The result of probing one language's toolchain (§0.9-C honest leg-disable). */
data class RunnerAvailability(val available: Boolean, val reason: String, val binary: String? = null)

/** A compile step (optional) + a run step, both as full ProcessBuilder command lists. */
data class RunnerCommands(
    /** The file the student source is written to inside the temp dir. */
    val sourceFileName: String,
    /** Optional compile command (e.g. g++). Null → interpreted language, no compile. */
    val compileCommand: List<String>?,
    /** The command that runs the program. */
    val runCommand: List<String>,
)

/**
 * One language runner. [language] is the R-LANG id ("r"|"python"|"cpp"|"alk").
 * Implementations are stateless; the resolved binary path is cached by [ToolchainProbe].
 */
interface LanguageRunner {
    val language: String

    /** Resolve + version-probe the toolchain. Missing → `available=false` (the leg degrades). */
    fun probe(): RunnerAvailability

    /**
     * Build the compile/run commands for a [source] program inside [workDir].
     * Precondition: [probe] returned available; [resolvedBinary] is its binary.
     */
    fun commandsFor(source: String, workDir: File, resolvedBinary: String): RunnerCommands
}

/**
 * Resolves a binary by the §0.9-C order: (1) env override, (2) PATH, (3) known default
 * install locations. Probes `--version` (or an equivalent harmless invocation) to confirm
 * the binary actually runs. Resolution is memoized per (binary-set) for the process lifetime.
 */
object ToolchainProbe {

    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /** Default probe wall-clock budget — a `--version` must not hang the chain. */
    private const val PROBE_TIMEOUT_SECONDS = 8L

    private val cache = HashMap<String, String?>()

    /**
     * Resolve a binary. [envVar] is the override env (e.g. JARVIS_RSCRIPT); [pathNames] are the
     * names tried on PATH (e.g. ["python","python3"]); [defaultLocations] are absolute fallback
     * paths (globs allowed via [defaultGlobs]). Returns the runnable path, or null if none works.
     */
    @Synchronized
    fun resolve(
        cacheKey: String,
        envVar: String?,
        pathNames: List<String>,
        defaultGlobs: List<String> = emptyList(),
        versionArgs: List<String> = listOf("--version"),
    ): String? {
        cache[cacheKey]?.let { return it }
        if (cache.containsKey(cacheKey)) return null // resolved to null before

        val candidates = buildList {
            envVar?.let { System.getenv(it)?.takeIf { v -> v.isNotBlank() }?.let(::add) }
            addAll(pathNames)
            addAll(expandGlobs(defaultGlobs))
        }
        for (cand in candidates) {
            if (runsVersion(cand, versionArgs)) {
                cache[cacheKey] = cand
                return cand
            }
        }
        cache[cacheKey] = null
        return null
    }

    /** Expand simple `*`-globs against the filesystem (e.g. R-* in Program Files). */
    private fun expandGlobs(globs: List<String>): List<String> = globs.flatMap { g ->
        if (!g.contains('*')) return@flatMap listOf(g)
        val star = g.indexOf('*')
        val prefix = g.substring(0, star)
        val parent = File(prefix).parentFile ?: return@flatMap emptyList<String>()
        val rest = g.substring(star) // e.g. "*\bin\Rscript.exe"
        if (!parent.isDirectory) return@flatMap emptyList<String>()
        val sep = File.separatorChar
        val tail = rest.substringAfter(sep, "") // "bin\Rscript.exe"
        val dirPrefix = File(prefix).name.removeSuffix("*") // "R-" (the segment prefix before *)
        parent.listFiles { f -> f.isDirectory && f.name.startsWith(dirPrefix) }
            .orEmpty()
            .map { File(it, tail).path }
            .filter { File(it).exists() }
    }

    private fun runsVersion(binary: String, versionArgs: List<String>): Boolean = try {
        val proc = ProcessBuilder(listOf(binary) + versionArgs)
            .redirectErrorStream(true)
            .start()
        val finished = proc.waitFor(PROBE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            false
        } else {
            proc.exitValue() == 0
        }
    } catch (_: Exception) {
        false
    }

    fun windows(): Boolean = isWindows

    /** Test seam: drop the memoized resolution so a test can re-probe. */
    @Synchronized
    fun clearCacheForTest() = cache.clear()
}

/** R runner — `Rscript main.R`. Env override JARVIS_RSCRIPT; Windows default `C:\Program Files\R\R-*\bin\Rscript.exe`. */
class RRunner : LanguageRunner {
    override val language = "r"
    override fun probe(): RunnerAvailability {
        val bin = ToolchainProbe.resolve(
            cacheKey = "rscript",
            envVar = "JARVIS_RSCRIPT",
            pathNames = listOf("Rscript"),
            defaultGlobs = if (ToolchainProbe.windows())
                listOf("C:\\Program Files\\R\\R-*\\bin\\Rscript.exe")
            else
                listOf("/usr/bin/Rscript", "/usr/local/bin/Rscript"),
        )
        return if (bin != null) RunnerAvailability(true, "Rscript at $bin", bin)
        else RunnerAvailability(false, "Rscript not found (set JARVIS_RSCRIPT or add R to PATH)")
    }

    override fun commandsFor(source: String, workDir: File, resolvedBinary: String) = RunnerCommands(
        sourceFileName = "main.R",
        compileCommand = null,
        runCommand = listOf(resolvedBinary, "main.R"),
    )
}

/** Python runner — `python main.py`, fallback `python3`. Env override JARVIS_PYTHON. */
class PythonRunner : LanguageRunner {
    override val language = "python"
    override fun probe(): RunnerAvailability {
        val bin = ToolchainProbe.resolve(
            cacheKey = "python",
            envVar = "JARVIS_PYTHON",
            pathNames = listOf("python", "python3"),
        )
        return if (bin != null) RunnerAvailability(true, "python at $bin", bin)
        else RunnerAvailability(false, "python/python3 not found (set JARVIS_PYTHON or add python to PATH)")
    }

    override fun commandsFor(source: String, workDir: File, resolvedBinary: String) = RunnerCommands(
        sourceFileName = "main.py",
        compileCommand = null,
        runCommand = listOf(resolvedBinary, "main.py"),
    )
}

/** C++ runner — `g++ -std=c++17 main.cpp -o main`, then run the binary. Env override JARVIS_GXX. */
class CppRunner : LanguageRunner {
    override val language = "cpp"
    override fun probe(): RunnerAvailability {
        val bin = ToolchainProbe.resolve(
            cacheKey = "gxx",
            envVar = "JARVIS_GXX",
            pathNames = listOf("g++"),
        )
        return if (bin != null) RunnerAvailability(true, "g++ at $bin", bin)
        else RunnerAvailability(false, "g++ not found (set JARVIS_GXX or add MinGW/g++ to PATH)")
    }

    override fun commandsFor(source: String, workDir: File, resolvedBinary: String): RunnerCommands {
        val exeName = if (ToolchainProbe.windows()) "main.exe" else "main"
        val runPath = File(workDir, exeName).path
        return RunnerCommands(
            sourceFileName = "main.cpp",
            compileCommand = listOf(resolvedBinary, "-std=c++17", "main.cpp", "-o", exeName),
            runCommand = listOf(runPath),
        )
    }
}

/**
 * Alk runner — the verified wrapper script (Task 0): `tools/alk/alki.cmd <file>` (Windows) /
 * `sh tools/alk/alki.sh <file>` (POSIX). NEVER the raw classpath form. The wrapper directory is
 * resolved via JARVIS_ALK_DIR (deploy override) → the repo `tools/alk` dir (relative to the working
 * directory, the repo root in normal runs). The wrapper itself locates `v4.3/bin` relatively.
 */
class AlkRunner : LanguageRunner {
    override val language = "alk"

    private fun alkToolsDir(): File {
        System.getenv("JARVIS_ALK_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        return File("tools/alk").absoluteFile
    }

    private fun wrapperFile(): File {
        val dir = alkToolsDir()
        return if (ToolchainProbe.windows()) File(dir, "alki.cmd") else File(dir, "alki.sh")
    }

    override fun probe(): RunnerAvailability {
        // Java must be present AND the wrapper + extracted interpreter must exist.
        val java = ToolchainProbe.resolve(
            cacheKey = "java",
            envVar = "JARVIS_JAVA",
            pathNames = listOf("java"),
            versionArgs = listOf("-version"),
        ) ?: return RunnerAvailability(false, "java not found (Alk runs on the JVM via the wrapper)")
        val wrapper = wrapperFile()
        if (!wrapper.exists()) {
            return RunnerAvailability(false, "Alk wrapper missing at ${wrapper.path} (run `node tools/fetch-alk.mjs`)")
        }
        val jar = File(alkToolsDir(), "v4.3/bin/alk.jar")
        if (!jar.exists()) {
            return RunnerAvailability(false, "Alk interpreter not fetched (${jar.path} absent — run `node tools/fetch-alk.mjs`)")
        }
        return RunnerAvailability(true, "Alk wrapper at ${wrapper.path} (java $java)", wrapper.path)
    }

    override fun commandsFor(source: String, workDir: File, resolvedBinary: String): RunnerCommands {
        // resolvedBinary is the wrapper path. The wrapper takes the alk file as its argument.
        val alkFile = File(workDir, "main.alk").path
        val run = if (ToolchainProbe.windows()) {
            listOf("cmd", "/c", resolvedBinary, alkFile)
        } else {
            listOf("sh", resolvedBinary, alkFile)
        }
        return RunnerCommands(
            sourceFileName = "main.alk",
            compileCommand = null,
            runCommand = run,
        )
    }
}

/** The runner registry. bash/posix-c deliberately ABSENT (R-6-Q8 → rubric leg). */
object LanguageRunners {
    private val runners: Map<String, LanguageRunner> = listOf(
        RRunner(),
        PythonRunner(),
        CppRunner(),
        AlkRunner(),
    ).associateBy { it.language }

    /** Returns the runner for an R-LANG id, or null (→ execution leg defers; bash/posix-c land here). */
    fun forLanguage(language: String?): LanguageRunner? {
        val key = language?.lowercase()?.trim() ?: return null
        // Normalize "c++"→"cpp"; "python3"→"python".
        val norm = when (key) {
            "c++", "cplusplus" -> "cpp"
            "python3" -> "python"
            else -> key
        }
        return runners[norm]
    }

    fun all(): Collection<LanguageRunner> = runners.values
}
