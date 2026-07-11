# Google Play 등록 인계 (다음 세션용)

> 이 문서는 **다음 세션에서 Play 개발자 등록·앱 출시**를 이어갈 때의 체크리스트다. 비밀(keystore·비번)은 `.local`(gitignore, §D)에만 있고 여기엔 **위치와 절차만** 적는다.

## 0. 지금까지 상태 (2026-07-11)

- **앱은 라이브 PWA**: <https://geuneul.vercel.app> · 설치 안내 <https://geuneul.vercel.app/install>
- **서명된 TWA 빌드 완료** (Bubblewrap 1.24.1):
  - 다운로드 APK(사이드로딩·라이브): <https://geuneul.vercel.app/geuneul.apk> (repo `frontend/public/geuneul.apk`)
  - **Play 업로드용 AAB**: `.local/twa-build/geuneul-release.aab` (App Bundle — Play는 APK가 아니라 **AAB** 업로드)
  - 도메인 검증: <https://geuneul.vercel.app/.well-known/assetlinks.json> (라이브)
- **개인정보처리방침(라이브)**: <https://geuneul.vercel.app/privacy> (Play 필수 항목 — 등록 시 이 URL 입력)
- **패키지명**: `app.vercel.geuneul.twa`
- **서명 SHA-256**: `.local/twa-build/signing-sha256.txt` (assetlinks.json 값과 동일)

### 🔑 비밀 위치 (`.local/`, 절대 커밋·유출 금지)
| 항목 | 위치 |
|---|---|
| 서명 keystore | `.local/android-twa.keystore` |
| keystore 비번·별칭 | `.local/android-twa.env` |
| 업로드용 AAB/APK/manifest | `.local/twa-build/` |

> ⚠️ **keystore 분실 = 앱 업데이트 영구 불가.** `.local`을 별도 백업(암호화)해 두는 것을 권장. (Play App Signing에 등록하면 구글이 앱 서명키를 관리하고, 이 keystore는 “업로드 키”가 된다 — 업로드 키는 재설정 가능하나 초기 등록 시 이 키로 서명.)

## 1. 개발자 계정 등록 (진행 중)

- 계정: `akftjdwn@gmail.com` · 개인(individual) 계정 · **$25 1회** · 신원 확인(신분증)
- 웹사이트: `https://geuneul.vercel.app` · 언어: 한국어 · 전화: 본인 휴대폰(`+82 …`, 인증 필요)
- Apps 설문: 앱 수 `1` · 수익화 `No` · 카테고리 `None of the above`

## 2. 출시 전 필수(개인 계정 허들)

- **폐쇄 테스트(Closed testing) 12명 이상 × 연속 14일** 옵트인 후에야 프로덕션 출시 신청 가능.
  - Play Console → Testing → Closed testing 트랙 → 테스터 이메일(또는 Google 그룹) 12+ 등록 → 옵트인 링크 공유.
  - 테스터 구하기: 지인 12명(Gmail) / 개발자 상호 테스트 커뮤니티(r/androiddev 등). **가짜 계정 금지.**

## 3. 앱 등록 체크리스트

- [ ] 앱 만들기(이름 “그늘”, 기본 언어 한국어, 무료)
- [ ] **AAB 업로드**: `.local/twa-build/geuneul-release.aab` (Play App Signing 동의)
- [ ] 개인정보처리방침 URL: `https://geuneul.vercel.app/privacy`
- [ ] 콘텐츠 등급 설문(유틸리티/지도, 폭력·성인 없음)
- [ ] 대상 연령·데이터 보안 양식(Data safety): 위치·이메일·사진·UGC 수집 사실대로(위 privacy와 일치)
- [ ] 스토어 등록정보: 짧은/긴 설명, 아이콘(512, `frontend/public/icon-512.png`), 피처 그래픽, 스크린샷(`docs/media/*` 재사용 가능)
- [ ] 앱 카테고리: 지도/내비게이션 또는 라이프스타일
- [ ] 광고 없음·인앱결제 없음 표기

## 4. AAB 재빌드(필요 시)

버전 올리거나 재생성해야 하면 (JDK17 + Android SDK 필요, TS-033 참고):

```bash
# .local/twa-build/twa-manifest.json 에서 appVersionCode/appVersionName 증가 후
cd <bubblewrap 프로젝트>   # twa-manifest.json 있는 곳
export JAVA_HOME=<JDK17 경로>              # 예: ~/.local/share/mise/installs/java/17.0.2/Contents/Home
export ANDROID_HOME=<Android SDK 경로>
source /Users/seongju/geuneul/.local/android-twa.env
export BUBBLEWRAP_KEYSTORE_PASSWORD="$ANDROID_TWA_STOREPASS"
export BUBBLEWRAP_KEY_PASSWORD="$ANDROID_TWA_KEYPASS"
bubblewrap update && bubblewrap build --skipPwaValidation
# → app-release-bundle.aab (Play 업로드) / app-release-signed.apk (사이드로딩)
```

빌드 함정(위저드 크래시·SDK 레이아웃·빈 Groovy 값)은 [`TROUBLESHOOTING.md` TS-033](../TROUBLESHOOTING.md) 참고.

## 5. 참고 — WebAPK가 이미 “진짜 설치”

Play 등록은 **선택**이다. 안드로이드는 `/install`의 **WebAPK 원탭**이 이미 런처 아이콘·전체화면의 실제 설치 앱을 만든다(경고·비용 0). Play는 “정식 스토어 배포 경험” 트로피용. 급하지 않으면 14일 테스트를 병행하며 천천히 진행.
