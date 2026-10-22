# QA-only same-directory, no-overwrite publication. Readers never see the open writer.
function Write-NetworkHandshakeMarker([string]$Path) {
    $target=[IO.Path]::GetFullPath($Path)
    if([IO.Path]::GetFileName($target) -cnotin @('finish.request','stop.request')){throw 'Only fixed handshake markers are permitted.'}
    $directory=[IO.Path]::GetDirectoryName($target)
    $entry=Get-Item -LiteralPath $directory -Force -ErrorAction Stop
    while($null -ne $entry){
        if($entry.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Aliased marker path forbidden.'}
        $entry=$entry.Parent
    }
    $temporary=$target+'.tmp'
    if(Test-Path -LiteralPath $target){throw 'Existing marker preserved.'}
    $file=[IO.FileStream]::new($temporary,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$file.WriteByte(49);$file.Flush($true)}finally{$file.Dispose()}
    # Same volume, after close. File.Move refuses an existing destination.
    # On any failure the temporary is retained; do not pretend the signal was sent.
    [IO.File]::Move($temporary,$target)
}
