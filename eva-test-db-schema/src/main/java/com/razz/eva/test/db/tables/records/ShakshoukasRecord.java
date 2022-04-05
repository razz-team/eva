/*
 * This file is generated by jOOQ.
 */
package com.razz.eva.test.db.tables.records;


import com.razz.eva.test.db.enums.ShakshoukasState;
import com.razz.eva.test.db.tables.Shakshoukas;

import java.time.Instant;
import java.util.UUID;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record8;
import org.jooq.Row8;
import org.jooq.impl.UpdatableRecordImpl;


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
public class ShakshoukasRecord extends UpdatableRecordImpl<ShakshoukasRecord> implements com.razz.jooq.record.TypedStatefulEntityRecord<UUID, ShakshoukasState>, Record8<UUID, UUID, ShakshoukasState, String, Boolean, Instant, Instant, Long> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>shakshoukas.id</code>.
     */
    public ShakshoukasRecord setId(UUID value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>shakshoukas.id</code>.
     */
    public UUID getId() {
        return (UUID) get(0);
    }

    /**
     * Setter for <code>shakshoukas.employee_id</code>.
     */
    public ShakshoukasRecord setEmployeeId(UUID value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>shakshoukas.employee_id</code>.
     */
    public UUID getEmployeeId() {
        return (UUID) get(1);
    }

    /**
     * Setter for <code>shakshoukas.state</code>.
     */
    public ShakshoukasRecord setState(ShakshoukasState value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>shakshoukas.state</code>.
     */
    public ShakshoukasState getState() {
        return (ShakshoukasState) get(2);
    }

    /**
     * Setter for <code>shakshoukas.eggs_count</code>.
     */
    public ShakshoukasRecord setEggsCount(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>shakshoukas.eggs_count</code>.
     */
    public String getEggsCount() {
        return (String) get(3);
    }

    /**
     * Setter for <code>shakshoukas.with_pita</code>.
     */
    public ShakshoukasRecord setWithPita(Boolean value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>shakshoukas.with_pita</code>.
     */
    public Boolean getWithPita() {
        return (Boolean) get(4);
    }

    /**
     * Setter for <code>shakshoukas.record_updated_at</code>.
     */
    public ShakshoukasRecord setRecordUpdatedAt(Instant value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>shakshoukas.record_updated_at</code>.
     */
    public Instant getRecordUpdatedAt() {
        return (Instant) get(5);
    }

    /**
     * Setter for <code>shakshoukas.record_created_at</code>.
     */
    public ShakshoukasRecord setRecordCreatedAt(Instant value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>shakshoukas.record_created_at</code>.
     */
    public Instant getRecordCreatedAt() {
        return (Instant) get(6);
    }

    /**
     * Setter for <code>shakshoukas.version</code>.
     */
    public ShakshoukasRecord setVersion(Long value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>shakshoukas.version</code>.
     */
    public Long getVersion() {
        return (Long) get(7);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<UUID> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record8 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row8<UUID, UUID, ShakshoukasState, String, Boolean, Instant, Instant, Long> fieldsRow() {
        return (Row8) super.fieldsRow();
    }

    @Override
    public Row8<UUID, UUID, ShakshoukasState, String, Boolean, Instant, Instant, Long> valuesRow() {
        return (Row8) super.valuesRow();
    }

    @Override
    public Field<UUID> field1() {
        return Shakshoukas.SHAKSHOUKAS.ID;
    }

    @Override
    public Field<UUID> field2() {
        return Shakshoukas.SHAKSHOUKAS.EMPLOYEE_ID;
    }

    @Override
    public Field<ShakshoukasState> field3() {
        return Shakshoukas.SHAKSHOUKAS.STATE;
    }

    @Override
    public Field<String> field4() {
        return Shakshoukas.SHAKSHOUKAS.EGGS_COUNT;
    }

    @Override
    public Field<Boolean> field5() {
        return Shakshoukas.SHAKSHOUKAS.WITH_PITA;
    }

    @Override
    public Field<Instant> field6() {
        return Shakshoukas.SHAKSHOUKAS.RECORD_UPDATED_AT;
    }

    @Override
    public Field<Instant> field7() {
        return Shakshoukas.SHAKSHOUKAS.RECORD_CREATED_AT;
    }

    @Override
    public Field<Long> field8() {
        return Shakshoukas.SHAKSHOUKAS.VERSION;
    }

    @Override
    public UUID component1() {
        return getId();
    }

    @Override
    public UUID component2() {
        return getEmployeeId();
    }

    @Override
    public ShakshoukasState component3() {
        return getState();
    }

    @Override
    public String component4() {
        return getEggsCount();
    }

    @Override
    public Boolean component5() {
        return getWithPita();
    }

    @Override
    public Instant component6() {
        return getRecordUpdatedAt();
    }

    @Override
    public Instant component7() {
        return getRecordCreatedAt();
    }

    @Override
    public Long component8() {
        return getVersion();
    }

    @Override
    public UUID value1() {
        return getId();
    }

    @Override
    public UUID value2() {
        return getEmployeeId();
    }

    @Override
    public ShakshoukasState value3() {
        return getState();
    }

    @Override
    public String value4() {
        return getEggsCount();
    }

    @Override
    public Boolean value5() {
        return getWithPita();
    }

    @Override
    public Instant value6() {
        return getRecordUpdatedAt();
    }

    @Override
    public Instant value7() {
        return getRecordCreatedAt();
    }

    @Override
    public Long value8() {
        return getVersion();
    }

    @Override
    public ShakshoukasRecord value1(UUID value) {
        setId(value);
        return this;
    }

    @Override
    public ShakshoukasRecord value2(UUID value) {
        setEmployeeId(value);
        return this;
    }

    @Override
    public ShakshoukasRecord value3(ShakshoukasState value) {
        setState(value);
        return this;
    }

    @Override
    public ShakshoukasRecord value4(String value) {
        setEggsCount(value);
        return this;
    }

    @Override
    public ShakshoukasRecord value5(Boolean value) {
        setWithPita(value);
        return this;
    }

    @Override
    public ShakshoukasRecord value6(Instant value) {
        setRecordUpdatedAt(value);
        return this;
    }

    @Override
    public ShakshoukasRecord value7(Instant value) {
        setRecordCreatedAt(value);
        return this;
    }

    @Override
    public ShakshoukasRecord value8(Long value) {
        setVersion(value);
        return this;
    }

    @Override
    public ShakshoukasRecord values(UUID value1, UUID value2, ShakshoukasState value3, String value4, Boolean value5, Instant value6, Instant value7, Long value8) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ShakshoukasRecord
     */
    public ShakshoukasRecord() {
        super(Shakshoukas.SHAKSHOUKAS);
    }

    /**
     * Create a detached, initialised ShakshoukasRecord
     */
    public ShakshoukasRecord(UUID id, UUID employeeId, ShakshoukasState state, String eggsCount, Boolean withPita, Instant recordUpdatedAt, Instant recordCreatedAt, Long version) {
        super(Shakshoukas.SHAKSHOUKAS);

        setId(id);
        setEmployeeId(employeeId);
        setState(state);
        setEggsCount(eggsCount);
        setWithPita(withPita);
        setRecordUpdatedAt(recordUpdatedAt);
        setRecordCreatedAt(recordCreatedAt);
        setVersion(version);
    }
}
