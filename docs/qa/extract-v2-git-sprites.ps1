param(
    [Parameter(Mandatory = $true)] [string] $InventoryPath,
    [Parameter(Mandatory = $true)] [string] $ZipPath,
    [Parameter(Mandatory = $true)] [string] $GitMirrorRoot,
    [Parameter(Mandatory = $true)] [string] $AssetDirectory
)

$ErrorActionPreference = 'Stop'
[IO.Directory]::CreateDirectory($AssetDirectory) | Out-Null
$inventory = Get-Content -Raw -Encoding utf8 $InventoryPath | ConvertFrom-Json
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($ZipPath)
try {
    $entry = $zip.Entries | Where-Object FullName -like '*.html'
    $entryStream = $entry.Open()
    $memory = [IO.MemoryStream]::new()
    try {
        $entryStream.CopyTo($memory)
        $htmlBytes = $memory.ToArray()
        $html = [Text.Encoding]::UTF8.GetString($htmlBytes)
    } finally {
        $memory.Dispose()
        $entryStream.Dispose()
    }
} finally {
    $zip.Dispose()
}

$articles = [regex]::Matches($html, '<article class="card">(?<body>.*?)</article>', 'Singleline')
if ($articles.Count -ne 37) { throw "Expected 37 cards, found $($articles.Count)" }

$results = for ($index = 0; $index -lt $articles.Count; $index++) {
    $body = $articles[$index].Groups['body'].Value
    $urlMatch = [regex]::Match($body, '<a href="(?<v>https://github.com/[^"]+)"')
    $blobMatch = [regex]::Match($body, 'Git blob:\s*(?<v>[0-9a-f]{40})')
    $target = $inventory.cards[$index].suggested_target_file
    $targetPath = Join-Path $AssetDirectory $target

    if ($blobMatch.Success) {
        $sourceUrl = $urlMatch.Groups['v'].Value
        $gitDirectory = if ($sourceUrl -match 'rullerzhou-afk/clawd-on-desk') {
            Join-Path $GitMirrorRoot 'clawd-on-desk.git'
        } elseif ($sourceUrl -match 'abderrahimghazali/clawd-pet') {
            Join-Path $GitMirrorRoot 'clawd-pet.git'
        } else {
            throw "Unknown repository for $target"
        }
        $expectedBlob = $blobMatch.Groups['v'].Value
        $gitArguments = @("--git-dir=$gitDirectory", 'cat-file', 'blob', $expectedBlob)
        $gitProcess = Start-Process -FilePath 'git.exe' -ArgumentList $gitArguments `
            -RedirectStandardOutput $targetPath -WindowStyle Hidden -Wait -PassThru
        if ($gitProcess.ExitCode -ne 0) { throw "git cat-file failed for $target" }
        $actualBlob = (git hash-object $targetPath).Trim()
        if ($actualBlob -ne $expectedBlob) {
            throw "Git blob mismatch for $target`: expected $expectedBlob got $actualBlob"
        }
        $originType = 'git_blob'
        $templateId = $null
    } else {
        $sourceUrl = $null
        $expectedBlob = $null
        $originType = 'zip_custom_template'
        $templateId = $inventory.cards[$index].template_id
        $range = $inventory.cards[$index].byte_range
        $start = [int] $range.start_0_based
        $length = [int] $range.end_exclusive - $start
        $customBytes = [byte[]]::new($length)
        [Array]::Copy($htmlBytes, $start, $customBytes, 0, $length)
        [IO.File]::WriteAllBytes($targetPath, $customBytes)
    }

    $fileInfo = Get-Item -LiteralPath $targetPath
    $digest = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetPath).Hash.ToLowerInvariant()
    [ordered]@{
        target = $target
        origin_type = $originType
        source_url = $sourceUrl
        git_blob_sha = $expectedBlob
        template_id = $templateId
        bytes = $fileInfo.Length
        sha256 = $digest
    }
}

if (($results | Where-Object origin_type -eq 'git_blob').Count -ne 35) {
    throw 'Expected 35 Git-backed assets'
}
if (($results | Where-Object origin_type -eq 'zip_custom_template').Count -ne 2) {
    throw 'Expected 2 custom assets'
}
$results | ConvertTo-Json -Depth 3
