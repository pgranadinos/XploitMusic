# Monitor AuraMusic Logs
# Ejecuta este script para ver los logs de búsqueda en tiempo real

Write-Host "=== Monitoreando logs de AuraMusic ===" -ForegroundColor Cyan
Write-Host "Busca algo en la aplicación y verás los resultados aquí" -ForegroundColor Yellow
Write-Host ""

adb logcat | Select-String -Pattern "AuraDebug|OkHttp" --Context 0,1
