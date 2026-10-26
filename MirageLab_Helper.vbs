Option Explicit
Dim sh, fso, labDir, stateDir, runtimeDir, agent, javaw, clientFile, clientDir, cmd, logFile
Dim expectedSize, actualSize, i, statusText, ans, srcDir, launchCmd, qq
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

srcDir = fso.GetParentFolderName(WScript.ScriptFullName)
labDir = sh.ExpandEnvironmentStrings("%USERPROFILE%") & "\Downloads\MirageLab"
stateDir = labDir & "\state"
runtimeDir = labDir & "\runtime"
agent = srcDir & "\MirageLabAgent.jar"
clientFile = labDir & "\client_path.txt"
logFile = runtimeDir & "\launcher.log"
javaw = ResolveJavaw()
expectedSize = 259235

EnsureFolder labDir
EnsureFolder stateDir
EnsureFolder runtimeDir
EnsureFolder labDir & "\routes"

If IsMirageRunning() Then
    If IsCurrentSession() Then
        WriteAscii stateDir & "\panel.txt", "overlay " & Timer
        WScript.Quit 0
    Else
        ans = MsgBox("Existe uma sessao antiga do MirageLab aberta." & vbCrLf & vbCrLf & _
                     "Ela precisa ser encerrada para iniciar o Helper v10.0.3." & vbCrLf & _
                     "Isso tambem fecha o cliente Mirage Realms dessa sessao." & vbCrLf & vbCrLf & _
                     "Encerrar a sessao antiga agora?", vbYesNo + vbExclamation, "MirageLab Helper v10.0.3")
        If ans <> vbYes Then WScript.Quit 0
        KillMirage
        WScript.Sleep 1200
    End If
End If

If Not fso.FileExists(agent) Then
    MsgBox "MirageLabAgent.jar nao encontrado em:" & vbCrLf & agent, vbCritical, "MirageLab Helper v10.0.3"
    WScript.Quit 1
End If
actualSize = fso.GetFile(agent).Size
If actualSize <> expectedSize Then
    MsgBox "JAR incorreto." & vbCrLf & vbCrLf & "Encontrado: " & actualSize & " bytes" & vbCrLf & _
           "Esperado Helper v10.0.3: " & expectedSize & " bytes", vbCritical, "MirageLab Helper v10.0.3"
    WScript.Quit 2
End If
If Len(javaw) = 0 Or Not fso.FileExists(javaw) Then
    MsgBox "Java 21 nao encontrado. Configure JAVA_HOME ou instale Java 21; use o README para instrucoes." & vbCrLf & javaw, vbCritical, "MirageLab Helper v10.0.3"
    WScript.Quit 3
End If

clientDir = ResolveClientDir()
If clientDir = "" Then
    MsgBox "Nao foi encontrado Obfuscated.jar automaticamente." & vbCrLf & vbCrLf & _
           "Instale o Mirage Realms no Steam ou coloque uma copia do seu Obfuscated.jar original " & _
           "na mesma pasta do MirageLab.exe e execute novamente." & vbCrLf & vbCrLf & _
           "O arquivo do jogo nao acompanha este pacote.", vbExclamation, "MirageLab Helper v10.0.3"
    WScript.Quit 4
End If
' Keep the last valid client location for the next launch. This is not an installer.
If Not fso.FileExists(clientFile) Then
    WriteUnicode clientFile, clientDir
ElseIf LCase(ReadClientPath(clientFile)) <> LCase(clientDir) Then
    WriteUnicode clientFile, clientDir
End If

On Error Resume Next
If fso.FileExists(runtimeDir & "\agent_status.txt") Then fso.DeleteFile runtimeDir & "\agent_status.txt", True
If fso.FileExists(runtimeDir & "\launcher_previous.log") Then fso.DeleteFile runtimeDir & "\launcher_previous.log", True
If fso.FileExists(logFile) Then fso.MoveFile logFile, runtimeDir & "\launcher_previous.log"
On Error GoTo 0

sh.CurrentDirectory = clientDir
' Build arguments from the single quotation character instead of nested VBScript literal quotes.
' Windows cmd /D /S /C requires an extra pair of quotes surrounding the whole command.
qq = Chr(34)
launchCmd = qq & javaw & qq
launchCmd = launchCmd & " --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED"
launchCmd = launchCmd & " -javaagent:" & qq & agent & qq
launchCmd = launchCmd & " -XX:+DisableAttachMechanism"
launchCmd = launchCmd & " -XX:ErrorFile=" & qq & runtimeDir & "\hs_err_pid%p.log" & qq
launchCmd = launchCmd & " -Denv=native -cp " & qq & "Obfuscated.jar" & qq
launchCmd = launchCmd & " uk.co.harveydogs.mirage.client.desktop.DesktopLauncher"
cmd = "cmd.exe /D /S /C " & qq & launchCmd & " > " & qq & logFile & qq & " 2>&1" & qq
sh.Run cmd, 0, False

