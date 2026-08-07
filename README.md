# Bottom_mart

물류 창고 재고 흐름 추적 시스템 — 순수 Java + JDBC + MySQL

Spring Boot 등 프레임워크 없이 `java.sql` 표준 API만 사용합니다.

## 프로젝트 구조

```
lib/        mysql-connector-j (JDBC 드라이버)
schema.sql  DB/테이블 생성 DDL (13개 테이블)
src/com/bottommart/
  db/       DBConnection - JDBC 커넥션 유틸
  model/    테이블별 VO 클래스
  dao/      테이블별 CRUD DAO 클래스
  Main.java CRUD 동작 데모
```

## 처음 받았을 때 (팀원용 셋업)

1. **DB 생성**
   ```
   mysql -u root -p < schema.sql
   ```
2. **DB 접속 정보 설정**
   `db.properties.example`을 복사해서 `db.properties`를 프로젝트 루트에 만들고 본인 비밀번호를 입력합니다.
   `db.properties`는 `.gitignore`에 있어서 커밋되지 않습니다 (비밀번호를 절대 git에 올리지 마세요).
   ```
   cp db.properties.example db.properties
   ```
3. **IntelliJ에서 열기**
   - File > Project Structure > Modules > Dependencies 에서 `lib/mysql-connector-j-26.7.0.jar` 추가
   - `src` 폴더를 Sources Root로 지정
   - `Main.java` 실행

## 커맨드라인에서 빌드/실행

```
javac -cp "lib/mysql-connector-j-26.7.0.jar" -d out $(find src -name "*.java")
java -cp "out;lib/mysql-connector-j-26.7.0.jar" com.bottommart.Main
```

## 테이블 목록 (확정 스키마 v2)

APP_USER, PARTNER, WAREHOUSE, ZONE, ITEM, STOCK_LOT, OUTBOUND, STOCK_TRANSFER,
STOCK_CHANGE_LOG, RETURN_DISPOSAL, ALERT, APPROVAL, USER_WAREHOUSE

- `STOCK_LOT`이 재고 수량의 유일한 출처입니다 (별도 stock 요약 테이블 없음).
- `USER` 테이블은 MySQL 예약어라 `APP_USER`로 명명했습니다.
