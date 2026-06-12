Stop-ScheduledTask -TaskName "GroceryAlertServer" -ErrorAction SilentlyContinue
$proc = Get-Process -Name "pythonw" -ErrorAction SilentlyContinue | Where-Object { $_.Id -ne $pid }
if ($proc) { $proc | Stop-Process -Force }
Write-Output "Server stopped."
