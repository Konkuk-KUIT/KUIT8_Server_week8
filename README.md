# KUIT 8기 Server · 8주차 미션

## 웹 서버의 원리 — Custom Tomcat

Java Socket으로 간단한 HTTP 서버를 구현하며 요청·응답 흐름을 이해하고, 객체지향적으로 리팩토링하는 미션입니다. 실제 Apache Tomcat 전체를 구현하는 프로젝트는 아닙니다.

- [8주차 워크북](https://app.notion.com/p/3ea664a49c4a80c58f26c13be436e7a5)
- [HTTP Tomcat 구현 미션](https://app.notion.com/p/3ea664a49c4a803db05be4a9ef21a602)
- [1단계 상세 요구사항](https://app.notion.com/p/3ea664a49c4a802485a3ddac99ec5e66)
- [2단계 상세 요구사항](https://app.notion.com/p/3ea664a49c4a80ccb278d8f12af83ae6)
- 마감일과 영상은 디스코드 공지를 확인해 주세요.

## 시작하기

1. 이 저장소를 본인의 GitHub 계정으로 Fork합니다.
2. Fork한 저장소를 clone합니다. 아래 `YOUR_GITHUB_ID`를 본인 계정으로 바꿉니다.

```bash
git clone https://github.com/YOUR_GITHUB_ID/KUIT8_Server_week8.git
cd KUIT8_Server_week8
git switch -c YOUR_NICKNAME
```

`YOUR_NICKNAME`도 본인의 이름 또는 닉네임으로 바꿉니다. 저장소 이름을 변경해서 Fork했다면 clone 주소와 폴더 이름도 바꿔 주세요.

### 실행 환경

- JDK 21
- Gradle 8.5: 포함된 Wrapper를 사용하므로 Gradle을 따로 설치할 필요는 없습니다.
- IntelliJ IDEA에서는 Project SDK와 Gradle JVM을 모두 JDK 21로 설정합니다.
- 터미널에서도 `java -version`이 21인지 확인합니다. JDK 21이 설치되어 있어도 다른 버전을 선택한 상태라면 변경해야 합니다.
- 첫 실행은 Gradle 및 테스트 의존성을 다운로드하므로 인터넷 연결이 필요합니다.

`build.gradle`과 `webapp`이 있는 프로젝트 최상위 폴더에서 실행합니다.

```bash
./gradlew run
```

Windows PowerShell에서는 `./gradlew.bat run`을 사용합니다. macOS/Linux에서 권한 오류가 나면 `chmod +x gradlew`를 먼저 실행합니다.

브라우저에서 http://localhost:8080 에 접속합니다. **시작 코드에서는 Hello World가 표시되는 것이 정상**입니다. HTML 화면 반환부터 직접 구현합니다. 서버 종료는 `Ctrl+C`입니다.

포트가 사용 중이라면 다른 포트로 실행합니다.

```bash
./gradlew run --args='8081'
```

이 경우 http://localhost:8081 로 접속합니다. IDE에서는 `webserver.WebServer.main()`을 실행하며, 작업 디렉터리를 프로젝트 최상위 폴더로 설정합니다.

## 제공 코드와 직접 구현할 부분

```text
src/main/java/
├── webserver/   # 서버 실행, 연결별 요청 처리 시작 코드
├── http/util/  # 파라미터 파싱, 폼 본문 읽기 보조 함수
├── model/      # User
└── db/         # 메모리 기반 사용자 저장소
src/test/java/  # 제공 코드 검증 테스트, 직접 작성할 테스트
webapp/         # 실습용 HTML, CSS, JavaScript, 이미지
```

| 제공되는 부분 | 부원이 구현할 부분 |
| --- | --- |
| 서버 연결 및 스레드 실행 | 요청 경로·메서드·헤더 해석과 기능 연결 |
| 파라미터 파싱·폼 본문 읽기 보조 함수 | 쿼리 문자열·Content-Length를 추출하고 보조 함수를 사용하기 |
| User와 메모리 저장소 | 회원가입, 로그인, 쿠키에 따른 접근 제어 |
| 실습 화면 | 파일 응답, 리다이렉트, 적절한 응답 헤더 |
| 보조 코드의 검증 테스트 | HttpRequest·HttpResponse 리팩토링과 자신의 구현 테스트 |

현재 `RequestHandler.run()`은 모든 요청에 Hello World를 반환합니다. `HttpRequest`와 `HttpResponse`는 직접 만들며, 제공 함수의 내부 구현을 다시 작성할 필요는 없습니다. 리팩토링 과정에서 수정해도 되지만 제공 테스트가 검증하는 동작은 유지해 주세요.

### 보조 함수의 사용 범위

**`HttpRequestUtils.parseQueryParameter(String)`**

- GET에서는 URL에서 분리한 쿼리 문자열을 `?` 없이 전달합니다.
- POST에서는 URL 디코딩하지 않은 원래 폼 본문을 전달합니다.
- 키와 값을 UTF-8로 URL 디코딩합니다. `+`는 공백으로, `%2B`는 문자 `+`로 복원합니다.
- `name=`과 `name`은 모두 빈 문자열 값으로 처리합니다. null·빈 입력은 빈 Map, 빈 `&` 구간은 무시합니다.
- 빈 키, 중복된 키(디코딩 후 기준), 잘못된 `%` 이스케이프는 `IllegalArgumentException`으로 처리합니다.
- 필수 항목이나 비밀번호 유효성을 판단하는 함수는 아닙니다. 사용자 기능에서 필요한 값을 확인하세요.

**`IOUtils.readData(BufferedReader, int)`**

- 헤더의 끝인 빈 줄까지 읽은 **동일한 BufferedReader**와 `Content-Length`를 전달합니다. 본문을 읽기 위해 같은 입력 스트림에 새 Reader를 만들지 마세요.
- 본문은 디코딩 전 ASCII로 표현되는 `application/x-www-form-urlencoded` 형식으로 한정합니다. 한글 입력도 폼 인코딩 후 `%ED...`와 같이 전송되므로 지원합니다.
- 길이 0은 빈 문자열, 음수 길이는 `IllegalArgumentException`, 본문이 일찍 끝나면 `EOFException`입니다. 인코딩되지 않은 비 ASCII 문자는 `IOException`입니다.
- HTTP Content-Length는 **바이트 수**입니다. 이 함수는 위의 제한된 폼 형식에서만 문자 수와 바이트 수가 같다는 전제로 동작합니다. 원시 한글 본문·JSON·파일 업로드·chunked 본문을 읽는 범용 함수가 아닙니다.

**`MemoryUserRepository`**

- `getInstance()`로 공통 저장소를 얻습니다. 동시 저장·조회가 가능하도록 제공됩니다.
- 없는 아이디를 조회하면 null입니다. null 사용자·아이디는 허용하지 않습니다.
- 같은 아이디로 저장하면 기존 사용자를 교체합니다. 기본 미션에서는 서로 다른 아이디로 회원가입을 확인하며, 중복 가입 정책은 선택 심화 항목입니다.
- `findAll()`은 저장소와 분리된 수정 불가 컬렉션을 반환합니다. 순서는 보장하지 않습니다.
- 메모리에만 저장하므로 **서버를 재시작하면 가입 정보가 사라집니다.**

## 1단계 — HTTP 서버 구현

먼저 `RequestHandler.run()`에서 동작을 완성합니다.

- [x] `/`, `/index.html` 요청에 `webapp/index.html` 반환하기
- [x] 요청에 따라 다른 HTML 파일도 반환하기
- [x] GET 회원가입: 쿼리 문자열로부터 User를 생성하고 저장하기
- [x] POST 회원가입: 폼의 method를 post로 바꾸고 본문으로부터 User를 생성하고 저장하기
- [ ] 회원가입 성공 시 302 응답으로 `/index.html`에 리다이렉트하기
- [ ] 아이디로 사용자를 조회하고 비밀번호를 비교하여 로그인 성공·실패 처리하기
- [ ] 로그인 성공 응답에 `Set-Cookie: logined=true; Path=/`를 넣고 `/index.html`로 리다이렉트하기
- [ ] 로그인 실패 시 `/user/login_failed.html`로 리다이렉트하기
- [ ] 요청의 Cookie에서 `logined` 값을 확인해 사용자 목록 접근을 제어하기
- [ ] CSS 요청에 `Content-Type: text/css`와 파일 내용을 반환하기

GET 회원가입을 먼저 구현한 뒤 POST로 전환합니다. **최종 제출 폼은 POST**이며, GET 회원가입 처리의 최종 유지 여부는 필수가 아닙니다.

### 요청 경로와 기대 동작

| 요청 | 기대 동작 |
| --- | --- |
| `GET /`, `GET /index.html` | 메인 HTML 반환 |
| `GET /user/form.html` | 회원가입 폼 반환 |
| `GET /user/signup?...` | GET 회원가입 학습 단계 |
| `POST /user/signup` | 본문으로 회원가입 후 `/index.html`로 302 이동 |
| `GET /user/login.html` | 로그인 폼 반환 |
| `POST /user/login` | userId 조회 및 password 비교 후 성공·실패 처리 |
| `GET /user/userList`, `GET /user/list.html` | 로그인 상태라면 `webapp/user/list.html` 반환, 아니면 `/user/login.html`로 302 이동 |
| `GET /css/styles.css` | CSS 파일 반환 |

로그인에서는 `User.equals()`로 전체 필드를 비교하지 않습니다. 로그인 폼은 아이디와 비밀번호만 전달합니다. 저장된 사용자가 null인지 먼저 확인한 뒤 비밀번호를 비교하세요.

서버가 쿠키를 설정할 때는 **Set-Cookie 응답 헤더**, 브라우저가 쿠키를 보낼 때는 **Cookie 요청 헤더**를 사용합니다. 여러 쿠키 중 `logined`의 값이 정확히 `true`인지 확인합니다. 로그인 실패 검증은 쿠키를 지운 상태나 새 시크릿 창에서 진행합니다. 이미 로그인된 상태에서 다시 로그인에 실패했을 때 기존 쿠키를 지우는 처리는 선택 심화 항목입니다.

302 응답의 `Location`에는 파일 시스템 경로가 아닌 `/index.html` 같은 URL 경로를 사용합니다. 빈 본문이라면 `Content-Length: 0`을 명시할 수 있고, 로그인에서는 `Set-Cookie`도 함께 필요합니다. 이번 실습은 연결 하나에서 요청 한 건을 처리하므로 `Connection: close`를 명시하고 응답 후 연결을 닫는 방식으로 구현하면 됩니다.

### 미션 범위

- 사용자 목록은 제공된 **정적 예시 화면**입니다. 실제 가입자를 HTML로 동적 생성할 필요는 없습니다.
- 제공된 Q&A HTML의 화면 반환은 연습할 수 있지만, 질문 등록·수정·삭제, 검색, FAQ, 프로필 기능은 필수 미션이 아닙니다.
- 정적 리소스의 필수 범위는 HTML과 CSS입니다. 이미지·JavaScript 응답은 선택 확장입니다. Bootstrap은 외부 CDN을 사용하므로 화면 스타일 일부는 인터넷 연결에 영향을 받습니다.
- POST 본문은 일반 HTML 폼 형식으로 한정합니다. JSON·multipart·chunked·지속 연결·실서비스 인증은 이번 필수 미션에서 제외합니다.
- `logined=true` 쿠키는 요청·응답 학습을 위한 방식이며 실제 서비스의 인증 방식으로 사용하지 않습니다.
- 잘못된 요청의 상세 오류 응답, 중복 가입 정책, 입력값 검증 강화는 선택 심화 항목입니다. 다만 제공 함수의 예외가 발생하면 해당 요청 처리를 종료하고 연결을 닫도록 처리하세요.

## 2단계 — 객체지향 리팩토링

- [ ] HTTP 메서드, 상태 코드 등 반복되는 상수를 Enum으로 정리하기
- [ ] `HttpRequest`로 요청 분석 책임을 분리하고 정적 팩토리 메서드 활용하기
- [ ] `HttpResponse`로 응답 생성 책임 분리하기
- [ ] `forward(path)`와 `redirect(path)` 구현하기
- [ ] 요청 분석 및 응답 생성 테스트 작성하기
- [ ] 리팩토링 후에도 1단계 기능이 유지되는지 확인하기

테스트 코드는 `src/test/java`에 작성합니다. 요청 예제 파일이 필요하면 `src/test/resources`에 두고 클래스패스로 읽습니다. 본인 컴퓨터의 절대 경로를 테스트에 넣지 마세요.

```bash
./gradlew test
```

Windows PowerShell에서는 `./gradlew.bat test`를 사용합니다.

**시작 코드에는 보조 함수와 사용자 저장소를 검증하는 테스트 24개가 포함되어 있으며, 미션 구현 전에도 모두 통과해야 합니다.** 이 테스트들은 미션 완성도를 평가하는 테스트가 아닙니다. 통과했다고 미션이 완료된 것은 아니며, 자신의 HTTP 요청·응답 구현에 대한 테스트를 별도로 작성해야 합니다. Maven의 `mvn test`는 사용하지 않습니다.

최소한 다음 동작을 자신의 테스트와 브라우저에서 확인하세요.

- 메서드·경로·헤더·폼 본문이 올바르게 분석되는지
- 정상 로그인과 잘못된 비밀번호·없는 아이디 로그인이 구분되는지
- 쿠키가 없을 때 목록 접근이 차단되고, 로그인 후 목록 화면이 표시되는지
- `/user/list.html`로 직접 접근해도 로그인 검사가 적용되는지
- 302 응답의 Location과 로그인 성공 시 Set-Cookie가 올바른지
- HTML·CSS 응답의 내용, Content-Type, **바이트 기준 Content-Length**가 맞는지

응답은 개발자 도구의 Network 탭에서도 확인할 수 있습니다. 서버가 실행 중일 때 아래 명령은 302 응답을 자동으로 따라가지 않아 헤더를 확인하기 편합니다.

```bash
curl -i http://localhost:8080/user/userList
```

## 선택 미션

[SSR·CSR 요청–응답 흐름 그려보기](https://app.notion.com/p/3ea664a49c4a80f48fb2d3763278ab9f)

브라우저, 프론트엔드 서버, 백엔드 서버를 포함해 각 방식의 흐름을 그립니다. 수행했다면 PR 본문에 이미지 또는 접근 가능한 링크를 첨부합니다.

## 제출 방법

1. 본인 Fork 저장소의 이름·닉네임 브랜치에서 진행합니다.
2. 별도의 이름 폴더를 만들지 않고 제공된 프로젝트 코드를 수정합니다.
3. 기능과 테스트를 확인한 뒤 commit하고 본인 브랜치에 push합니다.
4. **base 저장소: `Konkuk-KUIT/KUIT8_Server_week8`, base 브랜치: `main`**으로 PR을 생성합니다.
5. head 저장소는 본인의 Fork, compare 브랜치는 작업한 이름·닉네임 브랜치로 선택합니다.
6. PR 본문에 완료한 요구사항, 동작 확인 결과, 추가한 테스트, 트러블 슈팅을 정리합니다.

영상과 제출 마감은 디스코드 공지를 확인해 주세요.
