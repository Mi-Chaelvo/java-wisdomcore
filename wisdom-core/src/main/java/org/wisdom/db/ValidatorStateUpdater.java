package org.wisdom.db;

import org.tdf.common.util.ByteArraySet;
import org.tdf.common.util.FastByteComparisons;
import org.wisdom.core.account.Transaction;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ValidatorStateUpdater extends AbstractStateUpdater<Long> {
    @Override
    public Map<byte[], Long> getGenesisStates() {
        return Collections.emptyMap();
    }

    @Override
    public Set<byte[]> getRelatedKeys(Transaction transaction, Map<byte[], Long> store) {
        if(transaction.type != Transaction.Type.COINBASE.ordinal()) return Collections.emptySet();
        ByteArraySet set = new ByteArraySet();
        set.add(transaction.to);
        return set;
    }

    @Override
    public Long update(Map<byte[], Long> related, byte[] id, Long state, TransactionInfo info) {
        Transaction transaction = info.getTransaction();
        if(transaction.type != Transaction.Type.COINBASE.ordinal()) return state;
        if(FastByteComparisons.equal(transaction.to, id)) return state + 1;
        return state;
    }

    @Override
    public Long createEmpty(byte[] publicKeyHash) {
        return 0L;
    }
}
