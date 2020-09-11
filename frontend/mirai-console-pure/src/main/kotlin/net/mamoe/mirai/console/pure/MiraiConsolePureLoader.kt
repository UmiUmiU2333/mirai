/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress(
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE",
    "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
    "INVISIBLE_SETTER",
    "INVISIBLE_GETTER",
    "INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER",
)
@file:OptIn(ConsoleInternalAPI::class, ConsolePureExperimentalAPI::class)

package net.mamoe.mirai.console.pure

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.MiraiConsoleImplementation
import net.mamoe.mirai.console.MiraiConsoleImplementation.Companion.start
import net.mamoe.mirai.console.data.AutoSavePluginDataHolder
import net.mamoe.mirai.console.pure.noconsole.SystemOutputPrintStream
import net.mamoe.mirai.console.util.ConsoleExperimentalAPI
import net.mamoe.mirai.console.util.ConsoleInternalAPI
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScope
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.utils.DefaultLogger
import net.mamoe.mirai.utils.minutesToMillis
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * mirai-console-pure CLI 入口点
 */
object MiraiConsolePureLoader {
    @JvmStatic
    fun main(args: Array<String>) {
        parse(args, exitProcess = true)
        startAsDaemon()
        try {
            runBlocking {
                MiraiConsole.job.join()
            }
        } catch (e: CancellationException) {
            // ignored
        }
    }

    @ConsolePureExperimentalAPI
    fun printlnHelpMessage() {
        val help = listOf(
            "" to "Mirai-Console[Pure FrontEnd] v" + kotlin.runCatching {
                net.mamoe.mirai.console.internal.MiraiConsoleBuildConstants.version
            }.getOrElse { "<unknown>" },
            "" to "",
            "--help" to "显示此帮助",
            "" to "",
            "--no-console" to "使用无终端操作环境",
            "--dont-setup-terminal-ansi" to
                    "[NoConsole] [Windows Only] 不进行ansi console初始化工作",
            "--drop-ansi" to "[NoConsole] 禁用 ansi",
            "--safe-reading" to
                    "[NoConsole] 如果启动此选项, console在获取用户输入的时候会获得一个安全的空字符串\n" +
                    "            如果不启动, 将会直接 error",
        )
        val prefixPlaceholder = String(CharArray(
            help.maxOfOrNull { it.first.length }!! + 3
        ) { ' ' })

        fun printOption(optionName: String, value: String) {
            if (optionName == "") {
                println(value)
                return
            }
            print(optionName)
            print(prefixPlaceholder.substring(optionName.length))
            val lines = value.split('\n').iterator()
            if (lines.hasNext()) println(lines.next())
            lines.forEach { line ->
                print(prefixPlaceholder)
                println(line)
            }
        }
        help.forEach { (optionName, value) ->
            printOption(optionName, value)
        }
    }

    @ConsolePureExperimentalAPI
    fun parse(args: Array<String>, exitProcess: Boolean = false) {
        val iterator = args.iterator()
        while (iterator.hasNext()) {
            when (val option = iterator.next()) {
                "--help" -> {
                    printlnHelpMessage()
                    if (exitProcess) exitProcess(0)
                    return
                }
                "--no-console" -> {
                    ConsolePureSettings.noConsole = true
                }
                "--dont-setup-terminal-ansi" -> {
                    ConsolePureSettings.setupAnsi = false
                }
                "--drop-ansi" -> {
                    ConsolePureSettings.dropAnsi = true
                    ConsolePureSettings.setupAnsi = false
                }
                "--safe-reading" -> {
                    ConsolePureSettings.noConsoleSafeReading = true
                }
                else -> {
                    println("Unknown option `$option`")
                    printlnHelpMessage()
                    if (exitProcess)
                        @Suppress("UNREACHABLE_CODE")
                        exitProcess(1)
                    return
                }
            }
        }
        if (ConsolePureSettings.noConsole)
            SystemOutputPrintStream // Setup Output Channel
    }

    @Suppress("MemberVisibilityCanBePrivate")
    @ConsoleExperimentalAPI
    fun startAsDaemon(instance: MiraiConsoleImplementationPure = MiraiConsoleImplementationPure()) {
        instance.start()
        overrideSTD()
        startupConsoleThread()
    }
}

internal object ConsoleDataHolder : AutoSavePluginDataHolder,
    CoroutineScope by MiraiConsole.childScope("ConsoleDataHolder") {
    @ConsoleExperimentalAPI
    override val autoSaveIntervalMillis: LongRange = 1.minutesToMillis..10.minutesToMillis

    @ConsoleExperimentalAPI
    override val dataHolderName: String
        get() = "Pure"
}

internal fun overrideSTD() {
    System.setOut(
        PrintStream(
            BufferedOutputStream(
                logger = DefaultLogger("stdout").run { ({ line: String? -> info(line) }) }
            )
        )
    )
    System.setErr(
        PrintStream(
            BufferedOutputStream(
                logger = DefaultLogger("stderr").run { ({ line: String? -> warning(line) }) }
            )
        )
    )
}


internal object ConsoleCommandSenderImplPure : MiraiConsoleImplementation.ConsoleCommandSenderImpl {
    override suspend fun sendMessage(message: String) {
        kotlin.runCatching {
            lineReader.printAbove(message)
        }.onFailure {
            consoleLogger.error("Exception while ConsoleCommandSenderImplPure.sendMessage", it)
        }
    }

    override suspend fun sendMessage(message: Message) {
        return sendMessage(message.toString())
    }
}