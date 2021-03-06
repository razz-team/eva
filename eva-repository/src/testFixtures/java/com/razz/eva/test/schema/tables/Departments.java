/*
 * This file is generated by jOOQ.
 */
package com.razz.eva.test.schema.tables;


import com.razz.eva.test.schema.DefaultSchema;
import com.razz.eva.test.schema.Keys;
import com.razz.eva.test.schema.enums.DepartmentsState;
import com.razz.eva.test.schema.tables.records.DepartmentsRecord;
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
public class Departments extends TableImpl<DepartmentsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>departments</code>
     */
    public static final Departments DEPARTMENTS = new Departments();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<DepartmentsRecord> getRecordType() {
        return DepartmentsRecord.class;
    }

    /**
     * The column <code>departments.id</code>.
     */
    public final TableField<DepartmentsRecord, UUID> ID = createField(DSL.name("id"), SQLDataType.UUID.nullable(false), this, "");

    /**
     * The column <code>departments.name</code>.
     */
    public final TableField<DepartmentsRecord, String> NAME = createField(DSL.name("name"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>departments.boss</code>.
     */
    public final TableField<DepartmentsRecord, UUID> BOSS = createField(DSL.name("boss"), SQLDataType.UUID.nullable(false), this, "");

    /**
     * The column <code>departments.headcount</code>.
     */
    public final TableField<DepartmentsRecord, Integer> HEADCOUNT = createField(DSL.name("headcount"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>departments.ration</code>.
     */
    public final TableField<DepartmentsRecord, String> RATION = createField(DSL.name("ration"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>departments.state</code>.
     */
    public final TableField<DepartmentsRecord, DepartmentsState> STATE = createField(DSL.name("state"), SQLDataType.VARCHAR.nullable(false).asEnumDataType(DepartmentsState.class), this, "");

    /**
     * The column <code>departments.record_updated_at</code>.
     */
    public final TableField<DepartmentsRecord, Instant> RECORD_UPDATED_AT = createField(DSL.name("record_updated_at"), SQLDataType.TIMESTAMP(6).nullable(false), this, "", new InstantConverter());

    /**
     * The column <code>departments.record_created_at</code>.
     */
    public final TableField<DepartmentsRecord, Instant> RECORD_CREATED_AT = createField(DSL.name("record_created_at"), SQLDataType.TIMESTAMP(6).nullable(false), this, "", new InstantConverter());

    /**
     * The column <code>departments.version</code>.
     */
    public final TableField<DepartmentsRecord, Long> VERSION = createField(DSL.name("version"), SQLDataType.BIGINT.nullable(false), this, "");

    private Departments(Name alias, Table<DepartmentsRecord> aliased) {
        this(alias, aliased, null);
    }

    private Departments(Name alias, Table<DepartmentsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>departments</code> table reference
     */
    public Departments(String alias) {
        this(DSL.name(alias), DEPARTMENTS);
    }

    /**
     * Create an aliased <code>departments</code> table reference
     */
    public Departments(Name alias) {
        this(alias, DEPARTMENTS);
    }

    /**
     * Create a <code>departments</code> table reference
     */
    public Departments() {
        this(DSL.name("departments"), null);
    }

    public <O extends Record> Departments(Table<O> child, ForeignKey<O, DepartmentsRecord> key) {
        super(child, key, DEPARTMENTS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public UniqueKey<DepartmentsRecord> getPrimaryKey() {
        return Keys.DEPARTMENTS_PKEY;
    }

    @Override
    public List<UniqueKey<DepartmentsRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.DEPARTMENTS_NAME_KEY, Keys.DEPARTMENTS_BOSS_KEY);
    }

    @Override
    public TableField<DepartmentsRecord, Long> getRecordVersion() {
        return VERSION;
    }

    @Override
    public Departments as(String alias) {
        return new Departments(DSL.name(alias), this);
    }

    @Override
    public Departments as(Name alias) {
        return new Departments(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Departments rename(String name) {
        return new Departments(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Departments rename(Name name) {
        return new Departments(name, null);
    }

    // -------------------------------------------------------------------------
    // Row9 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row9<UUID, String, UUID, Integer, String, DepartmentsState, Instant, Instant, Long> fieldsRow() {
        return (Row9) super.fieldsRow();
    }
}
