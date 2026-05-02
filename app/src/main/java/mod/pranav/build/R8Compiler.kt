package mod.pranav.build

import a.a.a.yq
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import com.android.tools.r8.origin.Origin
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.Diagnostic
import java.nio.file.Files
import java.nio.file.Paths

class R8Compiler(
    private val rules: MutableList<String>,
    private val configs: Array<String>,
    val libs: Array<String>,
    private val inputs: Array<String>,
    private val minApi: Int,
    private val multiDexEnabled: Boolean,
    val yq: yq
) {

    fun compile() {
        val output = Paths.get(yq.binDirectoryPath, "dex")
        Files.createDirectories(output)
        
        val errorLog = StringBuilder()
        val diagnosticsHandler = object : DiagnosticsHandler {
            override fun error(diagnostic: Diagnostic) {
                errorLog.append("ERROR: ").append(diagnostic.diagnosticMessage).append("\n")
            }
            override fun warning(diagnostic: Diagnostic) {
                // Warnings are ignored to keep logs clean
            }
            override fun info(diagnostic: Diagnostic) {}
        }

        val builder = R8Command.builder(diagnosticsHandler)
            .addProgramFiles(inputs.map { Paths.get(it) })
            .addProguardConfiguration(rules, Origin.unknown())
            .addProguardConfigurationFiles(configs.map { Paths.get(it) })
            .setProguardMapOutputPath(Paths.get(yq.proguardMappingPath))
            .setMinApiLevel(minApi)
            .addLibraryFiles(libs.map { Paths.get(it) })
            .setOutput(output, OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE)
            
        if (multiDexEnabled) {
            // R8 automatically multi-dexes when OutputMode.DexIndexed is used without a main dex list
        }

        try {
            R8.run(builder.build())
        } catch (e: Exception) {
            // Throw the exact captured error back to the Service
            if (errorLog.isNotEmpty()) {
                throw RuntimeException("R8 Error:\n$errorLog", e)
            } else {
                throw e
            }
        }
    }
}
