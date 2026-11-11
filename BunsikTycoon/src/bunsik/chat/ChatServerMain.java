package bunsik.chat;

import bunsik.common.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** 게임 서버와 독립적으로 공통 CHAT 규약을 검증하는 실행용 서버. */
public final class ChatServerMain {
    private final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger();

    public static void main(String[] args) throws IOException {
        new ChatServerMain().run(AppConfig.load().serverPort());
    }

    private void run(int port) throws IOException {
        try (ServerSocket listener = new ServerSocket(port)) {
            System.out.println("CHAT server listening on " + listener.getLocalPort());
            while (true) {
                Socket socket = listener.accept();
                new Thread(() -> serve(socket), "chat-peer-" + nextId.get()).start();
            }
        }
    }

    private void serve(Socket socket) {
        Peer peer = null;
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(10000);
            Message hello = Message.parse(reader.readLine());
            if (hello.type() != MessageType.HELLO || hello.size() != 2
                    || hello.get(0).isBlank() || hello.get(0).length() > 20
                    || !(hello.get(1).equals("SOLO") || hello.get(1).equals("VERSUS"))) {
                throw new IllegalArgumentException("Expected HELLO|nickname|SOLO or VERSUS");
            }
            peer = new Peer(socket, nextId.getAndIncrement(), hello.get(0));
            peer.send(new Message(MessageType.WELCOME, peer.id));
            peers.add(peer);
            socket.setSoTimeout(0);
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    Message request = Message.parse(line);
                    if (request.type() == MessageType.LEAVE && request.size() == 0) { break; }
                    Message chat = ChatMessages.broadcast(peer.id, peer.nickname, request);
                    for (Peer recipient : peers) {
                        try { recipient.send(chat); }
                        catch (IOException e) { recipient.socket.close(); peers.remove(recipient); }
                    }
                } catch (IllegalArgumentException e) {
                    peer.send(new Message(MessageType.TOAST, "Invalid chat message"));
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("CHAT connection closed: " + e.getMessage());
        } finally {
            if (peer != null) { peers.remove(peer); }
        }
    }

    private static final class Peer {
        final Socket socket;
        final int id;
        final String nickname;
        final BufferedWriter writer;

        Peer(Socket socket, int id, String nickname) throws IOException {
            this.socket = socket;
            this.id = id;
            this.nickname = nickname;
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        synchronized void send(Message message) throws IOException {
            writer.write(message.encode());
            writer.newLine();
            writer.flush();
        }
    }
}
