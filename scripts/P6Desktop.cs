// External Windows desktop QA only. No Minecraft classes, hooks, or image synthesis.
// Win32 contracts: learn.microsoft.com/windows/win32/api/winuser/{nf-winuser-sendinput,
// nf-winuser-setforegroundwindow,nf-winuser-attachthreadinput,ns-winuser-keybdinput}; shellapi/nf-shellapi-commandlinetoargvw.
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class P6Desktop
{
    private delegate bool EnumProc(IntPtr window, IntPtr state);
    [StructLayout(LayoutKind.Sequential)] private struct Rect { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] private struct Point { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] private struct MouseInput { public int X, Y; public uint Data, Flags, Time; public UIntPtr Extra; }
    [StructLayout(LayoutKind.Sequential)] private struct KeyInput { public ushort Key, Scan; public uint Flags, Time; public UIntPtr Extra; }
    [StructLayout(LayoutKind.Explicit)] private struct InputUnion { [FieldOffset(0)] public MouseInput Mouse; [FieldOffset(0)] public KeyInput Key; }
    [StructLayout(LayoutKind.Sequential)] private struct Input { public uint Type; public InputUnion Data; }
    [StructLayout(LayoutKind.Sequential)] private struct Message { public IntPtr Window; public uint Id; public UIntPtr WParam; public IntPtr LParam; public uint Time; public Point Position; public uint Private; }
    [StructLayout(LayoutKind.Sequential)] private struct GuiThreadInfo { public uint Size, Flags; public IntPtr Active, Focus, Capture, MenuOwner, MoveSize, Caret; public Rect CaretRect; }
    [StructLayout(LayoutKind.Sequential)] private struct MonitorInfo { public uint Size; public Rect Monitor, Work; public uint Flags; }
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc callback, IntPtr state);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] private static extern bool IsIconic(IntPtr window);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr window, StringBuilder text, int maximum);
    [DllImport("user32.dll")] private static extern bool GetClientRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")] private static extern bool ClientToScreen(IntPtr window, ref Point point);
    [DllImport("user32.dll")] private static extern IntPtr MonitorFromRect(ref Rect rectangle, uint flags);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern bool GetMonitorInfo(IntPtr monitor, ref MonitorInfo information);
    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern bool ShowWindow(IntPtr window, int command);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool BringWindowToTop(IntPtr window);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool AttachThreadInput(uint from, uint to, bool attach);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool GetGUIThreadInfo(uint thread, ref GuiThreadInfo information);
    [DllImport("user32.dll")] private static extern short GetAsyncKeyState(int key);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern bool PeekMessage(out Message message, IntPtr window, uint minimum, uint maximum, uint remove);
    [DllImport("user32.dll")] private static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] private static extern IntPtr WindowFromPoint(Point point);
    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint count, Input[] inputs, int size);
    [DllImport("user32.dll")] private static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
    [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)] private static extern IntPtr CommandLineToArgvW(string command, out int count);
    [DllImport("kernel32.dll")] private static extern IntPtr LocalFree(IntPtr allocation);
    [DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();

    public sealed class WindowInfo
    {
        public long Handle; public int ProcessId, Width, Height; public bool Foreground; public string Title;
    }

    public static string[] ParseCommand(string command)
    {
        if (String.IsNullOrWhiteSpace(command) || command.Length > 262144) throw new InvalidOperationException("Missing or oversized process command.");
        int count; IntPtr memory = CommandLineToArgvW(command, out count);
        if (memory == IntPtr.Zero) throw new InvalidOperationException("Cannot parse process command.");
        try { var result = new string[count]; for (int i = 0; i < count; i++) result[i] = Marshal.PtrToStringUni(Marshal.ReadIntPtr(memory, i * IntPtr.Size)); return result; }
        finally { LocalFree(memory); }
    }

    private static string Title(IntPtr window) { var text = new StringBuilder(1024); GetWindowText(window, text, text.Capacity); return text.ToString(); }
    private static void Check(IntPtr window, int process, bool foreground)
    {
        uint actual; GetWindowThreadProcessId(window, out actual);
        if (actual != process || !IsWindowVisible(window) || Title(window).IndexOf("Minecraft", StringComparison.OrdinalIgnoreCase) < 0)
            throw new InvalidOperationException("Owned visible Minecraft window no longer matches.");
        if (foreground && (GetForegroundWindow() != window || IsIconic(window))) throw new InvalidOperationException("Owned Minecraft window is not foreground; no input/capture performed.");
    }
    private static IntPtr Find(int process)
    {
        var matches = new List<IntPtr>();
        EnumWindows(delegate(IntPtr window, IntPtr unused) { uint actual; GetWindowThreadProcessId(window, out actual);
            if (actual == process && IsWindowVisible(window) && Title(window).IndexOf("Minecraft", StringComparison.OrdinalIgnoreCase) >= 0) matches.Add(window); return true; }, IntPtr.Zero);
        if (matches.Count != 1) throw new InvalidOperationException("Expected exactly one owned visible Minecraft window.");
        return matches[0];
    }
    private static Rectangle Bounds(IntPtr window)
    {
        Rect rect; Point origin = new Point();
        if (!GetClientRect(window, out rect) || !ClientToScreen(window, ref origin)) throw new InvalidOperationException("Cannot resolve client rectangle.");
        int width = rect.Right - rect.Left, height = rect.Bottom - rect.Top;
        if (width < 100 || height < 100 || width > 7680 || height > 4320) throw new InvalidOperationException("Invalid or oversized client rectangle.");
        return new Rectangle(origin.X, origin.Y, width, height);
    }
    private static void VisiblePoint(int process, int x, int y)
    {
        uint actual; GetWindowThreadProcessId(WindowFromPoint(new Point { X = x, Y = y }), out actual);
        if (actual != process) throw new InvalidOperationException("Client point is obscured by another window; operation refused.");
    }
    private static bool ContainsWhole(Rect outer, Rectangle inner)
    {
        return inner.Width > 0 && inner.Height > 0 && outer.Right > outer.Left && outer.Bottom > outer.Top &&
            inner.Left >= outer.Left && inner.Top >= outer.Top && (long)inner.Left + inner.Width <= outer.Right && (long)inner.Top + inner.Height <= outer.Bottom;
    }
    private static void RequirePhysicalClient(Rectangle rectangle)
    {
        // WindowFromPoint alone can report an HWND beyond a physical display. Never capture virtual gaps,
        // taskbar space, or an off-screen part; require the ENTIRE client in one actual monitor work area.
        // Win32: nf-winuser-monitorfromrect, nf-winuser-getmonitorinfow, ns-winuser-monitorinfo.
        var native = new Rect { Left = rectangle.Left, Top = rectangle.Top, Right = rectangle.Right, Bottom = rectangle.Bottom };
        IntPtr monitor = MonitorFromRect(ref native, 0); // MONITOR_DEFAULTTONULL, never a nearest fallback.
        var info = new MonitorInfo { Size = (uint)Marshal.SizeOf(typeof(MonitorInfo)) };
        if (monitor == IntPtr.Zero || !GetMonitorInfo(monitor, ref info) || !ContainsWhole(info.Monitor, rectangle) || !ContainsWhole(info.Work, rectangle))
            throw new InvalidOperationException("Whole Minecraft client must fit one physical monitor work area; move/resize the owned window before clicking or capturing.");
    }
    private static void RequireUnchangedPhysicalClient(IntPtr window, Rectangle expected)
    {
        if (Bounds(window) != expected) throw new InvalidOperationException("Minecraft client moved/resized during operation; retry explicitly.");
        RequirePhysicalClient(expected);
    }
    private static void RequireIdleInput(uint thread)
    {
        var info = new GuiThreadInfo { Size = (uint)Marshal.SizeOf(typeof(GuiThreadInfo)) };
        if (!GetGUIThreadInfo(thread, ref info) || info.Capture != IntPtr.Zero || info.MenuOwner != IntPtr.Zero || info.MoveSize != IntPtr.Zero || (info.Flags & 30) != 0)
            throw new InvalidOperationException("Focus fallback refused: input queue is unavailable or a mouse/menu operation is active.");
    }
    private static void FocusOwnedWindow(IntPtr window, int process)
    {
        Check(window, process, false);
        if (IsIconic(window)) ShowWindow(window, 9);
        SetForegroundWindow(window); Thread.Sleep(150);
        if (GetForegroundWindow() == window) { Check(window, process, true); return; }
        IntPtr foreground = GetForegroundWindow(); uint ignored;
        uint currentThread = GetCurrentThreadId(), targetThread = GetWindowThreadProcessId(window, out ignored);
        uint foregroundThread = GetWindowThreadProcessId(foreground, out ignored);
        if (foreground == IntPtr.Zero || targetThread == 0 || foregroundThread == 0) throw new InvalidOperationException("Cannot establish focus thread ownership.");
        // Ensure this external helper's thread owns a message queue without removing/dispatching queued input.
        Message unused; PeekMessage(out unused, new IntPtr(-1), 0, 0, 0);
        RequireIdleInput(foregroundThread); RequireIdleInput(targetThread);
        // AttachThreadInput resets per-queue keyboard state. Never attach during a held key/button gesture.
        for (int key = 1; key <= 254; key++) if ((GetAsyncKeyState(key) & 0x8000) != 0) throw new InvalidOperationException("Focus fallback refused while a key or mouse button is held.");
        if (GetForegroundWindow() != foreground) throw new InvalidOperationException("Foreground changed during focus preparation; retry explicitly.");
        var attached = new List<uint>();
        try
        {
            foreach (uint thread in new uint[] { foregroundThread, targetThread })
            {
                if (thread == currentThread || attached.Contains(thread)) continue;
                if (!AttachThreadInput(currentThread, thread, true)) throw new InvalidOperationException("Windows rejected temporary input-queue attachment.");
                attached.Add(thread);
            }
            Check(window, process, false);
            // One attempt, no polling/sleep/input while attached. Native OS calls are not hard-preemptible.
            if (!BringWindowToTop(window)) throw new InvalidOperationException("Windows refused to raise the owned Minecraft window.");
            SetForegroundWindow(window);
        }
        finally
        {
            bool detached = true;
            for (int index = attached.Count - 1; index >= 0; index--) if (!AttachThreadInput(currentThread, attached[index], false)) detached = false;
            if (!detached) throw new InvalidOperationException("Windows reported a temporary input-queue detach failure; stop automation.");
        }
        Thread.Sleep(150); Check(window, process, true);
    }
    private static Input Keyboard(ushort key, ushort scan, uint flags) { return new Input { Type = 1, Data = new InputUnion { Key = new KeyInput { Key = key, Scan = scan, Flags = flags } } }; }
    private static uint WheelData(string value)
    {
        int notches;
        if (!Int32.TryParse(value, System.Globalization.NumberStyles.AllowLeadingSign, System.Globalization.CultureInfo.InvariantCulture, out notches) || notches == 0 || notches < -10 || notches > 10)
            throw new ArgumentException("Wheel value must be a signed, nonzero notch count from -10 to 10.");
        return unchecked((uint)(notches * 120)); // Win32 MOUSEINPUT: signed WHEEL_DELTA encoded in DWORD.
    }
    private static void Send(IntPtr window, int process, List<Input> inputs)
    {
        Check(window, process, true);
        if (SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf(typeof(Input))) != inputs.Count)
            throw new InvalidOperationException("Windows rejected some input (possibly UIPI); success is not established.");
    }
    private static ushort KeyCode(string key)
    {
        key = key.Trim().ToUpperInvariant();
        if (key.Length == 1 && ((key[0] >= 'A' && key[0] <= 'Z') || (key[0] >= '0' && key[0] <= '9'))) return key[0];
        int function; if (key.StartsWith("F") && Int32.TryParse(key.Substring(1), out function) && function >= 1 && function <= 12) return (ushort)(111 + function);
        switch (key) { case "CTRL": return 17; case "SHIFT": return 16; case "ALT": return 18; case "ENTER": return 13; case "ESC": case "ESCAPE": return 27;
            case "TAB": return 9; case "SPACE": return 32; case "BACKSPACE": return 8; case "DELETE": return 46; case "INSERT": return 45;
            case "HOME": return 36; case "END": return 35; case "PAGEUP": return 33; case "PAGEDOWN": return 34;
            case "LEFT": return 37; case "UP": return 38; case "RIGHT": return 39; case "DOWN": return 40; default: throw new ArgumentException("Unsupported key name."); }
    }

    // Each invocation checks ownership. Explicit focus uses no synthetic input; other actions require foreground.
    public static WindowInfo Act(int process, string action, double x, double y, int referenceWidth, int referenceHeight, string value, string capturePath, bool rightClick)
    {
        IntPtr previousDpi = SetThreadDpiAwarenessContext(new IntPtr(-4)); // PER_MONITOR_AWARE_V2, calling thread only.
        if (previousDpi == IntPtr.Zero) throw new InvalidOperationException("Cannot establish physical-pixel DPI coordinates.");
        try
        {
            IntPtr window = Find(process); Check(window, process, false);
            if (action == "focus") FocusOwnedWindow(window, process);
            Rectangle rect = Bounds(window);
            if (action != "inspect" && action != "focus") Check(window, process, true);
            if (action == "click" || action == "hover" || action == "wheel" || action == "capture") RequirePhysicalClient(rect);
            if (action == "click" || action == "hover" || action == "wheel")
            {
                uint wheel = action == "wheel" ? WheelData(value) : 0;
                if (referenceWidth < 1 || referenceHeight < 1 || Double.IsNaN(x) || Double.IsNaN(y) || x < 0 || y < 0 || x >= referenceWidth || y >= referenceHeight)
                    throw new ArgumentException("Mouse point lies outside reference client rectangle.");
                int px = rect.Left + (int)Math.Floor(x * rect.Width / referenceWidth), py = rect.Top + (int)Math.Floor(y * rect.Height / referenceHeight);
                VisiblePoint(process, px, py); Check(window, process, true);
                if (!SetCursorPos(px, py)) throw new InvalidOperationException("Cannot position cursor.");
                // Allow GLFW's next poll to consume WM_MOUSEMOVE before click, hover completion or wheel.
                Thread.Sleep(100); VisiblePoint(process, px, py);
                RequireUnchangedPhysicalClient(window, rect); Check(window, process, true);
                if (action == "click") Send(window, process, new List<Input> { new Input { Data = new InputUnion { Mouse = new MouseInput { Flags = rightClick ? 8u : 2u } } },
                    new Input { Data = new InputUnion { Mouse = new MouseInput { Flags = rightClick ? 16u : 4u } } } });
                else if (action == "wheel") Send(window, process, new List<Input> { new Input { Data = new InputUnion { Mouse = new MouseInput { Flags = 0x0800, Data = wheel } } } });
                // hover intentionally sends no mouse-button or wheel events.
            }
            else if (action == "key")
            {
                string[] names = (value ?? "").Split('+'); if (names.Length < 1 || names.Length > 4) throw new ArgumentException("Use one to four chord keys.");
                var keys = new List<ushort>(); var events = new List<Input>();
                foreach (string name in names) { ushort key = KeyCode(name); if (keys.Contains(key)) throw new ArgumentException("Duplicate chord key."); keys.Add(key); events.Add(Keyboard(key, 0, key >= 33 && key <= 46 ? 1u : 0u)); }
                for (int i = keys.Count - 1; i >= 0; i--) events.Add(Keyboard(keys[i], 0, (keys[i] >= 33 && keys[i] <= 46 ? 1u : 0u) | 2u));
                Send(window, process, events);
            }
            else if (action == "text")
            {
                if (String.IsNullOrEmpty(value) || value.Length > 512) throw new ArgumentException("Literal text must contain 1-512 UTF-16 code units.");
                var events = new List<Input>(); foreach (char character in value) { if (Char.IsControl(character)) throw new ArgumentException("Control characters are not literal text; use key actions."); events.Add(Keyboard(0, character, 4)); events.Add(Keyboard(0, character, 6)); } Send(window, process, events);
            }
            else if (action == "capture")
            {
                VisiblePoint(process, rect.Left + 1, rect.Top + 1); VisiblePoint(process, rect.Right - 2, rect.Top + 1);
                VisiblePoint(process, rect.Left + 1, rect.Bottom - 2); VisiblePoint(process, rect.Right - 2, rect.Bottom - 2); VisiblePoint(process, rect.Left + rect.Width / 2, rect.Top + rect.Height / 2);
                using (var bitmap = new Bitmap(rect.Width, rect.Height, PixelFormat.Format32bppArgb))
                {
                    using (var graphics = Graphics.FromImage(bitmap)) { Check(window, process, true); RequireUnchangedPhysicalClient(window, rect); graphics.CopyFromScreen(rect.Left, rect.Top, 0, 0, rect.Size, CopyPixelOperation.SourceCopy); }
                    Check(window, process, true); RequireUnchangedPhysicalClient(window, rect);
                    using (var output = new FileStream(capturePath, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { bitmap.Save(output, ImageFormat.Png); output.Flush(true); }
                }
            }
            else if (action != "inspect" && action != "focus") throw new ArgumentException("Unknown desktop action.");
            return new WindowInfo { Handle = window.ToInt64(), ProcessId = process, Width = rect.Width, Height = rect.Height, Foreground = GetForegroundWindow() == window, Title = Title(window) };
        }
        finally { SetThreadDpiAwarenessContext(previousDpi); }
    }
}