For i = 1 To 48
    WScript.Sleep 250
    If fso.FileExists(runtimeDir & "\agent_status.txt") Then
        statusText = ReadUtf8BestEffort(runtimeDir & "\agent_status.txt")
        If Len(Trim(statusText)) > 0 Then WScript.Quit 0
    End If
Next
MsgBox "O cliente foi iniciado, mas a confirmacao do Helper v10.0.3 nao apareceu no log." & vbCrLf & _
       "Se o Mirage abriu, use a aba Status > Gerar diagnostico.", vbExclamation, "MirageLab Helper v10.0.3"


' Locate a valid game folder without user interaction. The portable game JAR wins,
' followed by the saved location, then common Steam libraries and app manifests.
Function ResolveClientDir()
    Dim r, bases, b
    ResolveClientDir = ""
    r = ValidFolder(srcDir)
    If r <> "" Then ResolveClientDir = r: Exit Function
    r = ValidFolder(srcDir & "\Mirage Realms")
    If r <> "" Then ResolveClientDir = r: Exit Function
    r = ValidFolder(srcDir & "\MirageRealms")
    If r <> "" Then ResolveClientDir = r: Exit Function
    r = ValidFolder(ReadClientPath(clientFile))
    If r <> "" Then ResolveClientDir = r: Exit Function

    bases = Array( _
        sh.ExpandEnvironmentStrings("%PROGRAMFILES(X86)%") & "\Steam", _
        sh.ExpandEnvironmentStrings("%PROGRAMFILES%") & "\Steam", _
        sh.ExpandEnvironmentStrings("%LOCALAPPDATA%") & "\Steam", _
        sh.ExpandEnvironmentStrings("%USERPROFILE%") & "\Steam", _
        SteamRegistry("HKCU\Software\Valve\Steam\SteamPath"), _
        SteamRegistry("HKLM\SOFTWARE\WOW6432Node\Valve\Steam\InstallPath"), _
        SteamRegistry("HKLM\SOFTWARE\Valve\Steam\InstallPath") _
    )
    For Each b In bases
        If Len(b) > 3 Then
            r = FindSteamClient(b)
            If r <> "" Then ResolveClientDir = r: Exit Function
        End If
    Next
End Function

