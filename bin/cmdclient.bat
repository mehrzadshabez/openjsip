@echo off

REM !!! Set here the OpenJSIP installation directory WITHOUT slash at the end. !!!
set base_dir=c:\openjsip

cd %base_dir%

set CLASSPATH="lib\jain-sdp-1.0.115.jar;lib\jain-sip-api-1.2.jar;lib\jain-sip-ri-1.2.115.jar;lib\jain-sip-sdp-1.2.115.jar;lib\log4j-1.2.15.jar;lib\snmp.jar;lib\openjsip.jar;lib\openjsip-remote.jar"

java -cp %CLASSPATH% openjsip.CmdClient %*

