$desktop = [Environment]::GetFolderPath("Desktop")
$scriptsDir = "C:\Users\91935\AppData\Local\Temp\opencode\grocery-alert"

function Create-HotkeyShortcut($name, $scriptName, $hotkey, $desc) {
    $linkPath = Join-Path $desktop "$name.lnk"
    $wshell = New-Object -ComObject WScript.Shell
    $shortcut = $wshell.CreateShortcut($linkPath)
    $shortcut.TargetPath = "powershell.exe"
    $shortcut.Arguments = "-ExecutionPolicy Bypass -NoProfile -File `"$scriptsDir\$scriptName`""
    $shortcut.WorkingDirectory = $scriptsDir
    $shortcut.Description = $desc
    $shortcut.HotKey = $hotkey
    $shortcut.WindowStyle = 7  # Minimized
    $shortcut.Save()
    Write-Output "Created: $linkPath  ($hotkey)"
}

Create-HotkeyShortcut "Start Grocery Server" "start-server.ps1" "Ctrl+Alt+S" "Start the grocery alert WebSocket server"
Create-HotkeyShortcut "Stop Grocery Server"  "stop-server.ps1"  "Ctrl+Alt+X" "Stop the grocery alert WebSocket server"
