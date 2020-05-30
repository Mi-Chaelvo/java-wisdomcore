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

package org.wisdom.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tdf.common.util.ChainCache;
import org.wisdom.core.validate.CompositeBlockRule;
import org.wisdom.core.validate.MerkleRule;
import org.wisdom.core.validate.Result;
import org.wisdom.db.BlockWrapper;
import org.wisdom.db.WisdomRepository;
import org.wisdom.merkletree.MerkleTreeManager;

import java.util.Collection;
import java.util.List;

@Component
@Deprecated
public class PendingBlocksManager {

    @Autowired
    private WisdomBlockChain bc;

    @Autowired
    private WisdomRepository wisdomRepository;

    @Autowired
    private CompositeBlockRule rule;

    @Autowired
    private MerkleRule merkleRule;

    private Logger logger = LoggerFactory.getLogger(PendingBlocksManager.class);

    @Autowired
    private MerkleTreeManager merkleTreeManager;

    public void addPendingBlock(Block b) {
        Block lastConfirmed = wisdomRepository.getLatestConfirmed();
        if (b.nHeight <= lastConfirmed.nHeight || wisdomRepository.containsBlock(b.getHash())) {
            logger.info("the block has written");
            return;
        }
        Result res = rule.validateBlock(b);
        if (!res.isSuccess()) {
            logger.error("validate the block fail error = " + res.getMessage());
            return;
        }
        Result result = merkleRule.validateBlock(b);
        if (!result.isSuccess()) {
            merkleTreeManager.writeBlockToCache(b);
        }
        b.weight = 1;
        wisdomRepository.writeBlock(b);
    }

    // 区块的写入全部走这里
    public void addPendingBlocks(Collection<? extends Block> blocks) {
        blocks.forEach(this::addPendingBlock);
    }
}
