// smtc_bridge.cpp - DLL C++/WinRT: SMTC via ISystemMediaTransportControlsInterop::GetForWindow.
// La app la llama via JNA/FFM desde su hilo con message pump.
#include <windows.h>
#include <roapi.h>
#include <SystemMediaTransportControlsInterop.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Storage.Streams.h>

using namespace winrt;
using namespace winrt::Windows::Media;

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

} // extern "C"
