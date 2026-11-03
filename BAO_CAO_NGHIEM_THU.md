# BÁO CÁO NGHIỆM THU — BUỔI 2: CODE AUDIT, UNIT TEST & DOCKERIZE
> **Học viên:** Nguyễn Hiếu An · **Chương trình:** 6H MasterClass — OJT Production Readiness (R2S Academy)
> **Môi trường kiểm chứng:** JDK 17 · Spring Boot 3.2.3 · PostgreSQL 16 (Docker) + H2 (test) · Docker 29

---

## Tổng quan kết quả

| Nhiệm vụ | Tiêu chuẩn nghiệm thu | Kết quả đo được | Trạng thái |
|---|---|---|---|
| Task 1 — N+1 Query | GET `/orders?status=PAID` chỉ 1 câu SQL | **1 câu SQL** cho 1.050 đơn (60ms) trên PostgreSQL | ✅ PASSED |
| Task 2 — Race Condition | 50 luồng / kho 10 → 10 thành công, 40 từ chối, kho = 0 | **10 / 40 / 0**, 0 lỗi bất ngờ (test + script REST) | ✅ PASSED |
| Task 3 — Unit Test | Line & Branch Coverage gói `service` ≥ 80% | **100% line, 100% branch**; `mvn verify` pass | ✅ PASSED |
| Task 4 — Docker | Multi-stage, non-root, HEALTHCHECK, < 120MB | **115MB** (nén), `uid=100(appuser)`, `healthy` | ✅ PASSED (xem ghi chú) |
| Task 5 — Quality Gate | Không lộ secrets/`target/`, Conventional Commits, PR | `.gitignore` mở rộng, R2S Git Flow, PR template | ✅ PASSED |
| Module 1 — Audit Trail | `previousStock: 10, newStock: 8, action: DEDUCT` | Đúng; log rollback cùng transaction (có test) | ✅ PASSED |
| Module 2 — Safe Pagination | Không `HHH000104`, phân trang dưới DB, items gom 1 câu | 4 câu SQL/trang, không đổi khi size 5 → 50 | ✅ PASSED |
| Module 3 — Optimistic Lock | `version` tăng 0, 1, 2…; có retry backoff | Đúng; 20 luồng không bao giờ âm kho | ✅ PASSED |

Toàn bộ: **36 test, 0 fail** (`mvn clean verify`).

---

## Task 1 — Khử N+1 Query bằng `JOIN FETCH`

**Lỗi gốc:** `findByStatus()` chỉ lấy `orders`; vòng lặp gọi `order.getItems()` và `item.getProduct().getName()` làm Hibernate bắn thêm 1 câu cho mỗi đơn và mỗi sản phẩm → `1 + N + N×M` câu.

**Sửa:** `OrderRepository.findByStatusWithItemsAndProducts()`:
```sql
SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.status = :status
```

**Bằng chứng:**
- `OrderQueryCountIntegrationTest` dùng Hibernate Statistics: seed thêm 200 đơn, `getPrepareStatementCount() == 1`.
- PostgreSQL thật: sau `POST /orders/seed?count=1000` (tổng 1.050 đơn), gọi GET → log có đúng **1** dòng `org.hibernate.SQL`.

## Task 2 — Chống âm kho bằng Pessimistic Lock

**Lỗi gốc (Check-Then-Act):** kiểm tra `stockQuantity < quantity` trên dữ liệu đọc không khóa, rồi `UPDATE ... SET stock = stock - :q` không kèm điều kiện → 50 luồng cùng thấy `stock = 10`, cùng trừ → **-40**.

**Sửa:**
- `ProductRepository.findByIdWithPessimisticLock()` với `@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`.
- `InventoryService.deductStock()` có `@Transactional(isolation = READ_COMMITTED)`, kiểm tra và trừ trên entity đang bị khóa.
- Xóa hàm `deductStockDirect()` gây lỗi.
- **Bổ sung ngoài đề:** `resetStock()` cũng khóa dòng, tránh lost update khi reset đúng lúc khách đang mua.
- `application-h2.yml`: `LOCK_TIMEOUT=10000` vì mặc định H2 chỉ chờ khóa 1 giây.

