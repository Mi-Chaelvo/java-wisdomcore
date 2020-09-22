/*
 * Copyright (c) [2018]
 * This file is part of the java-wisdomcore
 *
 * The java-wisdomcore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The java-wisdomcore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the java-wisdomcore. If not, see <http://www.gnu.org/licenses/>.
 */

package org.wisdom.service.Impl;

import com.alibaba.fastjson.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.wisdom.ApiResult.APIResult;
import org.wisdom.command.Configuration;
import org.wisdom.contract.AssetDefinition.Asset;
import org.wisdom.contract.AssetDefinition.AssetChangeowner;
import org.wisdom.contract.AssetDefinition.AssetIncreased;
import org.wisdom.contract.AssetDefinition.AssetTransfer;
import org.wisdom.contract.RateheightlockDefinition.Rateheightlock;
import org.wisdom.contract.RateheightlockDefinition.RateheightlockDeposit;
import org.wisdom.contract.RateheightlockDefinition.RateheightlockWithdraw;
import org.wisdom.core.Block;
import org.wisdom.core.WisdomBlockChain;
import org.wisdom.core.incubator.Incubator;
import org.wisdom.core.incubator.RateTable;
import org.wisdom.dao.TransactionDaoJoined;
import org.wisdom.db.AccountState;
import org.wisdom.db.SyncTransactionCustomize;
import org.wisdom.db.WisdomRepository;
import org.wisdom.keystore.crypto.RipemdUtility;
import org.wisdom.keystore.crypto.SHA3Utility;
import org.wisdom.keystore.wallet.KeystoreAction;
import org.wisdom.protobuf.tcp.command.HatchModel;
import org.wisdom.service.HatchService;
import org.wisdom.core.account.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wisdom.util.ByteUtil;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.stream.Collectors.toList;

@Service
public class HatchServiceImpl implements HatchService {

    @Autowired
    WisdomBlockChain wisdomBlockChain;

    @Autowired
    RateTable rateTable;

    @Autowired
    Configuration configuration;

    @Autowired
    private WisdomRepository repository;

    @Autowired
    SyncTransactionCustomize syncTransactionCustomize;

    @Autowired
    TransactionDaoJoined transDaoJoined;

