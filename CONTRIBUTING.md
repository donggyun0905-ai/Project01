# 팀 협업 가이드

팀원 4명 중 Git을 처음 쓰는 사람이 있다는 전제로, 최대한 단순하게 정리했습니다.
GitFlow 같은 복잡한 전략은 안 쓰고 **"개인별 브랜치 + PR(Pull Request)"** 만 씁니다.

## 브랜치 전략

- `master` 브랜치는 **항상 정상 동작하는 상태**를 유지합니다. `master`에 직접 `push`하지 않습니다.
- 작업을 시작할 때 자기 브랜치를 하나 만들어서 그 안에서만 작업합니다.
  - 브랜치 이름: 그냥 자기 이름(영문) 하나로. 예: `donggyun`, `minsu`
  - 같은 사람이 동시에 여러 작업을 하면: `이름/작업내용` 예: `minsu/alert-crud`
- 작업이 끝나면 GitHub에서 **PR(Pull Request)**을 만들고, 팀원 최소 1명이 확인한 뒤 `master`에 merge합니다.

## 처음 한 번만 하면 되는 설정

```
git config --global user.name "본인 이름"
git config --global user.email "본인 이메일"
```

## 실제 작업 순서 (명령어 치트시트)

**1. 저장소 받기 (맨 처음 한 번만)**
```
git clone https://github.com/donggyun0905-ai/Project01.git
cd Project01
```

**2. 작업 시작 전 – 항상 최신 상태로 갱신**
```
git checkout master
git pull origin master
```

**3. 내 브랜치 만들기**
```
git checkout -b 내이름
```

**4. 작업하고 저장(commit)하기**
```
git add .
git commit -m "무엇을 했는지 한 줄로 (예: ALERT CRUD 추가)"
```

**5. 내 브랜치를 GitHub에 올리기**
```
git push -u origin 내이름
```
(맨 처음 push할 때만 `-u`를 붙입니다. 이후로는 그냥 `git push`만 하면 됩니다.)

**6. GitHub에서 PR 만들기**
- 저장소 페이지(https://github.com/donggyun0905-ai/Project01) 접속하면 "Compare & pull request" 버튼이 보임 → 클릭
- 뭘 했는지 간단히 설명 쓰고 → **Create pull request**
- 팀원에게 링크 공유해서 봐달라고 요청

**7. 리뷰 끝나면 Merge**
- PR 페이지에서 **Merge pull request** 버튼 클릭
- 그다음 내 컴퓨터에서도 `master`를 최신화:
```
git checkout master
git pull origin master
```

## Git 용어가 헷갈릴 때

- **커밋(commit)**: "여기까지 저장" 버튼. 내 컴퓨터 안에서만 기록됨 (아직 GitHub엔 안 올라감)
- **브랜치(branch)**: 원본을 건드리지 않고 혼자 작업할 수 있는 복사본
- **병합(merge)**: 브랜치에서 완성한 내용을 다른 브랜치(보통 `master`)에 합치는 것
- **PR(Pull Request)**: "내 브랜치를 master에 합쳐줘"라고 올리는 요청서. GitHub 사이트 기능이며, 팀원이 검토한 뒤 버튼 한 번으로 merge까지 처리됨
- **충돌(conflict)**: 두 사람이 같은 파일의 같은 줄을 다르게 고쳐서 Git이 자동으로 못 합치는 상황. 사람이 직접 열어서 어느 쪽이 맞는지 골라줘야 함
- **`git pull`**: 남들이 올린 최신 내용을 내 컴퓨터로 받아오기
- **`git push`**: 내가 commit한 내용을 GitHub로 올리기 (commit ≠ push, commit만 하면 아직 내 컴퓨터에만 있는 것)

헷갈릴 때는 일단 `git status`부터 쳐보세요. 지금 뭐가 바뀌었는지, 뭘 해야 하는지 안내 문구가 같이 나옵니다.

## 충돌 나기 쉬운 파일 — 미리 조율하기

- **`schema.sql`**: 테이블 구조를 바꿔야 하면 단체 대화방에 먼저 얘기하고 수정하세요.
- **`Main.java`**: 다 같이 보는 데모/테스트 파일이라 동시에 고치면 충돌이 잘 납니다. 개인적으로 테스트해보고 싶은 코드는 여기 넣지 말고, 필요하면 `MainTest이름.java`처럼 별도 파일을 임시로 만들어 쓰고 커밋하지 마세요.
- **`db.properties`**: `.gitignore`에 들어있어서 각자 로컬에만 있고 GitHub에는 절대 안 올라갑니다 → 이 파일은 애초에 충돌이 날 수가 없습니다. 각자 자기 비밀번호로 알아서 채우면 됩니다.

## IDE에서 명령어 없이 GUI로 하고 싶다면

- **IntelliJ**: 화면 우측 하단 브랜치 이름 표시 부분 클릭 → New Branch. Commit은 `Ctrl+K`, Push는 `Ctrl+Shift+K`
- **Eclipse**: 프로젝트 우클릭 > Team > Switch To > New Branch. Commit은 Team > Commit, Push는 Team > Push to Upstream
- PR은 두 경우 다 결국 GitHub 웹사이트에서 만듭니다 (IDE에서 push까지만 하고, PR 생성은 브라우저에서).