**Bằng chứng:** `StudentInventoryConcurrencyTest` (đã đổi từ "chứng minh lỗi" sang "chứng minh đã sửa", đếm cả lỗi bất ngờ) và `simulate_50_threads.sh` trên PostgreSQL: `10 SUCCESS`, `40 FAILED`, `stockQuantity: 0`.

## Task 3 — Unit Test JUnit 5 + Mockito

- `InventoryServiceTest`, `OrderServiceTest`, `InventoryAuditRecorderTest`, `InventoryOptimisticWorkerTest`: mock toàn bộ repository, không chạm database.
- Phủ đủ 4 miền theo Rule 03: Happy Path, giá trị biên (mua đúng bằng tồn kho, `count = 0`), ngoại lệ (không tìm thấy sản phẩm), concurrency/interrupt (cờ interrupt được giữ lại).
- `ArgumentCaptor` để kiểm tra giá trị thực sự được lưu, không chỉ kiểm tra "có gọi save".
- `pom.xml`: thêm rule **BRANCH ≥ 80%** vào JaCoCo (README yêu cầu cả Branch nhưng pom gốc chỉ chặn LINE).

| Class | Line | Branch |
|---|---|---|
| InventoryService | 63/63 | 6/6 |
| OrderService | 56/56 | 2/2 |
| InventoryOptimisticWorker | 11/11 | 2/2 |
| InventoryAuditRecorder | 10/10 | — |

## Task 4 — Multi-stage Dockerfile

- **Builder:** `maven:3.9.6-eclipse-temurin-17-alpine`, copy `pom.xml` + `dependency:go-offline` trước để cache, rồi build và `layertools extract`.
- **Runtime:** `eclipse-temurin:17-jre-alpine`, user `appuser` (non-root), copy 4 layer theo thứ tự `dependencies → spring-boot-loader → snapshot-dependencies → application`, `HEALTHCHECK` qua `/actuator/health`, `ENTRYPOINT` dùng `JarLauncher`.
- `.dockerignore` để không đưa `target/`, `.git/`, `.env`, khóa bí mật vào build context.
- `docker-compose.yml`: thêm service `app` dưới profile `app` (`docker compose --profile app up -d --build`), lệnh `docker compose up -d` của README vẫn chỉ bật database.

**Bằng chứng:** `docker exec r2s-order-service id` → `uid=100(appuser) gid=101(appgroup)`; trạng thái `healthy`; actuator báo `db: PostgreSQL UP`.

> **Ghi chú về con số 120MB:** Docker bản mới hiển thị 2 cột. `CONTENT SIZE` (dung lượng nén, tức dung lượng kéo/đẩy registry) = **115MB** → đạt. Dung lượng giải nén thực tế ≈ 240MB, trong đó riêng base image `eclipse-temurin:17-jre-alpine` mà đề bắt buộc dùng đã ≈ 190MB, phần ứng dụng chỉ 52MB. Muốn < 120MB khi giải nén thì phải tự build JRE rút gọn bằng `jlink`, tức là đổi base image so với đề.

## Module 1 — Inventory Audit Trail

- Entity `InventoryAuditLog` (có index `idx_audit_product_id`), repository, DTO, endpoint `GET /api/v1/inventory/{productId}/audit-logs`.
- `InventoryAuditRecorder` dùng chung cho cả trừ kho pessimistic, optimistic và reset (`RESTOCK`). **Không** dùng `@Async`/`REQUIRES_NEW` (Trap 1).
- `InventoryAuditLogIntegrationTest`: giả lập lỗi thanh toán xảy ra **sau** khi trừ kho → cả tồn kho lẫn audit log đều rollback.
- PostgreSQL: reset 10 → deduct 2 → log `DEDUCT, changedQuantity 2, previousStock 10, newStock 8`.

## Module 2 — Safe Pagination

