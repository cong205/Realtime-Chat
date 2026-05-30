# Realtime-Chat (Backend + Frontend)

Ứng dụng chat thời gian thực với backend Spring Boot (Java 17, Maven) và frontend SPA nhẹ (vanilla JS). Dự án bao gồm:

- REST API cho authentication, user, conversation, message, file upload.
- WebSocket (STOMP over SockJS) để gửi/nhận tin nhắn thời gian thực.
- Lưu trữ file (avatars, attachments) trên filesystem dưới thư mục `uploads/` và được phục vụ qua `/uploads/**`.

This README viết bằng tiếng Việt, chứa hướng dẫn chạy, cấu hình và danh sách endpoint quan trọng để test bằng Postman.

**Quick Links**
- Source chính: [chat-production/src/main/java](chat-production/src/main/java)
- Frontend SPA: [chat-production/src/main/resources/static/js/main.js](chat-production/src/main/resources/static/js/main.js)
- CSS: [chat-production/src/main/resources/static/css/style.css](chat-production/src/main/resources/static/css/style.css)
- Application properties: [chat-production/src/main/resources/application.properties](chat-production/src/main/resources/application.properties)
- Database schema (SQL): [schema.sql](schema.sql)

---

## Tính năng chính

- Đăng ký / đăng nhập (JWT + Refresh token)
- Danh sách cuộc hội thoại (1-1 và nhóm)
- Tin nhắn thời gian thực qua WebSocket (STOMP)
- Gửi file đính kèm trong tin nhắn và upload avatar
- Reactions / message status (read/seen) endpoints

---

## Yêu cầu (Prerequisites)

- Java 17 (JDK)
- Maven 3.6+
- SQL Server (mặc định cấu hình trong `application.properties`) hoặc chỉnh sửa `spring.datasource.*` để dùng DB khác

---

## Cấu hình chính

Chỉnh các thông số trong [chat-production/src/main/resources/application.properties](chat-production/src/main/resources/application.properties), ví dụ:

```properties
server.port=8081
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=RealtimeChatProduction;...
spring.datasource.username=sa
spring.datasource.password=123456
spring.servlet.multipart.max-file-size=50MB
```

Lưu ý: thư mục lưu file tĩnh là `uploads/` (tạo cùng cấp với project khi chạy). Resource handler ánh xạ `/uploads/**` → filesystem (xem [chat-production/src/main/java/com/ndcong/chat/config/WebMvcConfig.java](chat-production/src/main/java/com/ndcong/chat/config/WebMvcConfig.java)).

---

## Chạy ứng dụng (development)

Từ thư mục `chat-production`:

```powershell
# chạy trực tiếp bằng Maven
mvn -DskipTests spring-boot:run

# hoặc xây artifacts rồi chạy
mvn -DskipTests package
java -jar target/*.jar
```

Sau khi server chạy, frontend tĩnh (SPA) được phục vụ tại `http://localhost:8081/` (mặc định). Nếu chỉnh port, thay đổi `server.port` trong `application.properties`.

---

## Endpoints REST chính

Chú ý: hầu hết endpoint yêu cầu header `Authorization: Bearer <accessToken>` sau khi login.

Authentication
- POST `/api/auth/login` — body: `{ "username": "...", "password": "..." }` → trả về `accessToken` và `refreshToken`.
- POST `/api/auth/refresh` — body: `{ "refreshToken": "..." }` → lấy access token mới.
- POST `/api/auth/logout` — body: `{ "refreshToken": "..." }`

Users
- POST `/api/users/register` — đăng ký user
- GET `/api/users/me` — lấy profile user hiện tại (requires auth)
- PUT `/api/users/me` — cập nhật profile (body: `fullName`, `avatarUrl`)
- POST `/api/users/me/avatar` — upload avatar (multipart `file`)
- GET `/api/users/search?q=...` — tìm kiếm user

Conversations
- GET `/api/conversations` — lấy danh sách conversations của user
- POST `/api/conversations` — tạo conversation mới (body: `conversationName`, `isGroup`, `memberIds`)
- GET `/api/conversations/{conversationId}` — lấy thông tin conversation
- GET `/api/conversations/{conversationId}/members` — lấy thành viên
- POST `/api/conversations/{conversationId}/members` — thêm member
- DELETE `/api/conversations/{conversationId}/members/{memberId}` — xóa member
- GET `/api/conversations/{conversationId}/messages?page=1&pageSize=20` — lấy lịch sử tin nhắn (kết quả đã kèm attachments)

