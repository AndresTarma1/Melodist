// smtc_bridge.cpp - DLL C++/WinRT: SMTC via ISystemMediaTransportControlsInterop::GetForWindow.
// La app la llama via JNA/FFM desde su hilo con message pump.
#include <windows.h>
#include <shlobj.h>
#include <propvarutil.h>
#include <roapi.h>
#include <SystemMediaTransportControlsInterop.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Storage.Streams.h>

using namespace winrt;
using namespace winrt::Windows::Media;

// PKEY_AppUserModel_ID: para que el panel de medios muestre el nombre de la app,
// el acceso directo del menú Inicio debe llevar el mismo AppUserModelID que el proceso.
static const PROPERTYKEY PKEY_AppUserModel_ID = { {0x9F4C2855,0x9F79,0x4B39,{0xA8,0xD0,0xE1,0xD4,0x2D,0xE1,0xD5,0xF3}}, 5 };

static winrt::Windows::Media::ISystemMediaTransportControls g_smtc{ nullptr };
static winrt::event_token g_buttonToken{};

static void (*g_onPlay)() = nullptr;
static void (*g_onPause)() = nullptr;
static void (*g_onNext)() = nullptr;
static void (*g_onPrevious)() = nullptr;
static void (*g_onStop)() = nullptr;

extern "C" {

__declspec(dllexport) int __cdecl smtc_init(void* hwnd)
{
    if (g_smtc) return 0;
    try {
        // GetForWindow exige una ventana top-level válida del proceso. IsWindow distingue
        // "hwnd inválido" (retorno -2) de un fallo de GetForWindow (hr).
        if (!IsWindow(static_cast<HWND>(hwnd))) return -2;
        winrt::init_apartment();
        winrt::hstring cls(L"Windows.Media.SystemMediaTransportControls");
        winrt::com_ptr<ISystemMediaTransportControlsInterop> interop;
        HRESULT hr = RoGetActivationFactory(
            static_cast<HSTRING>(winrt::get_abi(cls)),
            __uuidof(ISystemMediaTransportControlsInterop),
            reinterpret_cast<void**>(interop.put()));
        if (FAILED(hr)) return (int)hr;

        void* smtcPtr = nullptr;
        hr = interop->GetForWindow(static_cast<HWND>(hwnd),
                                   winrt::guid_of<winrt::Windows::Media::ISystemMediaTransportControls>(),
                                   &smtcPtr);
        if (FAILED(hr) || !smtcPtr) return (int)hr;

        g_smtc = winrt::Windows::Media::ISystemMediaTransportControls(smtcPtr, winrt::take_ownership_from_abi);
        g_buttonToken = g_smtc.ButtonPressed([](ISystemMediaTransportControls const&, SystemMediaTransportControlsButtonPressedEventArgs const& args) {
            switch (args.Button()) {
                case SystemMediaTransportControlsButton::Play:     if (g_onPlay) g_onPlay(); break;
                case SystemMediaTransportControlsButton::Pause:    if (g_onPause) g_onPause(); break;
                case SystemMediaTransportControlsButton::Next:     if (g_onNext) g_onNext(); break;
                case SystemMediaTransportControlsButton::Previous: if (g_onPrevious) g_onPrevious(); break;
                case SystemMediaTransportControlsButton::Stop:     if (g_onStop) g_onStop(); break;
                default: break;
            }
        });
        return 0;
    } catch (hresult_error const& e) {
        return (int)e.code();
    } catch (...) {
        return -1;
    }
}

__declspec(dllexport) void __cdecl smtc_enable(bool enabled)
{
    if (!g_smtc) return;
    g_smtc.IsEnabled(enabled);
}

__declspec(dllexport) void __cdecl smtc_set_playback_state(int state)
{
    if (!g_smtc) return;
    g_smtc.PlaybackStatus(state == 3 ? MediaPlaybackStatus::Playing
                       : state == 1 ? MediaPlaybackStatus::Paused
                                    : MediaPlaybackStatus::Stopped);
}

__declspec(dllexport) void __cdecl smtc_set_buttons(bool play, bool pause, bool next, bool previous, bool stop)
{
    if (!g_smtc) return;
    g_smtc.IsPlayEnabled(play);
    g_smtc.IsPauseEnabled(pause);
    g_smtc.IsNextEnabled(next);
    g_smtc.IsPreviousEnabled(previous);
    g_smtc.IsStopEnabled(stop);
}

__declspec(dllexport) void __cdecl smtc_set_metadata(const wchar_t* title, const wchar_t* artist, const wchar_t* album)
{
    if (!g_smtc) return;
    auto updater = g_smtc.DisplayUpdater();
    updater.Type(MediaPlaybackType::Music);
    updater.MusicProperties().Title(title ? title : L"");
    updater.MusicProperties().Artist(artist ? artist : L"");
    updater.MusicProperties().AlbumTitle(album ? album : L"");
    updater.Update();
}

__declspec(dllexport) void __cdecl smtc_clear_metadata()
{
    if (!g_smtc) return;
    auto updater = g_smtc.DisplayUpdater();
    updater.ClearAll();
    updater.Update();
}

__declspec(dllexport) void __cdecl smtc_set_callbacks(void (*onPlay)(), void (*onPause)(), void (*onNext)(), void (*onPrevious)(), void (*onStop)())
{
    g_onPlay = onPlay;
    g_onPause = onPause;
    g_onNext = onNext;
    g_onPrevious = onPrevious;
    g_onStop = onStop;
}

__declspec(dllexport) void __cdecl smtc_release()
{
    if (g_smtc) {
        g_smtc.IsEnabled(false);
        g_smtc = nullptr;
    }
}

// ── AppUserModelID del acceso directo ───────────────────────────────────────

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

} // extern "C"
