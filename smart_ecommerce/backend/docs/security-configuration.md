# Cấu hình JWT và database theo môi trường

Backend yêu cầu cấu hình rõ trước khi mở datasource. Không có khóa ký JWT hoặc
mật khẩu database dự phòng. Kiểm tra cấu hình chỉ xác minh định dạng; cấu hình
hợp lệ chưa chứng minh database kết nối được hoặc schema đã tồn tại.

`application.properties` không đặt mặc định cho cả bốn biến `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`. Nếu terminal/IDE trước đây dựa vào
fallback trong source, cần cung cấp đủ bốn biến trước lần chạy tiếp theo;
không tự thay bằng credential hoặc khóa mặc định khác. Các override property
Spring từ cơ chế cấu hình riêng vẫn được validator hiện có kiểm tra. Thay đổi
source này không cập nhật secret store, mật khẩu PostgreSQL hay tiến trình đang
chạy của môi trường nào.

Source hiện không có file profile `application-dev`, `application-test` hoặc
`application-prod` riêng. Các môi trường dùng cấu hình chung và giá trị được
inject; không thêm profile chứa secret để tránh yêu cầu cấu hình. `.env.example`
chỉ là mẫu, không tự được Spring Boot nạp.

## Các biến cần cung cấp

| Biến môi trường | Property Spring hiện có | Yêu cầu |
|---|---|---|
| `DB_URL` | `spring.datasource.url` | Bắt buộc; URL JDBC PostgreSQL hợp lệ, có database, không chứa username/password hoặc credential trong URL |
| `DB_USERNAME` | `spring.datasource.username` | Bắt buộc, không rỗng hoặc chỉ khoảng trắng |
| `DB_PASSWORD` | `spring.datasource.password` | Bắt buộc, không rỗng hoặc chỉ khoảng trắng |
| `JWT_SECRET` | `app.jwt.secret` | Bắt buộc; Base64 chuẩn, tối thiểu 32 byte sau giải mã; dùng khóa ngẫu nhiên riêng cho mỗi môi trường |
| `JWT_EXPIRATION_MS` | `app.jwt.expiration` | Tùy chọn; số nguyên dương theo millisecond; mặc định `86400000` (24 giờ) |

`JWT_SECRET` là Base64 của dữ liệu khóa ngẫu nhiên, không phải mật khẩu đem
encode Base64. Độ dài chuỗi Base64 không thay thế độ dài byte sau giải mã.
Backend giữ cách tạo khóa `Keys.hmacShaKeyFor(...)` và `signWith(secretKey)`:
thuật toán HMAC được thư viện chọn theo khóa; không ép mọi khóa thành HS256.

URL có dạng `jdbc:postgresql://<HOST>:<PORT>/<DATABASE>`. Cung cấp credential
qua hai biến riêng, không đặt vào phần user-info hoặc query của URL. Query
không được override host/port/database (`host`, `port`, `dbname`, `PGHOST`,
`PGPORT`, `PGDBNAME`) hoặc dùng `service` để lấy cấu hình kết nối bên ngoài.
Các tùy chọn như `sslmode` vẫn được chấp nhận. Không đưa
secret vào URL, command-line arguments, ảnh chụp màn hình hoặc log lỗi cấu hình.

Thiếu/blank biến bắt buộc, key Base64 sai hoặc dưới 32 byte, expiration không
hợp lệ phải làm ứng dụng dừng trước datasource và initializer. Khi cấu hình
hợp lệ, Hibernate vẫn dùng `ddl-auto=validate`; database/schema cần được chuẩn
bị riêng. Startup thực sự có thể ghi role và tài khoản bootstrap nếu cấu hình
initializer cho phép, nên chỉ dùng database dev/test/demo đã chủ động chọn.

## Thiết lập trong PowerShell trên Windows

Chạy trong một terminal PowerShell mới. Các biến dưới đây thuộc **process hiện
tại và các process con**, không ghi vào User/Machine environment hoặc file.
Đoạn lệnh không in khóa JWT hoặc mật khẩu database.

