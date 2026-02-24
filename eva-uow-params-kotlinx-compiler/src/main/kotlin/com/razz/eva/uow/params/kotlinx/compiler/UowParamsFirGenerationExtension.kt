package com.razz.eva.uow.params.kotlinx.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class UowParamsFirGenerationExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    companion object {
        val PLUGIN_KEY = UowParamsGenerationKey
        private val UOW_PARAMS_CLASS_ID = ClassId(
            FqName("com.razz.eva.uow.params.kotlinx"),
            Name.identifier("UowParams"),
        )
        private val UOW_PARAMS_SHORT_NAME = Name.identifier("UowParams")
        private val SERIALIZABLE_CLASS_ID = ClassId(
            FqName("kotlinx.serialization"),
            Name.identifier("Serializable"),
        )
        private val SERIALIZABLE_SHORT_NAME = Name.identifier("Serializable")
        private val SERIALIZATION_STRATEGY_CLASS_ID = ClassId(
            FqName("kotlinx.serialization"),
            Name.identifier("SerializationStrategy"),
        )
        private val SERIALIZATION_NAME = Name.identifier("serialization")
    }

    object UowParamsGenerationKey : GeneratedDeclarationKey()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        // No annotation-based predicates; we check raw FIR directly to avoid resolution issues.
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        // Called during SUPERTYPES phase - neither resolvedSuperTypes nor
        // resolvedAnnotationClassIds are safe. Check raw FIR only.
        if (!maybeQualifying(classSymbol)) return setOf()
        return setOf(SERIALIZATION_NAME)
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        if (callableId.callableName != SERIALIZATION_NAME) return listOf()
        if (context == null) return listOf()
        val owner = context.owner
        // UowParams<PARAMS : UowParams<PARAMS>> - the type argument is always the implementing
        // class itself due to the recursive generic bound. Derive it from the owner's ClassId
        // instead of resolving supertypes (which is not safe during the SUPERTYPES phase).
        val ownerType = owner.classId.constructClassLikeType(
            typeArguments = ConeTypeProjection.EMPTY_ARRAY,
            isMarkedNullable = false,
        )
        val returnType = SERIALIZATION_STRATEGY_CLASS_ID.constructClassLikeType(
            typeArguments = arrayOf(ownerType),
            isMarkedNullable = false,
        )
        val function = createMemberFunction(
            owner = owner,
            key = PLUGIN_KEY,
            name = SERIALIZATION_NAME,
            returnType = returnType,
        )
        return listOf(function.symbol)
    }

    @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
    private fun maybeQualifying(classSymbol: FirClassSymbol<*>): Boolean {
        if (!hasSerializableAnnotationRef(classSymbol)) return false
        if (!hasUowParamsSupertypeRef(classSymbol)) return false
        val alreadyDeclaresSerialization = classSymbol.declarationSymbols.any { decl ->
            decl is FirNamedFunctionSymbol &&
                decl.name == SERIALIZATION_NAME &&
                decl.valueParameterSymbols.isEmpty()
        }
        return !alreadyDeclaresSerialization
    }

    @OptIn(SymbolInternals::class)
    private fun hasSerializableAnnotationRef(classSymbol: FirClassSymbol<*>): Boolean {
        // Check annotations from raw FIR without triggering resolution.
        return classSymbol.fir.annotations.any { annotation ->
            if (annotation !is FirAnnotationCall) return@any false
            matchesTypeRef(annotation.annotationTypeRef, SERIALIZABLE_CLASS_ID, SERIALIZABLE_SHORT_NAME)
        }
    }

    @OptIn(SymbolInternals::class)
    private fun hasUowParamsSupertypeRef(classSymbol: FirClassSymbol<*>): Boolean {
        // Check raw type refs without triggering supertype resolution.
        return classSymbol.fir.superTypeRefs.any { typeRef ->
            matchesTypeRef(typeRef, UOW_PARAMS_CLASS_ID, UOW_PARAMS_SHORT_NAME)
        }
    }

    private fun matchesTypeRef(typeRef: org.jetbrains.kotlin.fir.types.FirTypeRef, classId: ClassId, shortName: Name): Boolean {
        return when (typeRef) {
            is FirResolvedTypeRef -> (typeRef.coneType as? ConeClassLikeType)?.classId == classId
            is FirUserTypeRef -> typeRef.qualifier.lastOrNull()?.name == shortName
            else -> false
        }
    }
}
