# A3 공통 게임 계약

기준: README 7-4·SPEC 기존 계약, 사용자 확인으로 합의 완료(2026-11-13).
MessageType과 STATE 필드 순서는 변경하지 않는다.

- 메뉴·가격·조리 시간: `common/Menu.java`
- 서버 계산에 사용할 밸런스: `GameConfig.java`
- 논리 화면 640×480, 테이블 4개·대기줄 3개·부엌·시작 위치: `StoreLayout.java`
- 테이블 상태: EMPTY → WAITING_ORDER → WAITING_FOOD → EATING → DIRTY → EMPTY
- 불변 상태와 STATE 변환: `StoreSnapshot.toMessage()/fromMessage()`
- 음식 없음은 `-`, 빈 대기줄은 `-`, 테이블은 항상 인덱스 순서대로 4개.
- 인내심은 0~1000, 식사/빈 테이블/청소 상태처럼 인내심이 적용되지 않을 때 -1.
- 만족도는 0~100, 아직 계산 전이면 -1. 조리 시간은 ms, 메뉴 없음은 null로 표현.
- 화면은 서버 STATE를 표시하며 조리·점수·손님 생성은 클라이언트가 계산하지 않는다.

B2·B3 연동: Store에서 위 스냅샷을 생성하고 게임 루프가 전송한다. MOVE(x,y,왼쪽보기),
INTERACT(x,y), COOK(메뉴), HELLO(닉네임,SOLO), LEAVE는 기존 메시지를 사용한다.
검증은 `GameContractTest`의 README 예시 왕복·빈 식당·잘못된 필드 검사로 수행한다.
