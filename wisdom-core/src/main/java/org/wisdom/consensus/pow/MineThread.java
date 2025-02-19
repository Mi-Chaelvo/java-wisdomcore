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

package org.wisdom.consensus.pow;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.wisdom.encoding.BigEndian;
import org.wisdom.core.Block;
import org.wisdom.core.event.NewBlockMinedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Date;

@Component
@Scope("prototype")
@Slf4j(topic = "miner")
public class MineThread {
    private volatile boolean terminated;

    private static SecureRandom SECURE_RANDOM;

    static {
        SECURE_RANDOM = new SecureRandom();
    }

    @Autowired
    private ApplicationContext ctx;

    @Async
    public void mine(Block block, long startTime, long endTime) {
        block.setWeight(1);
        log.info("start mining at height " + block.nHeight + " target = " + Hex.encodeHexString(block.nBits));
        block = pow(block, startTime, endTime);
        terminated = true;
        if (block != null) {
            ctx.publishEvent(new NewBlockMinedEvent(this, block));
        }
    }

    public Block pow(Block block, long parentBlockTimeStamp, long endTime) {
        byte[] nBits = block.nBits;
        while (!terminated) {
            byte[] tmp = new byte[32];
            SECURE_RANDOM.nextBytes(tmp);
            block.nNonce = tmp;
            block.nTime = System.currentTimeMillis() / 1000;
            if (block.nTime <= parentBlockTimeStamp) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {

                }
                continue;
            }
            if (block.nTime >= endTime) {
                log.error("mining timeout, dead line = " + new Date(endTime * 1000).toString() + "consider upgrade your hardware");
                return null;
            }
            byte[] hash = Block.calculatePOWHash(block);
            if (BigEndian.compareUint256(hash, nBits) < 0) {
                return block;
            }
        }
        return null;
    }

    public void terminate() {
        terminated = true;
    }

    public boolean isTerminated() {
        return terminated;
    }
}
