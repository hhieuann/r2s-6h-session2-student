## Tóm tắt thay đổi
<!-- Mô tả ngắn gọn PR này giải quyết vấn đề gì -->

## Bằng chứng kiểm thử
- [ ] `mvn verify` pass (JaCoCo Line & Branch Coverage >= 80% trên `com.r2s.masterclass.service`)
- [ ] Log SQL đã kiểm tra (số câu SQL, không có `HHH000104`)

## AI Usage Decision Note (Mandatory for AI-Assisted PRs)
- **Prompt IDs Used:** <!-- vd: DEV-02, DEV-03, DEV-05, DEV-10 -->
- **Scope of AI Generation:** <!-- phần nào do AI sinh -->
- **Human Modifications & Edge Cases Fixed:** <!-- phần nào con người sửa/bổ sung sau khi audit -->
- **SQL Query Plan / Performance Evidence:** <!-- số câu SQL, thời gian phản hồi -->
- **Security & PII Check:**
  - [ ] No secrets/PII in prompt
  - [ ] No secrets committed (`.env`, keys, `target/`)
  - [ ] Unit tests Branch Coverage >= 80%
