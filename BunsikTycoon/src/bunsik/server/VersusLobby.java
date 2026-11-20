package bunsik.server;

import bunsik.common.MessageType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 2P 대기실 */
final class VersusLobby {

    private static final int MAX_PLAYERS = 2;

    private final GameServer server;
    private final List<ClientHandler> members = new ArrayList<>();
    private final Map<ClientHandler, Boolean> ready = new HashMap<>();
    private boolean playing;

    VersusLobby(GameServer server) {
        this.server = server;
    }

    synchronized void join(ClientHandler h) {
        if (members.contains(h)) {
            return;
        }
        if (playing) {
            reject(h, "지금은 게임이 진행 중이에요. 끝난 뒤에 다시 들어와 주세요.");
            return;
        }
        if (members.size() >= MAX_PLAYERS) {
            reject(h, "대기실이 가득 찼어요 (최대 " + MAX_PLAYERS + "명).");
            return;
        }
        members.add(h);
        ready.put(h, false);
        server.log("대기실 입장: " + h.nickname() + " (" + members.size() + "/" + MAX_PLAYERS + ")");
        broadcastState();
    }

    private void reject(ClientHandler h, String reason) {
        h.send(MessageType.REJECT, reason);
        h.close();
    }

    synchronized void setReady(ClientHandler h, boolean isReady) {
        if (playing || !members.contains(h)) {
            return;
        }
        if (ready.get(h) == isReady) {
            server.log("중복 준비 요청 무시: " + h.nickname());
            return;
        }
        ready.put(h, isReady);
        server.log("준비 " + (isReady ? "완료" : "취소") + ": " + h.nickname());
        broadcastState();
        if (members.size() == MAX_PLAYERS && members.stream().allMatch(m -> ready.get(m))) {
            startGame();
        }
    }

    synchronized boolean contains(ClientHandler h) {
        return members.contains(h);
    }

    synchronized void leave(ClientHandler h) {
        if (members.remove(h)) {
            ready.remove(h);
            server.log("대기실 퇴장: " + h.nickname());
            broadcastState();
        }
    }

    private void startGame() {
        playing = true;
        members.forEach(m -> ready.put(m, false));
        server.log("경쟁 모드: " + members.get(0).nickname() + " vs " + members.get(1).nickname());
        new GameRoom(server, new ArrayList<>(members), true, this::onGameFinished).start();
    }

    /** 게임 종료 후 대기실 복귀 (준비 해제 상태) */
    synchronized void onGameFinished() {
        playing = false;
        members.forEach(m -> ready.put(m, false));
        broadcastState();
    }

    /** LOBBY|접속번호:닉네임:준비 ... */
    private void broadcastState() {
        Object[] entries = new Object[members.size()];
        for (int i = 0; i < members.size(); i++) {
            ClientHandler m = members.get(i);
            entries[i] = m.id() + ":" + m.nickname() + ":" + ready.get(m);
        }
        for (ClientHandler m : members) {
            m.send(MessageType.LOBBY, entries);
        }
    }
}