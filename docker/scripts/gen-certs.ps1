#Requires -Version 5.1
param(
    [string]$PublicHost = "",
    [string]$TurnHost = "",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$dockerDir = Split-Path $PSScriptRoot -Parent
if (-not $OutDir) { $OutDir = Join-Path $dockerDir "certs" }

$envFile = Join-Path $dockerDir ".env"
if (-not $PublicHost -and (Test-Path $envFile)) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^PUBLIC_HOST=(.+)$') { $PublicHost = $Matches[1].Trim() }
        if ($_ -match '^TURN_HOST=(.+)$') { $TurnHost = $Matches[1].Trim() }
    }
}
if (-not $PublicHost) { throw "Укажите -PublicHost или PUBLIC_HOST в docker/.env" }
if (-not $TurnHost) { $TurnHost = "turn.$PublicHost" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$key = Join-Path $OutDir "privkey.pem"
$cert = Join-Path $OutDir "fullchain.pem"
$cfg = Join-Path $OutDir "san.cnf"

@"
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = req_ext
x509_extensions = req_ext

[dn]
CN = $PublicHost

[req_ext]
subjectAltName = @alt

[alt]
DNS.1 = $PublicHost
DNS.2 = $TurnHost
"@ | Set-Content -Path $cfg -Encoding ascii

openssl req -x509 -nodes -newkey rsa:2048 -days 825 `
    -keyout $key -out $cert -config $cfg
Remove-Item $cfg -Force
Write-Host "OK: $cert"
Write-Host "OK: $key"
Write-Host "SAN: $PublicHost, $TurnHost"
