package com.razz.eva.persistence

/**
 * Which of a transaction manager's two pools served a call.
 *
 * Derived by the manager from the constructor argument the provider was passed as, not declared by the
 * provider itself: a provider cannot know which slot a manager put it in, and a deployment may point both
 * pools at one host, so the address alone does not separate reads from writes.
 */
enum class PoolRole { PRIMARY, REPLICA }
