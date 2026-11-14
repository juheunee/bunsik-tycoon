package bunsik.common;

public enum Menu {
    TTEOKBOKKI("떡볶이", 4000, 30), GIMBAP("김밥", 3000, 25),
    SUNDAE("순대", 5000, 35), TWIGIM("튀김", 6000, 40), EOMUK("어묵", 3000, 20);

    public final String label;
    public final int cookTimeMs;
    public final int price;

    Menu(String label, int cookTimeMs, int price) {
        this.label = label;
        this.cookTimeMs = cookTimeMs;
        this.price = price;
    }
}
