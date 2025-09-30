@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set "DIR=%~dp0"
if "%DIR%"=="" set DIR=.
set "DIR=%DIR:~0,-1%"
set "MAVEN_PROJECTBASEDIR=%DIR%"

set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

if exist "%MAVEN_PROJECTBASEDIR%\.mvn\maven.config" (
  set /p MAVEN_CONFIG=<"%MAVEN_PROJECTBASEDIR%\.mvn\maven.config"
)

if exist "%WRAPPER_JAR%" goto execute

if exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper" goto download
mkdir "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper"

:download
set DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
if exist "%WRAPPER_JAR%" goto execute
powershell -Command "Invoke-WebRequest -Uri %DOWNLOAD_URL% -OutFile '%WRAPPER_JAR:"=%'"" >NUL 2>&1
if exist "%WRAPPER_JAR%" goto execute
if defined MVNW_VERBOSE echo Failed to download %WRAPPER_JAR% from %DOWNLOAD_URL%, falling back to system Maven if available. >&2

for %%I in (mvn.cmd mvn.bat mvn.exe mvn) do (
  where %%I >NUL 2>&1 && (set "MVN_CMD=%%I" & goto run_mvn)
)
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.bat" set "MVN_CMD=%MAVEN_HOME%\bin\mvn.bat"
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.exe" set "MVN_CMD=%MAVEN_HOME%\bin\mvn.exe"
if defined MVN_CMD goto run_mvn

echo Error: Failed to download %WRAPPER_JAR% and no system Maven (mvn) command is available. >&2
exit /b 1

:execute
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if defined JAVA_HOME if exist "%JAVA_EXE%" goto run_wrapper

set "JAVA_EXE=java"
"%JAVA_EXE%" -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto run_wrapper

echo Error: JAVA_HOME is not defined correctly. >&2
echo   We cannot execute java >&2
exit /b 1

:run_wrapper
"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
endlocal
exit /b %ERRORLEVEL%

:run_mvn
"%MVN_CMD%" -f "%MAVEN_PROJECTBASEDIR%\pom.xml" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" %*
endlocal
exit /b %ERRORLEVEL%