```powershell
Set-Location -LiteralPath 'D:\CDCNTT\smart_ecommerce\backend'

$env:DB_URL = Read-Host 'JDBC PostgreSQL URL cua database dev/test (khong credential trong URL)'
$env:DB_USERNAME = Read-Host 'Database username cua moi truong nay'

$dbPasswordInput = Read-Host 'Database password' -AsSecureString
$dbPasswordPointer = [IntPtr]::Zero
try {
    $dbPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($dbPasswordInput)
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($dbPasswordPointer)
}
finally {
    if ($dbPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($dbPasswordPointer)
    }
    $dbPasswordInput.Dispose()
}

# Sinh khoa moi cho moi truong local/test; khong thay khoa production tai day.
$jwtKeyBytes = New-Object byte[] 64
$jwtRandomSource = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtRandomSource.GetBytes($jwtKeyBytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
}
finally {
    $jwtRandomSource.Dispose()
    [Array]::Clear($jwtKeyBytes, 0, $jwtKeyBytes.Length)
}
```

Có thể chọn 32 byte thay vì 64 byte; cả hai đủ mức tối thiểu. Chạy lại đoạn sinh
khóa sẽ thay khóa trong terminal này: token ký bằng khóa trước sẽ không còn hợp
lệ trên instance dùng khóa mới. Giữ một khóa local trong phiên làm việc nếu cần
restart mà vẫn giữ session; không sinh lại tùy tiện cho môi trường đang phục vụ
người dùng. Biến môi trường chứa giá trị plaintext trong process, dù password
được nhập bằng secure prompt.

Kiểm tra **chỉ trạng thái có cấu hình**, không hiển thị giá trị:

```powershell
@('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET') | ForEach-Object {
    $configuredValue = [Environment]::GetEnvironmentVariable($_, 'Process')
    [pscustomobject]@{
        Variable = $_
        Configured = -not [string]::IsNullOrWhiteSpace($configuredValue)
    }
}
Remove-Variable -Name configuredValue -ErrorAction SilentlyContinue
```

Không dùng `Get-ChildItem Env:`, `Write-Output $env:JWT_SECRET` hoặc các lệnh
dump toàn bộ configuration để kiểm tra secret. Bảng trên chỉ kiểm tra presence;
startup validator mới kiểm tra định dạng. Khi đã chọn đúng database dev/test,
có thể chạy backend từ **chính terminal này**:

```powershell
.\mvnw.cmd spring-boot:run
```

Đây là hướng dẫn chạy ứng dụng, không phải lệnh đã được thực thi để kiểm chứng
database trong thay đổi cấu hình này. Khi kết thúc process, có thể xóa các biến
trong terminal; việc này không sửa database hoặc file:

```powershell
@('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'JWT_EXPIRATION_MS') |
    ForEach-Object { [Environment]::SetEnvironmentVariable($_, $null, 'Process') }
```

## Chạy bằng VS Code

**Cách 1 — kế thừa process environment:** đặt biến trong PowerShell như trên,
sau đó mở `code .` tại thư mục backend. VS Code phải được khởi động từ process
đã có biến; nếu đã mở trước đó, đóng các process VS Code cũ và mở lại. Terminal
tích hợp có biến không đồng nghĩa Java debugger đã khởi động trước đó cũng có
biến. Không chèn secret thật vào `launch.json`.

