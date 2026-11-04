package bunsik.server;

import bunsik.common.GameConfig;
import bunsik.common.MessageType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 경쟁 모드(2P) 대기실. 최대 2명이 들어와 둘 다 준비를 누르면 게임방을 만들어 시작한다.
 *
 * <p>여러 직원 스레드가 동시에 입장·준비·퇴장을 부를 수 있으므로 모든 메서드를 synchronized로 잠근다.
 */
final class VersusLobby {

    private final GameServer server;
    private final List<ClientHandler> members = new ArrayList<>();
    private final Map<ClientHandler, Boolean> ready = new HashMap<>();
    private boolean playing;

    VersusLobby(GameServer server) {
        this.server = server;
    }

    synchronized void join(ClientHandler h) {
        if (playing) {
            reject(h, "지금은 게임이 진행 중이에요. 끝난 뒤에 다시 들어와 주세요.");
        } else if (members.size() >= GameConfig.VERSUS_PLAYERS) {
            reject(h, "대기실이 가득 찼어요 (최대 " + GameConfig.VERSUS_PLAYERS + "명).");
        } else {
            members.add(h);
            ready.put(h, false);
            GameServer.log("대기실 입장: " + h.nickname() + " (" + members.size() + "/" + GameConfig.VERSUS_PLAYERS + ")");
            broadcastState();
        }
    }

    private void reject(ClientHandler h, String reason) {
        h.send(MessageType.REJECT, reason);
        h.close();
    }

    synchronized void setReady(ClientHandler h, boolean isReady) {
        if (playing || !members.contains(h)) {
            return;
        }
        ready.put(h, isReady);
        broadcastState();
        boolean allReady = members.size() == GameConfig.VERSUS_PLAYERS
                && members.stream().allMatch(m -> ready.getOrDefault(m, false));
        if (allReady) {
            playing = true;
            members.forEach(m -> ready.put(m, false)); // 다음 판을 위해 미리 준비 해제
            GameServer.log("경쟁 모드 시작: " + members.get(0).nickname() + " vs " + members.get(1).nickname());
            new GameRoom(server, new ArrayList<>(members), true).start();
        }
    }

    synchronized void leave(ClientHandler h) {
        if (members.remove(h)) {
            ready.remove(h);
            GameServer.log("대기실 퇴장: " + h.nickname());
            broadcastState();
        }
    }

    /** 게임이 끝나면 두 사람은 대기실(준비 해제 상태)로 돌아온다 */
    synchronized void onGameFinished() {
        playing = false;
        members.forEach(m -> ready.put(m, false));
        broadcastState();
    }

    private void broadcastState() {
        Object[] entries = new Object[members.size()];
        for (int i = 0; i < members.size(); i++) {
            ClientHandler m = members.get(i);
            entries[i] = m.id() + ":" + m.nickname() + ":" + ready.getOrDefault(m, false);
        }
        for (ClientHandler m : members) {
            m.send(MessageType.LOBBY, entries);
        }
    }
}
