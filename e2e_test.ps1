$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
function Write-OK { param($m) Write-Host "OK: $m" -ForegroundColor Green }
function Write-ERR { param($m) Write-Host "ERR: $m" -ForegroundColor Red }

# Register
try{
  $regBody = @{username='e2e_user'; email='e2e_user@example.com'; password='password123'} | ConvertTo-Json
  $reg = Invoke-RestMethod -Method Post -Uri "$base/api/users/register" -ContentType 'application/json' -Body $regBody
  Write-OK "Registered user e2e_user"
} catch {
  Write-Host "Register may have failed or user exists: $_" -ForegroundColor Yellow
}

# Login
try{
  $loginBody = @{username='e2e_user'; password='password123'} | ConvertTo-Json
  $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $loginBody
  $token = $login.accessToken
  if(-not $token){ Write-ERR "No accessToken returned"; exit 1 }
  Write-OK "Logged in, token length: $($token.Length)"
} catch {
  Write-ERR "Login failed: $_"; exit 1
}
$headers = @{ Authorization = "Bearer $token" }

# Get profile
try{
  $me = Invoke-RestMethod -Uri "$base/api/users/me" -Headers $headers -Method Get
  Write-OK "Me: $($me.username) / $($me.id)"
} catch {
  Write-ERR "Get /me failed: $_"; exit 1
}

# Create conversation
try{
  $convBody = @{conversationName='E2E Conversation'; isGroup=$false} | ConvertTo-Json
  $conv = Invoke-RestMethod -Uri "$base/api/conversations" -Headers $headers -Method Post -ContentType 'application/json' -Body $convBody
  Write-OK "Created conversation: $($conv.id)"
} catch {
  Write-ERR "Create conversation failed: $_"; exit 1
}

# Send message (REST)
try{
  $msgBody = @{conversationId = $conv.id; senderId = $me.id; content='Hello from E2E test'; messageType='text'} | ConvertTo-Json
  $msg = Invoke-RestMethod -Uri "$base/api/messages" -Headers $headers -Method Post -ContentType 'application/json' -Body $msgBody
  Write-OK "Sent message: $($msg.id)"
} catch {
  Write-ERR "Send message failed: $_"; exit 1
}

# Fetch history
try{
  $hist = Invoke-RestMethod -Uri "$base/api/conversations/$($conv.id)/messages?page=1&pageSize=20" -Headers $headers -Method Get
  Write-OK "History count: $($hist.Count)"
} catch {
  Write-ERR "Fetch history failed: $_"; exit 1
}

# Upload file using HttpClient (multipart)
$file = Join-Path (Get-Location).Path "test_upload.txt"
if (-not (Test-Path $file)) { Write-ERR "Test file not found: $file"; exit 1 }
try{
  $client = New-Object System.Net.Http.HttpClient
  $content = New-Object System.Net.Http.MultipartFormDataContent
  $fs = [System.IO.File]::OpenRead($file)
  $fileContent = New-Object System.Net.Http.StreamContent($fs)
  $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/plain")
  $content.Add($fileContent, "file", "test_upload.txt")
  $content.Add((New-Object System.Net.Http.StringContent($msg.id.ToString())), "messageId")
  $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, "$base/api/files/upload")
  $req.Content = $content
  $req.Headers.Add("Authorization","Bearer $token")
  $resp = $client.SendAsync($req).Result
  if ($resp.IsSuccessStatusCode) { $body = $resp.Content.ReadAsStringAsync().Result; Write-OK "Uploaded file, response: $body" } else { Write-ERR "Upload failed: $($resp.StatusCode) - $($resp.Content.ReadAsStringAsync().Result)" }
  $fs.Close()
} catch {
  Write-ERR "Upload exception: $_"
}

Write-OK "E2E script finished"
