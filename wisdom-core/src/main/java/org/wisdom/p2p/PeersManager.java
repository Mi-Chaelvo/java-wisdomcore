package org.wisdom.p2p;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author sal 1564319846@qq.com
 * peers manager plugin
 */
@Component
@Slf4j(topic = "net")
public class PeersManager implements Plugin {
    private static final WisdomOuterClass.Ping PING = WisdomOuterClass.Ping.newBuilder().build();
    private static final WisdomOuterClass.Pong PONG = WisdomOuterClass.Pong.newBuilder().build();
    private PeerServer server;

    @Override
    public void onMessage(Context context, PeerServer server) {
        switch (context.getPayload().getCode()) {
            case PING:
                onPing(context, server);
                break;
            case PONG:
                context.keep();
                break;
            case LOOK_UP:
                onLookup(context, server);
                break;
            case PEERS:
                onPeers(context, server);
        }
    }

    @Override
    public void onStart(PeerServer server) {
        this.server = server;
    }

    private void onPing(Context context, PeerServer server) {
        context.response(PONG);
        context.pend();
    }

    private void onPong(Context context, PeerServer server) {
        context.keep();
    }

    private void onLookup(Context context, PeerServer server) {
        List<String> peers = new ArrayList<>();
        for (Peer p : server.getPeers()) {
            peers.add(p.toString());
        }
        peers.add(server.getSelf().toString());
        context.response(WisdomOuterClass.Peers.newBuilder().addAllPeers(peers).build());
    }

    private void onPeers(Context context, PeerServer server) {
        try {
            for (String p : context.getPayload().getPeers().getPeersList()) {
                Peer pr = Peer.parse(p);
                server.pend(pr);
            }
        } catch (Exception e) {
            log.error("parse peer fail");
        }
    }

    public List<Peer> getPeers() {
        return Optional.ofNullable(server)
                .map(PeerServer::getPeers).orElse(Collections.emptyList());
    }

    public String getSelfAddress() {
        return Optional.ofNullable(server)
                .map(PeerServer::getSelf)
                .map(Peer::toString).orElse("");
    }

}
