package org.wisdom.contract.MultipleDefinition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tdf.rlp.RLP;
import org.tdf.rlp.RLPCodec;
import org.tdf.rlp.RLPElement;
import org.wisdom.contract.AnalysisContract;
import org.wisdom.keystore.wallet.KeystoreAction;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Multiple implements AnalysisContract {

    @RLP(0)
    private byte[] assetHash;
    @RLP(1)
    private int max;
    @RLP(2)
    private int min;
    @RLP(3)
    private List<byte[]> pubList;//公钥
    @RLP(4)
    private List<byte[]> signatureList;//签名
    @RLP(5)
    private List<byte[]> pubkeyHashList;//公钥哈希

    private List<String> pubListAddress;

    @Override
    public boolean RLPdeserialization(byte[] payload) {
        Multiple multiple = RLPElement.fromEncoded(payload).as(Multiple.class);
        if (multiple == null) {
            return false;
        }
        this.assetHash = multiple.getAssetHash();
        this.max = multiple.getMax();
        this.min = multiple.getMin();
        this.pubList = multiple.getPubList();
        this.signatureList = multiple.getSignatureList();
        this.pubkeyHashList = multiple.getPubkeyHashList();
        return true;
    }

    @Override
    public byte[] RLPserialization() {
        return RLPCodec.encode(Multiple.builder()
                .assetHash(this.assetHash)
                .max(this.max)
                .min(this.min)
                .pubList(this.pubList)
                .signatureList(this.signatureList)
                .pubkeyHashList(this.pubkeyHashList).build());
    }

    private List<String> HexPubToAddressList() {
        List<String> list = new ArrayList<>();
        this.pubList.stream().forEach(publist -> {
            list.add(KeystoreAction.pubkeyToAddress(publist, (byte) 0x00, "WX"));
        });
        return list;
    }

    public static Multiple getMultiple(byte[] Rlpbyte) {
        return RLPElement.fromEncoded(Rlpbyte).as(Multiple.class);
    }

    public static Multiple getConvertMultiple(byte[] Rlpbyte) {
        Multiple multiple = getMultiple(Rlpbyte);
        multiple.setPubListAddress(multiple.HexPubToAddressList());
        return multiple;
    }
}
