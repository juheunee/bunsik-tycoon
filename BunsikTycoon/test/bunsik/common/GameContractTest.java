package bunsik.common;

public final class GameContractTest {
    public static void main(String[] args) {
        String line = "STATE|0|128000|214|7|6|84|300|296|true|TTEOKBOKKI|-|0|0|"
                + "WAITING_FOOD:TTEOKBOKKI:520:0:3,EATING:GIMBAP:-1:0:8,DIRTY:-:-1:41:0,WAITING_ORDER:-:820:0:13|900:5,250:16";
        var state = StoreSnapshot.fromMessage(Message.parse(line));
        check(line.equals(state.toMessage().encode()));
        check(StoreSnapshot.empty().equals(StoreSnapshot.fromMessage(StoreSnapshot.empty().toMessage())));
        check(Menu.values().length == 5 && Menu.TWIGIM.cookTimeMs == 6000);
        for (String invalid : new String[]{line.replace("|true|", "|yes|"),
                line.replace(":520:", ":1001:"), line + "|extra", line.replace("TTEOKBOKKI", "UNKNOWN"),
                line.substring(0, line.lastIndexOf('|')) + "|900:5:0"}) {
            try { StoreSnapshot.fromMessage(Message.parse(invalid)); throw new AssertionError(invalid); }
            catch (IllegalArgumentException expected) { }
        }
        check(StoreLayout.clampX(-100) == StoreLayout.PLAYER_RADIUS);
        check(StoreLayout.clampY(10000) < StoreLayout.HEIGHT);
        System.out.println("GameContractTest: PASS");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