**Cách 2 — envFile local:** sao chép `.env.example` thành `.env`, điền riêng cho
môi trường local và dùng Java debug configuration đọc file đó. `.env` đã được
ignore; `.env.example` chỉ có trường rỗng, không phải cấu hình chạy được.
Nếu mở thư mục `backend` làm workspace, mẫu `launch.json` là:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Smart E-Commerce - local environment",
      "request": "launch",
      "mainClass": "com.smart_ecommerce.backend.SmartEcommerceApplication",
      "cwd": "${workspaceFolder}",
      "envFile": "${workspaceFolder}/.env"
    }
  ]
}
```

Nếu workspace là thư mục `smart_ecommerce`, đổi `cwd` thành
`${workspaceFolder}/backend` và `envFile` thành `${workspaceFolder}/backend/.env`.
Mẫu này chỉ là hướng dẫn; không ghi đè launch configuration cá nhân đang có.

**Spring Boot không tự động đọc `.env`.** `envFile` do VS Code Java debugger đọc
và đưa vào process. Chạy Maven từ terminal hoặc launch bằng công cụ khác không
tự có biến chỉ vì `.env` nằm cạnh `pom.xml`; phải truyền process environment
qua công cụ đó. Không dùng `Invoke-Expression` để chạy nội dung `.env`.

Giữ file local có secret ngoài version control và không export/sync lên dịch vụ
chia sẻ. `.gitignore` không gỡ bỏ file đã tracked trước đó; nếu credential từng
được commit, xử lý lịch sử và rotation theo môi trường, không chỉ thêm ignore.

## Kiểm tra fail-fast và Postman trong môi trường cô lập

Các bước dưới đây là **kế hoạch kiểm thử**, không khẳng định đã chạy API thành
công. Không chạy ứng dụng/test với database `smart_ecommerce` đang chứa dữ liệu
cá nhân để thử lỗi cấu hình, expiry hoặc rotation. Dùng database PostgreSQL
dev/test riêng đã có schema tương ứng; không đổi schema bằng nhiệm vụ này.

Kiểm tra từng trường hợp: bỏ một biến bắt buộc; biến chỉ khoảng trắng; DB_URL
không phải JDBC PostgreSQL hoặc chứa credential; JWT sai Base64/khóa ngắn;
expiration bằng 0, âm hoặc không phải số. Mong đợi startup dừng rõ ràng và
diagnostics không chứa giá trị secret, trước khi datasource/initializer hoạt
động. Phân biệt lỗi cấu hình với lỗi kết nối/schema sau khi cấu hình hợp lệ.

Trong Postman, dùng các biến local/sensitive `baseUrl`, `customerEmail`,
`customerPassword`, `customerName`, `customerToken`, `staffToken`, `adminToken`.
Không đưa giá trị secret thật vào collection được chia sẻ. Register hiện luôn
tạo CUSTOMER; STAFF/ADMIN cần tài khoản test có role được cấp đúng phía server,
không thêm trường role vào request đăng ký.

Request đăng ký thực tế:

```http
POST {{baseUrl}}/api/users/register
Content-Type: application/json
```

```json
{
  "fullName": "{{customerName}}",
  "email": "{{customerEmail}}",
  "password": "{{customerPassword}}"
}
```

Mong đợi `201 UserResponse`. Request đăng nhập thực tế:

```http
POST {{baseUrl}}/api/users/login
Content-Type: application/json
```

```json
{
  "email": "{{customerEmail}}",
  "password": "{{customerPassword}}"
}
```

Mong đợi `200 LoginResponse`; lưu `accessToken` riêng làm `customerToken`.
Password sai mong đợi 401; tài khoản bị khóa mong đợi 403. Không log token.
Các request protected dùng `Authorization: Bearer {{customerToken}}`, thay
bằng staffToken/adminToken khi kiểm tra tài khoản tương ứng.

| Request thực tế | Tài khoản test/quyền | Kết quả mong đợi |
|---|---|---|
| `GET /api/cart` | CUSTOMER | 200 CartResponse |
| `GET /api/staff` | ADMIN | 200 danh sách STAFF |
| `GET /api/orders/management` | STAFF hoặc ADMIN | 200 danh sách quản lý order |
| `GET /api/shipping` | STAFF hoặc ADMIN | 200 danh sách quản lý shipping |
| `GET /api/staff` | CUSTOMER-only hoặc STAFF-only | 403 |
| `GET /api/cart` | ADMIN-only hoặc STAFF-only | 403 |
| `GET /api/cart` | Không Authorization | 401 |
| `GET /api/cart` | Token bị sửa/invalid signature | 401 |
| `GET /api/cart` | Token đã hết hạn | 401 |

Các request GET trên không cần body. `GET /api/cart` có thể khởi tạo giỏ hàng
nếu tài khoản chưa có giỏ, nên dùng tài khoản và database test. Role-based expectations
phải xét toàn bộ roles thực tế của tài khoản; người dùng mang cả ADMIN và
CUSTOMER vẫn có quyền CUSTOMER. `GET /api/test` public không kiểm chứng JWT.

Để kiểm expiry, chỉ trên instance và database test cô lập: đặt một
`JWT_EXPIRATION_MS` dương ngắn, đăng nhập, kiểm protected request trước/sau expiry,
rồi bỏ override và restart instance test. Không rút thời hạn token của instance
demo/production đang dùng. Test CORS bằng origin frontend chính xác đã cho phép;
Postman thành công không chứng minh browser CORS thành công.

## Chạy test và bảo vệ datasource test

Maven mặc định loại `*IntegrationTests` và `BackendApplicationTests` khỏi test
run. Lệnh dưới đây chạy các unit/config/security tests được chọn bởi cấu hình
mặc định, không tự chạy Spring/database integration tests:

```powershell
mvn -o test
```

Để kiểm tra riêng MVC/JWT regression trước khi chạy toàn bộ test mặc định:

```powershell
mvn -o '-Dtest=SecurityRegressionTests' test
```

Suite MVC này giữ controller, filter JWT và security chain thật; repository và
`GoogleAuthService` là mock, không xác minh token với Google/JWKS và không tạo
datasource. Các assertion authentication/authorization vẫn chạy như cũ.
`SecurityConfigurationTests` còn nạp `application.properties` thật trong
environment cô lập: thiếu/blank từng biến bắt buộc phải dừng trước context;
cấu hình giả hợp lệ chỉ mở context rỗng, không kết nối database. Các fixture
chỉ tồn tại trong test, không phải secret mặc định cho ứng dụng.

Đây là lệnh hướng dẫn; `-o` dùng dependency đã có trong Maven cache, không tải
package. Nếu cache thiếu dependency, lệnh offline sẽ thất bại; không bỏ offline
hoặc cài thêm package chỉ để làm cho một kết quả audit trông thành công.

Integration tests cần opt-in rõ bằng `-Dtest` và **ba biến TEST riêng**:
`TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`. Guard chỉ chấp nhận URL
JDBC PostgreSQL có database tên `smart_ecommerce_test` hoặc bắt đầu bằng
`smart_ecommerce_test_`; hostname có thể là local hoặc remote được chỉ định rõ.
Tên database đúng chỉ là một lớp kiểm tra: người chạy vẫn cần xác nhận đây là
database test/disposable, không dùng database có dữ liệu thật chỉ vì tên có
chữ `test`. Credential vẫn phải tách khỏi URL.

Thiếu biến TEST phải fail, **không fallback sang `DB_URL`/`DB_USERNAME`/
`DB_PASSWORD` đang cấu hình cho ứng dụng**. Guard test override datasource, sinh
JWT chỉ dùng cho test và tắt bootstrap ADMIN; không dùng key JWT production.
`@Transactional` không tự bảo đảm fixture/initializer không chạm dữ liệu thật.
Schema test phải được chuẩn bị riêng trước vì Hibernate vẫn validate; các lệnh
dưới đây không tạo database hoặc migration.

Thiết lập biến test trong terminal riêng bằng secure prompt:

```powershell
$env:TEST_DB_URL = Read-Host 'JDBC URL cua database test da co schema (smart_ecommerce_test hoac smart_ecommerce_test_*)'
$env:TEST_DB_USERNAME = Read-Host 'Username chi dung cho database test'
$testDbPasswordInput = Read-Host 'Database test password' -AsSecureString
$testDbPasswordPointer = [IntPtr]::Zero
try {
    $testDbPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($testDbPasswordInput)
    $env:TEST_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($testDbPasswordPointer)
}
finally {
    if ($testDbPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($testDbPasswordPointer)
    }
    $testDbPasswordInput.Dispose()
}

