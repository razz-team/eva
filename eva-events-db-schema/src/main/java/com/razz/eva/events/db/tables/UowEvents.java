/*
 * This file is generated by jOOQ.
 */
package com.razz.eva.events.db.tables;


import com.razz.eva.events.db.Events;
import com.razz.eva.events.db.Indexes;
import com.razz.eva.events.db.tables.records.UowEventsRecord;
import com.razz.jooq.converter.InstantConverter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.annotation.processing.Generated;

import org.jooq.Check;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row8;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "https://www.jooq.org",
        "jOOQ version:3.16.5",
        "schema version:001"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class UowEvents extends TableImpl<UowEventsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>events.uow_events</code>
     */
    public static final UowEvents UOW_EVENTS = new UowEvents();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<UowEventsRecord> getRecordType() {
        return UowEventsRecord.class;
    }

    /**
     * The column <code>events.uow_events.id</code>.
     */
    public final TableField<UowEventsRecord, UUID> ID = createField(DSL.name("id"), SQLDataType.UUID.nullable(false), this, "");

    /**
     * The column <code>events.uow_events.name</code>.
     */
    public final TableField<UowEventsRecord, String> NAME = createField(DSL.name("name"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>events.uow_events.idempotency_key</code>.
     */
    public final TableField<UowEventsRecord, String> IDEMPOTENCY_KEY = createField(DSL.name("idempotency_key"), SQLDataType.CLOB, this, "");

    /**
     * The column <code>events.uow_events.principal_name</code>.
     */
    public final TableField<UowEventsRecord, String> PRINCIPAL_NAME = createField(DSL.name("principal_name"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>events.uow_events.principal_id</code>.
     */
    public final TableField<UowEventsRecord, String> PRINCIPAL_ID = createField(DSL.name("principal_id"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>events.uow_events.occurred_at</code>.
     */
    public final TableField<UowEventsRecord, Instant> OCCURRED_AT = createField(DSL.name("occurred_at"), SQLDataType.TIMESTAMP(6).nullable(false), this, "", new InstantConverter());

    /**
     * The column <code>events.uow_events.model_events</code>.
     */
    public final TableField<UowEventsRecord, UUID[]> MODEL_EVENTS = createField(DSL.name("model_events"), SQLDataType.UUID.getArrayDataType(), this, "");

    /**
     * The column <code>events.uow_events.params</code>.
     */
    public final TableField<UowEventsRecord, String> PARAMS = createField(DSL.name("params"), SQLDataType.CLOB.nullable(false).defaultValue(DSL.field("'{}'::text", SQLDataType.CLOB)), this, "");

    private UowEvents(Name alias, Table<UowEventsRecord> aliased) {
        this(alias, aliased, null);
    }

    private UowEvents(Name alias, Table<UowEventsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>events.uow_events</code> table reference
     */
    public UowEvents(String alias) {
        this(DSL.name(alias), UOW_EVENTS);
    }

    /**
     * Create an aliased <code>events.uow_events</code> table reference
     */
    public UowEvents(Name alias) {
        this(alias, UOW_EVENTS);
    }

    /**
     * Create a <code>events.uow_events</code> table reference
     */
    public UowEvents() {
        this(DSL.name("uow_events"), null);
    }

    public <O extends Record> UowEvents(Table<O> child, ForeignKey<O, UowEventsRecord> key) {
        super(child, key, UOW_EVENTS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Events.EVENTS;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.UOW_EVENTS_OCCURRED_AT_IDX);
    }

    @Override
    public List<Check<UowEventsRecord>> getChecks() {
        return Arrays.asList(
            Internal.createCheck(this, DSL.name("uow_events_principal_id_length"), "(((char_length(principal_id) > 0) AND (char_length(principal_id) <= 100)))", true),
            Internal.createCheck(this, DSL.name("uow_events_principal_name_length"), "(((char_length(principal_name) > 0) AND (char_length(principal_name) <= 100)))", true)
        );
    }

    @Override
    public UowEvents as(String alias) {
        return new UowEvents(DSL.name(alias), this);
    }

    @Override
    public UowEvents as(Name alias) {
        return new UowEvents(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public UowEvents rename(String name) {
        return new UowEvents(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public UowEvents rename(Name name) {
        return new UowEvents(name, null);
    }

    // -------------------------------------------------------------------------
    // Row8 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row8<UUID, String, String, String, String, Instant, UUID[], String> fieldsRow() {
        return (Row8) super.fieldsRow();
    }
}
