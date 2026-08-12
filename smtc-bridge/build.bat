@echo off
rem Builds smtc_bridge.dll (AppUserModelID del acceso directo del menú Inicio).
rem El SMTC lo maneja Nucleus (nucleus.media-control); esta DLL solo conserva el fix del .lnk.
rem Requires Visual Studio 2022 + Windows SDK 10.0.26100 (ajusta la ruta del SDK si es otra).
call "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
cl /nologo /EHsc /std:c++17 /W3 /DUNICODE /D_UNICODE /LD smtc_bridge.cpp /link /DLL /SUBSYSTEM:CONSOLE ole32.lib user32.lib propsys.lib shell32.lib /OUT:smtc_bridge.dll
if exist smtc_bridge.dll (
    copy /y smtc_bridge.dll ..\mpv-resources\windows\smtc_bridge.dll
    echo OK: smtc_bridge.dll copiada a ..\mpv-resources\windows\
) else (
    echo ERROR: no se genero smtc_bridge.dll
)