# Chi chay sau khi da kiem tra isolation va schema test; khong tu dong chay.
mvn -o '-Dtest=*IntegrationTests,BackendApplicationTests' test
```

Có thể giới hạn `-Dtest` vào một integration test cần kiểm tra; vẫn cần đầy đủ
ba biến TEST. Không in giá trị các biến test, không dùng account DB production
và không tạo profile/migration bằng nhiệm vụ cấu hình này. Khi xong:

```powershell
@('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD') |
    ForEach-Object { [Environment]::SetEnvironmentVariable($_, $null, 'Process') }
```

Các integration commands ở tài liệu này **chưa được chạy** trong thay đổi
cấu hình; kết quả unit tests, nếu đã chạy, phải được báo riêng cùng lệnh và số
case, không thay thế bằng chứng database/API end-to-end.

## Chuẩn bị PostgreSQL test riêng trên Windows

Không chạy integration trên `smart_ecommerce`. Nếu chưa có database test được
xác nhận, cần người có quyền quản trị xem xét việc tạo database và role riêng
trước; không tự tạo hoặc sửa database phát triển. Tên đề xuất bên dưới chỉ dùng
cho môi trường test mới, không phải giá trị mặc định để chạy ứng dụng.

Kiểm tra metadata bằng tài khoản quản trị do người vận hành cung cấp; `-W` yêu
cầu nhập password kín, không đặt password trong câu lệnh:

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\17\bin'
& "$pgBin\psql.exe" -X -W -h localhost -p 5432 -U postgres -d postgres `
    -v ON_ERROR_STOP=1 -c "SELECT datname, pg_get_userbyid(datdba) AS owner FROM pg_database WHERE datname = 'smart_ecommerce_test_ppee';"
