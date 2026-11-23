# A7 게임 채팅 설계·검증 계획

기준: 2026-11-23, `feat/jh/in-game-chat` 생성 후 B5가 병합된 `main` ec5dc29를 반영. 아래 설계는 구현 전 판단을 기록한다. A7 구현 후 검증 결과는 6절에 별도로 기록한다. **확인**은 기준 시점의 병합 코드/PR 상태, **제안**은 당시 미합의 동작을 뜻한다.

## 1. 확인한 현재 상태와 B5 의존성

- **확인:** [개발 일정](https://app.notion.com/p/415dbdc4ceb2815b8212c65bc7567a76)의 A7 선행 조건은 B4·A6이다. [B4 PR #25](https://github.com/juheunee/bunsik-tycoon/pull/25)와 [A6 PR #26](https://github.com/juheunee/bunsik-tycoon/pull/26)은 병합됐다. 현재 코드는 2P 대기실, 같은 소켓의 경기·결과·대기실 복귀, 분할 화면을 가진다. 두 노트북과 실제 IME/키보드 검증은 아직 완료 근거가 없다.
- **확인:** [B5 이슈 #8](https://github.com/juheunee/bunsik-tycoon/issues/8)은 `B5 먼저 머지 후 A7 진행`을 적지만, 일정표 자체에는 B5가 A7 선행 조건으로 적혀 있지 않다. [B5 PR #28](https://github.com/juheunee/bunsik-tycoon/pull/28)은 **ec5dc29로 병합**됐다. 병합 코드에는 `NetworkClient` 하트비트/타임아웃, `GameFrame`·`GamePanel` 정지 UI와 카운트다운 중 결과, `ClientHandler`의 HELLO 검증, `GameRoom`의 상대 이탈 처리, `GameServer`의 종료·PING이 있다. PR은 `VersusLobby`를 수정하지 않았다. 자동 재접속은 일정 범위 밖이다.
- **확인:** `MessageType.CHAT`, `ChatMessages`, `Message`의 UTF-8 한 줄 규약은 이미 있다. 게임 `ClientHandler`에는 CHAT 분기가 없어 대기실 CHAT은 로그만 남고, 게임 CHAT은 `GameRoom`의 `Store.handle`로 흘러가 처리되지 않는다. 독립 `ChatServerMain`은 모든 접속자에게, 본인 포함, 방송하며 게임방 격리가 없다. 따라서 채팅 예제의 통과는 게임 채팅의 검증이 아니다.
- **확인:** `GameServer`는 연결마다 고유 ID를 발급하고 `ClientHandler`가 닉네임·`room`·`slot`을 갖는다. 2P 대기실 소속은 별도로 `VersusLobby.members`에 있다. 경기 시작 때 `GameRoom.start`가 `joinRoom`, 종료 때 `leaveRoom`을 호출한다. 2P 결과 후 같은 소켓으로 `LOBBY`가 전송되고 재경기할 수 있다. B5 코드에서는 영업 중 상대 이탈 시 정지·안내 후 3초 뒤 남은 사람의 승리로 끝나며, 카운트다운 중 이탈은 바로 결과를 보낸다. `GameFrame.session`은 **새 연결**의 오래된 EDT 콜백은 막지만 **같은 연결의 이전 경기/방**은 식별하지 않는다.
- **판단:** B5 전체 완료가 채팅 화면·입력 설계의 기술적 선행 조건은 아니었다. B4·A6만으로 `ChatPanel` 구조, 입력 제어, `ChatMessages` 재사용, 라우팅 계약을 준비할 수 있었다. 현재는 B5 병합 순서가 충족됐으므로 공유 파일 통합을 시작할 수 있다. 다만 방/종료 경계 계약과 수정 구간은 차민과 먼저 합의한다.

| 구분 | 진행 범위 | 근거/조건 |
|---|---|---|
| B4·A6만으로 가능 | 이 문서, `ChatPanel` 화면·입력 설계와 독립 단위 테스트 설계; 합의 후 별도 컴포넌트 구현 가능 | 기존 CHAT 규약과 2P 화면·대기실 코드가 병합됨. 이 표는 구현 전 의존성 판단 |
| B5 계약 합의 필요 | 방 나가기/이탈/재접속 정책, 게임 종료 전후 CHAT 허용 시점, 대기실·경기 동일 대화 범위, `PAUSE` 중 채팅, 수신/전송 순서 | B5 PR이 연결 종료와 경기 종료 이벤트를 바꿈. 이슈의 제안과 최종 병합본은 다를 수 있음 |
| B5 병합 후 통합·검증 | 공유 파일 수정, 종료·하트비트·일시정지 중 CHAT 경합, 카운트다운 이탈, 최종 회귀 테스트 | PR #28이 병합돼 코드 기준점이 확정됨. 채팅 계약 합의 후 진행 |

참고 코드: `common/{Message,MessageType,ChatMessages}.java`, `chat/{ChatServerMain,ChatClientMain}.java`, `client/{NetworkClient,JavaChatClientView}.java`, `client/ui/{GameFrame,GamePanel,LobbyPanel,ResultPanel}.java`, `server/{ClientHandler,GameServer,VersusLobby,GameRoom}.java`. 참고 문서: `AGENTS.md`, `README.md` 7-4·9-2, `SPEC.md` 4.3, `docs/client-week2.md`, 위 일정과 PR.

## 2. 채팅 동작 및 화면 설계

### 기존 구조와 재사용

- **확인:** `ChatMessages.request`는 한 필드 요청을 만들고 정규화·길이를 검사한다. `requestText`는 서버의 방향별 필드 검사, `broadcast`는 서버가 ID·닉네임을 붙인 3필드 응답, `display`는 표시 문자열을 만든다. `Message`는 `|`·CR·LF를 공백으로 치환한다. SPEC 4.3은 1~200 UTF-16 단위, 공백만 있는 본문 거절, 길이 초과 거절을 정한다. 콜론·쉼표·한글은 허용한다.
- **확인:** 기존 `ChatClientMain`은 별도 소켓·수신 스레드·창을 만들고 수신 시 `invokeLater`로 표시한다. 수업 예제 `JavaChatClientView`는 `readUTF/writeUTF`와 자체 스레드/소켓을 사용해 현 게임의 줄 단위 `Message`와 호환되지 않는다.
- **제안:** 게임에서는 `NetworkClient` 한 연결의 단일 송신 큐·수신 스레드와 `GameFrame.onMessage`를 그대로 사용한다. CHAT 전용 소켓이나 수신 스레드는 만들지 않는다. `NetworkClient.send`는 송신 순서를 보존하고 `ClientHandler.send`는 writer 동기화로 한 줄의 바이트가 섞이지 않게 한다. 다만 여러 서버 스레드의 **의미적 이벤트 순서**는 별도 보장되지 않으므로 방 경계는 서버에서 직렬화해야 한다.

### ChatPanel과 포커스

- **제안:** `ui/ChatPanel` 하나가 기록 보기(`JScrollPane`/읽기 전용 텍스트)와 입력(`JTextField`)을 소유한다. `GameFrame`이 현재 연결의 대화 모델을 소유하고 동일 컴포넌트를 `LobbyPanel`과 `GamePanel`의 채팅 영역에 재배치하거나, 모델을 공유한 두 뷰를 사용한다. 하나의 스크롤 위치를 유지하려면 단일 컴포넌트가 간단하다. 게임의 1200×700 논리 좌표에서 식당 화면을 가리지 않도록 하단/측면의 제한된 영역을 정하고, `UiKit.bounds`·리사이즈 동작에 맞춘다. 상대 식당 H 숨기기는 채팅 패널의 가시성·크기를 바꾸지 않는다. 대기실 버튼/참가자 정보도 가리지 않는 배치를 실제 화면에서 확인한다.
- **제안:** 게임에서는 `Enter` 최초 **pressed**에만 입력 시작. 게임 `WHEN_IN_FOCUSED_WINDOW` 바인딩은 `JTextField` 포커스 중에도 살아 있으므로, 키 바인딩을 게임 포커스 전용으로 좁히거나 `chatEditing` 가드로 WASD·방향키·SPACE·H·P·ESC의 게임 액션을 막는다. `GamePanel.clearInput()`을 포커스 이동 **전에** 호출해 누른 이동 키와 `spaceDown`·`hDown`을 지운다. 전송/취소 뒤 게임 조작 영역에 포커스를 요청하되 창 전환·일시정지·종료 상태면 강제로 빼앗지 않는다. 수신 CHAT은 기록만 갱신하며 포커스를 변경하지 않는다.
- **제안:** Enter를 연 키와 전송 키로 분리한다. 게임 영역에서 Enter를 처리한 뒤 키 release를 보기 전에는 입력 필드의 전송 액션을 허용하지 않는다. held/repeat 가드와 `JTextField`의 action event 한 번만 전송을 사용한다. IME 조합 중 Enter는 조합 확정에 맡기고 확정 문자가 남아 있는지 검사한 뒤 전송한다. `KeyListener`로 문자를 가로채지 않는다. 실제 OS/한글 입력기에서 조합 확정과 전송이 겹치는지 반드시 수동 확인한다.
- **제안:** 게임에서 Enter는 유효한 텍스트를 `ChatMessages.request`로 만들고 `NetworkClient.send`에 한 번 넣은 뒤 입력을 비우고 게임 포커스로 복귀한다. 빈 문자열/공백만 있으면 전송하지 않고 입력 유지. Esc는 텍스트를 버리고 게임 포커스 복귀. 대기실은 전송 후 입력 필드에 포커스를 유지해 다음 메시지를 쓸 수 있게 한다. 대기실에선 게임 키 차단 대신 `READY` 버튼의 기본 버튼/Enter 동작이 입력을 가로채지 않는지 점검한다.
- **제안:** 입력 중 결과/타이틀/연결 종료가 오면 입력을 닫고 전송을 막는다. `PAUSE` 중 입력을 허용할지는 차민과 결정하되 게임 조작은 정지 상태를 따른다. 결과 화면 채팅은 일정에 없으므로 **지원하지 않는 안**을 권장한다. 결과 화면에는 이전 기록을 보여줄 필요가 있는지 별도 결정이 필요하다.

### 기록 수명과 스크롤

- **제안:** 2P 대기실 → 카운트다운 → 경기 → 결과 → **같은 대기실** 복귀에는 기록을 유지한다. 게임 중 대화가 경기 결과와 함께 사라지지 않고 같은 연결/대기실이 유지되기 때문이다. 새로운 연결·방 입장 또는 타이틀로 나갈 때 기록과 미완성 입력을 지운다. 1P에는 상대가 없으므로 이번 A7에서 채팅을 제공하지 않는 안을 권장한다.
- **제안:** 사용자가 맨 아래를 보고 있을 때만 새 메시지 도착 시 자동으로 아래로 스크롤한다. 과거 기록을 읽는 중이면 현재 위치를 유지한다. 기록 보유량 상한은 UI 메모리 정책이 필요할 때만 정하고, 통신 규약에는 추가하지 않는다.
- **미결정:** 현 B4에는 명시적 방 ID가 없고 `VersusLobby` 하나만 있다. 테스트의 ‘서로 다른 두 방’은 현 코드에서 동시에 만들 수 없다. 실제 다중 방 지원을 A7 범위로 늘리지 않고, 라우팅을 참가자 집합으로 추상화해 격리 단위 테스트를 설계한다. 같은 대기실의 연속 경기 기록을 어디서 끊을지와 새 방 개념은 차민과 확인한다.

## 3. 통신 계약과 서버 라우팅

- **확인된 형식:** C→S `CHAT|본문`, S→C `CHAT|접속번호|닉네임|본문`. `ChatMessages`와 SPEC 4.3으로 표현 가능하므로 새 메시지 종류나 필드는 기본안에서 필요 없다. `ChatMessages.requestText`가 요청 1필드, `broadcast`가 서버의 발신자 ID·닉네임을 강제한다. 잘못된 요청은 해당 발신자에게 `TOAST`로 안내하고 연결은 유지하는 기존 SPEC 정책을 따른다.
- **제안:** `ClientHandler`가 수신한 CHAT을 게임 상태 명령과 분리해 서버가 확인한 `VersusLobby.contains(handler)` 또는 활성 `GameRoom` 참가자/연결 상태에 따라 라우팅한다. 클라이언트가 보낸 방 번호·닉네임·ID는 쓰지 않는다. 방 미소속/HELLO 전/닫힌 연결에는 전달하지 않는다. 대기실에서 게임으로 전환하는 동안 같은 참가자 집합을 유지하되 순서를 직렬화한다. 경기 중 CHAT은 `Store.handle`에 넣지 않으며 게임 점수·시간을 바꾸지 않는다. `GameRoom`의 게임 상태 변경은 계속 루프 스레드에서만 한다.
- **제안:** 서버는 **발신자 포함** 같은 방 참가자에게만 CHAT 응답을 보낸다. 클라이언트는 전송 직후 로컬 기록에 추가하지 않고 서버 echo 수신 때 한 번 표시한다. 그러면 서버가 거절한 요청도 성공처럼 보이지 않고 중복도 없다. 본인 메시지 강조가 필요하면 `WELCOME` ID와 응답 ID를 비교하되 형식은 바꾸지 않는다.
- **제안:** `ClientHandler.send`의 `synchronized(out)`를 재사용해 STATE/PING/CHAT 각 줄의 물리적 혼합을 방지한다. `NetworkClient`는 같은 sender 큐를 재사용한다. `GameFrame.onMessage`의 CHAT 처리는 EDT에서 화면과 현재 연결/방 상태를 확인한다. B5의 PING은 기존처럼 `GameFrame`의 기본 분기에서 화면 처리 없이 넘긴다. `COUNTDOWN`·`STATE`와 CHAT이 섞여도 phase 전환을 CHAT이 건드리지 않는다.
- **제안:** 서버가 방 나가기/이탈을 처리한 시점을 경계로 이후 CHAT 요청은 거절/무시한다. 전송과 LEAVE가 같은 클라이언트 큐에 있으면 전송 순서는 보존되지만, 서버가 다른 수신 스레드에서 상대 이탈을 처리하는 경우 경계 순서는 별도 합의가 필요하다. **이전 방/경기의 늦은 CHAT 차단**에는 서버의 소속 확인과 방 전환 이벤트 직렬화가 핵심이다. `GameFrame.session`만으로 같은 소켓의 오래된 메시지를 구별할 수 없다. 서버가 `CHAT` → 방 종료/전환 알림 → 새 방 메시지의 순서를 보장할 수 없다면 최소한의 방 세대 식별자를 양방향 CHAT에 추가해야 하며, 이는 `MessageType`/README 7-4 변경을 포함한 사전 합의가 필요하다. 아직 확정하지 않는다.
- **B5 확인/미확인:** 병합된 #28은 상대 이탈 후 정지·TOAST·3초 뒤 GAME_OVER, 카운트다운 중 즉시 결과, 2초 PING/6초 타임아웃을 구현했다. 재접속·새 방 ID·대기실 membership 변경은 포함하지 않았다. 종료 중 CHAT을 허용할지, `PAUSE`/`GAME_OVER` 전후 송신 순서를 어떻게 보장할지, 동일 소켓 재경기에서 이전 CHAT을 어떻게 가를지는 합의가 필요하다. 자동 재접속은 일정 밖이지만 사용자가 수동으로 다시 접속하는 경우 새 `NetworkClient`/세션 번호를 사용한다.

## 4. 파일별 작업 범위와 차민이와 합의할 사항

| 파일/클래스 | 주흔 A7 변경안 | 병합된 차민 B5 #28 변경 | 충돌·먼저 합의할 경계 | 시작 조건 |
|---|---|---|---|---|
| `common/ChatMessages`, `Message`, `MessageType` | 기존 CHAT 사용, 필요 시 방향별 검증만 보완 | `MessageType`에 PING·PAUSE 추가 | CHAT 형식 변경 필요 여부, 200 UTF-16 단위와 TOAST 정책. 변경 시 README 7-4 동시 갱신 | 설계 지금; 프로토콜 변경은 양자 합의 후 |
| 새 `client/ui/ChatPanel` | 기록·입력·스크롤·포커스 API | 없음 | 부모 화면과 소유권·기록 수명 | 설계 지금; 독립 구현은 UI 계약 후 가능 |
| `LobbyPanel` | 채팅 영역 배치·입력 포커스 | PR diff 없음 | READY/Enter와 배치, 대기실 이탈 이벤트 | B5 이벤트 계약 후 통합 권장 |
| `GameFrame` | CHAT 수신·전송, phase/연결별 기록 관리 | PAUSE, 카운트다운 결과, 종료 흐름 | CHAT 허용 phase, 종료/결과 경계, EDT 세션 필터 | B5 병합 후 |
| `GamePanel` | Enter, 게임 키 차단, 기록 영역·포커스 | 정지 오버레이 | `WHEN_IN_FOCUSED_WINDOW`, ESC/H/P, `clearInput`, 정지 중 채팅 | B5 병합 후; 수정 전 차민에게 알림 |
| `NetworkClient` | 기존 sender/receiver 사용; 원칙상 수정 불필요 | PING, 수신 타임아웃 | LEAVE와 CHAT 큐 순서, 닫힌 연결 송신 | B5 병합 후 확인 |
| `ClientHandler` | CHAT 1필드 검증, 서버 확인 신원으로 라우팅 | HELLO/PING/종료 검증 | 방/연결 소속 조회, 종료와 동시 CHAT 거절 시점 | B5 병합 후; 수정 전 차민에게 알림 |
| `VersusLobby` | 대기실 참가자만 중계하는 좁은 메서드/API | 현 PR diff에는 없음; 이슈에는 후보 | 대기실 membership과 시작/종료 이벤트 직렬화 | 차민과 API 합의 후 |
| `GameRoom` | 경기 참가자 CHAT 전송 경로 또는 안전한 소속 조회; `Store` 중복 로직 없음 | 이탈/정지/결과 처리 | `connected`, `finished`, `closing`, 전송 순서; 게임 상태는 루프만 변경 | B5 병합 후; 수정 전 차민에게 알림 |
| `GameServer` | 필요 시 서버 라우팅 진입점 | PING, 중복 종료 방지, 서버 shutdown | 방 미소속/종료 판단과 송신 직렬화 | B5 병합 후 |
| `README.md` 7-4·9-2, `SPEC.md` 4.3 | 실제 채팅 통합 뒤 범위/검증 상태 갱신 | B5의 PING·PAUSE 문서 반영은 PR 체크 미완료 | 같은 문서 수정 시 각 프로토콜 변경을 같이 반영 | 각자의 계약 합의 후 |

**먼저 합의할 질문:** (1) 대기실과 게임을 하나의 채팅 범위로 볼지, 결과/다음 경기 중 수신 정책은 무엇인지; (2) 상대 이탈·LEAVE·TIMEOUT·PAUSE·GAME_OVER 중 CHAT 허용 경계와 서버 이벤트 순서; (3) 방 전환 보장을 기존 메시지 순서로 할지 방 세대 식별자가 필요한지; (4) 잘못된 CHAT에 대한 TOAST와 본인 포함 echo; (5) 공유 파일 수정 구간. 별도 합의 전에는 새 방 ID/프로토콜을 도입하지 않는다.

## 5. 테스트 계획

아래는 설계 시 작성한 케이스다. 독립 서버의 `ChatIntegrationTest` 외에 실제 게임 서버/소켓을 쓰는 `test/bunsik/server/GameChatIntegrationTest.java`, Swing 액션 검사 `test/bunsik/client/ui/GameChatInputTest.java`, 기존 `VersusScreenTest`의 대기실·게임 채팅 경로를 구현했다. 자동화 표기의 ‘가능’은 해당 시나리오가 모두 완료됐다는 뜻이 아니다. 실제 IME·물리 키보드·두 노트북은 별도 확인이 필요하다.

| #·종류/위치 | 사전 조건 | 실행 동작 | 기대 결과 | 자동화 | 선행 의존 |
|---|---|---|---|---|---|
| 1 통합 `GameChatIntegrationTest` | 2P 동일 대기실, 두 소켓 | A가 CHAT 전송 | A·B가 서버 echo 각 1회, A 로컬 선표시 없음 | 가능 | 라우팅·표시 정책 합의 |
| 2 서버 단위 + 통합 | 서로 다른 방 두 개의 가짜 참가자 집합 | 방 A에서 CHAT | 방 B·미소속 연결에는 0건 | 단위 가능; 현 서버로 실통합 불가 | 방 추상화 계약; 현 B4는 대기실 하나 |
| 3 Swing + 통합 | 대기실 기록 1건, 두 명 준비 | START·STATE·COUNTDOWN·CHAT 교차 수신 | 같은 대화 기록 유지, 수신 한 번, 조작 시작 정상 | 가능 | B4·A6, phase 정책 |
| 4 Swing + 실제 키보드 | 게임 조작 가능, 텍스트 필드 미포커스 | Enter, 입력, Enter, 이동 | 한 번 전송, 이동 영역 포커스 복귀·이동 가능 | 액션 자동 + 수동 | 입력 바인딩 |
| 5 Swing | 채팅 입력 중 | WASD·방향키·SPACE·H·P | MOVE/COOK/INTERACT/DISCARD 없음, H 토글 없음, 문자/편집 정상 | 액션 자동 + IME 수동 | B5 P 바인딩 병합 |
| 6 Swing | 이동 키가 눌린 상태 | Enter로 채팅 시작, 키 release 없이 기다림 | `pressed`·edge latch 초기화, 추가 MOVE 없음 | 가능 | `GamePanel` 입력 API |
| 7 단위 + 통합 | 연결된 A·B | 빈값·공백·201자·200자·한글·`|`·CR/LF 전송 | 빈/초과는 송신 없음 또는 서버 TOAST; 유효값만 1회, 구분자/줄바꿈 공백 치환 | 가능 | `ChatMessages` 현 규약 |
| 8 Swing + 실제 키보드 | 게임/대기실 입력 | Enter 길게 누르기·빠른 연속 전송·한글 조합 중 Enter | 최초 Enter는 열기만, 확정 1회당 최대 1건, 조합/편집 정상 | 부분 자동 + IME 수동 필수 | 입력 가드/IME 정책 |
| 9 통합 | 양측 2P, CHAT 송신 대기 | LEAVE·상대 창 닫기·타임아웃/서버 종료와 전송 경합 | 종료 경계 이후 중계 없음, 남은 사용자 B5 안내/결과 정상, 예외/중복 종료 없음 | 부분 자동 + 실수동 | B5 병합 |
| 10 통합 + Swing | 기존 소켓 종료 또는 재경기, 새 연결/방 | 이전 수신 콜백 지연 후 새 CHAT | 이전 연결/방 기록이 새 화면에 섞이지 않음 | 가능 | 방 경계 계약, B5 병합 |
| 11 통합 + Swing | 게임 STATE 주기 송신 | CHAT 연속 전송/수신, STATE·PING·PAUSE와 교차 | 라인 파싱·순서/phase 정상, EDT 오류·입력 포커스 탈취 없음 | 가능 | B5 병합 |
| 12 기존 회귀 + 수동 | 1P 및 2P 실제 서버 | 1P 이동·조리·결과, 2P 준비·H·결과·재경기 | 기존 동작 유지, 채팅 화면이 조작/버튼을 가리지 않음 | 기존 테스트 + 두 노트북/물리 키보드 수동 | B5·A7 통합 |

추가 서버 단위 검사: HELLO 전 CHAT/위조 3필드 CHAT, 방 미소속 CHAT, 본인 포함 echo, 종료 중 CHAT, `GameRoom` 루프의 STATE와 동시 송신. 로컬 다중 클라이언트 통합에서는 소켓 두 개와 실제 `GameServer`를 사용한다. 실제 한글 입력기, 키 반복, 포커스, 두 노트북 네트워크 동작은 자동 테스트 통과만으로 완료 처리하지 않는다.

## 6. 구현 순서와 완료 기준

1. 구현됨: B5가 반영된 `main`에서 기존 CHAT 형식과 게임 연결을 재사용했다. `ClientHandler`·`VersusLobby`·`GameRoom`이 소속 대기실/게임방에 중계하고, `GameFrame`·`GamePanel`·`LobbyPanel`·새 `ChatPanel`이 입력과 기록을 연결한다. 별도 채팅 소켓이나 새 메시지 타입은 없다.
2. 자동 검증됨: Java 21 대상 컴파일 및 `verify-game.cmd`, `GameChatInputTest`, `VersusScreenTest`, `GameChatIntegrationTest`, `SessionScreenTest` 통과. 테스트 범위는 로컬 소켓·Swing 액션이며 물리 키 입력을 대신하지 않는다.
3. 남은 확인: 실제 한글 입력기 조합/Enter와 물리 키 반복·포커스, 두 노트북 간 전송, 이탈·재접속 경합의 재현성. 현재 B4에는 대기실이 하나뿐이므로 동시에 존재하는 두 2P 방의 격리는 실서버에서 검증할 수 없다. 새 방 기능은 A7 범위에 넣지 않았다.
4. 완료 기준: 같은 방의 양방향 대기실·게임 채팅과 격리, 본인 1회 표시, 입력·포커스·IME, 이탈/종료/재경기 경계, STATE 교차 수신, 1P/2P 회귀를 자동 및 실제 키보드·두 노트북에서 확인하고 상대 리뷰 후 병합한다.
