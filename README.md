# Bàn phím tùy chỉnh (Android)

Bàn phím riêng viết bằng Kotlin, có màn hình để chỉnh kích thước từng hàng phím, khoảng cách, thụt lề và độ rộng các phím đặc biệt (Shift, Xóa, ?123, Enter, Space).

## Tính năng

- Bố cục QWERTY tiếng Anh, có chế độ số và ký hiệu (`?123`).
- Chỉnh chiều cao từng hàng (4 hàng).
- Chỉnh khoảng cách giữa các phím.
- Chỉnh thụt lề hàng ASDF.
- Chỉnh độ rộng phím Space và các phím đặc biệt (tỉ lệ so với phím thường).
- Chỉnh khoảng đệm dưới cùng để tránh thanh điều hướng của máy.
- Có phần xem trước ngay trong màn hình cài đặt.
- Nút Xóa giữ lâu sẽ xóa liên tục.

**Gõ tiếng Việt (Telex):** nút **VI/EN** ở hàng dưới cùng để chuyển chế độ.
- `s` sắc, `f` huyền, `r` hỏi, `x` ngã, `j` nặng, `z` bỏ dấu
- `aa` → â, `ee` → ê, `oo` → ô, `aw` → ă, `ow`/`uw` → ơ/ư, `uow` → ươ, `dd` → đ

Ví dụ: `Vieejt` → Việt, `tieengs` → tiếng, `dduowcj` → được.

## Cách build

1. Cài [Android Studio](https://developer.android.com/studio) trên máy tính.
2. Mở Android Studio, chọn **File > Open** và chọn thư mục `CustomKeyboard`.
3. Đợi Gradle sync xong. Nếu được hỏi về Android SDK, cứ chọn cài đặt mặc định.
4. Cắm điện thoại Android vào máy tính, bật **Gỡ lỗi USB** trong Tùy chọn nhà phát triển, rồi nhấn nút **Run** (▶).

Hoặc build ra file APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**, sau đó copy file `app-debug.apk` sang điện thoại và cài.

### Không có máy tính: build trên GitHub

1. Tạo tài khoản tại [github.com](https://github.com) và tạo một repository mới (để Private cũng được).
2. Tải toàn bộ nội dung thư mục `CustomKeyboard` lên repository đó (bao gồm thư mục `.github`).
3. Vào tab **Actions**, chọn workflow **Build APK**, nhấn **Run workflow** (hoặc chỉ cần push lên nhánh `main`).
4. Khi chạy xong, mở lần chạy đó, kéo xuống mục **Artifacts** và tải `custom-keyboard-debug-apk`. Giải nén sẽ được file `app-debug.apk`.
5. Copy file APK sang điện thoại, cho phép cài đặt từ nguồn không xác định rồi cài.

## Cách dùng

1. Mở app **Bàn phím tùy chỉnh** trên điện thoại.
2. Nhấn **1. Mở cài đặt để bật bàn phím**, bật "Bàn phím tùy chỉnh" trong danh sách.
3. Nhấn **2. Chọn bàn phím đang dùng** và chọn "Bàn phím tùy chỉnh".
4. Kéo các thanh trượt để chỉnh. Phần xem trước sẽ đổi ngay.
5. Mở một ô nhập liệu bất kỳ (ví dụ ứng dụng Ghi chú) để thử bàn phím. Mỗi lần mở bàn phím, kích thước mới sẽ được áp dụng.

## Gợi ý chỉnh

- Nếu hay chạm nhầm hàng dưới: tăng **Hàng dưới cao** và giảm **Đệm dưới cùng**.
- Nếu Space quá xa các phím khác: tăng **Phím Space** lên hoặc giảm **Phím Shift / Xóa / ?123 / Enter**.
- Nếu phím bị sát mép trên máy có thanh điều hướng lớn: tăng **Đệm dưới cùng**.

## Cấu trúc mã nguồn

```
app/src/main/java/com/example/customkeyboard/
├── KeySizeSettings.kt    Lưu và đọc kích thước (SharedPreferences)
├── KeyboardBuilder.kt    Dựng giao diện bàn phím và xử lý chạm
├── KeyboardService.kt    Dịch vụ bàn phím hệ thống (gửi chữ vào ô nhập)
└── SettingsActivity.kt   Màn hình cài đặt và thanh trượt
```

Muốn đổi bố cục phím, sửa các hàm `letterRows()`, `symbolRows()` và `bottomRow()` trong `KeyboardBuilder.kt`. Muốn đổi màu, sửa phần `companion object` cùng file.
