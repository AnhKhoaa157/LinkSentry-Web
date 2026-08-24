package com.lyanhkhoa.linksentry.scan.application;

import com.lyanhkhoa.linksentry.common.api.ResponseLocale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Localizes only the static title and explanation copy owned by the server.
 *
 * <p>Rule findings remain English in the analysis domain and in history. This
 * catalog is deliberately keyed by stable rule ID and has no interpolation or
 * access to submitted URL text, so localization cannot echo attacker-controlled
 * URL components.
 */
public final class FindingCopyLocalizer {

    private static final Map<String, String> ENGLISH_TITLES = Map.ofEntries(
            Map.entry("BRAND_DOMAIN_MISMATCH", "Hostname names a brand it is not registered to"),
            Map.entry("BRAND_LOOKALIKE_HOSTNAME", "Hostname closely resembles a known brand"),
            Map.entry("BRAND_IMPERSONATION_APK", "Brand impersonation paired with an Android package"),
            Map.entry("ENCODED_CHARACTERS", "Link path contains encoded characters"),
            Map.entry("EXCESSIVE_SUBDOMAINS", "Unusually deep subdomain structure"),
            Map.entry("EXCESSIVE_URL_LENGTH", "Link is unusually long"),
            Map.entry("IP_LITERAL_HOST", "Address uses a raw IP instead of a domain name"),
            Map.entry("KNOWN_URL_SHORTENER", "Link uses a URL-shortening service"),
            Map.entry("MISSING_HTTPS", "Connection is not encrypted"),
            Map.entry("PUNYCODE_HOST", "Domain name uses internationalised (Punycode) encoding"),
            Map.entry("SPECIAL_USE_OR_PRIVATE_HOST", "Address uses a private or special-use IP range"),
            Map.entry("SUSPICIOUS_KEYWORDS", "Subdomain uses a sensitive-sounding word"));

