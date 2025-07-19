On Error Resume Next
Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

' Get the script's directory and parent directory
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
parentDir = fso.GetParentFolderName(scriptDir)

' Change to parent directory (where target/opencv-demo-1.0-SNAPSHOT.jar is located)
WshShell.CurrentDirectory = parentDir

' Build the command
javafxPath = """C:\Program Files\Java\javafx-sdk-24.0.2\lib"""
jarPath = "target\opencv-demo-1.0-SNAPSHOT.jar"

' Check if JAR exists
If Not fso.FileExists(jarPath) Then
    MsgBox "Error: Could not find " & jarPath, 16, "File Not Found"
    WScript.Quit
End If

' Build the command
strCommand = "java --enable-native-access=javafx.graphics,ALL-UNNAMED " & _
            "--add-opens java.base/java.lang=ALL-UNNAMED " & _
            "--add-opens java.base/sun.nio.ch=ALL-UNNAMED " & _
            "--add-opens java.base/java.io=ALL-UNNAMED " & _
            "-Dfile.encoding=UTF-8 " & _
            "--module-path " & javafxPath & " " & _
            "--add-modules javafx.controls,javafx.fxml,javafx.graphics " & _
            "-jar " & jarPath

' Run the command with hidden window (0)
WshShell.Run strCommand, 0, False

' Check for errors
If Err.Number <> 0 Then
    MsgBox "Error running application: " & Err.Description, 16, "Error"
End If 