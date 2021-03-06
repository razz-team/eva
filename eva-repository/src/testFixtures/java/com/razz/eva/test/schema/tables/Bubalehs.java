/*
 * This file is generated by jOOQ.
 */
package com.razz.eva.test.schema.tables;


import com.razz.eva.test.schema.DefaultSchema;
import com.razz.eva.test.schema.Indexes;
import com.razz.eva.test.schema.Keys;
import com.razz.eva.test.schema.enums.BubalehsState;
import com.razz.eva.test.schema.tables.records.BubalehsRecord;
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


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "https://www.jooq.org",
        "jOOQ version:3.16.5",
        "schema version:004"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Bubalehs extends TableImpl<BubalehsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>bubalehs</code>
     */
    public static final Bubalehs BUBALEHS = new Bubalehs();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<BubalehsRecord> getRecordType() {
        return BubalehsRecord.class;
    }

    /**
     * The column <code>bubalehs.id</code>.
     */
    public final TableField<BubalehsRecord, UUID> ID = createField(DSL.name("id"), SQLDataType.UUID.nullable(false), this, "");

    /**
     * The column <code>bubalehs.employee_id</code>.
     */
    public final TableField<BubalehsRecord, UUID> EMPLOYEE_ID = createField(DSL.name("employee_id"), SQLDataType.UUID.nullable(false), this, "");

    /**
     * The column <code>bubalehs.state</code>.
     */
    public final TableField<BubalehsRecord, BubalehsState> STATE = createField(DSL.name("state"), SQLDataType.VARCHAR.nullable(false).asEnumDataType(BubalehsState.class), this, "");

    /**
     * The column <code>bubalehs.taste</code>.
     */
    public final TableField<BubalehsRecord, String> TASTE = createField(DSL.name("taste"), SQLDataType.VARCHAR(30).nullable(false), this, "");

    /**
     * The column <code>bubalehs.produced_on</code>.
     */
    public final TableField<BubalehsRecord, Instant> PRODUCED_ON = createField(DSL.name("produced_on"), SQLDataType.TIMESTAMP(6).nullable(false), this, "", new InstantConverter());

    /**
     * The column <code>bubalehs.volume</code>.
     */
    public final TableField<BubalehsRecord, String> VOLUME = createField(DSL.name("volume"), SQLDataType.VARCHAR(30).nullable(false), this, "");

    /**
     * The column <code>bubalehs.record_updated_at</code>.
     */
    public final TableField<BubalehsRecord, Instant> RECORD_UPDATED_AT = createField(DSL.name("record_updated_at"), SQLDataType.TIMESTAMP(6).nullable(false), this, "", new InstantConverter());

    /**
     * The column <code>bubalehs.record_created_at</code>.
     */
    public final TableField<BubalehsRecord, Instant> RECORD_CREATED_AT = createField(DSL.name("record_created_at"), SQLDataType.TIMESTAMP(6).nullable(false), this, "", new InstantConverter());

    /**
     * The column <code>bubalehs.version</code>.
     */
    public final TableField<BubalehsRecord, Long> VERSION = createField(DSL.name("version"), SQLDataType.BIGINT.nullable(false), this, "");

    private Bubalehs(Name alias, Table<BubalehsRecord> aliased) {
        this(alias, aliased, null);
    }

    private Bubalehs(Name alias, Table<BubalehsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>bubalehs</code> table reference
     */
    public Bubalehs(String alias) {
        this(DSL.name(alias), BUBALEHS);
    }

    /**
     * Create an aliased <code>bubalehs</code> table reference
     */
    public Bubalehs(Name alias) {
        this(alias, BUBALEHS);
    }

    /**
     * Create a <code>bubalehs</code> table reference
     */
    public Bubalehs() {
        this(DSL.name("bubalehs"), null);
    }

    public <O extends Record> Bubalehs(Table<O> child, ForeignKey<O, BubalehsRecord> key) {
        super(child, key, BUBALEHS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.BUBALEHS_EMPLOYEE_ID_NON_CONSUMED);
    }

    @Override
    public UniqueKey<BubalehsRecord> getPrimaryKey() {
        return Keys.BUBALEHS_PKEY;
    }

    @Override
    public TableField<BubalehsRecord, Long> getRecordVersion() {
        return VERSION;
    }

    @Override
    public Bubalehs as(String alias) {
        return new Bubalehs(DSL.name(alias), this);
    }

    @Override
    public Bubalehs as(Name alias) {
        return new Bubalehs(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Bubalehs rename(String name) {
        return new Bubalehs(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Bubalehs rename(Name name) {
        return new Bubalehs(name, null);
    }

    // -------------------------------------------------------------------------
    // Row9 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row9<UUID, UUID, BubalehsState, String, Instant, String, Instant, Instant, Long> fieldsRow() {
        return (Row9) super.fieldsRow();
    }
}
