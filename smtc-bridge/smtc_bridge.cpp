// smtc_bridge.dll — AppUserModelID del acceso directo del menú Inicio.
//
// El SMTC ya lo maneja Nucleus (`nucleus.media-control` / MediaControlService). Lo único que esta
// DLL conserva es `smtc_fix_shortcut_aumid`: pone el `System.AppUserModel.ID` en el acceso directo
// del menú Inicio que apunta a este exe, para que el panel de medios de Windows muestre el nombre
// de la app ("PaltaSound") y no "Aplicación Desconocida". Sin esto, una instalación fresca del MSI
// (cuyo acceso directo electron-builder crea sin AppUserModelID) volvería a mostrar "Aplicación
// Desconocida".
#include <windows.h>
#include <shlobj.h>
#include <propvarutil.h>

// PKEY_AppUserModel_ID: para que el panel de medios muestre el nombre de la app,
// el acceso directo del menú Inicio debe llevar el mismo AppUserModelID que el proceso.
static const PROPERTYKEY PKEY_AppUserModel_ID = { {0x9F4C2855,0x9F79,0x4B39,{0xA8,0xD0,0xE1,0xD4,0x2D,0xE1,0xD5,0xF3}}, 5 };

static bool set_lnk_aumid(const wchar_t* lnkPath, const wchar_t* aumid)
{
    IShellLinkW* link = nullptr;
    if (FAILED(CoCreateInstance(CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&link)))) return false;

    IPersistFile* persist = nullptr;
    if (FAILED(link->QueryInterface(IID_PPV_ARGS(&persist)))) { link->Release(); return false; }
    if (FAILED(persist->Load(lnkPath, STGM_READWRITE))) { persist->Release(); link->Release(); return false; }

    IPropertyStore* props = nullptr;
    if (FAILED(link->QueryInterface(IID_PPV_ARGS(&props)))) { persist->Release(); link->Release(); return false; }

    PROPVARIANT pv;
    PropVariantInit(&pv);
    bool ok = false;
    if (SUCCEEDED(InitPropVariantFromString(aumid, &pv))) {
        ok = SUCCEEDED(props->SetValue(PKEY_AppUserModel_ID, pv)) &&
             SUCCEEDED(props->Commit()) &&
             SUCCEEDED(persist->Save(lnkPath, TRUE));
        PropVariantClear(&pv);
    }
    props->Release(); persist->Release(); link->Release();
    return ok;
}

static bool search_lnk_dir(const wchar_t* dir, const wchar_t* exePath, const wchar_t* aumid)
{
    wchar_t pattern[MAX_PATH];
    wsprintfW(pattern, L"%s\\*", dir);

    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(pattern, &fd);
    if (h == INVALID_HANDLE_VALUE) return false;
    bool found = false;
    do {
        if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            if (wcscmp(fd.cFileName, L".") != 0 && wcscmp(fd.cFileName, L"..") != 0) {
                wchar_t sub[MAX_PATH];
                wsprintfW(sub, L"%s\\%s", dir, fd.cFileName);
                if (search_lnk_dir(sub, exePath, aumid)) { found = true; break; }
            }
            continue;
        }
        size_t len = wcslen(fd.cFileName);
        if (len < 4 || _wcsicmp(fd.cFileName + len - 4, L".lnk") != 0) continue;

        wchar_t lnkPath[MAX_PATH];
        wsprintfW(lnkPath, L"%s\\%s", dir, fd.cFileName);

        IShellLinkW* link = nullptr;
        if (FAILED(CoCreateInstance(CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&link)))) continue;
        IPersistFile* persist = nullptr;
        if (SUCCEEDED(link->QueryInterface(IID_PPV_ARGS(&persist))) && SUCCEEDED(persist->Load(lnkPath, STGM_READ))) {
            wchar_t target[MAX_PATH];
            if (SUCCEEDED(link->GetPath(target, MAX_PATH, NULL, 0)) &&
                _wcsicmp(target, exePath) == 0) {
                found = set_lnk_aumid(lnkPath, aumid);
                persist->Release(); link->Release();
                break;
            }
            persist->Release();
        }
        link->Release();
    } while (FindNextFileW(h, &fd) && !found);
    FindClose(h);
    return found;
}

// Busca el acceso directo del menú Inicio que apunta a este exe y le pone el AppUserModelID.
__declspec(dllexport) int __cdecl smtc_fix_shortcut_aumid(const wchar_t* aumid)
{
    wchar_t exePath[MAX_PATH];
    if (!GetModuleFileNameW(NULL, exePath, MAX_PATH)) return -1;

    const wchar_t* bases[] = {
        L"%APPDATA%\\Microsoft\\Windows\\Start Menu\\Programs",
        L"%ProgramData%\\Microsoft\\Windows\\Start Menu\\Programs",
    };
    for (const wchar_t* base : bases) {
        wchar_t expanded[MAX_PATH];
        if (ExpandEnvironmentStringsW(base, expanded, MAX_PATH) && search_lnk_dir(expanded, exePath, aumid)) {
            return 0;
        }
    }
    return -2; // no se encontró acceso directo (por ejemplo, en dev)
}
