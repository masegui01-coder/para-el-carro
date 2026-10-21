# Genera el icono de la app (Android adaptable + web) a partir de img/icono-original.jpg.
# Uso: powershell -ExecutionPolicy Bypass -File tools\build-icono.ps1
Add-Type -AssemblyName System.Drawing
$raiz = Split-Path $PSScriptRoot -Parent
$src = [System.Drawing.Image]::FromFile((Join-Path $raiz 'img\icono-original.jpg'))

function Recorte([int]$cx, [int]$cy, [int]$lado, [int]$px, [string]$destino) {
    $bmp = New-Object System.Drawing.Bitmap $px, $px
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $orig = New-Object System.Drawing.Rectangle ([int]($cx - $lado / 2)), ([int]($cy - $lado / 2)), $lado, $lado
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $px, $px), $orig, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    New-Item -ItemType Directory -Force (Split-Path $destino) | Out-Null
    $bmp.Save($destino, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# Android: capa de 108 dp; el sistema solo enseña el centro (~72 dp), así que el recorte es
# más amplio para que la burra y el carro queden en la zona visible.
$res = Join-Path $raiz 'android\app\src\main\res'
@{ mdpi = 108; hdpi = 162; xhdpi = 216; xxhdpi = 324; xxxhdpi = 432 }.GetEnumerator() | ForEach-Object {
    Recorte 390 360 700 $_.Value (Join-Path $res "mipmap-$($_.Key)\ic_launcher_fondo.png")
}

# Web y ficha de Google Play: recorte cerrado sobre la burra y el carro
Recorte 390 330 520 512 (Join-Path $raiz 'img\icono-512.png')
Recorte 390 330 520 192 (Join-Path $raiz 'img\icono-192.png')
Recorte 390 330 520 180 (Join-Path $raiz 'img\apple-touch-icon.png')
Recorte 390 330 520 32 (Join-Path $raiz 'img\favicon-32.png')
$src.Dispose()
'Iconos generados'