    private static final Map<String, FindingCopy> VIETNAMESE_COPY = Map.ofEntries(
            Map.entry(
                    "BRAND_DOMAIN_MISMATCH",
                    new FindingCopy(
                            "Tên máy chủ chứa tên thương hiệu nhưng không thuộc tên miền đã đăng ký cho thương hiệu đó",
                            "Địa chỉ này chứa tên của một thương hiệu đã biết, nhưng tên miền thực sự đăng ký cho liên kết không thuộc thương hiệu đó. Đây là kỹ thuật thường được dùng để giả mạo trang đăng nhập hoặc tài khoản của tổ chức đáng tin cậy. Điều này không chứng minh liên kết độc hại — tên thương hiệu có thể chỉ là trùng hợp — nhưng bạn nên xác minh trước khi nhập thông tin đăng nhập.")),
            Map.entry(
                    "BRAND_LOOKALIKE_HOSTNAME",
                    new FindingCopy(
                            "Tên máy chủ gần giống một thương hiệu đã biết",
                            "Một phần địa chỉ này gần giống tên của một thương hiệu đã biết — chẳng hạn như bị đổi một ký tự hoặc dùng ký tự giống nhau từ bảng chữ cái khác — nhưng tên miền thực sự đăng ký cho liên kết không thuộc thương hiệu đó. Đây là kỹ thuật thường được dùng để giả mạo trang đăng nhập hoặc tài khoản của tổ chức đáng tin cậy. Điều này không chứng minh liên kết độc hại, nhưng bạn nên xác minh trước khi nhập thông tin đăng nhập.")),
            Map.entry(
                    "BRAND_IMPERSONATION_APK",
                    new FindingCopy(
                            "Giả mạo thương hiệu đi kèm gói ứng dụng Android",
                            "Địa chỉ này kết hợp tên thương hiệu đã biết với tải xuống một gói ứng dụng Android. Hãy xác minh qua kênh chính thức của tổ chức trước khi cài đặt bất kỳ thứ gì. Tín hiệu này không xác nhận liên kết độc hại.")),
            Map.entry(
                    "ENCODED_CHARACTERS",
                    new FindingCopy(
                            "Đường dẫn liên kết chứa ký tự được mã hóa",
                            "Một phần đường dẫn của địa chỉ này được mã hóa phần trăm thay vì hiển thị dưới dạng văn bản thông thường. Điều này thường vô hại, nhưng cũng có thể được dùng để che giấu các ký tự đáng ngờ khi nhìn nhanh.")),
            Map.entry(
                    "EXCESSIVE_SUBDOMAINS",
                    new FindingCopy(
                            "Cấu trúc tên miền con sâu bất thường",
                            "Tên máy chủ có nhiều cấp tên miền con hơn dự kiến. Kỹ thuật này đôi khi được dùng để làm cho một tên miền không đáng tin trông giống thương hiệu đáng tin bằng cách đẩy tên miền đăng ký thực sự ra xa hơn trong địa chỉ.")),
            Map.entry(
                    "EXCESSIVE_URL_LENGTH",
                    new FindingCopy(
                            "Liên kết dài bất thường",
                            "Liên kết này dài hơn nhiều so với địa chỉ web thông thường. Độ dài quá mức đôi khi được dùng để che giấu đích thực sự hoặc chôn chi tiết đáng ngờ ở nơi khó nhận thấy.")),
            Map.entry(
                    "IP_LITERAL_HOST",
                    new FindingCopy(
                            "Địa chỉ dùng IP thô thay vì tên miền",
                            "Liên kết này trỏ trực tiếp đến một địa chỉ mạng dạng số thay vì một website có tên. Dịch vụ hợp pháp hầu như luôn được truy cập bằng tên; một địa chỉ trơ có thể che giấu đơn vị thực sự vận hành trang web.")),
            Map.entry(
                    "KNOWN_URL_SHORTENER",
                    new FindingCopy(
                            "Liên kết dùng dịch vụ rút gọn URL",
                            "Địa chỉ này thuộc một dịch vụ rút gọn liên kết, dịch vụ này che giấu đích thực sự cho đến khi liên kết được mở. Dịch vụ rút gọn được sử dụng hợp pháp rộng rãi, nhưng đặc điểm này cũng khiến chúng thường được dùng để che giấu đích độc hại.")),
            Map.entry(
                    "MISSING_HTTPS",
                    new FindingCopy(
                            "Kết nối không được mã hóa",
                            "Liên kết này dùng HTTP thay vì HTTPS, vì vậy dữ liệu gửi đến hoặc nhận từ trang web không được mã hóa. Bất kỳ ai trên cùng mạng cũng có thể đọc hoặc thay đổi dữ liệu, kể cả mật khẩu hay thông tin nhạy cảm khác.")),
            Map.entry(
                    "PUNYCODE_HOST",
                    new FindingCopy(
                            "Tên miền dùng mã hóa quốc tế (Punycode)",
                            "Một phần địa chỉ này được mã hóa để biểu diễn các ký tự ngoài bảng chữ cái Latin. Cách mã hóa này cho phép tên miền trông giống thương hiệu đáng tin bằng các ký tự gần giống, nên cần được kiểm tra kỹ hơn dù đôi khi được sử dụng hợp pháp.")),
            Map.entry(
                    "SPECIAL_USE_OR_PRIVATE_HOST",
                    new FindingCopy(
                            "Địa chỉ dùng dải IP riêng hoặc dành cho mục đích đặc biệt",
                            "Liên kết này trỏ trực tiếp đến một địa chỉ dành cho mạng riêng, máy cục bộ, tài liệu hoặc mục đích đặc biệt khác. Đây không phải địa chỉ website công cộng thông thường và cần được xác minh thêm.")),
            Map.entry(
                    "SUSPICIOUS_KEYWORDS",
                    new FindingCopy(
                            "Tên miền con chứa từ ngữ có vẻ nhạy cảm",
                            "Tên miền con của địa chỉ này chứa một từ thường được dùng để giả mạo trang đăng nhập hoặc xác minh tài khoản. Dịch vụ hợp pháp hiếm khi cần những từ như vậy bên ngoài tên miền đã đăng ký của mình.")));

    private FindingCopyLocalizer() {}

    /** Returns the stable rule catalog used for bounded history filters and insight titles. */
    public static Set<String> knownRuleIds() {
        return ENGLISH_TITLES.keySet();
    }

    /** Returns only backend-owned, non-interpolated title copy for an aggregate rule ID. */
    public static Optional<String> localizedTitle(String ruleId, ResponseLocale locale) {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(locale, "locale");
        String englishTitle = ENGLISH_TITLES.get(ruleId);
        if (englishTitle == null) {
            return Optional.empty();
        }
        if (locale == ResponseLocale.VIETNAMESE) {
            return Optional.ofNullable(VIETNAMESE_COPY.get(ruleId))
                    .map(FindingCopy::title)
                    .or(() -> Optional.of(englishTitle));
        }
        return Optional.of(englishTitle);
    }

    /** Returns localized copy, falling back to the canonical English finding copy. */
    public static FindingCopy localize(
            String ruleId, String englishTitle, String englishExplanation, ResponseLocale locale) {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(englishTitle, "englishTitle");
        Objects.requireNonNull(englishExplanation, "englishExplanation");
        Objects.requireNonNull(locale, "locale");

        if (locale == ResponseLocale.VIETNAMESE) {
            return VIETNAMESE_COPY.getOrDefault(ruleId, new FindingCopy(englishTitle, englishExplanation));
        }
        return new FindingCopy(englishTitle, englishExplanation);
    }

    public record FindingCopy(String title, String explanation) {
        public FindingCopy {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(explanation, "explanation");
        }
    }
}
