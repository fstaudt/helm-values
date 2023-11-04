package io.github.fstaudt.helm.idea.service

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes.STDERR
import com.intellij.execution.process.ProcessOutputTypes.STDOUT
import com.intellij.openapi.util.Key
import java.time.Duration
import java.time.temporal.ChronoUnit

private val TIMEOUT = Duration.of(60, ChronoUnit.SECONDS).toMillis()

internal fun GeneralCommandLine.execute(callback: GeneralCommandLineResult.() -> Unit) {
    val processHandler = OSProcessHandler(withRedirectErrorStream(true))
    val processOutputLogger = ProcessOutputLogger().also { processHandler.addProcessListener(it) }
    processHandler.startNotify()
    val exited = processHandler.waitFor(TIMEOUT)
    callback(GeneralCommandLineResult(processHandler.exitCode, !exited, processOutputLogger.output()))
}

internal data class GeneralCommandLineResult(val exitCode: Int?, val timeout: Boolean, val output: String)

private class ProcessOutputLogger : ProcessAdapter() {
    private val output = StringBuffer()

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (outputType == STDERR || outputType == STDOUT) {
            val text = event.text
            output.append(text)
        }
    }

    fun output() = output.toString()
}
