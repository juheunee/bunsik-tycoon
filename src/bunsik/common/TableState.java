package bunsik.common;

/**
 * 테이블 한 개가 거치는 상태. 손님 한 명의 일생이 이 순서로 흘러간다.
 *
 * <pre>
 * EMPTY → WAITING_ORDER → WAITING_FOOD → EATING → DIRTY → (청소) → EMPTY
 *            └────────── 인내심 0: 화내며 떠남 ──────────┘ → EMPTY
 * </pre>
 */
public enum TableState {
    /** 비어 있음. 입구에서 기다리던 손님이 앉을 수 있다. */
    EMPTY,
    /** 손님이 앉아 주문을 받아 주길 기다림. (스페이스 → 주문 받기) */
    WAITING_ORDER,
    /** 주문 완료, 음식을 기다림. (주문한 음식을 들고 스페이스 → 서빙) */
    WAITING_FOOD,
    /** 식사 중. 인내심 바가 사라지고 일정 시간 뒤 떠난다. */
    EATING,
    /** 빈 그릇과 골드가 남아 있음. 다른 손님이 앉을 수 없다. (스페이스 → 청소 + 골드 획득) */
    DIRTY
}
