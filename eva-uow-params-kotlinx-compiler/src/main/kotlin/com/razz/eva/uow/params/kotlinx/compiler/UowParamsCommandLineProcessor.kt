package com.razz.eva.uow.params.kotlinx.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class UowParamsCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "com.razz.eva.uow-params-kotlinx"
    override val pluginOptions: Collection<AbstractCliOption> = listOf()
}
