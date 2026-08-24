# Recompiles Project01 and copies it into Tomcat's webapps/dmart.
# Usage: from the Project01 folder, run  .\deploy-tomcat.ps1
# Re-run this after every code change, then restart Tomcat (it does not hot-reload classes).

$CatalinaHome = "C:\Jsp\Tomcat 10.1"
$JavaHome = "C:\Java\jdk-24"
$WebappName = "dmart"

$ProjectRoot = $PSScriptRoot
$Webapp = Join-Path $CatalinaHome "webapps\$WebappName"
$Classes = Join-Path $Webapp "WEB-INF\classes"
$Lib = Join-Path $Webapp "WEB-INF\lib"

Write-Host "1. Compiling sources..."
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
New-Item -ItemType Directory -Force -Path $Lib | Out-Null

# lib 폴더 안의 jar 전부를 컴파일 클래스패스로 씁니다.
# (예전엔 mysql/servlet-api 2개만 하드코딩했는데, 안주희 님 리포트/통계/내보내기
#  기능이 쓰는 POI·OpenCSV·OpenPDF 등이 추가되면서 그 방식으로는 관리가 안 돼서
#  lib 폴더 안 jar를 자동으로 다 긁어오는 방식으로 바꿨습니다. 새 jar가 생겨도
#  lib 폴더에 넣기만 하면 이 스크립트는 그대로 씁니다.)
$LibJars = Get-ChildItem -Path (Join-Path $ProjectRoot "lib") -Filter "*.jar" | ForEach-Object { $_.FullName }
$Cp = $LibJars -join ";"
$JavaFiles = Get-ChildItem -Path (Join-Path $ProjectRoot "com") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

& "$JavaHome\bin\javac.exe" -encoding UTF-8 -cp $Cp -d $Classes $JavaFiles
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compile failed - deploy aborted" -ForegroundColor Red
    exit 1
}

Write-Host "2. Copying config and libraries..."
Copy-Item (Join-Path $ProjectRoot "db.properties") $Classes -Force
# 컴파일에 쓴 jar 전부를 톰캣이 실제로 돌 때 찾는 WEB-INF/lib 에도 그대로 복사합니다.
Copy-Item (Join-Path $ProjectRoot "lib\*.jar") $Lib -Force
Copy-Item (Join-Path $ProjectRoot "WEB-INF\web.xml") (Join-Path $Webapp "WEB-INF\web.xml") -Force

Write-Host "3. Copying static files (html/css/js/images)..."
foreach ($dir in @("html", "css", "js", "images")) {
    $src = Join-Path $ProjectRoot $dir
    if (Test-Path $src) {
        Copy-Item $src $Webapp -Recurse -Force
    }
}

Write-Host "4. Done. Deployed to: $Webapp" -ForegroundColor Green
Write-Host "Restart Tomcat to pick up the change:"
Write-Host "  `$env:CATALINA_HOME = '$CatalinaHome'; `$env:JAVA_HOME = '$JavaHome'"
Write-Host "  Invoke-Expression `"$CatalinaHome\bin\shutdown.bat`""
Write-Host "  Invoke-Expression `"$CatalinaHome\bin\startup.bat`""
Write-Host ""
Write-Host "API base URL: http://localhost:8080/$WebappName"
