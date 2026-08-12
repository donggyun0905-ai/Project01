# Project01

물류 창고 재고 흐름 추적 시스템 — 순수 Java + JDBC + MySQL

Spring Boot 등 프레임워크 없이 `java.sql` 표준 API만 사용합니다.

> 팀원과 같이 작업하는 방법(브랜치 전략, PR 올리는 법, Git 용어 설명)은 [CONTRIBUTING.md](CONTRIBUTING.md) 참고.

## 프로젝트 구조

```
lib/        mysql-connector-j (JDBC 드라이버)
schema.sql  DB/테이블 생성 DDL (13개 테이블)
seed_item.sql            ITEM 초기 데이터 250개
seed_warehouse_zone.sql  WAREHOUSE 10개 + ZONE 30개
seed_partner_user.sql    PARTNER 16개 + APP_USER 7명 + USER_WAREHOUSE 12건
seed_stock_lot.sql       STOCK_LOT 초기 데이터 400개 (FIFO/FEFO 검증용, 2단계 참고)
com/_mart/
  db/       DBConnection - JDBC 커넥션 유틸 + 트랜잭션 헬퍼(executeInTransaction)
  dto/      테이블별 DTO(Data Transfer Object) 클래스 - 데이터를 담아 나르는 용도
  dao/      테이블별 CRUD DAO(Data Access Object) 클래스 - 실제 SQL 실행 담당
  util/     PasswordUtil - SHA-256 비밀번호 해시
  Main.java CRUD 동작 데모
.project, .classpath   Eclipse용 프로젝트 파일 (Import 시 자동 인식)
```

`src` 폴더 없이 프로젝트 루트 바로 아래에 `com/_mart/...` 패키지가 오는 구조입니다.
즉 **프로젝트 루트 자체가 소스 루트**입니다 — IntelliJ든 Eclipse든 별도 설정 없이 프로젝트 루트를 그대로 소스 루트로 잡으면 됩니다.

DTO와 DAO는 짝으로 동작합니다: **DTO(데이터를 담는 그릇) ↔ DAO(그 데이터를 DB에 넣고 빼는 역할) ↔ DB**.

DAO 메서드는 `Connection`을 직접 열지 않고 파라미터로 받습니다. 여러 테이블에 걸친 작업(예: 창고 등록 + 구역 등록 + 로트 등록)을 하나의 트랜잭션으로 묶어야 할 때, 호출하는 쪽(`Main.java`)이 `DBConnection.executeInTransaction(...)`으로 커넥션 하나를 만들어 여러 DAO 호출에 전달하고, 중간에 예외가 나면 전체가 자동으로 롤백됩니다. 자세한 사용법은 `Main.testFullFlowWithTransaction()` 참고.

### 향후 확장 예상안 (요구사항분석서 기준)

지금은 재고 현황/입출고(1번 영역)의 CRUD만 구현된 상태입니다. 나머지 영역이 추가되면 아래처럼 하위 패키지가 늘어날 것으로 예상합니다 (뼈대만 미리 잡아둔 것이 아니라, 실제로 필요해질 때 그때그때 추가하는 방식을 권장):

```
com/_mart/
  dto/      (완료) 13개 테이블 데이터 홀더
  dao/      (완료) 13개 테이블 CRUD
  db/       (완료) 커넥션 + 트랜잭션 유틸
  util/     (완료) PasswordUtil 등 공용 유틸

  service/  (예정) DAO 여러 개를 조합하는 비즈니스 로직
              - FIFO/FEFO 출고 순서 추천, 재고부족/초과 판정, 이상 출고 감지 등
              - "DAO에 넣기엔 애매한 검증/판단 로직"이 여기로 이동
              - 예: StockTransferDao의 구역 검증 로직도 장기적으로는 여기로 옮기는 게 정석

  report/   (예정) 일일 보고서, 통계 대시보드용 집계
              - 재고 회전율, 거래처별 출고 랭킹, CSV/Excel 내보내기 등
              - 대부분 SELECT + GROUP BY 집계라 DAO에 조회 메서드를 추가하는 형태가 될 수도 있음

  (알림/승인 자체는 이미 ALERT, APPROVAL 테이블의 dto/dao로 CRUD까지는 되어 있고,
   "언제 자동으로 알림을 만들지"/"승인 워크플로 처리" 같은 판단 로직만 나중에 service/에 추가하면 됨)
```

