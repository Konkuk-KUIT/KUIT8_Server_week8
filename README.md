# KUIT 8기 Server · 8주차 미션

## 웹 서버의 원리 — Custom Tomcat

Java Socket으로 간단한 HTTP 서버를 구현하며 요청·응답 흐름을 이해하고, 객체지향적으로 리팩토링하는 미션입니다. 실제 Apache Tomcat 전체를 구현하는 프로젝트는 아닙니다.

- [8주차 워크북](https://app.notion.com/p/3ea664a49c4a80c58f26c13be436e7a5)
- [HTTP Tomcat 구현 미션](https://app.notion.com/p/3ea664a49c4a803db05be4a9ef21a602)
- [1단계 상세 요구사항](https://app.notion.com/p/3ea664a49c4a802485a3ddac99ec5e66)
- [2단계 상세 요구사항](https://app.notion.com/p/3ea664a49c4a80ccb278d8f12af83ae6)
- 마감일과 영상은 디스코드 공지를 확인해 주세요.

## 실행 환경

- JDK 21
- Gradle 8.5 (저장소에 포함된 Wrapper 사용)
- IntelliJ IDEA 사용 시 Project SDK와 Gradle JVM을 모두 JDK 21로 설정합니다.
- 터미널에서도 `java -version`이 21인지 확인합니다.

프로젝트 최상위 폴더에서 실행합니다.

```bash
./gradlew run
```

Windows에서는 `gradlew.bat run`을 사용합니다. macOS/Linux에서 실행 권한 오류가 발생하면 `chmod +x gradlew`를 먼저 실행합니다.

브라우저에서 http://localhost:8080 에 접속합니다. 시작 코드에서 **Hello World**가 표시되는 것은 정상입니다. HTML 화면 반환부터 직접 구현하는 미션입니다. 종료는 `Ctrl+C`입니다.

다른 포트가 필요한 경우:

```bash
./gradlew run --args='8081'
```

IDE에서 실행하려면 `webserver.WebServer`의 `main()`을 실행합니다. 이때 작업 디렉터리는 `webapp` 폴더가 있는 프로젝트 최상위 폴더여야 합니다.

## 시작 코드 구조

```text
src/main/java/
├── webserver/   # 서버 실행 및 연결별 요청 처리
├── http/util/  # 요청 파라미터와 본문 처리 보조 코드
├── model/      # User
└── db/         # 메모리 기반 사용자 저장소
webapp/         # HTML, CSS, JavaScript, 이미지
```

`RequestHandler.run()`은 현재 모든 요청에 Hello World를 반환합니다. 제공된 유틸리티는 학습용 보조 코드이며, 모든 입력과 예외 상황을 처리하는 완성된 HTTP 파서는 아닙니다.

## 1단계 — HTTP 서버 구현

우선 `RequestHandler.run()`에서 요청을 읽고 응답하는 동작을 구현합니다.

- [ ] `/`, `/index.html` 요청에 `webapp/index.html` 반환하기
- [ ] 요청 경로에 맞는 다른 HTML 파일 반환하기
- [ ] GET 회원가입: 쿼리 문자열을 읽어 `MemoryUserRepository`에 저장하기
- [ ] POST 회원가입: 폼의 method를 바꾸고 요청 본문을 읽어 저장하기
- [ ] 302 상태 코드와 `Location` 헤더로 리다이렉트하기
- [ ] 로그인 성공·실패 처리와 쿠키 설정하기
- [ ] 로그인 여부에 따라 사용자 목록 화면 접근을 허용하거나 로그인 화면으로 이동시키기
- [ ] CSS 요청에 파일 내용과 적절한 `Content-Type` 반환하기

사용자 목록은 제공된 정적 화면을 표시하면 됩니다. 저장소의 사용자들을 HTML로 동적 생성하는 것은 이번 필수 요구사항이 아닙니다.

### 워크북과 코드 확인 시 참고

- 본문 읽기 유틸리티의 실제 이름은 `IOUtils.readData(...)`입니다.
- 로그인 실패 화면은 `webapp/user/login_failed.html`입니다.
- CSS 파일은 `webapp/css/styles.css`입니다.
- 쿠키를 설정하는 **서버 응답 헤더는 `Set-Cookie`**, 이후 브라우저가 보내는 **요청 헤더는 `Cookie`**입니다.
- `logined=true` 쿠키는 동작 이해를 위한 실습 방식이며 실제 서비스의 인증 방식으로 사용하지 않습니다.

## 2단계 — 객체지향 리팩토링

- [ ] HTTP 메서드, 상태 코드 등 반복되는 상수를 Enum으로 정리하기
- [ ] `HttpRequest`로 요청 분석 책임 분리하기
- [ ] 정적 팩토리 메서드를 활용해 요청 객체 생성하기
- [ ] `HttpResponse`로 응답 생성 책임 분리하기
- [ ] `forward(path)`와 `redirect(path)` 구현하기
- [ ] 요청 분석 및 응답 생성 테스트 작성하기
- [ ] 리팩토링 후에도 1단계 동작이 유지되는지 확인하기

```bash
./gradlew test
```

**시작 코드에는 제공된 테스트가 없습니다.** 테스트를 작성하기 전에는 `test NO-SOURCE`로 표시될 수 있으며, 빌드 성공만으로 미션 완료를 판단할 수 없습니다. 직접 작성한 테스트와 브라우저 동작으로 요구사항을 확인해 주세요. Maven의 `mvn test`는 이 프로젝트의 실행 명령이 아닙니다.

## 선택 미션

[SSR·CSR 요청–응답 흐름 그려보기](https://app.notion.com/p/3ea664a49c4a80f48fb2d3763278ab9f)

브라우저, 프론트엔드 서버, 백엔드 서버를 포함하여 각 방식의 요청–응답 흐름을 그려봅니다.

## 제출 방법

1. 이 저장소를 본인 GitHub 계정으로 Fork합니다.
2. Fork한 저장소를 clone하고 본인의 이름 또는 닉네임으로 브랜치를 만듭니다.
3. 별도의 이름 폴더를 만들지 않고 제공된 프로젝트 코드를 수정합니다.
4. 구현 및 검증을 마친 뒤 본인의 브랜치에 commit하고 push합니다.
5. **원본 `Konkuk-KUIT/KUIT8_Server_week8` 저장소의 `main` 브랜치**를 대상으로 PR을 생성합니다.
6. PR 본문에 구현 내용, 동작 확인 결과, 작성한 테스트와 트러블 슈팅을 정리합니다.

