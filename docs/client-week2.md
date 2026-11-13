# 2주차 클라이언트 구현 (A3·A4·A5)

기준 일정: [Notion 분식왕 개발 단계 및 일정](https://app.notion.com/p/415dbdc4ceb2815b8212c65bc7567a76), 2026-11-11~11-17.
사용자 확인에 따라 기존 README·SPEC 게임 계약을 사용한다.

## 구현과 실행

JDK 21 이상을 PATH에 설정하고 저장소 루트에서 실행한다.

```powershell
.\verify-game.cmd
java -cp out bunsik.client.ClientMain
java -cp out bunsik.client.ClientMain --preview
```

- A3: 메뉴 5종·밸런스·좌표·테이블 상태·불변 StoreSnapshot, STATE 왕복 변환.
- A4: 닉네임/IP/포트/모드 입력, 비동기 소켓 통신, 식당 렌더링, 이동·경계·안내,
  화면 크기에 따른 확대/축소, 포커스를 잃으면 이동 키 해제.
- A5: 메뉴 카드를 중앙 접시로 드래그하면 COOK 전송. 응답 대기/조리 중 중복 요청과 ESC 잠금.
  서버 STATE의 조리 시간만 표시하며 자체 영업 계산을 하지 않음. 완료 음식 표시·ESC 복귀·SPACE 버리기,
  GAME_OVER 결과·다시 하기(새 접속)·처음 화면 복귀.
- 참고 이미지: `docs/screenshots/` 9장. 그림은 Sprites.java에서 직접 그렸으며 새 외부 이미지는 사용하지 않았다.
- 참고 이미지에 맞춰 StoreLayout에 오른쪽 부엌·테이블 2×2·캐릭터 시작 위치를 정의했다.
- 대기실·2P 화면·H 숨기기는 같은 스타일로 미리 볼 수 있으며 기존 LOBBY/STATE/READY 형식을 처리한다.

상단 **화면 미리보기** 메뉴에서 시작·식당·카운트다운·부엌·조리 중·완료·결과·대기실·2P를 선택한다.
미리보기의 SPACE는 위치에 관계없이 부엌을 연다. 메뉴 드롭 시 화면 확인용 조리 타이머만 실행한다.
실제 접속에서는 OPEN_KITCHEN·STATE·COOK_DONE·GAME_OVER를 서버에서 받아야 한다.

## 검증과 남은 의존 사항

`verify-game.cmd`는 기반 검사, 기존 독립 채팅 소켓 검사, STATE 계약 검사,
새 NetworkClient의 실제 루프백 소켓 검사(UTF-8·명령 순서·EDT·EOF), 화면 렌더링/드롭/잠금 검사를 실행한다.
화면 검증 이미지는 `out/screens/`에 생성하며 커밋하지 않는다.

현재 GameServer/ClientHandler는 HELLO/WELCOME/LEAVE까지만 처리한다.
B2(Store 영업 규칙)·B3(GameRoom 루프)가 미구현이므로 실제 3분 한 판, 점수·서빙·음식 버리기·재경기는
아직 검증할 수 없다. 서버를 독립 채팅 서버로 대체해 영업이 구현됐다고 표시하지 않는다.
클라이언트 명령은 MOVE(x,y,왼쪽보기), INTERACT(x,y), COOK(메뉴코드)를 사용한다.
실제 서버와 두 노트북 검증은 B2·B3 병합 후 수행한다.
