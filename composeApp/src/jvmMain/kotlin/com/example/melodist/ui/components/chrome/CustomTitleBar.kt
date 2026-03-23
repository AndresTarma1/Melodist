package com.example.melodist.ui.components

import WindowMargins
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.*
import com.sun.jna.win32.W32APIOptions

// ─────────────────────────────────────────────────────────────────────────────
// JNA interfaces
// Must be internal or public — NOT private — so JNA reflection can access it.
// ─────────────────────────────────────────────────────────────────────────────

@Suppress("FunctionName")
internal interface User32Ex : User32 {
    fun SetWindowLong(hWnd: HWND, nIndex: Int, wndProc: WindowProc): LONG_PTR
    fun SetWindowLongPtr(hWnd: HWND, nIndex: Int, wndProc: WindowProc): LONG_PTR
    fun CallWindowProc(proc: LONG_PTR, hWnd: HWND, uMsg: Int, uParam: WPARAM, lParam: LPARAM): LRESULT
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom WndProc
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Installs a custom Win32 window procedure that:
 *  - Hides the native title bar (WM_NCCALCSIZE → 0)
 *  - Preserves minimize / restore / close animations via DWM shadow extension
 *  - Enables window resizability with WS_CAPTION style
 *
 * NOTE: [WindowMargins] is intentionally a public top-level class in its own
 * file (WindowMargins.kt). JNA uses reflection to read its @JvmField fields;
 * if the class is private or nested inside a Kotlin file the Java module system
 * blocks reflective access and throws IllegalAccessException on Java 9+.
 */
internal class CustomWindowProcedure(private val windowHandle: HWND) : WindowProc {


    private val WM_NCCALCSIZE = 0x0083
    private val WM_NCHITTEST  = 0x0084
    private val HTCLIENT      = 1   // cursor está en el área cliente → sin resize
    private val GWLP_WNDPROC  = -4

    private val margins = WindowMargins(
        leftBorderWidth    = 0,
        topBorderHeight    = 0,
        rightBorderWidth   = -1,
        bottomBorderHeight = -1
    )

    private val USER32EX: User32Ex? =
        runCatching { Native.load("user32", User32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS) }
            .onFailure { { "Could not load user32 library" } }
            .getOrNull()

    private val defaultWndProc: LONG_PTR = if (is64Bit()) {
        USER32EX?.SetWindowLongPtr(windowHandle, GWLP_WNDPROC, this) ?: LONG_PTR(-1)
    } else {
        USER32EX?.SetWindowLong(windowHandle, GWLP_WNDPROC, this) ?: LONG_PTR(-1)
    }

    init {
        enableResizability()
        enableBorderAndShadow()
    }

    override fun callback(hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
        return when (uMsg) {
            // Remove native title bar / non-client area so the whole window is client area
            WM_NCCALCSIZE -> LRESULT(0)

            // Hit-test: when the window is maximized (via WindowMaximizer, which keeps
            // WindowPlacement.Floating but fills the work area) the OS still thinks the
            // window is floating and allows resize cursors at the edges. We suppress that
            // by returning HTCLIENT for every pixel, which tells Windows "everything here
            // is client area — no resize borders".
            WM_NCHITTEST -> {
                val defaultResult = USER32EX?.CallWindowProc(defaultWndProc, hWnd, uMsg, wParam, lParam)
                    ?: return LRESULT(0)

                // Query the actual Win32 placement to check SW_MAXIMIZE
                val placement = WinUser.WINDOWPLACEMENT()
                User32.INSTANCE.GetWindowPlacement(hWnd, placement)

                if (placement.showCmd == SW_MAXIMIZE) {
                    // Maximized → disable all resize hit areas
                    LRESULT(HTCLIENT.toLong())
                } else {
                    defaultResult
                }
            }

            else -> USER32EX?.CallWindowProc(defaultWndProc, hWnd, uMsg, wParam, lParam) ?: LRESULT(0)
        }
    }

