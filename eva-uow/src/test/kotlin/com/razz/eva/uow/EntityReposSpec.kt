package com.razz.eva.uow

import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Ration
import com.razz.eva.domain.RationAllocation
import com.razz.eva.domain.Tag
import com.razz.eva.domain.TxnView
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.EntityRepositoryNotFoundException
import com.razz.eva.repository.KeyDeletable
import com.razz.eva.repository.KeyUpdatable
import com.razz.eva.repository.RationAllocationRepository
import com.razz.eva.repository.TagRepository
import com.razz.eva.repository.TxnViewRepository
import com.razz.eva.repository.UpdatableEntityRepository
import com.razz.eva.repository.hasEntityRepo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.time.LocalDate
import java.util.UUID.randomUUID

class EntityReposSpec : BehaviorSpec({

    Given("EntityRepos is configured for Tag and RationAllocation") {
        val tagRepo = TagRepository(mockk(), mockk())
        val rationAllocationRepo = RationAllocationRepository(mockk(), mockk())

        val entityRepos = EntityRepos(
            Tag::class hasEntityRepo tagRepo,
            RationAllocation::class hasEntityRepo rationAllocationRepo,
        )

        And("Tag entity is defined") {
            val tag = Tag.environmentTag(randomUUID(), "production")

            When("Principal gets repository for entity") {
                val repo = entityRepos.repoFor(tag)

                Then("Principal should get a correct repository") {
                    repo shouldBe tagRepo
                }
            }

            When("Principal gets deletable repository for entity") {
                val repo = entityRepos.deletableRepoFor(tag)

                Then("Principal should get a correct deletable repository") {
                    repo shouldBe tagRepo
                }
            }
        }

        And("RationAllocation entity is defined") {
            val allocation = RationAllocation.allocation(
                employeeId = EmployeeId(randomUUID()),
                ration = Ration.BUBALEH,
                effectiveDate = LocalDate.now(),
                quantity = 5,
            )

            When("Principal gets repository for entity") {
                val repo = entityRepos.repoFor(allocation)

                Then("Principal should get a correct repository") {
                    repo shouldBe rationAllocationRepo
                }
            }

            When("Principal tries to get deletable repository for non-deletable entity") {
                val getDeletableRepo = {
                    entityRepos.deletableRepoFor(allocation as com.razz.eva.domain.DeletableEntity)
                }

                Then("Principal should get an exception") {
                    shouldThrow<ClassCastException> {
                        @Suppress("UNCHECKED_CAST")
                        getDeletableRepo()
                    }
                }
            }
        }
    }

    Given("EntityRepos is configured only for Tag") {
        val tagRepo = TagRepository(mockk(), mockk())

        val entityRepos = EntityRepos(
            Tag::class hasEntityRepo tagRepo,
        )

        And("RationAllocation entity is defined") {
            val allocation = RationAllocation.allocation(
                employeeId = EmployeeId(randomUUID()),
                ration = Ration.BUBALEH,
                effectiveDate = LocalDate.now(),
                quantity = 5,
            )

            When("Principal tries to get repository for unconfigured entity") {
                val getRepo = {
                    entityRepos.repoFor(allocation)
                }

                Then("Principal should get EntityRepositoryNotFoundException") {
                    val ex = shouldThrow<EntityRepositoryNotFoundException> {
                        getRepo()
                    }
                    ex.message shouldBe "Repository is not found for entity: $allocation"
                }
            }
        }
    }

    Given("Empty EntityRepos") {
        val entityRepos = EntityRepos()

        And("Tag entity is defined") {
            val tag = Tag.tag(randomUUID(), "test", "value")

            When("Principal tries to get repository") {
                val getRepo = {
                    entityRepos.repoFor(tag)
                }

                Then("Principal should get EntityRepositoryNotFoundException") {
                    shouldThrow<EntityRepositoryNotFoundException> {
                        getRepo()
                    }
                }
            }
        }
    }

    Given("EntityRepos is configured with KeyDeletable Tag repository") {
        val tagRepo = TagRepository(mockk(), mockk())

        val entityRepos = EntityRepos(
            Tag::class hasEntityRepo tagRepo,
        )

        When("Principal gets key deletable repository for Tag class") {
            val repo = entityRepos.keyDeletableRepoFor(Tag::class)

            Then("Principal should get a KeyDeletable repository") {
                repo.shouldBeInstanceOf<KeyDeletable<Tag, Tag.Key>>()
                repo shouldBe tagRepo
            }
        }
    }

    Given("EntityRepos without configured repository for entity class") {
        val tagRepo = TagRepository(mockk(), mockk())

        val entityRepos = EntityRepos(
            Tag::class hasEntityRepo tagRepo,
        )

        data class UnregisteredTag(val id: String) : com.razz.eva.domain.DeletableEntity()

        When("Principal tries to get key deletable repository for unconfigured class") {
            val getRepo = {
                entityRepos.keyDeletableRepoFor(UnregisteredTag::class)
            }

            Then("Principal should get EntityRepositoryNotFoundException") {
                shouldThrow<EntityRepositoryNotFoundException> {
                    getRepo()
                }
            }
        }
    }

    Given("EntityRepos is configured with UpdatableEntity repository") {
        val updatableRepo = TxnViewRepository(mockk(), mockk())

        val entityRepos = EntityRepos(
            TxnView::class hasEntityRepo updatableRepo,
        )

        And("TxnMaterialisedView entity is defined") {
            val txnView = TxnView(randomUUID(), 100, "USD")

            When("Principal gets updatable repository for entity") {
                val repo = entityRepos.updatableRepoFor(txnView)

                Then("Principal should get a correct updatable repository") {
                    repo shouldBe updatableRepo
                }
            }
        }
    }

    Given("EntityRepos is configured with KeyUpdatable repository") {
        val keyUpdatableRepo = TxnViewRepository(mockk(), mockk())

        val entityRepos = EntityRepos(
            TxnView::class hasEntityRepo keyUpdatableRepo,
        )

        When("Principal gets key updatable repository for TxnMaterialisedView class") {
            val repo = entityRepos.keyUpdatableRepoFor(TxnView::class)

            Then("Principal should get a KeyUpdatable repository") {
                repo.shouldBeInstanceOf<KeyUpdatable<TxnView, TxnView.Key>>()
                repo shouldBe keyUpdatableRepo
            }
        }
    }

    Given("EntityRepos with non-KeyUpdatable repo for updatable entity") {
        val updatableRepo = TxnViewRepository(mockk(), mockk())

        val entityRepos = EntityRepos(
            TxnView::class hasEntityRepo updatableRepo,
        )

        When("Principal tries to get key updatable repository") {
            val getRepo = {
                entityRepos.keyUpdatableRepoFor(TxnView::class)
            }

            Then("Principal should get IllegalStateException") {
                val ex = shouldThrow<IllegalStateException> {
                    getRepo()
                }
                ex.message shouldBe "Repository for ${TxnView::class} does not support key-based update. " +
                    "Implement KeyUpdatable interface to enable this feature."
            }
        }
    }
})
