package bunsik.chat;

import bunsik.common.*;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import javax.swing.*;

/** 아직 게임 화면이 없는 단계에서 실행하는 CHAT 전용 창. */
public final class ChatClientMain {
    private final JTextArea history = new JTextArea();
    private final JTextField input = new JTextField();
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final Socket socket = new Socket();
    private volatile BufferedWriter writer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientMain().open());
    }

    private void open() {
        String nickname = JOptionPane.showInputDialog(null, "닉네임 (1~20자)");
        if (nickname == null) { sender.shutdown(); return; }
        nickname = new Message(MessageType.HELLO, nickname).get(0).strip();
        if (nickname.isEmpty() || nickname.length() > 20) { sender.shutdown(); return; }
        JFrame frame = new JFrame("분식왕 채팅 · " + nickname);
        history.setEditable(false);
        frame.add(new JScrollPane(history), BorderLayout.CENTER);
        frame.add(input, BorderLayout.SOUTH);
        input.setEnabled(false);
        input.addActionListener(event -> {
            try {
                Message request = ChatMessages.request(input.getText());
                sender.execute(() -> {
                    try { send(request); }
                    catch (IOException e) { append("전송 실패: " + e.getMessage()); close(); }
                });
                input.setText("");
            } catch (IllegalArgumentException e) { append("본문은 1~200자로 입력하세요."); }
        });
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { close(); }
        });
        frame.setSize(480, 360);
        frame.setVisible(true);
        String name = nickname;
        new Thread(() -> connect(name), "chat-reader").start();
    }

    private void connect(String nickname) {
        try {
            AppConfig config = AppConfig.load();
            socket.connect(new InetSocketAddress(config.serverHost(), config.serverPort()), config.connectTimeoutMs());
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            send(new Message(MessageType.HELLO, nickname, "VERSUS"));
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Message message = Message.parse(line);
                    switch (message.type()) {
                        case WELCOME -> SwingUtilities.invokeLater(() -> {
                            input.setEnabled(true); input.requestFocusInWindow();
                        });
                        case CHAT -> append(ChatMessages.display(message));
                        case TOAST, REJECT -> append(message.get(0));
                        default -> { }
                    }
                }
            }
            append("서버 연결이 종료되었습니다.");
        } catch (IOException | IllegalArgumentException e) { append("연결 오류: " + e.getMessage()); }
        finally { close(); }
    }

    private synchronized void send(Message message) throws IOException {
        writer.write(message.encode()); writer.newLine(); writer.flush();
    }

    private void append(String text) {
        SwingUtilities.invokeLater(() -> {
            history.append(text + "\n"); history.setCaretPosition(history.getDocument().getLength());
        });
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) { }
        sender.shutdownNow();
        SwingUtilities.invokeLater(() -> input.setEnabled(false));
    }
}
