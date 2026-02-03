package com.razz.eva.repository

import com.razz.eva.domain.Tag
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.test.schema.tables.Tag as TagTable
import com.razz.eva.test.schema.tables.records.TagRecord
import org.jooq.Condition
import org.jooq.DSLContext
import java.util.UUID

class TagRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
) : JooqKeyDeletableEntityRepository<Tag, Tag.Key, TagRecord>(queryExecutor, dslContext, TagTable.TAG) {

    override fun toRecord(entity: Tag): TagRecord =
        TagRecord(entity.subjectId, entity.name, entity.value)

    override fun fromRecord(record: TagRecord): Tag =
        Tag(record.subjectId, record.name, record.value)

    override fun entityCondition(entity: Tag): Condition =
        TagTable.TAG.SUBJECT_ID.eq(entity.subjectId)
            .and(TagTable.TAG.NAME.eq(entity.name))

    override fun keyCondition(key: Tag.Key): Condition =
        TagTable.TAG.SUBJECT_ID.eq(key.subjectId)
            .and(TagTable.TAG.NAME.eq(key.name))

    suspend fun listBySubject(subjectId: UUID): List<Tag> =
        listAllWhere(TagTable.TAG.SUBJECT_ID.eq(subjectId))

    suspend fun findBySubjectAndName(subjectId: UUID, name: String): Tag? =
        findOneWhere(TagTable.TAG.SUBJECT_ID.eq(subjectId).and(TagTable.TAG.NAME.eq(name)))

    suspend fun existsForSubject(subjectId: UUID): Boolean =
        existsWhere(TagTable.TAG.SUBJECT_ID.eq(subjectId))
}
