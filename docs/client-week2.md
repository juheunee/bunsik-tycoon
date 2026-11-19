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
- A5: 메뉴별 표시 앞에서 SPACE를 누르면 기존 COOK을 전송한다. 식당 화면에서 서버 조리 상태를 표시하고, 이동을 유지한다. B3 완료 시 보유 음식은 서버가 갱신한다. 지정 표시 앞 SPACE로 폐기하고, 결과 화면에서 새 접속 또는 타이틀 복귀를 선택한다.
- 참고 이미지: `docs/screenshots/` 9장. 그림은 Sprites.java에서 직접 그렸으며 새 외부 이미지는 사용하지 않았다.
- 참고 이미지에 맞춰 StoreLayout에 오른쪽 부엌·테이블 2×2·캐릭터 시작 위치를 정의했다.
- 대기실·2P 화면·H 숨기기는 같은 스타일로 미리 볼 수 있으며 기존 LOBBY/STATE/READY 형식을 처리한다.

상단 **화면 미리보기** 메뉴에서 시작·식당·카운트다운·부엌·조리 중·완료·결과·대기실·2P를 선택한다. 조리 화면 미리보기는 표시용 스냅샷을 사용한다.

## 검증과 남은 의존 사항

`verify-game.cmd`는 Java 21 대상 빌드와 기존 채팅·STATE·NetworkClient·화면 검사를 실행한다. 화면 검사 이미지는 `out/screens/`에 생성한다. 그래픽 환경의 추가 검사 명령은 `java -cp out bunsik.client.SessionScreenTest`다.

B3는 GitHub main에 병합됐다. 당시 서버는 한 번에 한 음식만 조리하고, 완성하면 자동으로 플레이어에게 전달했다.

## 키보드 조리 서버 계약

클라이언트와 서버는 `StoreLayout`의 조리대·폐기 위치·설거지통 좌표와 상호작용 범위를 사용한다. 메시지 종류는 기존 `COOK`, `INTERACT`, `COOK_DONE`을 사용한다. 양손과 조리대별 상태 전송 형식은 [README 7-4](../README.md#7-4-통신-규칙)를 따른다.

실제 서버에서 떡볶이와 순대를 각각 시작해 두 조리가 함께 진행되는지 확인한다. 완성 음식을 두 손에 집고 각각의 주문 테이블에 서빙한다. 그릇이 남은 테이블은 기다리던 손님이 앉을 수 없고, 그릇을 집으면 바로 착석할 수 있다. 설거지통에서는 양손의 그릇을 한 번에 내려놓는다.
