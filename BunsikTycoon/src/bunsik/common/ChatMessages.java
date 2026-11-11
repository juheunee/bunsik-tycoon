package bunsik.common;

/** CHAT의 방향별 필드 검사와 생성을 한곳에서 처리한다. */
public final class ChatMessages {
    public static final int MAX_TEXT_LENGTH = 200;

    private ChatMessages() { }

    public static Message request(String text) {
        return new Message(MessageType.CHAT, checkedText(text));
    }

    public static String requestText(Message message) {
        requireSize(message, 1);
        return checkedText(message.get(0));
    }

    public static Message broadcast(int id, String nickname, Message request) {
        if (id < 0 || nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Invalid chat sender");
        }
        return new Message(MessageType.CHAT, id, nickname, requestText(request));
    }

    public static String display(Message message) {
        requireSize(message, 3);
        if (message.getInt(0) < 0 || message.get(1).isBlank()) {
            throw new IllegalArgumentException("Invalid chat sender");
        }
        return "[" + message.get(1) + "] " + checkedText(message.get(2));
    }

    private static void requireSize(Message message, int size) {
        if (message == null || message.type() != MessageType.CHAT || message.size() != size) {
            throw new IllegalArgumentException("Invalid CHAT fields");
        }
    }

    private static String checkedText(String text) {
        if (text == null) { throw new IllegalArgumentException("Missing chat text"); }
        String clean = new Message(MessageType.CHAT, text).get(0).strip();
        if (clean.isEmpty() || clean.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Chat text must contain 1..200 UTF-16 units");
        }
        return clean;
    }
}
