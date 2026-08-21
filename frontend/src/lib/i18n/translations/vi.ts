import type { TranslationKey } from '@/lib/i18n/translations/en';

/**
 * Vietnamese strings. Typed against `TranslationKey` (not re-declared as its own literal
 * object) so a key added to or removed from `en.ts` fails this file to compile rather than
 * silently falling back to English at runtime.
 */
export const vi: Record<TranslationKey, string> = {
  'popup.eyebrow': 'Phân tích tab',
  'popup.title': 'LinkSentry',
  'popup.status.checking': 'Đang kiểm tra tab này…',
  'popup.status.ready': 'Sẵn sàng quét tab hiện tại.',
  'popup.status.unsupported':
    'Không thể quét tab này. Hãy mở một trang web http:// hoặc https:// thông thường, sau đó mở lại cửa sổ này.',
  'popup.status.rateLimited': 'Quá nhiều yêu cầu quét. Vui lòng đợi một chút rồi thử lại.',
  'popup.license.panelLabel': 'Trạng thái giấy phép',
  'popup.scan.button.scan': 'Quét tab này',
  'popup.scan.button.scanning': 'Đang quét…',
  'popup.scan.button.scanAgain': 'Quét lại',
  'popup.result.label': 'Kết quả phân tích',
  'popup.result.findingsHeading': 'Tín hiệu phát hiện',

  'languageSwitcher.label': 'Ngôn ngữ',
  'languageSwitcher.en': 'EN',
  'languageSwitcher.vi': 'VI',

  'license.checking': 'Đang kiểm tra cài đặt này…',
  'license.state.pending': 'Dùng thử',
  'license.state.licensed': 'Đã cấp phép',
  'license.state.expired': 'Đã hết hạn',
  'license.state.revoked': 'Đã thu hồi',
  'license.description.pending':
    'Cài đặt này chưa được cấp giấy phép. Vẫn có thể quét trong hạn mức dùng thử miễn phí.',
  'license.description.licensed': 'Cài đặt này hiện có toàn quyền truy cập.',
  'license.description.expired':
    'Giấy phép của cài đặt này đã hết hạn. Đã chuyển về hạn mức dùng thử miễn phí.',
  'license.description.revoked': 'Cài đặt này đã bị thu hồi. Đã chuyển về hạn mức dùng thử miễn phí.',
  'license.renewsOrExpires': 'Gia hạn hoặc hết hạn vào {date}',
  'license.noExpiry': 'Không có ngày hết hạn',
  'license.expiredOn': 'Đã hết hạn vào {date}.',
  'license.pendingActivation.title': 'Chờ kích hoạt',
  'license.pendingActivation.body':
    'Gửi mã này cho quản trị viên của bạn để yêu cầu cấp giấy phép cho cài đặt này. Việc sao chép mã không tự động cấp quyền truy cập — quản trị viên phải gắn mã vào một giấy phép.',
  'license.copyButton': 'Sao chép mã kích hoạt',
  'license.copyStatus.copied': 'Đã sao chép.',
  'license.copyStatus.failed': 'Không thể sao chép — vui lòng chọn mã theo cách thủ công.',
  'license.checkAgain': 'Kiểm tra lại trạng thái',
  'license.checkingAgain': 'Đang kiểm tra…',

  'findings.empty':
    'Không có tín hiệu nào được phát hiện bởi các quy tắc hiện tại. Điều này không có nghĩa là liên kết an toàn.',

  'riskBadge.low': 'Rủi ro thấp',
  'riskBadge.moderate': 'Rủi ro trung bình',
  'riskBadge.high': 'Rủi ro cao',
  'riskBadge.critical': 'Rủi ro nghiêm trọng',

  'nextSteps.heading': 'Bước tiếp theo được đề xuất',
  'nextSteps.action.low':
    'Không phát hiện tín hiệu rủi ro từ vựng đáng kể. Vẫn nên xác minh người gửi và sử dụng kênh chính thức trước khi nhập thông tin.',
  'nextSteps.action.moderate':
    'Hãy xem xét kỹ tên miền đã đăng ký. Nếu liên kết đến bất ngờ, hãy tự mở trang web chính thức thay vì dùng liên kết này.',
  'nextSteps.action.high':
    'Tránh mở liên kết hoặc nhập thông tin đăng nhập. Hãy xác minh yêu cầu qua ứng dụng, trang web hoặc kênh hỗ trợ chính thức của tổ chức.',
  'nextSteps.action.critical':
    'Không mở, đăng nhập, tải xuống hoặc chuyển tiếp liên kết này. Hãy báo cáo cho bộ phận an ninh hoặc CNTT của tổ chức bạn.',
  'nextSteps.copyButton': 'Sao chép tóm tắt an toàn',
  'nextSteps.copySuccess': 'Đã sao chép tóm tắt.',
  'nextSteps.copyFailure':
    'Không thể sao chép tóm tắt. Trình duyệt của bạn đã chặn quyền truy cập bộ nhớ tạm.',
  'nextSteps.disclaimer':
    'Bản tóm tắt chứa mức độ rủi ro, điểm số, tên miền đã đăng ký và tiêu đề các phát hiện. Không bao giờ bao gồm liên kết đã gửi.',
  'nextSteps.summary.title': 'Phân tích liên kết LinkSentry',
  'nextSteps.summary.riskLevelWord.low': 'Thấp',
  'nextSteps.summary.riskLevelWord.moderate': 'Trung bình',
  'nextSteps.summary.riskLevelWord.high': 'Cao',
  'nextSteps.summary.riskLevelWord.critical': 'Nghiêm trọng',
  'nextSteps.summary.riskLevel': 'Mức độ rủi ro: {level} (điểm {score}/100)',
  'nextSteps.summary.registeredDomain': 'Tên miền đã đăng ký: {domain}',
  'nextSteps.summary.domainUnknown': 'không xác định',
  'nextSteps.summary.findingsNone': 'Phát hiện: không có',
  'nextSteps.summary.findingsHeading': 'Các phát hiện:',
  'nextSteps.summary.recommendedAction': 'Hành động đề xuất: {action}',
  'nextSteps.summary.safetyNote':
    'Lưu ý: Phân tích từ vựng chỉ kiểm tra văn bản của liên kết. Nó không thể chứng minh rằng đích đến là an toàn.',

  'explain.button.idle': 'Giải thích kết quả này',
  'explain.button.loading': 'Đang tạo giải thích…',
  'explain.tryAgain': 'Thử lại',
  'explain.riskBadgeLabel': 'RỦI RO TỪ VỰNG {level}',
  'explain.detectedHeading': 'LinkSentry đã phát hiện',
  'explain.noSignals':
    'Không có tín hiệu từ vựng nào được phát hiện bởi các quy tắc hiện tại. Điều này không có nghĩa là liên kết an toàn.',
  'explain.aiContextLabel': 'Bối cảnh AI (tư vấn)',
  'explain.whatToDoHeading': 'Nên làm gì',
  'explain.disclaimer':
    'Thông tin này chỉ dựa trên các tín hiệu từ vựng. LinkSentry không bao giờ truy cập liên kết, vì vậy đây chỉ là tư vấn, không phải kết luận cuối cùng.',
};
