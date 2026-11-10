package bunsik.server;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 서버 관리자 창 */
public final class ServerFrame extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String DEFAULT_PORT = "5000";

    private final JTextField portField = new JTextField(DEFAULT_PORT, 6);
    private final JButton startButton = new JButton("서버 시작");
    private final JLabel countLabel = new JLabel("접속자 0명");
    private final JTextArea logArea = new JTextArea();
    private GameServer server;

    public ServerFrame() {
        super("도전! 분식왕 - 서버");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (server != null) {
                    server.shutdown();
                }
                dispose();
                System.exit(0);
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.add(new JLabel("포트"));
        top.add(portField);
        top.add(startButton);
        top.add(countLabel);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));

        JLabel ipLabel = new JLabel("다른 노트북에서 입력할 IP: " + String.join(", ", localAddresses()));
        ipLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(ipLabel, BorderLayout.SOUTH);
        setSize(620, 480);
        setLocationRelativeTo(null);

        startButton.addActionListener(e -> startServer());
        portField.addActionListener(e -> startServer());
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            log("포트는 숫자로 입력");
            return;
        }
        try {
            server = new GameServer(this, port);
            server.start();
        } catch (IOException e) {
            server = null;
            log("서버 시작 실패: 포트 " + port + " 사용 중 여부 확인 (" + e.getMessage() + ")");
            return;
        }
        startButton.setEnabled(false);
        portField.setEnabled(false);
        startButton.setText("실행 중");
    }

    /** 로그 한 줄 추가 */
    public void log(String text) {
        String line = "[" + LocalTime.now().format(TIME) + "] " + text + "\n";
        SwingUtilities.invokeLater(() -> {
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** 접속자 수 표시 갱신 */
    public void setClientCount(int count) {
        SwingUtilities.invokeLater(() -> countLabel.setText("접속자 " + count + "명"));
    }

    private static List<String> localAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address) {
                        result.add(addr.getHostAddress());
                    }
                }
            }
        } catch (IOException e) {
            result.add("확인 실패");
        }
        if (result.isEmpty()) {
            result.add("네트워크 연결 없음");
        }
        return result;
    }
}