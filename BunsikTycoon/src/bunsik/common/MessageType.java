package bunsik.common;

/** README 7-4와 SPEC 4에 정의된 메시지 종류. 값 순서는 해당 문서를 따른다. */
public enum MessageType {
    HELLO, READY, MOVE, INTERACT, COOK, LEAVE,
    WELCOME, REJECT, LOBBY, START, COUNTDOWN, STATE,
    OPEN_KITCHEN, COOK_DONE, TOAST, GAME_OVER, CHAT
}