    @Override
    public Object getBalance(String pubkeyhash) {
        try {
            byte[] pubkeyhashbyte = Hex.decodeHex(pubkeyhash.toCharArray());
            Optional<AccountState> ao = repository.getConfirmedAccountState(pubkeyhashbyte);
            if (!ao.isPresent()) {
                return APIResult.newFailResult(2000, "SUCCESS", 0);
            }
            long balance = ao.get().getAccount().getBalance();
            return APIResult.newFailResult(2000, "SUCCESS", balance);
        } catch (DecoderException e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getNonce(String pubkeyhash) {
        try {
            byte[] pubkey = Hex.decodeHex(pubkeyhash.toCharArray());
            Block block = repository.getBestBlock();
            Optional<AccountState> accountState = repository.getAccountStateAt(block.getHash(), pubkey);
            if (!accountState.isPresent()) {
                return APIResult.newFailResult(2000, "SUCCESS", 0);
            }
            long nonce = accountState.get().getAccount().getNonce();
            return APIResult.newFailResult(2000, "SUCCESS", nonce);
        } catch (DecoderException e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getTransfer(long height) {
        List<Map<String, Object>> list = transDaoJoined.getTransferByHeightAndTypeAndGas(height, 1, Transaction.GAS_TABLE[1]);
        JSONArray jsonArray = new JSONArray();
        for (Map<String, Object> map : list) {
            byte[] tranHash = (byte[]) map.get("tranHash");
            byte[] from = (byte[]) map.get("fromAddress");
            byte[] to = (byte[]) map.get("coinAddress");
            map.put("tranHash", Hex.encodeHexString(tranHash));
            map.put("fromAddress", KeystoreAction.pubkeyHashToAddress(RipemdUtility.ripemd160(SHA3Utility.keccak256(from)), (byte) 0x00, ""));
            map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(to, (byte) 0x00, ""));
            jsonArray.add(map);
        }
        return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
    }

    @Override
    public Object getHatch(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getHatchByHeightAndType(height, 9);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] tranHash = (byte[]) map.get("coinHash");
                byte[] to = (byte[]) map.get("coinAddress");
                byte[] payload = (byte[]) map.get("payload");
                HatchModel.Payload payloadproto = HatchModel.Payload.parseFrom(payload);
                int days = payloadproto.getType();
                String sharpubkeyhex = payloadproto.getSharePubkeyHash();
                String sharpubkey = "";
                if (sharpubkeyhex != null && sharpubkeyhex != "") {
                    byte[] sharepubkeyhash = Hex.decodeHex(sharpubkeyhex.toCharArray());
                    sharpubkey = KeystoreAction.pubkeyHashToAddress(sharepubkeyhash, (byte) 0x00, "");
                }
                map.put("coinHash", Hex.encodeHexString(tranHash));
                map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(to, (byte) 0x00, ""));
                map.put("blockType", days);
                map.put("inviteAddress", sharpubkey);
                map.remove("payload");
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "ERROR");
        }
    }

    @Override
    public Object getInterest(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getInterestByHeightAndType(height, 10);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] tranHash = (byte[]) map.get("tranHash");
                map.put("tranHash", Hex.encodeHexString(tranHash));
                byte[] to = (byte[]) map.get("coinAddress");
                map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(to, (byte) 0x00, ""));
                //分享者
                byte[] coinHash = (byte[]) map.get("coinHash");
                Transaction transaction = wisdomBlockChain.getTransaction(coinHash);
                if (transaction == null) {
                    return APIResult.newFailResult(5000, "Hatching transactions do not exist");
                }
                HatchModel.Payload payloadproto = HatchModel.Payload.parseFrom(transaction.payload);
                String sharpub = payloadproto.getSharePubkeyHash();
                if (sharpub != null && !sharpub.equals("")) {
                    map.put("inviteAddress", KeystoreAction.pubkeyHashToAddress(Hex.decodeHex(sharpub.toCharArray()), (byte) 0x00, ""));
                } else {
                    map.put("inviteAddress", "");
                }
                map.put("coinHash", Hex.encodeHexString(coinHash));
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getShare(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getShareByHeightAndType(height, 11);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] coinHash = (byte[]) map.get("coinHash");
                byte[] tranHash = (byte[]) map.get("tranHash");
                byte[] to = (byte[]) map.get("coinAddress");
                byte[] invite = (byte[]) map.get("inviteAddress");
                map.put("coinHash", Hex.encodeHexString(coinHash));
                map.put("tranHash", Hex.encodeHexString(tranHash));
                map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(to, (byte) 0x00, ""));
                map.put("inviteAddress", KeystoreAction.pubkeyHashToAddress(invite, (byte) 0x00, ""));
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getCost(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getCostByHeightAndType(height, 12);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] coinAddress = (byte[]) map.get("coinAddress");
                byte[] tranHash = (byte[]) map.get("tranHash");
                byte[] tradeHash = (byte[]) map.get("tradeHash");
                map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(coinAddress, (byte) 0x00, ""));
                map.put("tranHash", Hex.encodeHexString(tranHash));
                map.put("tradeHash", Hex.encodeHexString(tradeHash));
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getVote(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getVoteByHeightAndType(height, 2);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] toAddress = (byte[]) map.get("toAddress");
                byte[] coinAddress = (byte[]) map.get("coinAddress");
                byte[] coinHash = (byte[]) map.get("coinHash");
                map.put("toAddress", KeystoreAction.pubkeyHashToAddress(toAddress, (byte) 0x00, ""));
                map.put("coinAddress", KeystoreAction.pubkeyToAddress(coinAddress, (byte) 0x00, ""));
                map.put("coinHash", Hex.encodeHexString(coinHash));
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getCancelVote(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getCancelVoteByHeightAndType(height, 13);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] toAddress = (byte[]) map.get("toAddress");
                byte[] coinAddress = (byte[]) map.get("coinAddress");
                byte[] coinHash = (byte[]) map.get("coinHash");
                byte[] tradeHash = (byte[]) map.get("tradeHash");
                map.put("toAddress", KeystoreAction.pubkeyHashToAddress(toAddress, (byte) 0x00, ""));
                map.put("coinAddress", KeystoreAction.pubkeyToAddress(coinAddress, (byte) 0x00, ""));
                map.put("coinHash", Hex.encodeHexString(coinHash));
                map.put("tradeHash", Hex.encodeHexString(tradeHash));
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getMortgage(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getMortgageByHeightAndType(height, 14);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] coinAddress = (byte[]) map.get("coinAddress");
                byte[] coinHash = (byte[]) map.get("coinHash");
                map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(coinAddress, (byte) 0x00, ""));
                map.put("coinHash", Hex.encodeHexString(coinHash));
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getCancelMortgage(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getCancelMortgageByHeightAndType(height, 15);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] toAddress = (byte[]) map.get("coinAddress");
                byte[] coinHash = (byte[]) map.get("coinHash");
                byte[] tradeHash = (byte[]) map.get("tradeHash");
                map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(toAddress, (byte) 0x00, ""));
                map.put("coinHash", Hex.encodeHexString(coinHash));
                map.put("tradeHash", Hex.encodeHexString(tradeHash));
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getNowInterest(String tranhash) {
        try {
            byte[] trhash = Hex.decodeHex(tranhash.toCharArray());
            //孵化事务
            Transaction transaction = wisdomBlockChain.getTransaction(trhash);
            if (transaction == null) {
                return APIResult.newFailResult(5000, "Transaction unavailable. Check transaction hash");
            }
            Optional<AccountState> oa = repository.getConfirmedAccountState(transaction.to);
            if (!oa.isPresent()) {
                return APIResult.newFailResult(5000, "The account does not exist");
            }
            Map<byte[], Incubator> interestMap = oa.get().getInterestMap();
            if (!interestMap.containsKey(trhash)) {
                return APIResult.newFailResult(5000, "Error in incubation state acquisition");
            }
            //查询当前孵化记录
            Incubator incubator = interestMap.get(trhash);
            if (incubator.getInterest_amount() == 0 || incubator.getCost() == 0) {
                return APIResult.newFailResult(3000, "There is no interest to be paid");
            }
            HatchModel.Payload payloadproto = HatchModel.Payload.parseFrom(transaction.payload);
            int days = payloadproto.getType();
            String nowrate = rateTable.selectrate(transaction.height, days);
            //当前最高高度
            long maxhieght = wisdomBlockChain.getTopHeight();
            long differheight = maxhieght - incubator.getLast_blockheight_interest();
            int differdays = (int) (differheight / configuration.getDay_count(maxhieght));
            if (differdays == 0) {
                return APIResult.newFailResult(5000, "Interest less than one day");
            }
            BigDecimal aount = new BigDecimal(transaction.amount);
            BigDecimal nowratebig = new BigDecimal(nowrate);
            BigDecimal dayrate = aount.multiply(nowratebig);
            long dayratelong = dayrate.longValue();
            JSONObject jsonObject = new JSONObject();
            //判断利息金额小于每天利息
            if (incubator.getInterest_amount() < dayrate.longValue()) {
                jsonObject.put("dueinAmount", incubator.getInterest_amount());
                jsonObject.put("capitalAmount", incubator.getInterest_amount());
            } else {
                long muls = (long) (incubator.getInterest_amount() % dayrate.longValue());
                if (muls != 0) {
                    long syamount = muls;
                    jsonObject.put("dueinAmount", syamount);
                    jsonObject.put("capitalAmount", incubator.getInterest_amount());
                } else {
                    int maxdays = (int) (incubator.getInterest_amount() / dayrate.longValue());
                    long lastdays = 0;
                    if (maxdays > differdays) {
                        lastdays = differdays;
                    } else {
                        lastdays = maxdays;
                    }
                    //当前可获取利息
                    BigDecimal lastdaysbig = BigDecimal.valueOf(lastdays);
                    BigDecimal dayrtebig = BigDecimal.valueOf(dayratelong);
                    long interset = dayrtebig.multiply(lastdaysbig).longValue();
                    jsonObject.put("dueinAmount", interset);
                    jsonObject.put("capitalAmount", incubator.getInterest_amount());
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonObject);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getNowShare(String tranhash) {
        try {
            byte[] trhash = Hex.decodeHex(tranhash.toCharArray());
            //孵化事务
            Transaction transaction = wisdomBlockChain.getTransaction(trhash);
            if (transaction == null) {
                return APIResult.newFailResult(5000, "Transaction unavailable. Check transaction hash");
            }
            HatchModel.Payload payloadproto = HatchModel.Payload.parseFrom(transaction.payload);
            Optional<AccountState> oa = repository.getConfirmedAccountState(Hex.decodeHex(payloadproto.getSharePubkeyHash().toCharArray()));
            if (!oa.isPresent()) {
                return APIResult.newFailResult(5000, "The account does not exist");
            }
            Map<byte[], Incubator> shareMap = oa.get().getShareMap();
            if (!shareMap.containsKey(trhash)) {
                return APIResult.newFailResult(5000, "Error in incubation state acquisition");
            }
            //查询当前孵化记录
            Incubator incubator = shareMap.get(trhash);
            if (incubator == null) {
                return APIResult.newFailResult(5000, "Error in incubation state acquisition");
            }
            if (incubator.getShare_amount() == 0) {
                return APIResult.newFailResult(3000, "There is no share to be paid");
            }
            int days = payloadproto.getType();
            String nowrate = rateTable.selectrate(transaction.height, days);
            //当前最高高度
            long maxhieght = wisdomBlockChain.getTopHeight();
            long differheight = maxhieght - incubator.getLast_blockheight_share();
            int differdays = (int) (differheight / configuration.getDay_count(maxhieght));
            if (differdays == 0) {
                return APIResult.newFailResult(5000, "Interest less than one day");
            }
            BigDecimal aount = new BigDecimal(transaction.amount);
            BigDecimal nowratebig = new BigDecimal(nowrate);
            BigDecimal lv = BigDecimal.valueOf(0.1);
            BigDecimal nowlv = aount.multiply(nowratebig);
            BigDecimal dayrate = nowlv.multiply(lv);
            long dayrates = dayrate.longValue();
            JSONObject jsonObject = new JSONObject();
            //判断分享金额小于每天可提取的
            if (incubator.getShare_amount() < dayrate.longValue()) {
                jsonObject.put("dueinAmount", incubator.getShare_amount());
                jsonObject.put("capitalAmount", incubator.getShare_amount());
            } else {
                long muls = (long) (incubator.getShare_amount() % dayrate.longValue());
                if (muls != 0) {
                    long syamount = muls;
                    jsonObject.put("dueinAmount", syamount);
                    jsonObject.put("capitalAmount", incubator.getShare_amount());
                } else {
                    int maxdays = (int) (incubator.getShare_amount() / dayrate.longValue());
                    long lastdays = 0;
                    if (maxdays > differdays) {
                        lastdays = differdays;
                    } else {
                        lastdays = maxdays;
                    }
                    //当前可获取分享
                    BigDecimal lastdaysbig = BigDecimal.valueOf(lastdays);
                    BigDecimal dayratesbig = BigDecimal.valueOf(dayrates);
                    long share = dayratesbig.multiply(lastdaysbig).longValue();
                    jsonObject.put("dueinAmount", share);
                    jsonObject.put("capitalAmount", incubator.getShare_amount());
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonObject);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    @Deprecated
    public Object getTxrecordFromAddress(String address) {
        try {
            if (KeystoreAction.verifyAddress(address) == 0) {
                byte[] pubkeyhash = KeystoreAction.addressToPubkeyHash(address);
                List<Map<String, Object>> list = new ArrayList<>();
                List<Map<String, Object>> tolist = syncTransactionCustomize.selectTranto(pubkeyhash);
                for (Map<String, Object> to : tolist) {
                    Map<String, Object> maps = to;
                    String from = maps.get("from").toString();
                    String fromaddress = KeystoreAction.pubkeyToAddress(Hex.decodeHex(from.toCharArray()), (byte) 0x00, "");
                    maps.put("from", fromaddress);
                    String topubkeyhash = maps.get("to").toString();
                    String toaddress = KeystoreAction.pubkeyHashToAddress(Hex.decodeHex(topubkeyhash.toCharArray()), (byte) 0x00, "");
                    maps.put("to", toaddress);
                    long time = Long.valueOf(maps.get("datetime").toString());
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date dates = new Date(time * 1000);
                    String date = sdf.format(dates);
                    maps.put("datetime", date);
                    maps.put("type", "+");
                    list.add(maps);
                }
                List<Map<String, Object>> fromlist = syncTransactionCustomize.selectTranfrom(pubkeyhash);
                for (Map<String, Object> from : fromlist) {
                    Map<String, Object> maps = from;
                    String froms = maps.get("from").toString();
                    byte[] frompubhash = RipemdUtility.ripemd160(SHA3Utility.keccak256(Hex.decodeHex(froms.toCharArray())));
                    if (Arrays.equals(frompubhash, pubkeyhash)) {
                        String fromaddress = KeystoreAction.pubkeyHashToAddress(frompubhash, (byte) 0x00, "");
                        maps.put("from", fromaddress);
                        String topubkeyhash = maps.get("to").toString();
                        String toaddress = KeystoreAction.pubkeyHashToAddress(Hex.decodeHex(topubkeyhash.toCharArray()), (byte) 0x00, "");
                        maps.put("to", toaddress);
                        long time = Long.valueOf(maps.get("datetime").toString());
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        Date dates = new Date(time * 1000);
                        String date = sdf.format(dates);
                        maps.put("datetime", date);
                        maps.put("type", "-");
                        list.add(maps);
                    }
                }
                list = list.stream().sorted((p1, p2) -> Integer.valueOf(p1.get("height").toString()) - Integer.valueOf(p2.get("height").toString()))
                        .collect(toList());
                return APIResult.newFailResult(2000, "SUCCESS", list);
            } else {
                return APIResult.newFailResult(5000, "Address check error");
            }
        } catch (DecoderException e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getCoinBaseList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getCoinBaseByHeightAndType(height);
            int count = 0;
            Map<String, Object> maps = new HashMap<>();
            for (Map<String, Object> map : list) {
                if (Integer.valueOf(map.get("type").toString()) == 0) {
                    byte[] toAddress = (byte[]) map.get("coinAddress");
                    byte[] coinHash = (byte[]) map.get("coinHash");
                    map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(toAddress, (byte) 0x00, ""));
                    map.put("coinHash", Hex.encodeHexString(coinHash));
                    maps.putAll(map);
                    break;
                }
                count++;
            }
            maps.put("trancount", count);
            return APIResult.newFailResult(2000, "SUCCESS", maps);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getAssetList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getAssetByHeightAndType(height, 7);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] payload = (byte[]) map.get("tradeHash");
                if (payload[0] == 0) {//资产代币
                    byte[] coinHash = (byte[]) map.get("coinHash");
                    JSONObject jsonObject = new JSONObject();
                    Asset asset = Asset.getAsset(ByteUtil.bytearrayridfirst(payload));
                    jsonObject.put("code", asset.getCode());
                    jsonObject.put("owner", KeystoreAction.pubkeyHashToAddress(asset.getOwner(), (byte) 0x00, ""));
                    jsonObject.put("allowincrease", asset.getAllowincrease());
                    jsonObject.put("coinHash", Hex.encodeHexString(coinHash));
                    jsonObject.put("coinHash160", Hex.encodeHexString(RipemdUtility.ripemd160(coinHash)));
                    jsonObject.put("createdAt", map.get("createdAt"));
                    jsonArray.add(jsonObject);
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getAssetTransferList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getAssetTransferByHeightAndType(height, 8);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] payload = (byte[]) map.get("tradeHash");
                if (payload[0] == 1) {//资产转发
                    byte[] coinHash = (byte[]) map.get("coinHash");
                    byte[] tohash = (byte[]) map.get("coinHash160");
                    long gasPrice = (Long) map.get("gasPrice");
                    long fee = gasPrice * Transaction.GAS_TABLE[8];
                    AssetTransfer asset = AssetTransfer.getAssetTransfer(ByteUtil.bytearrayridfirst(payload));
                    map.put("fromAddress", KeystoreAction.pubkeyToAddress(asset.getFrom(), (byte) 0x00, ""));
                    map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(asset.getTo(), (byte) 0x00, ""));
                    map.put("amount", asset.getValue());
                    map.put("fee", fee);
                    map.put("coinHash", Hex.encodeHexString(coinHash));
                    map.put("coinHash160", Hex.encodeHexString(tohash));
                    map.remove("tradeHash");
                    map.remove("gasPrice");
                    jsonArray.add(map);
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getAssetOwnerList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getAssetOtherByHeightAndType(height, 8);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] payload = (byte[]) map.get("tradeHash");
                if (payload[0] == 0) {//更换拥有者
                    byte[] coinHash = (byte[]) map.get("coinHash");
                    byte[] tohash = (byte[]) map.get("coinHash160");
                    byte[] from = (byte[]) map.get("coinAddress");
                    long gasPrice = (Long) map.get("gasPrice");
                    long fee = gasPrice * Transaction.GAS_TABLE[8];
                    AssetChangeowner assetChangeowner = AssetChangeowner.getAssetChangeowner(ByteUtil.bytearrayridfirst(payload));
                    map.put("oldAddress", KeystoreAction.pubkeyToAddress(from, (byte) 0x00, ""));
                    map.put("newAddress", KeystoreAction.pubkeyHashToAddress(assetChangeowner.getNewowner(), (byte) 0x00, ""));
                    map.put("fee", fee);
                    map.put("coinHash", Hex.encodeHexString(coinHash));
                    map.put("coinHash160", Hex.encodeHexString(tohash));
                    map.remove("tradeHash");
                    map.remove("gasPrice");
                    map.remove("coinAddress");
                    jsonArray.add(map);
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getAssetIncreasedList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getAssetOtherByHeightAndType(height, 8);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] payload = (byte[]) map.get("tradeHash");
                if (payload[0] == 2) {//增发
                    byte[] coinHash = (byte[]) map.get("coinHash");
                    byte[] tohash = (byte[]) map.get("coinHash160");
                    byte[] from = (byte[]) map.get("coinAddress");
                    long gasPrice = (Long) map.get("gasPrice");
                    long fee = gasPrice * Transaction.GAS_TABLE[8];
                    AssetIncreased assetIncreased = AssetIncreased.getAssetIncreased(ByteUtil.bytearrayridfirst(payload));
                    map.put("ownerAddress", KeystoreAction.pubkeyToAddress(from, (byte) 0x00, ""));
                    map.put("fee", fee);
                    map.put("coinHash", Hex.encodeHexString(coinHash));
                    map.put("coinHash160", Hex.encodeHexString(tohash));
                    map.put("amount", assetIncreased.getAmount());
                    map.remove("tradeHash");
                    map.remove("gasPrice");
                    map.remove("coinAddress");
                    jsonArray.add(map);
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getDepositList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getDepositHeightAndType(height, 3);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] from = (byte[]) map.get("coinAddress");
                byte[] coinHash = (byte[]) map.get("coinHash");
                byte[] tradeHash = (byte[]) map.get("tradeHash");
                long gasPrice = (Long) map.get("gasPrice");
                long fee = gasPrice * Transaction.GAS_TABLE[3];
                map.put("coinAddress", KeystoreAction.pubkeyToAddress(from, (byte) 0x00, ""));
                map.put("coinHash", Hex.encodeHexString(coinHash));
                map.put("tradeHash", Hex.encodeHexString(tradeHash));
                map.put("fee", fee);
                map.remove("gasPrice");
                jsonArray.add(map);
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getBalanceList(List<String> addresslist) {
        try {
            JSONObject jsonObject = new JSONObject();
            for (String str : addresslist) {
                if (KeystoreAction.verifyAddress(str) != 0) {
                    continue;
                }
                byte[] pubkeyhashbyte = new byte[0];
                pubkeyhashbyte = Hex.decodeHex(Hex.encodeHexString(KeystoreAction.addressToPubkeyHash(str)).toCharArray());
                Optional<AccountState> ao = repository.getConfirmedAccountState(pubkeyhashbyte);
                long balance = 0;
                if (ao.isPresent()) {
                    balance = ao.get().getAccount().getBalance();
                }
                jsonObject.put(str, balance);
            }
            return APIResult.newSuccess(jsonObject);
        } catch (DecoderException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Object getRateheightLockList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getRatelockByHeightAndType(height, 7);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] payload = (byte[]) map.get("tradeHash");
                if (payload[0] == 4) {//定额条件比例支付
                    byte[] from = (byte[]) map.get("fromAddress");
                    byte[] coinHash = (byte[]) map.get("coinHash");
                    JSONObject jsonObject = new JSONObject();
                    Rateheightlock rateheightlock = Rateheightlock.getRateheightlock(ByteUtil.bytearrayridfirst(payload));
                    if (!Arrays.equals(rateheightlock.getDest(), new byte[20])) {
                        Optional<AccountState> accountStateOptional = repository.getConfirmedAccountState(rateheightlock.getDest());
                        if (!accountStateOptional.isPresent()) {
                            jsonObject.put("type", 0);
                        } else {
                            jsonObject.put("type", accountStateOptional.get().getType());
                        }
                    } else {
                        jsonObject.put("type", 1);
                    }
                    jsonObject.put("assetHash160", Hex.encodeHexString(rateheightlock.getAssetHash()));
                    jsonObject.put("multiple", rateheightlock.getOnetimedepositmultiple());
                    jsonObject.put("periodHeight", rateheightlock.getWithdrawperiodheight());
                    jsonObject.put("drawRate", rateheightlock.getWithdrawrate());
                    jsonObject.put("dest", Hex.encodeHexString(rateheightlock.getDest()));
                    jsonObject.put("fromAddress", KeystoreAction.pubkeyToAddress(from, (byte) 0x00, ""));
                    jsonObject.put("coinHash", Hex.encodeHexString(coinHash));
                    jsonObject.put("coinHash160", Hex.encodeHexString(RipemdUtility.ripemd160(coinHash)));
                    jsonObject.put("createdAt", map.get("createdAt"));
                    jsonObject.remove("tradeHash");
                    jsonArray.add(jsonObject);
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getRateheightLockDepositList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getRatelockInvokeByHeightAndType(height, 8);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] payload = (byte[]) map.get("tradeHash");
                if (payload[0] == 8) {//定额条件比例支付
                    byte[] coinHash = (byte[]) map.get("coinHash");
                    byte[] tohash = (byte[]) map.get("coinHash160");
                    byte[] from = (byte[]) map.get("coinAddress");
                    RateheightlockDeposit rateheightlockDeposit = RateheightlockDeposit.getRateheightlockDeposit(ByteUtil.bytearrayridfirst(payload));
                    map.put("fromAddress", KeystoreAction.pubkeyToAddress(from, (byte) 0x00, ""));
                    map.put("coinHash", Hex.encodeHexString(coinHash));
                    map.put("coinHash160", Hex.encodeHexString(tohash));
                    map.put("amount", rateheightlockDeposit.getValue());
                    map.remove("tradeHash");
                    map.remove("coinAddress");
                    jsonArray.add(map);
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public Object getRateheightLockWithdrawList(long height) {
        try {
            List<Map<String, Object>> list = transDaoJoined.getRatelockInvokeByHeightAndType(height, 8);
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> map : list) {
                byte[] payload = (byte[]) map.get("tradeHash");
                if (payload[0] == 9) {//定额条件比例获取
                    byte[] coinHash = (byte[]) map.get("coinHash");
                    byte[] tohash = (byte[]) map.get("coinHash160");
                    byte[] from = (byte[]) map.get("coinAddress");
                    RateheightlockWithdraw rateheightLockWithdraw = RateheightlockWithdraw.getRateheightlockWithdraw(ByteUtil.bytearrayridfirst(payload));
                    Optional<AccountState> accountStateOptional = repository.getConfirmedAccountState(rateheightLockWithdraw.getTo());
                    if (!accountStateOptional.isPresent()) {
                        map.put("type", 0);
                    } else {
                        map.put("type", accountStateOptional.get().getType());
                    }
                    map.put("deposithash", rateheightLockWithdraw.getDeposithash());
                    map.put("fromAddress", KeystoreAction.pubkeyToAddress(from, (byte) 0x00, ""));
                    map.put("coinAddress", KeystoreAction.pubkeyHashToAddress(rateheightLockWithdraw.getTo(), (byte) 0x00, ""));
                    map.put("coinHash", Hex.encodeHexString(coinHash));
                    map.put("coinHash160", Hex.encodeHexString(tohash));
                    map.remove("tradeHash");
                    jsonArray.add(map);
                }
            }
            return APIResult.newFailResult(2000, "SUCCESS", jsonArray);
        } catch (Exception e) {
            return APIResult.newFailResult(5000, "Exception error");
        }
    }

    @Override
    public APIResult getInviteTradeList(String address) {
        JSONArray jsonArray = new JSONArray();
        Optional<AccountState> a = repository.getConfirmedAccountState(KeystoreAction.addressToPubkeyHash(address));
        if (!a.isPresent()) {
            return APIResult.newSuccess(jsonArray);
        }
        AccountState accountState = a.get();
        Map<byte[], Incubator> shareMap = accountState.getShareMap();
        if (shareMap.size() == 0 || shareMap.isEmpty()) {
            return APIResult.newSuccess(jsonArray);
        }
        for (Map.Entry<byte[], Incubator> entry : shareMap.entrySet()) {
            Incubator incubator = entry.getValue();
            if (incubator.getShare_amount() > 0) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("coinHash", incubator.getTxhash());
                jsonObject.put("shareAmount", incubator.getShare_amount());
                APIResult apiResult = (APIResult) getNowShare(incubator.getTxhash());
                if (apiResult.getCode() == 5000) {
                    jsonObject.put("extractShare", 0);
                    jsonObject.put("status", 0);//没满足提取要求
                } else {
                    jsonObject.put("extractShare", getAmount((JSONObject) apiResult.getData()));
                    jsonObject.put("status", 1);//可提取
                }
                jsonArray.add(jsonObject);
            }
        }
        return APIResult.newSuccess(jsonArray);
    }

    @Override
    public APIResult getTradeList(String address) {
        JSONArray jsonArray = new JSONArray();
        Optional<AccountState> a = repository.getConfirmedAccountState(KeystoreAction.addressToPubkeyHash(address));
        if (!a.isPresent()) {
            return APIResult.newSuccess(jsonArray);
        }
        AccountState accountState = a.get();
        Map<byte[], Incubator> incubatorMap = accountState.getInterestMap();
        if (incubatorMap.size() == 0 || incubatorMap.isEmpty()) {
            return APIResult.newSuccess(jsonArray);
        }
        for (Map.Entry<byte[], Incubator> entry : incubatorMap.entrySet()) {
            Incubator incubator = entry.getValue();
            if (incubator.getCost() > 0) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("coinHash", Hex.encodeHexString(incubator.getTxid_issue()));
                jsonObject.put("cost", incubator.getCost());
                Transaction transaction = wisdomBlockChain.getTransaction(incubator.getTxid_issue());
                if (transaction == null) {
                    continue;
                }
                APIResult apiResult = (APIResult) getNowInterest(transaction.getHashHexString());
                if (apiResult.getCode() == 5000) {
                    jsonObject.put("extractableAmount", 0);
                    jsonObject.put("status", 2);//未满足要求
                } else {
                    if (apiResult.getCode() == 3000) {
                        jsonObject.put("extractableAmount", 0);
                        jsonObject.put("status", 1);//可领取本金
                    } else {
                        jsonObject.put("extractableAmount", getAmount((JSONObject) apiResult.getData()));
                        jsonObject.put("status", 0);//孵化中
                    }
                }
                jsonObject.put("type", transaction.getdays());
                jsonObject.put("rate", rateTable.selectrate(transaction.height, transaction.getdays()));
                Block block = wisdomBlockChain.getBlockByHash(transaction.blockHash);
                jsonObject.put("createdAt", block.nTime * 1000);
                jsonArray.add(jsonObject);
            }
        }
        return APIResult.newSuccess(jsonArray);
    }

    @Override
    public APIResult getCreatedAT(long height) {
        Block block = wisdomBlockChain.getHeaderByHeight(height);
        if (block == null) {
            return APIResult.newFailed("Unknown block height");
        }
        return APIResult.newSuccess(block.nTime);
    }

    public long getAmount(JSONObject jsonObject) {
        long dueinAmount = jsonObject.getLong("dueinAmount");
        long capitalAmount = jsonObject.getLong("capitalAmount");
        if (dueinAmount > capitalAmount) {
            return capitalAmount;
        }
        return dueinAmount;
    }
}