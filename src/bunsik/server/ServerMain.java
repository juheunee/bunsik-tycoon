package bunsik.server;

import bunsik.common.AppConfig;

import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * 서버 실행 시작점. 화면(GUI) 없이 콘솔(프롬프트)에서 돈다.
 *
 * <p>사용법: {@code java -cp out bunsik.server.ServerMain [포트]}
 * (포트를 안 주면 config.properties의 server.port, 그것도 없으면 5000)
 */
public final class ServerMain {

    private ServerMain() {
    }

    public static void main(String[] args) {
        AppConfig cfg = AppConfig.load();
        int port = cfg.getInt("server.port", 5000);
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("포트는 숫자여야 해요: " + args[0]);
                return;
            }
        }

        System.out.println("==============================================");
        System.out.println("   도전! 분식왕  -  게임 서버");
        System.out.println("==============================================");
        System.out.println(" 이 노트북에서 접속:   localhost : " + port);
        System.out.println(" 다른 노트북에서 접속: 아래 IP 중 하나 : " + port);
        printLocalAddresses();
        System.out.println(" 종료: Ctrl + C");
        System.out.println("----------------------------------------------");

        try {
            new GameServer(port).run();
        } catch (BindException e) {
            System.out.println("포트 " + port + "이(가) 이미 사용 중이에요. 서버가 이미 켜져 있는지 확인하거나 다른 포트로 실행하세요.");
        } catch (Exception e) {
            System.out.println("서버 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 같은 와이파이의 다른 노트북이 입력할 IP 후보를 보여 준다 */
    private static void printLocalAddresses() {
        try {
            boolean found = false;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address) {
                        System.out.println("     - " + addr.getHostAddress() + "   (" + ni.getDisplayName() + ")");
                        found = true;
                    }
                }
            }
            if (!found) {
                System.out.println("     - (네트워크 연결을 찾지 못했어요)");
            }
        } catch (Exception e) {
            System.out.println("     - (IP 확인 실패: " + e.getMessage() + ")");
        }
    }
}
