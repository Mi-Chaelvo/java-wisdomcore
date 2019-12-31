package org.wisdom.db;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.binary.Hex;
import org.tdf.common.serialize.Codec;
import org.tdf.common.store.NoDeleteStore;
import org.tdf.common.store.Store;
import org.tdf.common.trie.Trie;
import org.tdf.common.trie.TrieImpl;
import org.tdf.common.util.ByteArrayMap;
import org.tdf.rlp.RLPCodec;
import org.tdf.rlp.RLPElement;
import org.wisdom.core.Block;
import org.wisdom.crypto.HashUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;


public abstract class StateTrieAdapter<T> implements StateTrie<T> {
    private String TRIE;
    private String DELETED;
    private String ROOTS;

    @Getter(AccessLevel.PROTECTED)
    private NoDeleteStore<byte[], byte[]> trieStore;

    @Getter(AccessLevel.PROTECTED)
    private Store<byte[], byte[]> rootStore;

    @Getter(AccessLevel.PROTECTED)
    private Trie<byte[], T> trie;

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PACKAGE)
    private ForkedWisdomBlockChain chain;

    protected abstract String getPrefix();

    protected abstract Map<byte[], T> generateGenesisStates();

    public StateTrieAdapter(Block genesis, Class<T> clazz, DatabaseStoreFactory factory, boolean logDeletes, boolean reset) {
        TRIE = getPrefix() + "-trie";
        DELETED = getPrefix() + "-deleted";
        ROOTS = getPrefix() + "-trie-roots";

        rootStore = factory.create(ROOTS, reset);
        if (logDeletes) {
            trieStore = new NoDeleteStore<>(
                    factory.create(TRIE, reset),
                    factory.create(DELETED, reset)
            );
        } else {
            trieStore = new NoDeleteStore<>(
                    factory.create(TRIE, reset),
                    Store.getNop()
            );
        }
        trie = TrieImpl.newInstance(
                HashUtil::keccak256,
                trieStore,
                Codec.identity(),
                Codec.newInstance(RLPCodec::encode, x -> RLPElement.fromEncoded(x).as(clazz))
        );

        rootStore.put(genesis.hashPrevBlock, trie.getNullHash());
        if (rootStore.containsKey(genesis.getHash())) return;

        // sync to genesis
        Trie<byte[], T> tmp = trie.revert();
        generateGenesisStates().forEach(tmp::put);
        byte[] root = tmp.commit();
        tmp.flush();
        rootStore.put(genesis.getHash(), root);
    }

    public Optional<T> get(byte[] blockHash, byte[] publicKeyHash) {
        byte[] root = getRootStore().get(blockHash)
                .orElseThrow(() -> new RuntimeException(Hex.encodeHexString(blockHash) + " not synced"));
        return getTrie().revert(root).get(publicKeyHash);
    }

    public Map<byte[], T> batchGet(byte[] blockHash, Collection<byte[]> keys) {
        byte[] root = getRootStore().get(blockHash)
                .orElseThrow(() -> new RuntimeException(Hex.encodeHexString(blockHash) + " not synced"));
        Trie<byte[], T> trie = getTrie().revert(root);
        ByteArrayMap<T> m = new ByteArrayMap<>();
        keys.forEach(x -> {
            Optional<T> o = trie.get(x);
            if (!o.isPresent()) return;
            m.put(x, o.get());
        });
        return m;
    }
}
