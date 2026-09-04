package org.eclipse.kuksa.mockprovider

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.eclipse.kuksa.mockprovider.engine.RuleLoader
import org.eclipse.kuksa.mockprovider.provider.MockProvider
import org.eclipse.kuksa.mockprovider.provider.MockProviderConfig
import org.eclipse.kuksa.mockprovider.web.DashboardState
import org.eclipse.kuksa.mockprovider.web.MockProviderWebServer

// Default port and poll interval are CLI-program constants; wrapping them in named constants here adds no clarity.
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
fun main(args: Array<String>) {
    var host = "localhost"
    var port = 55556
    var rulesPath = "mock-environment/behavior-rules.yaml"
    var vssDir = "vss"
    var vssFile: String? = null
    var verbose = false
    var webPort: Int? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--host" -> if (i + 1 < args.size) host = args[++i]
            "--port" -> if (i + 1 < args.size) port = args[++i].toIntOrNull() ?: 55556
            "--rules" -> if (i + 1 < args.size) rulesPath = args[++i]
            "--vss-dir" -> if (i + 1 < args.size) vssDir = args[++i]
            "--vss-file" -> if (i + 1 < args.size) vssFile = args[++i]
            "--verbose", "-v" -> verbose = true
            "--generic" -> { /* Enabled by default */ }
            "--web-port" -> if (i + 1 < args.size) webPort = args[++i].toIntOrNull()
        }
        i++
    }

    println("==================================================")
    println("KUKSA Generic Actuator Mock Provider")
    println("Target Databroker : $host:$port")
    println("VSS Definitions   : ${vssFile ?: vssDir}")
    println("Rules Config      : $rulesPath")
    println("Mode              : Generic Passthrough + Rule Engine")
    if (webPort != null) {
        println("Web Dashboard     : enabled on port $webPort")
    } else {
        println("Web Dashboard     : disabled (use --web-port <N> to enable)")
    }
    println("==================================================")

    val config = MockProviderConfig(
        host = host,
        port = port,
        rulesPath = rulesPath,
        vssDirectoryPath = vssDir,
        vssFilePath = vssFile,
        verbose = verbose,
    )

    val dashboardState: DashboardState? = webPort?.let {
        val rules = runCatching { RuleLoader.load(rulesPath) }.getOrDefault(emptyList())
        DashboardState(host = host, port = port).also { state ->
            state.customRulesCount = rules.filter { r -> !r.actuator.contains("*") }.size
        }
    }

    val webServer: MockProviderWebServer? = dashboardState?.let {
        MockProviderWebServer(state = it, webPort = webPort, onStop = { Runtime.getRuntime().halt(0) })
    }

    val provider = MockProvider(config = config, dashboardState = dashboardState)

    Runtime.getRuntime().addShutdownHook(
        Thread {
            println("Stopping MockProvider...")
            provider.stop()
            webServer?.stop()
        },
    )

    webServer?.start()

    runBlocking {
        provider.start()
        println("MockProvider is running. Listening for actuation requests (v1 & v2)...")
        while (true) {
            delay(1000)
        }
    }
}
