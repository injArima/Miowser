[CmdletBinding()]
param([Parameter(Mandatory)][string]$EvidenceDirectory)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $EvidenceDirectory).Path
$reports = @(Get-ChildItem -LiteralPath $root -Recurse -Filter '*benchmarkData.json')
if ($reports.Count -eq 0) { throw 'No Macrobenchmark JSON report found.' }
$rows = foreach ($report in $reports) {
    $data = Get-Content -LiteralPath $report.FullName -Raw | ConvertFrom-Json
    foreach ($benchmark in $data.benchmarks) {
        foreach ($metric in $benchmark.metrics.PSObject.Properties) {
            $samples = @($metric.Value.runs | Sort-Object)
            if ($samples.Count -eq 0) { continue }
            $n = $samples.Count
            $median = if ($n % 2) { $samples[[int][Math]::Floor($n / 2)] } else { ($samples[$n / 2 - 1] + $samples[$n / 2]) / 2 }
            [pscustomobject]@{
                Journey = $benchmark.name; Metric = $metric.Name; Samples = $n
                Median = [Math]::Round($median, 3)
                P95NearestRank = [Math]::Round($samples[[int][Math]::Ceiling(0.95 * $n) - 1], 3)
                Source = $report.FullName
            }
        }
    }
}
$rows | Export-Csv -LiteralPath (Join-Path $root 'summary.csv') -NoTypeInformation
$rows | Format-Table Journey,Metric,Samples,Median,P95NearestRank -AutoSize
$frames = foreach ($report in $reports) {
    $data = Get-Content -LiteralPath $report.FullName -Raw | ConvertFrom-Json
    foreach ($benchmark in $data.benchmarks) {
        foreach ($metric in $benchmark.sampledMetrics.PSObject.Properties) {
            $samples = @($metric.Value.runs | ForEach-Object { $_ } | ForEach-Object { $_ })
            $late = if ($metric.Name -eq 'frameOverrunMs') { @($samples | Where-Object { $_ -gt 0 }).Count } else { $null }
            [pscustomobject]@{
                Journey = $benchmark.name; Metric = $metric.Name
                P50 = $metric.Value.P50; P95 = $metric.Value.P95; P99 = $metric.Value.P99
                Samples = $samples.Count
                PositiveOverrunFrames = $late
                PositiveOverrunPercent = if ($null -ne $late -and $samples.Count -gt 0) {
                    [Math]::Round(100.0 * $late / $samples.Count, 3)
                } else { $null }
                Source = $report.FullName
            }
        }
    }
}
$frames | Export-Csv -LiteralPath (Join-Path $root 'frames.csv') -NoTypeInformation
$frames | Format-Table Journey,Metric,P50,P95,P99 -AutoSize
Write-Output 'Frame percentiles use the original sampledMetrics JSON; do not interpret a journey aggregate as input latency.'