    private fun enableResizability() {
        val style = USER32EX?.GetWindowLong(windowHandle, GWL_STYLE) ?: return
        USER32EX.SetWindowLong(windowHandle, GWL_STYLE, style or WS_CAPTION)
    }

    private fun enableBorderAndShadow() {
        "dwmapi"
            .runCatching(NativeLibrary::getInstance)
            .onFailure {  { "Could not load dwmapi library" } }
            .getOrNull()
            ?.runCatching { getFunction("DwmExtendFrameIntoClientArea") }
            ?.onFailure { { "Could not enable window shadow" } }
            ?.getOrNull()
            ?.invoke(arrayOf(windowHandle, margins))
    }

    private fun is64Bit(): Boolean {
        val bitMode = System.getProperty("com.ibm.vm.bitmode")
        return System.getProperty("sun.arch.data.model", bitMode) == "64"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Title Bar Composable
// ─────────────────────────────────────────────────────────────────────────────

private val isWindows: Boolean
    get() = System.getProperty("os.name", "").lowercase().contains("win")

/**
 * Custom title bar that replaces the native Windows decoration.
 *
 * Requires in your Window { } block:
 *   undecorated = true
 *   resizable   = true
 */
@Composable
fun FrameWindowScope.CustomTitleBar(
    title: String,
    windowState: WindowState,
    onClose: () -> Unit,
) {
    val windowHandle: HWND? = if (isWindows) {
        remember(window) {
            val pointer = (window as? ComposeWindow)
                ?.windowHandle
                ?.let(::Pointer)
                ?: Native.getWindowPointer(window)
            HWND(pointer)
        }
    } else null

    if (windowHandle != null) {
        remember(windowHandle) { CustomWindowProcedure(windowHandle) }
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val onSurface    = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    val isCurrentlyMaximized =  windowState.placement == WindowPlacement.Maximized

    Surface(
        modifier       = Modifier.fillMaxWidth().height(38.dp),
        color          = surfaceColor,
        tonalElevation = 1.dp
    ) {
        WindowDraggableArea(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = {}, onDoubleClick = {  })
        ) {
            Row(
                modifier          = Modifier.fillMaxSize().padding(horizontal =  if(isCurrentlyMaximized) 0.dp else 8.dp ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint               = primaryColor,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight    = FontWeight.SemiBold,
                        fontSize      = 13.sp,
                        letterSpacing = 0.3.sp
                    ),
                    color    = onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))

                TitleBarButton(
                    onClick = {
                        if (isWindows && windowHandle != null) {
                            // Win32 CloseWindow() = minimiza con animación nativa (no cierra la ventana)
                            User32.INSTANCE.CloseWindow(windowHandle)
                        }
                    },
                    icon        = Icons.Outlined.Minimize,
                    description = "Minimizar",
                    tint        = onSurface.copy(alpha = 0.7f),
                    hoverColor  = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                TitleBarButton(
                    onClick     = {
                        val user32 = User32.INSTANCE

                        if (windowState.placement == WindowPlacement.Maximized) {
                            user32.ShowWindow(windowHandle, SW_RESTORE)
                        } else {
                            user32.ShowWindow(windowHandle, SW_MAXIMIZE)
                        }
                    },
                    icon        = if (isCurrentlyMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
                    description = if (isCurrentlyMaximized) "Restaurar" else "Maximizar",
                    tint        = onSurface.copy(alpha = 0.7f),
                    hoverColor  = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                TitleBarButton(
                    onClick     = onClose,
                    icon        = Icons.Filled.Close,
                    description = "Cerrar",
                    tint        = onSurface.copy(alpha = 0.7f),
                    hoverColor  = MaterialTheme.colorScheme.errorContainer,
                    hoverTint   = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TitleBarButton(
    onClick    : () -> Unit,
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint       : Color,
    hoverColor : Color,
    hoverTint  : Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor  = (if (isHovered) hoverColor else Color.Transparent)
    val iconTint  =(if (isHovered && hoverTint != null) hoverTint else tint)

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 38.dp)
            .background(bgColor)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = iconTint,
            modifier = Modifier.size(14.dp)
        )
    }
}