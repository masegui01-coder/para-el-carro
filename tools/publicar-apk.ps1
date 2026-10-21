# Compila la APK de pruebas y la deja en Google Drive (G:\Mi unidad\Para el Carro) para instalarla
# desde el móvil. Siempre con el mismo nombre (ParaElCarro.apk) y una copia con la versión.
# Uso: powershell -File tools\publicar-apk.ps1
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$drive = 'G:\Mi unidad\Para el Carro'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

Push-Location (Join-Path $raiz 'android')
try {
    & .\gradlew.bat assembleDebug --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación' }
} finally { Pop-Location }

$apk = Join-Path $raiz 'android\app\build\outputs\apk\debug\app-debug.apk'
$version = (Select-String -Path (Join-Path $raiz 'android\app\build.gradle.kts') -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
New-Item -ItemType Directory -Force $drive | Out-Null
Copy-Item $apk (Join-Path $drive 'ParaElCarro.apk') -Force
Copy-Item $apk (Join-Path $drive "ParaElCarro-v$version.apk") -Force
# Solo se guardan las 3 versiones más recientes con número
Get-ChildItem $drive -Filter 'ParaElCarro-v*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 3 | Remove-Item
Write-Host "APK v$version copiada a $drive"