Function ValidFolder(path)
    Dim jar
    ValidFolder = ""
    If Len(path) = 0 Then Exit Function
    On Error Resume Next
    path = Replace(path, "/", "\")
    If fso.FolderExists(path) Then
        jar = fso.BuildPath(path, "Obfuscated.jar")
        If fso.FileExists(jar) Then
            If fso.GetFile(jar).Size > 1048576 Then ValidFolder = fso.GetAbsolutePathName(path)
        End If
    End If
    Err.Clear
    On Error GoTo 0
End Function

Function SteamRegistry(regPath)
    SteamRegistry = ""
    On Error Resume Next
    SteamRegistry = sh.RegRead(regPath)
    If Err.Number <> 0 Then SteamRegistry = "": Err.Clear
    On Error GoTo 0
End Function

Function FindSteamClient(steamPath)
    Dim r, apps, libFile, data, line, parts, v, idx
    FindSteamClient = ""
    steamPath = Replace(steamPath, "/", "\")
    r = FindSteamApp(steamPath)
    If r <> "" Then FindSteamClient = r: Exit Function
    libFile = fso.BuildPath(steamPath, "steamapps\libraryfolders.vdf")
    If Not fso.FileExists(libFile) Then Exit Function
    data = ReadUtf8BestEffort(libFile)
    For Each line In Split(data, vbLf)
        parts = Split(Trim(line), Chr(34))
        If UBound(parts) >= 3 Then
            v = LCase(Trim(parts(1)))
            ' Valve supports "path" entries and an older numbered-library syntax.
            If v = "path" Or IsDigits(v) Then
                r = FindSteamApp(Replace(parts(3), "\\", "\"))
                If r <> "" Then FindSteamClient = r: Exit Function
            End If
        End If
    Next
End Function

Function FindSteamApp(steamPath)
    Dim r, common, apps, file, data, gameName, installDir
    FindSteamApp = ""
    If Not fso.FolderExists(steamPath) Then Exit Function
    common = fso.BuildPath(steamPath, "steamapps\common")
    r = ValidFolder(fso.BuildPath(common, "Mirage Realms"))
    If r <> "" Then FindSteamApp = r: Exit Function
    r = ValidFolder(fso.BuildPath(common, "MirageRealms"))
    If r <> "" Then FindSteamApp = r: Exit Function
    r = ValidFolder(fso.BuildPath(common, "Mirage Realms - Early Access"))
    If r <> "" Then FindSteamApp = r: Exit Function

    apps = fso.BuildPath(steamPath, "steamapps")
    If Not fso.FolderExists(apps) Then Exit Function
    On Error Resume Next
    For Each file In fso.GetFolder(apps).Files
        If LCase(Left(file.Name, 12)) = "appmanifest_" And LCase(Right(file.Name, 4)) = ".acf" Then
            data = ReadUtf8BestEffort(file.Path)
            gameName = LCase(VdfValue(data, "name"))
            If InStr(gameName, "mirage realms") > 0 Then
                installDir = VdfValue(data, "installdir")
                If installDir <> "" And InStr(installDir, "..") = 0 And InStr(installDir, "\") = 0 And InStr(installDir, "/") = 0 Then
                    r = ValidFolder(fso.BuildPath(common, installDir))
                    If r <> "" Then FindSteamApp = r: Exit For
                End If
            End If
        End If
    Next
    Err.Clear
    On Error GoTo 0
End Function

Function VdfValue(data, key)
    Dim line, parts
    VdfValue = ""
    For Each line In Split(data, vbLf)
        parts = Split(Trim(line), Chr(34))
        If UBound(parts) >= 3 Then
            If LCase(Trim(parts(1))) = LCase(key) Then VdfValue = parts(3): Exit Function
        End If
    Next
End Function

Function IsDigits(v)
    Dim i, c
    IsDigits = (Len(v) > 0)
    For i = 1 To Len(v)
        c = Mid(v, i, 1)
        If c < "0" Or c > "9" Then IsDigits = False: Exit Function
    Next
End Function

Sub EnsureFolder(p)
    If Not fso.FolderExists(p) Then fso.CreateFolder p
End Sub
Function IsMirageRunning()
    Dim svc, p, cl, nm
    IsMirageRunning = False
    On Error Resume Next
    Set svc = GetObject("winmgmts:\\.\root\cimv2")
    For Each p In svc.ExecQuery("SELECT Name,CommandLine FROM Win32_Process")
        nm = LCase("" & p.Name)
        If nm = "javaw.exe" Or nm = "java.exe" Then
            cl = LCase("" & p.CommandLine)
            If InStr(cl, "miragelabagent.jar") > 0 And InStr(cl, "obfuscated.jar") > 0 Then IsMirageRunning = True: Exit Function
        End If
    Next
    On Error GoTo 0
End Function
Function IsCurrentSession()
    Dim p, txt
    IsCurrentSession = False
    p = runtimeDir & "\agent_status.txt"
    If fso.FileExists(p) Then
        txt = ReadUtf8BestEffort(p)
        If Len(Trim(txt)) > 0 Then IsCurrentSession = True
    End If
End Function
Sub KillMirage()
    Dim svc, p, cl, nm
    On Error Resume Next
    Set svc = GetObject("winmgmts:\\.\root\cimv2")
    For Each p In svc.ExecQuery("SELECT Name,CommandLine FROM Win32_Process")
        nm = LCase("" & p.Name)
        If nm = "javaw.exe" Or nm = "java.exe" Then
            cl = LCase("" & p.CommandLine)
            If InStr(cl, "miragelabagent.jar") > 0 And InStr(cl, "obfuscated.jar") > 0 Then p.Terminate 0
        End If
    Next
    On Error GoTo 0
End Sub
Function ReadClientPath(p)
    Dim x
    ReadClientPath = ""
    If Not fso.FileExists(p) Then Exit Function
    On Error Resume Next
    Set x = fso.OpenTextFile(p, 1, False, -1)
    If Err.Number = 0 Then ReadClientPath = Trim(x.ReadAll): x.Close
    Err.Clear
    If ReadClientPath = "" Then
        Set x = fso.OpenTextFile(p, 1, False, 0)
        If Err.Number = 0 Then ReadClientPath = Trim(x.ReadAll): x.Close
    End If
    On Error GoTo 0
End Function
Function ReadUtf8BestEffort(p)
    Dim stm
    ReadUtf8BestEffort = ""
    On Error Resume Next
    Set stm = CreateObject("ADODB.Stream")
    stm.Type = 2: stm.Charset = "utf-8": stm.Open: stm.LoadFromFile p
    ReadUtf8BestEffort = stm.ReadText
    stm.Close
    On Error GoTo 0
End Function
Sub WriteAscii(p, s)
    Dim x
    Set x = fso.OpenTextFile(p, 2, True, 0): x.Write s: x.Close
End Sub
Sub WriteUnicode(p, s)
    Dim x
    Set x = fso.OpenTextFile(p, 2, True, -1): x.Write s: x.Close
End Sub

' V10: prefer JAVA_HOME, fall back to the known JDK21 install or PATH.
Function ResolveJavaw()
    Dim envJava, fallback, found, execObj
    ResolveJavaw = ""
    envJava = sh.ExpandEnvironmentStrings("%JAVA_HOME%")
    If envJava <> "%JAVA_HOME%" And envJava <> "" Then
        fallback = envJava & "\bin\javaw.exe"
        If fso.FileExists(fallback) Then ResolveJavaw = fallback: Exit Function
    End If
    fallback = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\javaw.exe"
    If fso.FileExists(fallback) Then ResolveJavaw = fallback: Exit Function
    On Error Resume Next
    Set execObj = sh.Exec("cmd.exe /c where javaw.exe")
    If Err.Number = 0 Then
        found = Trim(execObj.StdOut.ReadLine)
        If Len(found) > 0 And fso.FileExists(found) Then ResolveJavaw = found
    End If
    On Error GoTo 0
End Function
