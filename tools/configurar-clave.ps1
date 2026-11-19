# Configura la clave de subida a Google Play en un solo paso (botón ▶ en VS Code o PowerShell ISE,
# o: powershell -ExecutionPolicy Bypass -File tools\configurar-clave.ps1).
#
# - Si ya existe C:\Users\<tú>\claves\paraelcarro-upload.jks, pide su contraseña y la comprueba.
#   Si no la recuerdas, ofrece crear una clave nueva (solo antes de la primera subida a Play).
# - Si no existe, la crea con una contraseña aleatoria segura (sin ñ ni tildes).
# - Escribe la configuración en ~/.gradle/gradle.properties (sin duplicar líneas y conservando las demás).
# - Copia el .jks a Google Drive (G:\Mi unidad\Claves) si está disponible.
$ErrorActionPreference = 'Stop'

$keytool = 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
$carpeta = Join-Path $env:USERPROFILE 'claves'
$jks = Join-Path $carpeta 'paraelcarro-upload.jks'
$props = Join-Path $env:USERPROFILE '.gradle\gradle.properties'
$copiaDrive = 'G:\Mi unidad\Claves'
$alias = 'upload'

if (-not (Test-Path $keytool)) { throw "No encuentro keytool en $keytool. ¿Está instalado Android Studio?" }

# En Windows PowerShell 5.1, con 'Stop', lo que keytool escribe en stderr se convierte en error: se relaja dentro de las funciones.
function Probar-Clave([string]$pass) {
    $ErrorActionPreference = 'Continue'
    & $keytool -list -keystore $jks -storepass $pass -alias $alias *> $null
    return $LASTEXITCODE -eq 0
}

function Nueva-Clave {
    $ErrorActionPreference = 'Continue'
    # 24 caracteres alfanuméricos: seguros y sin problemas de codificación en gradle.properties
    $chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 24
    $rng.GetBytes($bytes)
    $pass = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })

    New-Item -ItemType Directory -Force $carpeta | Out-Null
    if (Test-Path $jks) {
        $viejo = "$jks.antiguo-$(Get-Date -Format yyyyMMdd-HHmmss)"
        Move-Item $jks $viejo
        Write-Host "La clave anterior se ha guardado como $viejo"
    }
    & $keytool -genkeypair -keystore $jks -alias $alias -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $pass -keypass $pass -dname 'CN=Tiza Estudio Games, L=Cordoba, ST=Cordoba, C=ES' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'keytool no ha podido crear la clave' }
    Write-Host "Clave nueva creada: $jks" -ForegroundColor Green
    return $pass
}

# 1. Conseguir una clave y su contraseña válidas
$nueva = $false
if (Test-Path $jks) {
    Write-Host "Ya tienes una clave: $jks"
    $pass = $null
    for ($i = 1; $i -le 3 -and -not $pass; $i++) {
        $seguro = Read-Host "Contraseña del .jks (intento $i de 3)" -AsSecureString
        $plano = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
        if (Probar-Clave $plano) { $pass = $plano; Write-Host 'Contraseña correcta.' -ForegroundColor Green }
        else { Write-Host 'Contraseña incorrecta.' -ForegroundColor Yellow }
    }
    if (-not $pass) {
        $r = Read-Host '¿Crear una clave NUEVA? Solo si aún no has subido ningún AAB a Google Play (s/n)'
        if ($r -notmatch '^[sS]') { throw 'Cancelado: no se ha cambiado nada.' }
        $pass = Nueva-Clave; $nueva = $true
    } elseif ($pass -match '[^\x20-\x7E]') {
        Write-Warning 'Tu contraseña tiene ñ, tildes u otros caracteres especiales y Gradle podría leerla mal.'
        $r = Read-Host '¿Crear una clave nueva con contraseña automática? Solo si aún no has subido nada a Play (s/n)'
        if ($r -match '^[sS]') { $pass = Nueva-Clave; $nueva = $true }
    }
} else {
    $pass = Nueva-Clave; $nueva = $true
}

# 2. Escribir gradle.properties sin duplicados, conservando el resto de líneas
New-Item -ItemType Directory -Force (Split-Path $props) | Out-Null
# [string[]] es necesario: si solo queda una línea, PowerShell la convierte en texto y "+=" las pegaría todas en una
[string[]]$lineas = if (Test-Path $props) { Get-Content $props | Where-Object { $_ -notmatch '^\s*upload(StoreFile|StorePassword|KeyAlias|KeyPassword)\s*=' -and $_.Trim() -ne '' } } else { @() }
# Arregla el fichero si una versión anterior de este script pegó las claves al final de otra línea
$lineas = @($lineas | ForEach-Object { $_ -replace 'uploadStoreFile=.*$', '' } | Where-Object { $_.Trim() -ne '' })
$lineas += @(
    "uploadStoreFile=$($jks -replace '\\', '/')",
    "uploadStorePassword=$pass",
    "uploadKeyAlias=$alias",
    "uploadKeyPassword=$pass"
)
Set-Content $props -Value $lineas -Encoding ASCII
Write-Host "Configuración guardada en $props" -ForegroundColor Green

# 3. Copia de seguridad en Google Drive
if (Test-Path 'G:\Mi unidad') {
    New-Item -ItemType Directory -Force $copiaDrive | Out-Null
    Copy-Item $jks $copiaDrive -Force
    Write-Host "Copia de seguridad: $copiaDrive\paraelcarro-upload.jks" -ForegroundColor Green
} else {
    Write-Warning 'No encuentro G:\Mi unidad (Google Drive). Copia el .jks a mano a un sitio fuera del PC.'
}

# 4. Huella SHA-256 (para assetlinks.json y Firebase)
$ErrorActionPreference = 'Continue'
# keytool en inglés para que la línea se llame siempre "SHA256:" (en español cambia el texto)
$salida = & $keytool -J-Duser.language=en -list -v -keystore $jks -storepass $pass -alias $alias 2>&1 | Out-String
$huella = if ($salida -match 'SHA-?256:\s*([0-9A-F:]{95})') { $Matches[1] } else { '(no se pudo leer; no es importante)' }

Write-Host ''
Write-Host '================ GUARDA ESTO EN TU GESTOR DE CONTRASEÑAS ================' -ForegroundColor Cyan
if ($nueva) {
    Write-Host "Contraseña del .jks: $pass" -ForegroundColor Cyan
    Set-Clipboard -Value $pass
    Write-Host '(Ya está copiada al portapapeles: pégala en passwords.google.com)' -ForegroundColor Cyan
} else {
    Write-Host 'Contraseña: la que acabas de escribir.' -ForegroundColor Cyan
}
Write-Host "Archivo: $jks   ·   Alias: $alias"
Write-Host "Huella de la clave de subida: $huella"
Write-Host '==========================================================================' -ForegroundColor Cyan
Write-Host ''
Write-Host 'Siguiente paso: tools\publicar-play.ps1 (genera el AAB para Google Play).'
