param(
    [Parameter(Mandatory = $true)]
    [string] $TomcatHome
)

$ErrorActionPreference = "Stop"

$serverXml = Join-Path $TomcatHome "conf\server.xml"
$resourceXml = Join-Path $PSScriptRoot "..\config\tomcat\global-resource.xml"
if (-not (Test-Path -LiteralPath $serverXml -PathType Leaf)) {
    throw "Tomcat server configuration was not found: $serverXml"
}

$document = [System.Xml.XmlDocument]::new()
$document.PreserveWhitespace = $true
$document.XmlResolver = $null
$settings = [System.Xml.XmlReaderSettings]::new()
$settings.DtdProcessing = [System.Xml.DtdProcessing]::Parse
$settings.XmlResolver = $null
$reader = [System.Xml.XmlReader]::Create($serverXml, $settings)
try {
    $document.Load($reader)
} finally {
    $reader.Dispose()
}

$globalResources = $document.SelectSingleNode("/Server/GlobalNamingResources")
$engine = $document.SelectSingleNode("/Server/Service/Engine")
if ($null -eq $globalResources -or $null -eq $engine) {
    throw "Tomcat server.xml must contain GlobalNamingResources and a Service Engine."
}

if ($document.DocumentElement.GetAttribute("port") -eq "8005") {
    $document.DocumentElement.SetAttribute("port", "18005")
}

$resourceDocument = [System.Xml.XmlDocument]::new()
$resourceDocument.Load((Resolve-Path -LiteralPath $resourceXml))
$resource = $document.ImportNode($resourceDocument.DocumentElement, $true)
$oldResource = $globalResources.SelectSingleNode("Resource[@name='jdbc/todo_db']")
if ($null -ne $oldResource) {
    [void] $globalResources.ReplaceChild($resource, $oldResource)
} else {
    [void] $globalResources.AppendChild($resource)
}

$realmXml = @'
<Realm className="org.apache.catalina.realm.DataSourceRealm"
       dataSourceName="jdbc/todo_db"
       userTable="users"
       userNameCol="username"
       userCredCol="password_hash"
       userRoleTable="user_roles"
       roleNameCol="role_name">
    <CredentialHandler className="org.apache.catalina.realm.SecretKeyCredentialHandler"
                       algorithm="PBKDF2WithHmacSHA256"
                       iterations="310000"
                       keyLength="256"
                       saltLength="16" />
</Realm>
'@
$realmDocument = [System.Xml.XmlDocument]::new()
$realmDocument.LoadXml($realmXml)
$dataSourceRealm = $document.ImportNode($realmDocument.DocumentElement, $true)

$lockOutRealm = $engine.SelectSingleNode("Realm[@className='org.apache.catalina.realm.LockOutRealm']")
if ($null -ne $lockOutRealm) {
    $realmParent = $lockOutRealm.SelectSingleNode("Realm")
    if ($null -ne $realmParent -and $realmParent.GetAttribute("className") -eq "org.apache.catalina.realm.CombinedRealm") {
        $combinedRealm = $realmParent
    } else {
        $combinedRealm = $document.CreateElement("Realm")
        $combinedRealm.SetAttribute("className", "org.apache.catalina.realm.CombinedRealm")
        if ($null -ne $realmParent) {
            [void] $lockOutRealm.ReplaceChild($combinedRealm, $realmParent)
            [void] $combinedRealm.AppendChild($realmParent)
        } else {
            [void] $lockOutRealm.AppendChild($combinedRealm)
        }
    }
    foreach ($configuredRealm in @($combinedRealm.SelectNodes("Realm[@className='org.apache.catalina.realm.DataSourceRealm']"))) {
        [void] $combinedRealm.RemoveChild($configuredRealm)
    }
    [void] $combinedRealm.AppendChild($dataSourceRealm)
} else {
    $existingRealm = $engine.SelectSingleNode("Realm")
    if ($null -ne $existingRealm) {
        [void] $engine.ReplaceChild($dataSourceRealm, $existingRealm)
    } else {
        [void] $engine.AppendChild($dataSourceRealm)
    }
}

$backupXml = "$serverXml.riegsecker-backup"
if (-not (Test-Path -LiteralPath $backupXml -PathType Leaf)) {
    Copy-Item -LiteralPath $serverXml -Destination $backupXml
}
$writerSettings = [System.Xml.XmlWriterSettings]::new()
$writerSettings.Encoding = [System.Text.UTF8Encoding]::new($false)
$writerSettings.Indent = $false
$temporaryXml = "$serverXml.riegsecker-tmp"
$writer = [System.Xml.XmlWriter]::Create($temporaryXml, $writerSettings)
try {
    $document.Save($writer)
} finally {
    $writer.Dispose()
}
Move-Item -LiteralPath $temporaryXml -Destination $serverXml -Force

Write-Host "Configured global jdbc/todo_db and the DataSourceRealm in $serverXml"
Write-Host "Original configuration backup: $backupXml"
