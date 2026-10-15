# 음악 앱 일반 계정 회원가입·로그인

광고주 `users` 및 광고 대상 가상 사용자 `target_users`와 별도로 `public.music_users`에 음악 앱 계정을 저장한다. 이메일은 공백 제거·소문자 변환 후 중복을 검사하고, 비밀번호는 bcrypt 해시로만 저장한다.

| 기능 | 메서드·경로 | 인증 | 성공 |
|---|---|---|---|
| 회원가입 | `POST /api/v1/auth/signup` | 불필요 | `201` |
| 로그인 | `POST /api/v1/auth/login` | 불필요 | `200` |
| 내 계정 조회 | `GET /api/v1/auth/me` | Bearer JWT | `200` |

```http
POST /api/v1/auth/signup
Content-Type: application/json

{"email":"user@example.com","password":"safe-password-123","displayName":"홍길동"}
```

```json
{"data":{"userId":1,"email":"user@example.com","displayName":"홍길동"}}
```

```http
POST /api/v1/auth/login
Content-Type: application/json

{"email":"user@example.com","password":"safe-password-123"}
```

```json
{"data":{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":3600,"user":{"userId":1,"email":"user@example.com","displayName":"홍길동"}}}
```

이후 `GET /api/v1/auth/me`에 `Authorization: Bearer <JWT>`를 보낸다. 토큰에는 사용자 ID가 들어가며 유효 기간은 1시간이다. 이메일 중복은 `409 EMAIL_ALREADY_REGISTERED`, 로그인 실패는 `401 INVALID_CREDENTIALS`, 잘못된 요청은 `400 INVALID_INPUT`, 토큰 없음·오류는 `401 UNAUTHORIZED`이다. 비밀번호는 UTF-8 기준 72바이트 이하여야 한다.

## 실행 설정

로컬은 `AUTH_JWT_SECRET_BASE64`를 생략할 수 있다. 이 경우 서버가 시작할 때 임시 키를 만들어 재시작하면 기존 토큰이 무효화된다. 운영 프로필은 이 값을 반드시 설정해야 서버가 시작된다. 32바이트 이상의 난수를 Base64로 인코딩해 `.env`에 보관한다. 예를 들어 PowerShell에서는 다음과 같이 생성할 수 있다.

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

프론트엔드가 별도 도메인이라면 `.env`의 `CORS_ALLOWED_ORIGINS`에 정확한 출처를 쉼표로 구분해 넣는다. 로컬 기본값은 Vite의 `http://localhost:5173`과 `http://127.0.0.1:5173`이다. DB 테이블은 Flyway `V2__music_user_account.sql`로 생성한다.

회원가입·로그인 테스트는 임시 H2 DB로 실행하므로 Docker가 필요하지 않다. 기존 전체 통합 테스트는 PostgreSQL Testcontainers를 사용한다.

```powershell
.\gradlew.bat test --tests '*AuthApiIntegrationTest'
```

현재 인증 보호 범위는 `/api/v1/auth/me`이다. 기존 광고·랭킹 API는 이전 계약을 유지한다. 특히 랭킹 위치·재생 요청의 `userId`는 아직 클라이언트 값이므로, 실제 사용자에게 공개하기 전 JWT의 사용자 ID로 교체하고 세션 소유권을 검사해야 한다.
