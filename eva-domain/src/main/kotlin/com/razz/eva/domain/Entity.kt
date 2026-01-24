package com.razz.eva.domain

/**
 * Marker interface for Entity types.
 *
 * Unlike [Model], entities have no explicit identity field - their identity
 * is determined by content equality (typically implemented as data classes).
 * Entities also have no lifecycle (state/events).
 */
interface Entity

/**
 * Marker interface for entities that can be created (inserted) via Unit of Work.
 */
interface CreatableEntity : Entity

/**
 * Marker interface for entities that can be deleted via Unit of Work.
 * Deletable entities must also be creatable (you need to create an entity before you can delete it).
 */
interface DeletableEntity : CreatableEntity
