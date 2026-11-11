package bunsik.common;

/** UTF-8 줄 단위 통신에서 사용하는 불변 메시지. 줄바꿈 자체는 송신자가 붙인다. */
public final class Message {
    private final MessageType type;
    private final String[] values;

    public Message(MessageType type, Object... values) {
        if (type == null || values == null) {
            throw new IllegalArgumentException("Message type and values must not be null");
        }
        this.type = type;
        this.values = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new IllegalArgumentException("Message value must not be null: " + i);
            }
            this.values[i] = values[i].toString().replace('|', ' ')
                    .replace('\r', ' ').replace('\n', ' ');
        }
    }

    public static Message parse(String line) {
        if (line == null || line.trim().isEmpty()
                || line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Expected one non-empty message line");
        }
        String[] parts = line.split("\\|", -1);
        MessageType type;
        try {
            type = MessageType.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown message type: " + parts[0], e);
        }
        Object[] values = new Object[parts.length - 1];
        System.arraycopy(parts, 1, values, 0, values.length);
        return new Message(type, values);
    }

    public String encode() {
        StringBuilder line = new StringBuilder(type.name());
        for (String value : values) {
            line.append('|').append(value);
        }
        return line.toString();
    }

    public MessageType type() { return type; }
    public int size() { return values.length; }

    public String get(int i) {
        if (i < 0 || i >= values.length) {
            throw new IllegalArgumentException("Missing message value: " + i);
        }
        return values[i];
    }

    public int getInt(int i) { return Integer.parseInt(get(i)); }
    public long getLong(int i) { return Long.parseLong(get(i)); }

    public boolean getBool(int i) {
        String value = get(i);
        if ("true".equals(value)) { return true; }
        if ("false".equals(value)) { return false; }
        throw new IllegalArgumentException("Expected true or false at value: " + i);
    }
}
