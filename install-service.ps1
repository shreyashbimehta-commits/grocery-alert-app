$taskName = "GroceryAlertServer"
$pythonw = "C:\Users\91935\AppData\Local\Programs\Python\Python312\pythonw.exe"
$scriptPath = "C:\Users\91935\AppData\Local\Temp\opencode\grocery-alert\server.py"
$workDir = "C:\Users\91935\AppData\Local\Temp\opencode\grocery-alert"

# Create scheduled task to run at startup
$action = New-ScheduledTaskAction -Execute $pythonw -Argument "`"$scriptPath`"" -WorkingDirectory $workDir
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)

# Run as current user when logged on, with highest privileges
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Highest

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force

Write-Output "Scheduled task '$taskName' created successfully."
Write-Output ""
Write-Output "To start the server now:   Start-ScheduledTask -TaskName '$taskName'"
Write-Output "To stop the server:        Stop-ScheduledTask -TaskName '$taskName'"
Write-Output "To check status:           Get-ScheduledTask -TaskName '$taskName' | Get-ScheduledTaskInfo"
Write-Output "To uninstall:              Unregister-ScheduledTask -TaskName '$taskName' -Confirm:`$false"
