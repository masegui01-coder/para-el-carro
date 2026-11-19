# Genera el AAB firmado para subirlo a Google Play (prueba cerrada / producción).
# Uso: powershell -File tools\publicar-play.ps1
# Requisitos en %USERPROFILE%\.gradle\gradle.properties (ver docs\google-play\README.md):
#   uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword
#   admobAppId, admobBannerId, admobInterstitialId
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

# Comprobaciones antes de compilar: sin clave de subida Google Play rechaza el AAB,
# y sin los IDs de AdMob la app saldría con anuncios de prueba.
$props = Join-Path $env:USERPROFILE '.gradle\gradle.properties'
$texto = if (Test-Path $props) { Get-Content $props -Raw } else { '' }
$faltan = @('uploadStoreFile', 'uploadStorePassword', 'uploadKeyPassword') | Where-Object { $texto -notmatch "(?m)^\s*$_\s*=\s*\S" }
if ($faltan) { throw "Faltan o están vacíos en $props : $($faltan -join ', '). Ejecuta tools\configurar-clave.ps1." }
$sinAdmob = @('admobAppId', 'admobBannerId', 'admobInterstitialId') | Where-Object { $texto -notmatch "(?m)^\s*$_\s*=" }
if ($sinAdmob) { Write-Warning "Sin $($sinAdmob -join ', '): la app llevará anuncios de PRUEBA de Google." }

Push-Location (Join-Path $raiz 'android')
try {
    & .\gradlew.bat testDebugUnitTest bundleRelease --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron los tests o la compilación' }
} finally { Pop-Location }

$version = (Select-String -Path (Join-Path $raiz 'android\app\build.gradle.kts') -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
$codigo = (Select-String -Path (Join-Path $raiz 'android\app\build.gradle.kts') -Pattern 'versionCode = (\d+)').Matches[0].Groups[1].Value
$aab = Join-Path $raiz 'android\app\build\outputs\bundle\release\app-release.aab'
$destino = Join-Path $raiz "ParaElCarro-v$version-$codigo.aab"
Copy-Item $aab $destino -Force
Write-Host "AAB listo: $destino"
Write-Host "Súbelo en Play Console > Probar y publicar > Pruebas > Prueba cerrada > Crear versión."
Write-Host "Recuerda: cada subida necesita un versionCode mayor (ahora $codigo) en android\app\build.gradle.kts."
