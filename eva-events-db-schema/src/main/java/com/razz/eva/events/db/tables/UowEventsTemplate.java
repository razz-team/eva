/*
 * This file is generated by jOOQ.
 */
package com.razz.eva.events.db.tables;


import com.razz.eva.events.db.Events;
import com.razz.eva.events.db.Indexes;
import com.razz.eva.events.db.tables.records.UowEventsTemplateRecord;
import com.razz.jooq.converter.InstantConverter;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import javax.annotation.processing.Generated;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "https://www.jooq.org",
        "jOOQ version:3.17.4",
        "schema version:001"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class UowEventsTemplate extends TableImpl<UowEventsTemplateRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>events.uow_events_template</code>
     */
    public static final UowEventsTemplate UOW_EVENTS_TEMPLATE = new UowEventsTemplate();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<UowEventsTemplateRecord> getRecordType() {
        return UowEventsTemplateRecord.class;
    }

    /**
     * The column <code>events.uow_events_template.id</code>.
     */
    public final TableField<UowEventsTemplateRecord, UUID> ID = createField(DSL.name("id"), SQLDataType.UUID.nullable(false), this, "");

    /**
     * The column <code>events.uow_events_template.name</code>.
     */
    public final TableField<UowEventsTemplateRecord, String> NAME = createField(DSL.name("name"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>events.uow_events_template.idempotency_key</code>.
     */
    public final TableField<UowEventsTemplateRecord, String> IDEMPOTENCY_KEY = createField(DSL.name("idempotency_key"), SQLDataType.CLOB, this, "");

    /**
     * The column <code>events.uow_events_template.principal_name</code>.
     */
    public final TableField<UowEventsTemplateRecord, String> PRINCIPAL_NAME = createField(DSL.name("principal_name"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>events.uow_events_template.principal_id</code>.
     */
    public final TableField<UowEventsTemplateRecord, String> PRINCIPAL_ID = createField(DSL.name("principal_id"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>events.uow_events_template.occurred_at</code>.
     */
    public final TableField<UowEventsTemplateRecord, Instant> OCCURRED_AT = createField(DSL.name("occurred_at"), SQLDataType.TIMESTAMP(6).nullable(false), this, "", new InstantConverter());

    /**
     * The column <code>events.uow_events_template.inserted_at</code>.
     */
    public final TableField<UowEventsTemplateRecord, Instant> INSERTED_AT = createField(DSL.name("inserted_at"), SQLDataType.TIMESTAMP(6).nullable(false), this, "", new InstantConverter());

    /**
     * The column <code>events.uow_events_template.model_events</code>.
     */
    public final TableField<UowEventsTemplateRecord, UUID[]> MODEL_EVENTS = createField(DSL.name("model_events"), SQLDataType.UUID.getArrayDataType(), this, "");

    /**
     * The column <code>events.uow_events_template.params</code>.
     */
    public final TableField<UowEventsTemplateRecord, String> PARAMS = createField(DSL.name("params"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>events.uow_events_template.incremental_query_id</code>.
     */
    public final TableField<UowEventsTemplateRecord, Long> INCREMENTAL_QUERY_ID = createField(DSL.name("incremental_query_id"), SQLDataType.BIGINT.nullable(false), this, "");

    private UowEventsTemplate(Name alias, Table<UowEventsTemplateRecord> aliased) {
        this(alias, aliased, null);
    }

    private UowEventsTemplate(Name alias, Table<UowEventsTemplateRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>events.uow_events_template</code> table reference
     */
    public UowEventsTemplate(String alias) {
        this(DSL.name(alias), UOW_EVENTS_TEMPLATE);
    }

    /**
     * Create an aliased <code>events.uow_events_template</code> table reference
     */
    public UowEventsTemplate(Name alias) {
        this(alias, UOW_EVENTS_TEMPLATE);
    }

    /**
     * Create a <code>events.uow_events_template</code> table reference
     */
    public UowEventsTemplate() {
        this(DSL.name("uow_events_template"), null);
    }

    public <O extends Record> UowEventsTemplate(Table<O> child, ForeignKey<O, UowEventsTemplateRecord> key) {
        super(child, key, UOW_EVENTS_TEMPLATE);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Events.EVENTS;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.UOW_EVENTS_TEMPLATE_NAME_IDEMPOTENCY_KEY_IDX);
    }

    @Override
    public UowEventsTemplate as(String alias) {
        return new UowEventsTemplate(DSL.name(alias), this);
    }

    @Override
    public UowEventsTemplate as(Name alias) {
        return new UowEventsTemplate(alias, this);
    }

    @Override
    public UowEventsTemplate as(Table<?> alias) {
        return new UowEventsTemplate(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public UowEventsTemplate rename(String name) {
        return new UowEventsTemplate(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public UowEventsTemplate rename(Name name) {
        return new UowEventsTemplate(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public UowEventsTemplate rename(Table<?> name) {
        return new UowEventsTemplate(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row10 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row10<UUID, String, String, String, String, Instant, Instant, UUID[], String, Long> fieldsRow() {
        return (Row10) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function10<? super UUID, ? super String, ? super String, ? super String, ? super String, ? super Instant, ? super Instant, ? super UUID[], ? super String, ? super Long, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function10<? super UUID, ? super String, ? super String, ? super String, ? super String, ? super Instant, ? super Instant, ? super UUID[], ? super String, ? super Long, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
