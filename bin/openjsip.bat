@echo off

REM !!! Set here the OpenJSIP installation directory WITHOUT slash at the end. !!!
set base_dir=c:\openjsip

set CLASSPATH="lib\jain-sdp-1.0.115.jar;lib\jain-sip-api-1.2.jar;lib\jain-sip-ri-1.2.115.jar;lib\jain-sip-sdp-1.2.115.jar;lib\log4j-1.2.15.jar;lib\snmp.jar;lib\openjsip.jar;lib\openjsip-remote.jar"

cd %base_dir%

if not "%1"=="start" goto usage
if "%2"=="location-service" goto start_location_service
if "%2"=="registrar" goto start_registrar
if "%2"=="proxy" goto start_proxy

:usage
echo openjsip.bat {command} {service}
echo where {command} is one of:
echo    start              - start the service

echo where {service} is one of:
echo    location-service   - SIP Location Service
echo    registrar          - SIP Registrar
echo    proxy              - SIP Proxy

goto end


:start_location_service
rem @echo on
java -Djava.rmi.server.codebase="file:/%base_dir%\lib\openjsip-remote.jar file:/%base_dir%\lib\jain-sip-api-1.2.jar" -Djava.security.policy=%base_dir%\policy\policy.all openjsip.locationservice.LocationService conf\location-service.properties
rem @echo off
goto end

:start_registrar
rem @echo on
java -Djava.rmi.server.codebase="file:/%base_dir%\lib\openjsip-remote.jar file:/%base_dir%\lib\jain-sip-api-1.2.jar" -Djava.security.policy=%base_dir%\policy\policy.all openjsip.registrar.Registrar conf\registrar.properties
rem @echo off
goto end

:start_proxy
rem @echo on
java -Djava.rmi.server.codebase="file:/%base_dir%\lib\openjsip-remote.jar file:/%base_dir%\lib\jain-sip-api-1.2.jar" -Djava.security.policy=%base_dir%\policy\policy.all openjsip.proxy.Proxy conf\proxy.properties
rem @echo off
goto end


:end
