package com.razz.eva.uow.params.kotlinx.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class UowParamsFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::UowParamsFirGenerationExtension
    }
}
