@echo off
echo Compilando ProxyChecker...

REM Crear directorio de salida
if not exist "build\classes" mkdir build\classes

REM Crear directorio de dependencias (simulado)
if not exist "lib" mkdir lib

echo.
echo Nota: Este proyecto requiere las siguientes dependencias de Maven:
echo - Apache HttpClient 5
echo - Jackson Databind
echo - Logback Classic
echo.
echo Para una compilacion completa, instale Maven y ejecute:
echo mvn clean compile
echo.

REM Compilar solo las clases que no dependen de bibliotecas externas
echo Compilando ProxyInfo.java...
javac -d build\classes src\main\java\es\srshiroh\ProxyInfo.java

if %errorlevel% neq 0 (
    echo Error compilando ProxyInfo.java
    pause
    exit /b 1
)

echo ProxyInfo.java compilado exitosamente.

echo.
echo Para ejecutar el proyecto completo:
echo 1. Instale Maven desde https://maven.apache.org/download.cgi
echo 2. Agregue Maven al PATH del sistema
echo 3. Ejecute: mvn clean compile
echo 4. Ejecute: mvn exec:java -Dexec.mainClass="es.srshiroh.ProxyCheckerApp"
echo.

pause
