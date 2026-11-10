package bunsik.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** 서버 테스트용 콘솔 클라이언트 */
public final class ConsoleClient {

    private static volatile boolean showState;

    private ConsoleClient() {
    }

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        System.out.print("서버 IP (Enter: localhost) > ");
        String host = scanner.nextLine().trim();
        System.out.print("포트 (Enter: 5000) > ");
        String portText = scanner.nextLine().trim();
        int port = portText.isEmpty() ? 5000 : Integer.parseInt(portText);

        try (Socket socket = new Socket(host.isEmpty() ? "localhost" : host, port)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            System.out.println("접속 완료. 보낼 메시지 입력 후 Enter (STATE 보기 켜기/끄기: /state, 종료: quit)");

            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (showState || !line.startsWith("STATE")) {
                            System.out.println("◀ " + line);
                        }
                    }
                } catch (IOException ignored) {
                }
                System.out.println("서버 연결 종료");
            });
            reader.setDaemon(true);
            reader.start();

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.equals("quit")) {
                    break;
                }
                if (line.equals("/state")) {
                    showState = !showState;
                    continue;
                }
                out.write(line);
                out.newLine();
                out.flush();
            }
        }
    }
}