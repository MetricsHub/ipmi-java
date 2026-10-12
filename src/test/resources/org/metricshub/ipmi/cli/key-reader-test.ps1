$source = Get-Content -Raw $args[0]
. ([scriptblock]::Create(($source -split '\[Console\]::TreatControlCAsInput')[0]))
function hex($b) { ($b | ForEach-Object { '{0:x2}' -f $_ }) -join ' ' }
function key($c, $name, $shift, $alt, $ctrl) { New-Object ConsoleKeyInfo($c, [ConsoleKey]::$name, $shift, $alt, $ctrl) }
$cases = @(
  @('a', (key 'a' 'A' $false $false $false), '61'),
  @('Enter', (key "`r" 'Enter' $false $false $false), '0d'),
  @('Ctrl+C', (key ([char]3) 'C' $false $false $true), '03'),
  @('Alt+a', (key 'a' 'A' $false $true $false), '1b 61'),
  @('Up', (key ([char]0) 'UpArrow' $false $false $false), '1b 5b 41'),
  @('Ctrl+Right', (key ([char]0) 'RightArrow' $false $false $true), '1b 5b 31 3b 35 43'),
  @('Shift+Alt+Up', (key ([char]0) 'UpArrow' $true $true $false), '1b 5b 31 3b 34 41'),
  @('Home', (key ([char]0) 'Home' $false $false $false), '1b 5b 48'),
  @('F1', (key ([char]0) 'F1' $false $false $false), '1b 4f 50'),
  @('Shift+F1', (key ([char]0) 'F1' $true $false $false), '1b 5b 31 3b 32 50'),
  @('F5', (key ([char]0) 'F5' $false $false $false), '1b 5b 31 35 7e'),
  @('Ctrl+F5', (key ([char]0) 'F5' $false $false $true), '1b 5b 31 35 3b 35 7e'),
  @('Delete', (key ([char]0) 'Delete' $false $false $false), '1b 5b 33 7e'),
  @('Shift+Tab', (key "`t" 'Tab' $true $false $false), '1b 5b 5a'),
  @('Tab', (key "`t" 'Tab' $false $false $false), '09'),
  @('e-acute', (key ([char]0xe9) 'Oem1' $false $false $false), 'c3 a9'),
  @('emoji, high surrogate (waits)', (key ([char]0xd83d) 'Packet' $false $false $false), ''),
  @('emoji, low surrogate (the pair)', (key ([char]0xde00) 'Packet' $false $false $false), 'f0 9f 98 80'),
  @('Ctrl+Space', (key ([char]0) 'Spacebar' $false $false $true), '00'),
  @('Ctrl+at (Ctrl+Shift+2)', (key ([char]0) 'D2' $true $false $true), '00'),
  @('AltGr dead key (tilde on AZERTY: Ctrl+Alt, no character yet)', (key ([char]0) 'D2' $false $true $true), ''),
  @('the tilde the dead key composes', (key '~' 'D2' $false $false $false), '7e'),
  @('AltGr+at (Ctrl+Alt with a character)', (key '@' 'D0' $false $true $true), '40'),
  @('Space', (key ' ' 'Spacebar' $false $false $false), '20'),
  @('Shift alone', (key ([char]0) 'NoName' $true $false $false), '')
)
$failed = 0
foreach ($case in $cases) {
  $got = hex (Encode $case[1])
  if ($got -ne $case[2]) { Write-Output ("FAIL {0}: expected [{1}] got [{2}]" -f $case[0], $case[2], $got); $failed++ }
  else { Write-Output ("ok   {0}: {1}" -f $case[0], $got) }
}
Write-Output "failed: $failed"
exit $failed
