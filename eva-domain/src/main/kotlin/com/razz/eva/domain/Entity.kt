package com.razz.eva.domain

/**
 * Base class for Entity types.
 *
 * Unlike [Model], entities have no explicit identity field - their identity
 * is determined by content equality (typically implemented as data classes).
 * Entities also have no lifecycle (state/events).
 */
abstract class Entity

/**
 * Base class for entities that can be created (inserted) via Unit of Work.
 */
abstract class CreatableEntity : Entity()

/**
 * Base class for entities that can be deleted via Unit of Work.
 * Deletable entities must also be creatable (you need to create an entity before you can delete it).
 */
abstract class DeletableEntity : CreatableEntity()
