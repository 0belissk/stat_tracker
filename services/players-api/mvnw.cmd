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

@SETLOCAL ENABLEDELAYEDEXPANSION

set "DIR=%~dp0"
if "%DIR%" == "" set DIR=.
set "BASE_DIR=%DIR%"

set "WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

if exist "%WRAPPER_JAR%" goto execute

if exist "%BASE_DIR%\.mvn\wrapper" goto download
mkdir "%BASE_DIR%\.mvn\wrapper"

:download
set DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
if exist "%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar" goto execute
powershell -Command "Invoke-WebRequest -Uri %DOWNLOAD_URL% -OutFile '%WRAPPER_JAR%'" || (
  echo Error: Please install PowerShell 3 or newer to download the Maven Wrapper.
  exit /b 1
)

:execute
"%JAVA_HOME%\bin\java" -classpath "%WRAPPER_JAR%" %WRAPPER_LAUNCHER% %*
