package org.wisdom.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tdf.rlp.RLP;

import javax.persistence.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = TransactionEntity.TABLE_TRANSACTION, indexes = {
        @Index(name = "transaction_type_index", columnList = TransactionEntity.COLUMN_TX_TYPE),
        @Index(name = "transaction_from_index", columnList = TransactionEntity.COLUMN_TX_FROM),
        @Index(name = "transaction_to_index", columnList = TransactionEntity.COLUMN_TX_TO),
        @Index(name = "transaction_payload_index", columnList = TransactionEntity.COLUMN_TX_PAYLOAD)
})
public class TransactionEntity {

    static final String COLUMN_TX_HASH = "tx_hash";
    static final String COLUMN_TX_VERSION = "version";
    static final String COLUMN_TX_TYPE = "type";
    static final String COLUMN_TX_NONCE = "nonce";
    static final String COLUMN_TX_FROM = "[from]";
    static final String COLUMN_TX_GAS_PRICE = "gas_price";
    static final String COLUMN_TX_AMOUNT = "amount";
    static final String COLUMN_TX_PAYLOAD = "payload";
    static final String COLUMN_TX_TO = "[to]";
    static final String COLUMN_TX_SIGNATURE = "signature";
    static final String TABLE_TRANSACTION= "transaction";
    public static final String COLUMN_WASM_PAYLOAD = "wasm_payload";


    @Column(name = COLUMN_TX_HASH, nullable = false)
    @Id
    public byte[] txHash;

    @Column(name = COLUMN_TX_VERSION, nullable = false)
    public int version;

    @Column(name = COLUMN_TX_TYPE, nullable = false)
    public int type;

    @Column(name = COLUMN_TX_NONCE, nullable = false)
    public long nonce;

    @Column(name = COLUMN_TX_FROM, nullable = false)
    public byte[] from;

    @Column(name = COLUMN_TX_GAS_PRICE, nullable = false)
    public long gasPrice;

    @Column(name = COLUMN_TX_AMOUNT, nullable = false)
    public long amount;

    @Column(name = COLUMN_TX_PAYLOAD, length = Integer.MAX_VALUE)
    public byte[] payload;

    @Column(name = COLUMN_TX_TO, nullable = false)
    public byte[] to;

    @Column(name = COLUMN_TX_SIGNATURE, nullable = false)
    public byte[] signature;

    @Column(name = COLUMN_WASM_PAYLOAD)
    public byte[] wasmPayload;
}
