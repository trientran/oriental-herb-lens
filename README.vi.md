# Med Herb Lens

[English](README.md) · Tiếng Việt

Nhận dạng cây thuốc bằng camera điện thoại, tra cứu 4.799 loài thực vật với tên tiếng Việt và
tiếng Anh, và tự huấn luyện mô hình phân loại ảnh của riêng bạn, tất cả ngay trên thiết bị. Med Herb
Lens chạy trên Android, iOS và trình duyệt web.

> **Bản thử nghiệm nghiên cứu.** Med Herb Lens là một dự án nghiên cứu. Mô hình nhận dạng thảo dược
> do nhóm chúng tôi tự huấn luyện, chạy ngay trên thiết bị của bạn và biết 2.721 loài, nhưng chúng
> tôi chưa có đủ ảnh cho phần lớn các loài, nên kết quả thường bị sai. Ứng dụng không dùng dịch vụ
> nhận dạng của bất kỳ công ty nào khác. Tuyệt đối không ăn hoặc dùng cây làm thuốc chỉ dựa vào kết
> quả của ứng dụng.

## Tải ứng dụng

- Trên trình duyệt: [med-herb-lens.pages.dev/app](https://med-herb-lens.pages.dev/app/)
- Android: [Google Play](https://play.google.com/store/apps/details?id=com.uri.lee.dl)
- iPhone và iPad: [App Store](https://apps.apple.com/app/id6819173768)

## Ứng dụng làm được gì

- **Nhận dạng.** Hướng camera vào cây hoặc chọn ảnh; ứng dụng gợi ý đó có thể là loài nào, kèm độ
  tin cậy. *Toàn cảnh* nhận dạng mọi thứ trong khung hình; *Chọn một cây* tìm từng cây và nhận dạng
  lần lượt. Việc nhận dạng diễn ra trên thiết bị và dùng được khi không có mạng.
- **Tra cứu và tìm kiếm.** 4.799 loài theo hệ thống phân loại của [GBIF](https://www.gbif.org),
  tìm được bằng tên tiếng Việt (có hoặc không dấu), tên tiếng Anh hoặc tên khoa học. Mỗi loài có
  tên gọi, phân loại và đầy đủ thông tin từ GBIF, cùng ảnh tham khảo của những người đóng góp trên
  GBIF và iNaturalist và ảnh do người dùng chia sẻ.
- **Đã lưu.** Các loài yêu thích và những loài bạn xem gần đây.
- **Đóng góp.** Người dùng đã đăng nhập có thể chia sẻ ảnh thảo dược (kèm nơi chụp) và đề xuất tên
  tiếng Việt. Ảnh được chia sẻ có thể bị báo cáo, và có thể ẩn ảnh của một người đóng góp.
- **Tự huấn luyện mô hình.** Thu thập ảnh những loài cây bạn quan tâm, huấn luyện bộ phân loại ngay
  trên thiết bị và dùng thử với camera. Chia sẻ mô hình dưới dạng tệp `.tflite` chuẩn có kèm danh
  sách loài, nên dùng được trong bất kỳ ứng dụng nào chạy mô hình TensorFlow Lite; khi được nhập vào
  Med Herb Lens trên thiết bị khác, mô hình còn có thể học tiếp. Bạn cũng có thể chia sẻ mô hình với
  mọi người ngay trong ứng dụng.
- **Chế độ nghiên cứu.** Chạy các thí nghiệm học liên tục ngay trên thiết bị: chọn bộ dữ liệu, kịch
  bản, chiến lược và số lần lặp, để thiết bị tự chạy (kể cả khi tắt màn hình), rồi lưu một tệp nén
  gồm mọi kết quả, số đo của thiết bị và các mô hình đã huấn luyện.
- **Cách trích dẫn.** Các bài báo đằng sau ứng dụng, trong mục Hồ sơ, sẵn sàng để sao chép.

## Quyền riêng tư

Ảnh được nhận dạng ngay trên thiết bị và chỉ rời khỏi thiết bị khi bạn chọn chia sẻ. Ứng dụng gửi
số liệu sử dụng ẩn danh (ví dụ những loài nào được nhận dạng) phục vụ nghiên cứu, không kèm tên,
email, ảnh hay vị trí; bạn có thể tắt trong mục Hồ sơ. Không có quảng cáo, không theo dõi. Chi tiết
trong [chính sách quyền riêng tư](https://med-herb-lens.pages.dev/pages/privacy-policy.html)
(bằng tiếng Anh).

## Nghiên cứu và cách trích dẫn

Nếu bạn dùng Med Herb Lens, dữ liệu hoặc mã nguồn của ứng dụng trong công trình của mình, vui lòng
trích dẫn:

Tran, T. P., Ud Din, F., Brankovic, L., Sanin, C., & Hester, S. M. (2025). Med Herb Lens: A
prototype AI app for medicinal plant identification. *Procedia Computer Science*, 270, 2603–2612.
[https://doi.org/10.1016/j.procs.2025.09.382](https://doi.org/10.1016/j.procs.2025.09.382)

Tran, T. P., Ud Din, F., Brankovic, L., Sanin, C., & Hester, S. M. (2026). Resource-efficient
continual learning for medicinal plant identification: A periodic retraining approach for
edge-deployed agricultural IoT applications. *IoT*, 7(3), 57.
[https://doi.org/10.3390/iot7030057](https://doi.org/10.3390/iot7030057)

## Công nghệ

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) và
  [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/): một ứng dụng cho
  Android, iOS và web
- [LiteRT](https://ai.google.dev/edge/litert) (TensorFlow Lite) để nhận dạng trên thiết bị, với các
  mô hình nhúng ảnh của [MediaPipe](https://ai.google.dev/edge/mediapipe) làm nền cho các mô hình
  người dùng tự huấn luyện; lớp cuối của chúng được huấn luyện bằng mã Kotlin dùng chung
- [Firebase](https://firebase.google.com): đăng nhập, ảnh và đề xuất tên được chia sẻ, số liệu sử
  dụng, cài đặt từ xa và App Check
- [Cloudflare](https://www.cloudflare.com): trang web và ứng dụng web (Pages), lưu trữ ảnh và mô
  hình (R2) và tải lên (Workers)
- [GBIF](https://www.gbif.org): danh mục loài và ảnh tham khảo

## Giấy phép

Mã nguồn được cấp phép theo [Apache License 2.0](LICENSE). Mô hình nhận dạng thảo dược, danh mục
loài như được biên soạn cho ứng dụng, tài liệu và nội dung trang web, cùng hình ảnh được cấp phép
theo [Creative Commons Ghi công 4.0 Quốc tế (CC BY 4.0)](LICENSE-CC-BY-4.0): bạn có thể chia sẻ và
chỉnh sửa cho bất kỳ mục đích nào, miễn là ghi công (xem *Nghiên cứu và cách trích dẫn*).
Tệp [NOTICE](NOTICE) cho biết phần nào theo giấy phép nào.

Tác phẩm của người khác có trong ứng dụng giữ nguyên điều khoản riêng: danh mục dựa trên dữ liệu
GBIF, ảnh tham khảo thuộc về tác giả của chúng (mỗi ảnh hiển thị kèm giấy phép, nhiều ảnh chỉ dùng
cho mục đích phi thương mại), các mô hình nhúng của MediaPipe theo Apache 2.0, và mỗi thư viện theo
giấy phép riêng. Mô hình người dùng chia sẻ trong ứng dụng thuộc về họ, được chia sẻ theo CC BY 4.0.

## Liên hệ

Trien Phat Tran, University of New England:
[ttran72@myune.edu.au](mailto:ttran72@myune.edu.au) hoặc [tptrien@gmail.com](mailto:tptrien@gmail.com)

<!-- about:end (trang Giới thiệu trên web hiển thị mọi nội dung phía trên dòng này) -->

Hướng dẫn cho nhà phát triển (tiếng Anh): xem [README.md](README.md#for-developers).
