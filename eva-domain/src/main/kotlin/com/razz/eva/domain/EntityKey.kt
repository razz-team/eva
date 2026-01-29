package com.razz.eva.domain

/**
 * Marker interface for types that can uniquely identify a [DeletableEntity].
 *
 * Keys represent the minimal set of fields needed to identify an entity
 * for deletion purposes, avoiding the need to fetch the full entity.
 *
 * Keys are typically implemented as nested data classes within the entity:
 * ```
 * data class Tag(
 *     val subjectId: UUID,
 *     val name: String,
 *     val value: String,
 * ) : DeletableEntity() {
 *
 *     data class Key(
 *         val subjectId: UUID,
 *         val name: String,
 *     ) : EntityKey<Tag>
 * }
 * ```
 *
 * For entities with multiple identification strategies, use a sealed hierarchy:
 * ```
 * data class Document(...) : DeletableEntity() {
 *
 *     sealed interface Key : EntityKey<Document>
 *     data class ById(val id: UUID) : Key
 *     data class BySlug(val ownerId: UUID, val slug: String) : Key
 * }
 * ```
 *
 * @param E the entity type this key identifies
 */
interface EntityKey<E : DeletableEntity>
