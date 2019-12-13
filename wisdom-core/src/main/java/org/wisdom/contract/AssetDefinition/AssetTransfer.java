package org.wisdom.contract.AssetDefinition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tdf.rlp.RLP;
import org.tdf.rlp.RLPCodec;
import org.tdf.rlp.RLPElement;
import org.wisdom.contract.AnalysisContract;
import org.wisdom.db.AccountState;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetTransfer implements AnalysisContract {
    @RLP(0)
    private byte[] from;
    @RLP(1)
    private byte[] to;
    @RLP(2)
    private long value;

    @Override
    public List<AccountState> update(List<AccountState> accountStateList) {
        return null;
    }

    @Override
    public boolean RLPdeserialization(byte[] payload) {
        try{
            AssetTransfer assetTransfer = RLPCodec.decode(payload, AssetTransfer.class);
            this.from= assetTransfer.getFrom();
            this.to= assetTransfer.getTo();
            this.value= assetTransfer.getValue();
        }catch (Exception e){
            return false;
        }
        return true;
    }

    @Override
    public byte[] RLPserialization() {
        return RLPCodec.encode(AssetTransfer.builder()
                                        .from(this.from)
                                        .to(this.to)
                                        .value(this.value).build());
    }
}
