package com.razz.eva.uow

import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Ration
import com.razz.eva.domain.RationAllocation
import com.razz.eva.domain.Tag
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.EntityRepositoryNotFoundException
import com.razz.eva.repository.RationAllocationRepository
import com.razz.eva.repository.TagRepository
import com.razz.eva.repository.hasEntityRepo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
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
})