## 처음 받았을 때 (팀원용 셋업)

1. **DB 생성**
   ```
   mysql -u root -p < schema.sql
   ```
2. **초기 데이터 시드 (선택)**
   FK 의존성 때문에 반드시 아래 순서대로 실행합니다 (ITEM → WAREHOUSE/ZONE → PARTNER/APP_USER → STOCK_LOT).
   ```
   mysql -u root -p < seed_item.sql
   mysql -u root -p < seed_warehouse_zone.sql
   mysql -u root -p < seed_partner_user.sql
   mysql -u root -p < seed_stock_lot.sql
   ```
   각 시드의 설계 근거(수치를 왜 이렇게 정했는지)는 `Project01_설계안/초기데이터_시드_정리.md` 참고.
3. **DB 접속 정보 설정**
   `db.properties.example`을 복사해서 `db.properties`를 프로젝트 루트에 만들고 본인 비밀번호를 입력합니다.
   `db.properties`는 `.gitignore`에 있어서 커밋되지 않습니다 (비밀번호를 절대 git에 올리지 마세요).
   ```
   cp db.properties.example db.properties
   ```
4. **IntelliJ에서 열기**
   - 프로젝트 폴더를 열고, File > Project Structure > Modules > Dependencies 에서 `lib/mysql-connector-j-26.7.0.jar` 추가
   - 프로젝트 루트 자체를 Sources Root로 지정 (`src`가 따로 없으므로 루트를 그대로 지정)
   - `Main.java` 실행
5. **Eclipse에서 열기**
   - File > Import > General > Existing Projects into Workspace
   - 프로젝트 폴더 선택 (이미 포함된 `.project`/`.classpath` 덕분에 소스 루트와 `lib/mysql-connector-j-26.7.0.jar` 라이브러리가 자동으로 잡힘)
   - `Main.java` 우클릭 > Run As > Java Application

   `.project`/`.classpath`가 왜 필요한가: Maven/Gradle 같은 빌드 도구가 없는 순수 JDBC 프로젝트라, Eclipse가 "어디가 소스 폴더인지" "어떤 jar를 참조해야 하는지"를 스스로 알아낼 방법이 없습니다. `.project`는 Eclipse에게 "이 폴더는 Java 프로젝트다"라고 알려주는 파일이고(이게 없으면 Import 목록에 아예 뜨지 않음), `.classpath`는 소스 폴더(프로젝트 루트 자체)와 `lib/mysql-connector-j-26.7.0.jar`를 라이브러리로 등록해둔 파일입니다. IntelliJ의 `.iml`은 각자 로컬에서 자동 생성되라고 git에서 제외했지만, 이 두 파일은 Eclipse 쪽엔 그런 자동 생성 수단이 없어서 예외적으로 커밋해뒀습니다.

## 커맨드라인에서 빌드/실행

```
javac -cp "lib/mysql-connector-j-26.7.0.jar" -d out $(find com -name "*.java")
java -cp "out;lib/mysql-connector-j-26.7.0.jar" com._mart.Main
```

## 테이블 목록 (확정 스키마 v2)

APP_USER, PARTNER, WAREHOUSE, ZONE, ITEM, STOCK_LOT, OUTBOUND, STOCK_TRANSFER,
STOCK_CHANGE_LOG, RETURN_DISPOSAL, ALERT, APPROVAL, USER_WAREHOUSE

- `STOCK_LOT`이 재고 수량의 유일한 출처입니다 (별도 stock 요약 테이블 없음).
- `USER` 테이블은 MySQL 예약어라 `APP_USER`로 명명했습니다.
