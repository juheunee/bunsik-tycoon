package bunsik.client;

import bunsik.common.Message;
import bunsik.common.MessageType;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 클라이언트의 "통신 담당". 서버 접속, 메시지 보내기, 받기, 끊기를 전담하고 화면 코드와는 분리되어 있다.
 *
 * <p><b>가장 중요한 규칙</b>: 메시지는 별도의 수신 스레드에서 받지만, Swing 화면은 정해진 스레드(EDT)에서만
 * 고쳐야 안전하다. 그래서 받은 메시지는 반드시 {@link SwingUtilities#invokeLater}로 화면 스레드에 넘긴다.
 * 이걸 빼먹으면 "가끔" 화면이 안 바뀌거나 깨지는, 재현하기 어려운 버그가 생긴다.
 */
public final class NetworkClient {

    /** 화면 쪽이 구현하는 받는 귀. 두 메서드 모두 화면 스레드(EDT)에서 호출된다. */
    public interface Listener {
        void onMessage(Message message);

        void onDisconnected();
    }

    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final Listener listener;
    private Socket socket;
    private PrintWriter out;
    private volatile boolean closing;

    public NetworkClient(Listener listener) {
        this.listener = listener;
    }

    /** 서버에 접속한다. 오래 걸릴 수 있으므로 화면 스레드가 아닌 곳에서 호출할 것. */
    public void connect(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> readLoop(in), "net-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop(BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final Message m;
                try {
                    m = Message.parse(line);
                } catch (IllegalArgumentException e) {
                    continue; // 모르는 메시지는 무시
                }
                SwingUtilities.invokeLater(() -> listener.onMessage(m));
            }
        } catch (IOException e) {
            // 연결 끊김 → 아래에서 알림
        } finally {
            if (!closing) {
                SwingUtilities.invokeLater(listener::onDisconnected);
            }
        }
    }

    public synchronized void send(MessageType type, Object... args) {
        if (out != null && !closing) {
            out.println(new Message(type, args).encode());
        }
    }

    /** 스스로 나갈 때. 서버에 인사(LEAVE)하고 닫는다. 이때는 onDisconnected가 호출되지 않는다. */
    public void close() {
        send(MessageType.LEAVE);
        closing = true;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // 이미 닫힘
        }
    }
}