if ($LASTEXITCODE -ne 0) { throw 'Khong kiem tra duoc database test' }
```

Nếu chưa tồn tại, các lệnh sau là **đề xuất cần duyệt**, không phải lệnh đã chạy.
Thực hiện trong psql kết nối database quản trị `postgres`, không kết nối database
phát triển. Dừng nếu role hoặc database đã tồn tại; không thêm DROP/TRUNCATE để
làm script chạy lại được:

```sql
CREATE ROLE ppee_test_runner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
\password ppee_test_runner
CREATE DATABASE smart_ecommerce_test_ppee OWNER ppee_test_runner TEMPLATE template0;
REVOKE CONNECT, TEMPORARY ON DATABASE smart_ecommerce_test_ppee FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE smart_ecommerce_test_ppee TO ppee_test_runner;
```

`\password` yêu cầu nhập riêng và không chứa password cố định trong script.
Role chỉ được cấp quyền đối tượng cho DB test; không cấp quyền trên bảng/schema
của DB phát triển. Role mới vẫn có thể hưởng CONNECT từ PUBLIC trên database
khác theo cấu hình cluster; không coi việc tạo role riêng là bằng chứng cô lập
tuyệt đối. Không tự sửa ACL của database phát triển để xử lý điều này.

DB test cần schema tương thích trước khi chạy vì Hibernate dùng validate. Một
phương án cần duyệt là sao chép **schema-only**, không sao chép dữ liệu người
dùng, từ schema hiện tại đã xác nhận. Ví dụ chạy tại thư mục workspace sau khi
đã duyệt provisioning, dùng file output mới không ghi đè file đang có:

```powershell
$testSchemaPath = Join-Path $env:TEMP ('ppee-test-schema-' + [guid]::NewGuid().ToString('N') + '.sql')
& "$pgBin\pg_dump.exe" -W -h localhost -p 5432 -U postgres -d smart_ecommerce `
    --schema-only --no-owner --no-privileges --no-comments --no-security-labels `
    --file $testSchemaPath
