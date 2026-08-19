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

$Cp = (Join-Path $ProjectRoot "lib\mysql-connector-j-26.7.0.jar") + ";" + (Join-Path $ProjectRoot "lib\jakarta.servlet-api-6.0.0.jar")
$JavaFiles = Get-ChildItem -Path (Join-Path $ProjectRoot "com") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

& "$JavaHome\bin\javac.exe" -encoding UTF-8 -cp $Cp -d $Classes $JavaFiles
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compile failed - deploy aborted" -ForegroundColor Red
    exit 1
}

Write-Host "2. Copying config and libraries..."
Copy-Item (Join-Path $ProjectRoot "db.properties") $Classes -Force
Copy-Item (Join-Path $ProjectRoot "lib\mysql-connector-j-26.7.0.jar") $Lib -Force
Copy-Item (Join-Path $ProjectRoot "WEB-INF\web.xml") (Join-Path $Webapp "WEB-INF\web.xml") -Force

Write-Host "3. Done. Deployed to: $Webapp" -ForegroundColor Green
Write-Host "Restart Tomcat to pick up the change:"
Write-Host "  `$env:CATALINA_HOME = '$CatalinaHome'; `$env:JAVA_HOME = '$JavaHome'"
Write-Host "  Invoke-Expression `"$CatalinaHome\bin\shutdown.bat`""
Write-Host "  Invoke-Expression `"$CatalinaHome\bin\startup.bat`""
Write-Host ""
Write-Host "API base URL: http://localhost:8080/$WebappName"
