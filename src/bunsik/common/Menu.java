package bunsik.common;

/**
 * 분식집 메뉴. 서버(조리 시간·가격 계산)와 클라이언트(화면 표시)가 같은 정의를 쓰도록 공통 패키지에 둔다.
 */
public enum Menu {

    TTEOKBOKKI("떡볶이", 4000, 30),
    GIMBAP("김밥", 3000, 25),
    SUNDAE("순대", 5000, 35),
    TWIGIM("튀김", 6000, 40),
    EOMUK("어묵", 3000, 20);

    /** 화면에 보이는 이름 */
    public final String label;
    /** 접시에 놓은 뒤 완성까지 걸리는 시간(밀리초) */
    public final int cookMs;
    /** 손님이 내는 기본 골드(여기에 팁이 더해짐) */
    public final int price;

    Menu(String label, int cookMs, int price) {
        this.label = label;
        this.cookMs = cookMs;
        this.price = price;
    }

    /** 메시지에 실을 코드. 없으면 "-" */
    public static String code(Menu m) {
        return m == null ? "-" : m.name();
    }

    /** 메시지의 코드를 메뉴로. "-" 이면 null */
    public static Menu fromCode(String code) {
        if (code == null || code.isBlank() || code.equals("-")) {
            return null;
        }
        return Menu.valueOf(code.trim());
    }

    /** "떡볶이를", "김밥을"처럼 받침에 맞는 조사를 붙인다. */
    public String withObjectParticle() {
        return label + (hasFinalConsonant(label) ? "을" : "를");
    }

    private static boolean hasFinalConsonant(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return false;
        }
        return (last - 0xAC00) % 28 != 0;
    }
}