if ($LASTEXITCODE -ne 0) { throw 'Khong xuat duoc schema-only; dung tai day' }

# Doc/kiem tra file schema truoc khi import. Chi import vao DB test moi, rong.
& "$pgBin\psql.exe" -X -W -h localhost -p 5432 -U ppee_test_runner `
    -d smart_ecommerce_test_ppee -v ON_ERROR_STOP=1 --single-transaction -f $testSchemaPath
if ($LASTEXITCODE -ne 0) { throw 'Schema test chua san sang; khong chay integration' }
```

Lệnh dump chỉ đọc schema của nguồn; lệnh import chỉ được chạy khi đã xác nhận
đúng DB test mới/rỗng. Không dùng bản dump có dữ liệu, không thêm `--clean`,
`--create`, hoặc restore vào `smart_ecommerce`. Schema-only export cần được
xem xét về extension/function/default trước khi import; thành công chưa bảo đảm
mọi constraint tương thích với test.

Sau provisioning, đăng nhập bằng `ppee_test_runner` và xác nhận
`current_database()`, `current_user`, tên bảng/schema, quyền của role. Đặt ba
biến TEST bằng secure prompt ở phần trên trong process test riêng. Không dùng
role `postgres` để chạy integration.

Các lớp Analytics, DataMining, OrderPhaseOne, Recommendation,
RecommendationTracking, Shipping, StaffManagement integration hiện có
`@Transactional` và `@Rollback` cho fixture. BackendApplicationTests và
StaffAuthorizationIntegrationTests không có transaction bao quanh toàn lớp;
startup `RoleDataInitializer` còn có thể ghi role ngoài transaction của test.
Không thấy cleanup DROP/TRUNCATE hoặc test base chung trong các lớp này.
Vì vậy rollback không thay thế database test tách biệt. Không tự xóa dữ liệu
test sau chạy; chỉ hủy/chuẩn bị lại DB dùng một lần theo quy trình được duyệt.

Nếu cần kiểm tra startup thật trên 8080, chọn rõ database test/demo đã chuẩn bị,
cung cấp bốn biến DB/JWT và để bootstrap ADMIN trống. Kiểm tra listener trước,
không tắt tiến trình khác. Startup có thể tạo role; chỉ thực hiện trên môi trường
đã cho phép ghi. `GET /api/test` chỉ xác nhận HTTP, không thay thế kiểm tra
schema và nghiệp vụ.

## Rotation: hướng dẫn vận hành riêng

Không tự rotate credential hoặc kết nối/sửa database trong thay đổi source này.
Nếu khóa/mật khẩu dự phòng cũ đã được dùng hoặc lộ, người vận hành cần:

1. Xác định đúng môi trường và nơi inject credential mà không in giá trị.
2. Chuẩn bị khóa mới ngẫu nhiên và DB credential theo quyền tối thiểu; cập nhật
   secret/env store của đúng môi trường, không commit secret.
3. Phối hợp cập nhật credential database với cấu hình ứng dụng và restart các
   instance theo kế hoạch. Việc thay password/grants PostgreSQL là thao tác
   vận hành riêng, không phải tự động xảy ra khi bỏ fallback.
4. Sau đổi khóa JWT, đăng nhập lại; kiểm token khóa cũ bị từ chối và token mới
   được chấp nhận trên protected endpoint. Không mặc định token cũ được giữ.
5. Xóa secret cũ khỏi chỗ lưu không cần thiết và xử lý lịch sử nếu đã commit;
   kiểm logs/collection/config chia sẻ không chứa credential.

Không thêm API mới, không đổi authentication DTO, quyền route, schema database,
payment provider hoặc Flutter trong phạm vi cấu hình này.
