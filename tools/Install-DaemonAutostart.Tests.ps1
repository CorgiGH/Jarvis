#Requires -Modules Pester

Describe "install-daemon-autostart (dry-run)" {
    BeforeAll {
        $env:JARVIS_DAEMON_INSTALL_DRYRUN = "1"
        $fakeExe = Join-Path $TestDrive "daemon\target\release\jarvis-daemon.exe"
        New-Item -ItemType Directory -Force -Path (Split-Path $fakeExe) | Out-Null
        Set-Content $fakeExe "" -Encoding ASCII
        $fakeSsh = Join-Path $TestDrive ".ssh\id_ed25519"
        New-Item -ItemType Directory -Force -Path (Split-Path $fakeSsh) | Out-Null
        Set-Content $fakeSsh "" -Encoding ASCII
        $script:ToolsDir = Join-Path $PSScriptRoot ".."
        $script:ScriptPath = Join-Path $script:ToolsDir "tools\install-daemon-autostart.ps1"
    }
    AfterAll { Remove-Item Env:\JARVIS_DAEMON_INSTALL_DRYRUN -ErrorAction SilentlyContinue }

    It "script file exists" { Test-Path $script:ScriptPath | Should -Be $true }

    It "jarvis-daemon-task.xml exists and contains TaskScheduler namespace" {
        $xmlPath = Join-Path $script:ToolsDir "tools\jarvis-daemon-task.xml"
        Test-Path $xmlPath | Should -Be $true
        [xml]$doc = Get-Content $xmlPath -Raw
        $doc.Task.GetAttribute("xmlns") | Should -Be "http://schemas.microsoft.com/windows/2004/02/mit/task"
    }

    It "XML contains both LogonTrigger and BootTrigger" {
        $xmlPath = Join-Path $script:ToolsDir "tools\jarvis-daemon-task.xml"
        [xml]$doc = Get-Content $xmlPath -Raw
        $ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
        $ns.AddNamespace("ts", "http://schemas.microsoft.com/windows/2004/02/mit/task")
        $doc.SelectSingleNode("//ts:LogonTrigger", $ns) | Should -Not -BeNullOrEmpty
        $doc.SelectSingleNode("//ts:BootTrigger", $ns)  | Should -Not -BeNullOrEmpty
    }

    It "XML restart-on-failure is 3 with PT1M delay" {
        $xmlPath = Join-Path $script:ToolsDir "tools\jarvis-daemon-task.xml"
        [xml]$doc = Get-Content $xmlPath -Raw
        $ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
        $ns.AddNamespace("ts", "http://schemas.microsoft.com/windows/2004/02/mit/task")
        $doc.SelectSingleNode("//ts:RestartOnFailure/ts:Count", $ns).InnerText | Should -Be "3"
        $doc.SelectSingleNode("//ts:RestartOnFailure/ts:Interval", $ns).InnerText | Should -Be "PT1M"
    }

    It "dry-run install emits expected schtasks task names" {
        $output = & powershell -ExecutionPolicy Bypass -File $script:ScriptPath -DaemonExe $fakeExe -SshKey $fakeSsh 2>&1
        $names = $output | Select-String "Jarvis Daemon|Jarvis Reverse SSH Tunnel"
        $names.Count | Should -BeGreaterOrEqual 2
    }
}