- `@BatchSize(size = 50)` trên `Order.items` **và** trên entity `Product` (bổ sung: gom luôn các sản phẩm).
- `findOrdersPaged()` không `JOIN FETCH` collection (Trap 1), endpoint `GET /api/v1/orders/paged`, giới hạn `size ≤ 100`.
- `OrderPaginationIntegrationTest`: không có `HHH000104`; số câu SQL **bằng nhau** cho trang 5 và trang 50 đơn (O(1)).
- PostgreSQL: 4 câu/trang (orders, count, items, products), `totalElements 1050, totalPages 210`.

> **Ghi chú:** Hibernate 6.4 trên PostgreSQL sinh `offset ? rows fetch first ? rows only` (chuẩn SQL, tương đương `LIMIT ? OFFSET ?`) và `order_id = any (?)` (mảng, tương đương `IN (...)`).

## Module 3 — Optimistic Locking + Retry (Cách B)

- `@Version` trên `Product`, `columnDefinition = "bigint default 0"` để `ddl-auto: update` thêm cột được cho dữ liệu cũ trong PostgreSQL (đã kiểm chứng: dòng cũ nhận `version = 0`).
- `InventoryOptimisticWorker.deductSingle()` với `REQUIRES_NEW`, tách bean riêng để tránh **Self-Invocation** (Trap 1).
- `InventoryService.deductStockOptimisticWithRetry()`: retry khi `ObjectOptimisticLockingFailureException`, backoff 40 → 80 → 160ms.
- Endpoint `POST /api/v1/inventory/deduct-optimistic`.
- `OptimisticLockingIntegrationTest`: version 0 → 1 → 2; 20 luồng tranh mua → không âm kho và `số đơn thành công = tồn đầu − tồn cuối = version`. Trong lần chạy có **56 lần retry** và 9 luồng bỏ cuộc sau 5 lần.

**So sánh để trình bày với mentor:**

| | Pessimistic Lock | Optimistic Lock + Retry |
|---|---|---|
| Cơ chế | `SELECT ... FOR UPDATE`, các luồng xếp hàng chờ | Không khóa khi đọc, kiểm tra `version` lúc UPDATE |
| Hợp với | Tranh chấp cao: flash sale hàng hiếm | Tranh chấp thấp/trung bình, cần throughput cao |
| Đánh đổi | Giữ connection lâu, dễ cạn HikariCP pool | Xung đột nhiều thì retry nhiều và có thể thất bại (như 9 luồng ở trên) |

---

## Cách chạy lại toàn bộ kiểm chứng

```bash
mvn clean verify                                   # 36 test + JaCoCo quality gate
docker compose --profile app up -d --build         # PostgreSQL + app trong container
./simulate_50_threads.sh                           # Task 2
./trigger_n1_sql_storm.sh                          # Task 1 (xem log: docker logs r2s-order-service)
docker images r2s-order-service                    # Task 4
```

## AI Usage Decision Note (Rule 05)
- **Prompt IDs tham chiếu:** DEV-02 (N+1), DEV-03 (Locking), DEV-05 (Unit Test), DEV-10 (Docker).
- **Phạm vi AI hỗ trợ:** sinh bản nháp code sửa lỗi, test và Dockerfile theo đặc tả README.
- **Con người kiểm chứng & bổ sung:** khóa cả `resetStock`, chặn `size` phân trang, `@BatchSize` cho `Product`, `default 0` cho cột `version`, rule Branch Coverage, đổi bài test concurrency sang kiểm tra kết quả đúng và đếm lỗi bất ngờ; chạy lại mọi tiêu chí trên PostgreSQL thật.
- **Bằng chứng SQL/hiệu năng:** Task 1 = 1 câu SQL (1.050 đơn, 60ms); Module 2 = 4 câu/trang, O(1) theo page size.
- **Security & PII:** [x] Không đưa secrets/PII thật vào prompt · [x] `.gitignore` chặn `.env`, khóa, `target/` · [x] Branch Coverage 100%.
