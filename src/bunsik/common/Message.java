package bunsik.common;

import java.util.Arrays;

/**
 * 메시지 한 줄을 만들고(encode) 해석하는(parse) 전담 클래스.
 *
 * <p>메시지 형식을 만지는 코드를 이 클래스 한 곳에 모아 두면, 나중에 형식을 바꿀 때 여기만 고치면 된다.
 */
public final class Message {

    /** 칸 구분자. 정규식에서 특수문자이므로 split 할 때는 "\\|" 로 쓴다. */
    public static final String SEP = "|";

    private final MessageType type;
    private final String[] args;

    public Message(MessageType type, Object... args) {
        this.type = type;
        this.args = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            this.args[i] = clean(String.valueOf(args[i]));
        }
    }

    private Message(MessageType type, String[] args, boolean alreadyClean) {
        this.type = type;
        this.args = args;
    }

    /**
     * 받은 한 줄을 메시지로 해석한다.
     *
     * @throws IllegalArgumentException 모르는 종류이거나 빈 줄일 때. 호출한 쪽은 이 예외를 잡아 무시해야
     *                                  이상한 메시지 하나 때문에 프로그램이 멈추지 않는다.
     */
    public static Message parse(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("빈 메시지");
        }
        String[] parts = line.split("\\|", -1);
        MessageType type = MessageType.valueOf(parts[0].trim());
        return new Message(type, Arrays.copyOfRange(parts, 1, parts.length), true);
    }

    /** 네트워크로 보낼 한 줄 문자열로 만든다(줄바꿈은 보내는 쪽에서 붙인다). */
    public String encode() {
        StringBuilder sb = new StringBuilder(type.name());
        for (String a : args) {
            sb.append(SEP).append(a);
        }
        return sb.toString();
    }

    /** 값 안에 구분자나 줄바꿈이 섞이면 메시지가 깨지므로 다른 글자로 바꾼다. */
    public static String clean(String s) {
        return s.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    public MessageType type() {
        return type;
    }

    public int size() {
        return args.length;
    }

    public String get(int i) {
        if (i < 0 || i >= args.length) {
            throw new IllegalArgumentException(type + " 메시지에 " + i + "번째 값이 없음");
        }
        return args[i];
    }

    public int getInt(int i) {
        try {
            return Integer.parseInt(get(i).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(type + " 메시지의 " + i + "번째 값이 숫자가 아님: " + get(i));
        }
    }

    public long getLong(int i) {
        try {
            return Long.parseLong(get(i).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(type + " 메시지의 " + i + "번째 값이 숫자가 아님: " + get(i));
        }
    }

    public boolean getBool(int i) {
        return Boolean.parseBoolean(get(i).trim());
    }

    @Override
    public String toString() {
        return encode();
    }
}
