/*
 * This file is generated by jOOQ.
 */
package com.razz.eva.examples.schema.db.tables.records;


import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.annotation.processing.Generated;

import com.razz.eva.examples.schema.db.tables.Wallet;
import com.razz.jooq.record.BaseEntityRecord;
import org.jooq.Field;
import org.jooq.Record7;
import org.jooq.Row7;
import org.jooq.impl.TableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
        value = {
                "https://www.jooq.org",
                "jOOQ version:3.16.6",
                "schema version:001"
        },
        comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class WalletRecord extends TableRecordImpl<WalletRecord> implements BaseEntityRecord<UUID>, Record7<UUID, String, Long, Instant, Instant, Instant, Long> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>wallet.id</code>.
     */
    public WalletRecord setId(UUID value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>wallet.id</code>.
     */
    public UUID getId() {
        return (UUID) get(0);
    }

    /**
     * Setter for <code>wallet.currency</code>.
     */
    public WalletRecord setCurrency(String value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>wallet.currency</code>.
     */
    public String getCurrency() {
        return (String) get(1);
    }

    /**
     * Setter for <code>wallet.amount</code>.
     */
    public WalletRecord setAmount(Long value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>wallet.amount</code>.
     */
    public Long getAmount() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>wallet.expire_at</code>.
     */
    public WalletRecord setExpireAt(Instant value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>wallet.expire_at</code>.
     */
    public Instant getExpireAt() {
        return (Instant) get(3);
    }

    /**
     * Setter for <code>wallet.record_updated_at</code>.
     */
    public WalletRecord setRecordUpdatedAt(Instant value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>wallet.record_updated_at</code>.
     */
    public Instant getRecordUpdatedAt() {
        return (Instant) get(4);
    }

    /**
     * Setter for <code>wallet.record_created_at</code>.
     */
    public WalletRecord setRecordCreatedAt(Instant value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>wallet.record_created_at</code>.
     */
    public Instant getRecordCreatedAt() {
        return (Instant) get(5);
    }

    /**
     * Setter for <code>wallet.version</code>.
     */
    public WalletRecord setVersion(Long value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>wallet.version</code>.
     */
    public Long getVersion() {
        return (Long) get(6);
    }

    // -------------------------------------------------------------------------
    // Record7 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row7<UUID, String, Long, Instant, Instant, Instant, Long> fieldsRow() {
        return (Row7) super.fieldsRow();
    }

    @Override
    public Row7<UUID, String, Long, Instant, Instant, Instant, Long> valuesRow() {
        return (Row7) super.valuesRow();
    }

    @Override
    public Field<UUID> field1() {
        return Wallet.WALLET.ID;
    }

    @Override
    public Field<String> field2() {
        return Wallet.WALLET.CURRENCY;
    }

    @Override
    public Field<Long> field3() {
        return Wallet.WALLET.AMOUNT;
    }

    @Override
    public Field<Instant> field4() {
        return Wallet.WALLET.EXPIRE_AT;
    }

    @Override
    public Field<Instant> field5() {
        return Wallet.WALLET.RECORD_UPDATED_AT;
    }

    @Override
    public Field<Instant> field6() {
        return Wallet.WALLET.RECORD_CREATED_AT;
    }

    @Override
    public Field<Long> field7() {
        return Wallet.WALLET.VERSION;
    }

    @Override
    public UUID component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getCurrency();
    }

    @Override
    public Long component3() {
        return getAmount();
    }

    @Override
    public Instant component4() {
        return getExpireAt();
    }

    @Override
    public Instant component5() {
        return getRecordUpdatedAt();
    }

    @Override
    public Instant component6() {
        return getRecordCreatedAt();
    }

    @Override
    public Long component7() {
        return getVersion();
    }

    @Override
    public UUID value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getCurrency();
    }

    @Override
    public Long value3() {
        return getAmount();
    }

    @Override
    public Instant value4() {
        return getExpireAt();
    }

    @Override
    public Instant value5() {
        return getRecordUpdatedAt();
    }

    @Override
    public Instant value6() {
        return getRecordCreatedAt();
    }

    @Override
    public Long value7() {
        return getVersion();
    }

    @Override
    public WalletRecord value1(UUID value) {
        setId(value);
        return this;
    }

    @Override
    public WalletRecord value2(String value) {
        setCurrency(value);
        return this;
    }

    @Override
    public WalletRecord value3(Long value) {
        setAmount(value);
        return this;
    }

    @Override
    public WalletRecord value4(Instant value) {
        setExpireAt(value);
        return this;
    }

    @Override
    public WalletRecord value5(Instant value) {
        setRecordUpdatedAt(value);
        return this;
    }

    @Override
    public WalletRecord value6(Instant value) {
        setRecordCreatedAt(value);
        return this;
    }

    @Override
    public WalletRecord value7(Long value) {
        setVersion(value);
        return this;
    }

    @Override
    public WalletRecord values(UUID value1, String value2, Long value3, Instant value4, Instant value5, Instant value6, Long value7) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached WalletRecord
     */
    public WalletRecord() {
        super(Wallet.WALLET);
    }

    /**
     * Create a detached, initialised WalletRecord
     */
    public WalletRecord(UUID id, String currency, Long amount, Instant expireAt, Instant recordUpdatedAt, Instant recordCreatedAt, Long version) {
        super(Wallet.WALLET);

        setId(id);
        setCurrency(currency);
        setAmount(amount);
        setExpireAt(expireAt);
        setRecordUpdatedAt(recordUpdatedAt);
        setRecordCreatedAt(recordCreatedAt);
        setVersion(version);
    }
}
