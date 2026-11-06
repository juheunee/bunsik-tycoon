# 프로젝트 협업 규칙

> 작업 흐름: **브랜치 만들기 → 커밋 → 푸시 → PR → 상대 확인 → 머지**

---

## 1. 타입

브랜치와 커밋에 같은 타입을 쓴다.

| 타입 | 언제 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작은 그대로, 코드 정리 |
| `design` | 그래픽·화면 꾸미기 (`Sprites.java`, 색상, 배치) |
| `docs` | README, agent.md 등 문서 |
| `chore` | 설정, 스크립트, .gitignore 등 기타 |

---

## 2. 브랜치

```
이름/작업종류/짧은 설명
```

| 예시 | |
|---|---|
| `gildong/feat/chat` | 채팅 기능 |
| `gildong/fix/h-key-toggle` | H 키 오류 |
| `gildong/design/customer-walk` | 손님 걷기 애니메이션 |
| `gildong/docs/readme-team` | README 팀 정보 |

- 영어 소문자, 단어는 `-`로 연결, 2~4단어
- 이슈가 있으면 번호 추가: `fix/7-h-key-toggle`
- `main`에 직접 푸시 금지
- 브랜치 하나 = 작업 하나

---

## 3. 커밋 메시지

```
타입: 내용
타입(범위): 내용
```

| 예시 |
|---|
| `feat: 게임 중 채팅 입력창 추가` |
| `fix(client): 한글 입력 상태에서 H 키 안 되는 문제` |
| `feat(protocol): CHAT 메시지 종류 추가` |
| `design: 손님 표정 3단계로 변경` |
| `docs: README 앞으로 할 일 갱신` |
| `fix: 연결 끊김 시 게임방 정리 안 됨 (#7)` |

**범위 (선택)**

| 범위 | 대상 |
|---|---|
| `server` | `src/bunsik/server/` |
| `client` | `src/bunsik/client/` |
| `common` | `src/bunsik/common/` |
| `protocol` | `MessageType`, `Message`, `StoreSnapshot` (통신 규칙) |

- 타입은 **영어 소문자**
- 커밋 하나 = 변경 하나
- 이슈 번호는 맨 뒤 `(#번호)`

---

## 4. PR (Pull Request)

**제목**: 커밋 메시지와 같은 형식 → `feat: 게임 중 채팅`

**본문 예시**

```markdown
## 무엇을
-

## 확인 방법
-

## 체크
- [ ] 빌드 됨 (build.sh / build.bat)
- [ ] 네트워크 관련이면 노트북 2대로 테스트함
- [ ] 통신 규칙을 바꿨으면 README 7-4 갱신함
- [ ] 외부 이미지를 넣었으면 README 10 출처표에 기록함
- [ ] README 9-2 할 일 체크함
```

**머지 방법**

1. PR 작성 (**직접 머지 금지**)
2. 상대가 확인 후 머지 (Create a merge commit)
3. 머지한 브랜치 삭제 (선택)
4. 둘 다 `main` 최신으로 받기 (pull)

---

## 5. 프로젝트 규칙

### 통신 규칙
- `MessageType`·STATE 형식 변경은 **먼저 둘이 합의**
- 서버·클라이언트 수정은 **한 PR에 함께**
- 바꾸면 README 7-4 함께 갱신

### 코드 위치
| 무엇 | 어디에만 |
|---|---|
| 밸런스 숫자 (시간, 인내심, 속도 등) | `GameConfig.java`, `Menu.java` |
| 좌표 (테이블, 대기줄, 부엌) | `StoreLayout.java` |
| 그림 그리는 코드 | `Sprites.java` |
| 메시지 만들기·해석 | `Message.java`, `StoreSnapshot.java` |

### 스레드
- 서버 게임 상태 변경 → **게임 루프 스레드에서만** (`GameRoom` 대기줄로 전달)
- 클라이언트 화면 변경 → **`SwingUtilities.invokeLater`로만**

### 파일
- 커밋 금지: `config.local.properties`, `out/`, IDE 설정 파일
- 외부 이미지 추가 시 README 10 출처표에 **바로** 기록
- 외부 코드 복사 금지 (확인 시 F 학점)

### 작업 관리
- 시작할 때: README 9-2 할 일에 담당자 표시
- 끝나면: 체크
- 같이 할 수 있는 파일 (`GamePanel.java`, `GameRoom.java`, `Store.java`, `MessageType.java`) 수정 전 **미리 알리기**

---

## 6. 자바 네이밍

| 대상 | 규칙 | 예시 |
|---|---|---|
| 클래스 | PascalCase | `GameRoom`, `KitchenOverlay` |
| 메서드·변수 | camelCase | `onInteract()`, `cookRemainMs` |
| 상수 | UPPER_SNAKE | `PATIENCE_MS`, `TABLE_COUNT` |
| enum 값 | UPPER_SNAKE | `WAITING_FOOD`, `TTEOKBOKKI` |
| 패키지 | 소문자 | `bunsik.server.game` |
| 화면 클래스 | `~Panel` | `LobbyPanel` |
| 서버 메시지 처리 메서드 | `on~` | `onHello()`, `onCook()` |
| 시간 값 | 단위 붙이기 | `EAT_MS`, `timeLeftMs` |

---

## 7. 자주 쓰는 명령어

```bash
# 작업 시작
git switch main
git pull
git switch -c feat/chat

# 커밋
git add .
git commit -m "feat: 게임 중 채팅 입력창 추가"

# 푸시 (처음)
git push -u origin feat/chat
# 푸시 (이후)
git push

# 작업 중 main 최신 내용 반영
git switch main
git pull
git switch feat/chat
git merge main

# 머지 후 정리
git switch main
git pull
git branch -d feat/chat

# 내 계정 확인
git config user.name
git config user.email
```
