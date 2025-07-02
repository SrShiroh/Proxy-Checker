@echo off
echo ========================================
echo    ProxyChecker v1.0 - Ejecutor
echo ========================================
echo.

REM Verificar si Maven está disponible
where mvn >nul 2>&1
if %errorlevel% equ 0 (
    echo Maven encontrado. Compilando proyecto completo...
    mvn clean compile
    if %errorlevel% neq 0 (
        echo Error en la compilacion con Maven
        pause
        exit /b 1
    )
    echo.
    echo Ejecutando ProxyChecker con GUI...
    mvn exec:java -Dexec.mainClass="es.srshiroh.ProxyCheckerApp" -Dexec.args="--gui"
) else (
    echo Maven no encontrado en PATH del sistema.
    echo.
    echo Soluciones:
    echo 1. Agregar Maven al PATH del sistema
    echo 2. Usar la ruta completa de Maven
    echo 3. Ejecutar desde IDE (IntelliJ IDEA, Eclipse, etc.)
    echo.
    echo Ejemplo con ruta completa:
    echo "C:\ruta\a\maven\bin\mvn.cmd" clean compile
    echo "C:\ruta\a\maven\bin\mvn.cmd" exec:java -Dexec.mainClass="es.srshiroh.ProxyCheckerApp"
    echo.
    echo Ubicaciones comunes de Maven:
    echo - C:\apache-maven-3.x.x\bin\mvn.cmd
    echo - C:\tools\maven\bin\mvn.cmd
    echo - C:\Program Files\Apache\Maven\bin\mvn.cmd
    echo.
    pause
)
