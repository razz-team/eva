package com.razz.eva.uow.params.kotlinx.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.Name

class UowParamsIrGenerationExtension : IrGenerationExtension {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                declaration.acceptChildrenVoid(this)
                processClass(declaration, pluginContext)
            }
        })
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun processClass(irClass: IrClass, pluginContext: IrPluginContext) {
        val syntheticFunction = irClass.functions.find { func ->
            func.name == Name.identifier("serialization") &&
                func.parameters.none { it.kind == IrParameterKind.Regular } &&
                func.origin.let { origin ->
                    origin is IrDeclarationOrigin.GeneratedByPlugin &&
                        origin.pluginKey == UowParamsFirGenerationExtension.PLUGIN_KEY
                }
        } ?: return
        generateSerializationBody(irClass, syntheticFunction, pluginContext)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun generateSerializationBody(
        irClass: IrClass,
        function: IrSimpleFunction,
        pluginContext: IrPluginContext,
    ) {
        val companion = if (irClass.isObject) {
            irClass
        } else {
            irClass.companionObject() ?: return
        }
        val serializerFunc = companion.functions.find { func ->
            func.name == Name.identifier("serializer") &&
                func.parameters.none { it.kind == IrParameterKind.Regular } &&
                func.visibility == DescriptorVisibilities.PUBLIC
        } ?: return
        val startOffset = function.startOffset
        val endOffset = function.endOffset
        val call = IrCallImpl(
            startOffset, endOffset,
            serializerFunc.returnType,
            serializerFunc.symbol,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = IrGetObjectValueImpl(
                startOffset, endOffset,
                companion.defaultType,
                companion.symbol,
            )
        }
        function.body = pluginContext.irFactory.createExpressionBody(startOffset, endOffset, call)
    }
}