Messages
- POST `/api/messages` — tạo/tự lưu tin nhắn (dùng khi không dùng WebSocket)
- GET `/api/messages/{id}` — lấy tin nhắn theo id
- PUT `/api/messages/{id}` — cập nhật tin nhắn
- DELETE `/api/messages/{id}` — xóa tin nhắn
- POST `/api/messages/{id}/reactions` — thêm reaction
- POST `/api/messages/{id}/status` — set message status (e.g., read)

Files / Attachments
- POST `/api/files/upload` — multipart: `file` + `messageId` (UUID). Lưu file lên filesystem và tạo `MessageAttachment` liên kết với message.
- Truy cập file: `/uploads/{images|videos|files}/{uuid.ext}` (được serve bởi Spring)

Xem mã tham chiếu controller: [chat-production/src/main/java/com/ndcong/chat/controller](chat-production/src/main/java/com/ndcong/chat/controller).

---

## WebSocket & realtime

WebSocket STOMP endpoint: `/ws` (SockJS được kích hoạt). Cấu hình tại [chat-production/src/main/java/com/ndcong/chat/config/WebSocketConfig.java](chat-production/src/main/java/com/ndcong/chat/config/WebSocketConfig.java).

Client workflow (ví dụ với SockJS + StompJS):

```js
const socket = new SockJS('/ws');
const client = Stomp.over(socket);
client.connect({ Authorization: 'Bearer ' + accessToken }, () => {
	// subscribe cuộc hội thoại
	client.subscribe('/topic/conversation/' + conversationId, msg => console.log(JSON.parse(msg.body)));
	// gửi tin nhắn
	client.send('/app/chat/' + conversationId, {}, JSON.stringify({ content: 'Hello', messageType: 'text' }));
});
```

Server xử lý tin nhắn tại `@MessageMapping("/chat/{conversationId}")` và broadcast đến `/topic/conversation/{conversationId}` (xem [chat-production/src/main/java/com/ndcong/chat/controller/ChatWebSocketController.java](chat-production/src/main/java/com/ndcong/chat/controller/ChatWebSocketController.java)).

Ngoài ra server có thể gửi thông báo tới queue của user: `/user/queue/notifications`.

---

## Flow gửi file trong tin nhắn (attachment)

1. Tạo message (REST hoặc WebSocket) để có `messageId` (nếu client dùng upload trước, server sẽ không biết liên kết message). Thông thường frontend làm 2 bước: POST `/api/messages` → nhận message.id
2. Gửi file multipart lên `/api/files/upload` với fields: `file` (file binary) và `messageId` (UUID)
3. Server trả về `MessageAttachment` chứa `fileUrl` (ví dụ `/uploads/files/xxx.png`), frontend có thể reload messages để hiển thị attachment.

---

## Frontend

Frontend là một SPA vanilla JS, source: [chat-production/src/main/resources/static/js/main.js](chat-production/src/main/resources/static/js/main.js). Khi phát triển chỉnh sửa file tại `src/main/resources/static/` rồi build/restart server hoặc sao chép file vào `target/classes/static`.

Nếu browser báo `Uncaught SyntaxError: Unexpected end of input` hoặc lỗi JS tương tự, kiểm tra file built tại `chat-production/target/classes/static/js/main.js` — file này phải giống source.

---

## Debug / Troubleshooting

- 401 Unauthorized: kiểm tra header `Authorization: Bearer <token>` và thử refresh token qua `/api/auth/refresh`.
- DB connection: kiểm tra `spring.datasource.url` và thông tin người dùng.
- File upload fails: kiểm tra quyền ghi thư mục `uploads/` và `spring.servlet.multipart.max-file-size` trong `application.properties`.
- WebSocket không kết nối: kiểm tra `registerStompEndpoints` (`/ws`) và cho phép origin nếu cần.

---

## Contribution

1. Fork → tạo branch feature → gửi Pull Request.
2. Giữ coding style tương tự existing code.

---

Nếu bạn muốn, tôi có thể bổ sung phần API docs chi tiết (ví dụ schema request/response cho từng endpoint) hoặc tạo Postman collection để test nhanh.
