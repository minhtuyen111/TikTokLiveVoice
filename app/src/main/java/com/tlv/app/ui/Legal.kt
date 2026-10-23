package com.tlv.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val EFFECTIVE = "Có hiệu lực từ ngày 10/10/2026"

@Composable
private fun LegalPage(intro: String, sections: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Dim(EFFECTIVE)
        Text(intro, lineHeight = 22.sp)
        sections.forEachIndexed { i, (h, b) ->
            Text("${i + 1}. $h", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary)
            Text(b, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TermsScreen() = LegalPage(
    "Điều khoản sử dụng này điều chỉnh việc bạn truy cập và sử dụng ứng dụng Voice Live TikTok (\"Ứng dụng\"). " +
        "Bằng việc tạo tài khoản, đăng nhập hoặc tiếp tục sử dụng Ứng dụng, bạn xác nhận đã đọc, hiểu và đồng ý bị ràng buộc bởi toàn bộ điều khoản dưới đây. " +
        "Nếu không đồng ý, vui lòng ngừng sử dụng Ứng dụng.",
    listOf(
        "Dịch vụ cung cấp" to
            "Ứng dụng chuyển các bình luận của những buổi phát trực tiếp (LIVE) công khai thành giọng nói bằng công cụ đọc văn bản của thiết bị Android, " +
            "đồng thời có thể phát âm thanh vui nhộn (\"meme\") khi có người theo dõi, chia sẻ hoặc tặng quà. " +
            "Ứng dụng là công cụ hỗ trợ cho người phát trực tiếp và người xem; Ứng dụng không phát, lưu hay phân phối lại nội dung video của TikTok.",
        "Điều kiện sử dụng" to
            "Bạn phải từ 16 tuổi trở lên, hoặc có sự đồng ý của cha mẹ hay người giám hộ hợp pháp. Bạn cam kết thông tin đăng ký là chính xác, " +
            "dùng địa chỉ email thuộc quyền kiểm soát của mình và tuân thủ pháp luật hiện hành cũng như điều khoản của các nền tảng bên thứ ba mà bạn sử dụng cùng Ứng dụng.",
        "Tài khoản và bảo mật" to
            "Bạn chịu trách nhiệm giữ bí mật mật khẩu và mọi hoạt động diễn ra dưới tài khoản của mình. Hãy dùng mật khẩu mạnh, không chia sẻ cho người khác " +
            "và thông báo ngay cho chúng tôi khi nghi ngờ bị truy cập trái phép. Bạn cần xác minh email để dùng các chức năng LIVE. " +
            "Mỗi người nên dùng một tài khoản; việc mua bán, cho thuê hoặc chuyển nhượng tài khoản không được phép.",
        "Quy tắc sử dụng chấp nhận được" to
            "Bạn không được: (a) dùng Ứng dụng để quấy rối, đe dọa, phỉ báng, phát tán nội dung thù hận, bạo lực, khiêu dâm hoặc vi phạm pháp luật; " +
            "(b) gửi hoặc cố tình kích hoạt lượng yêu cầu bất thường nhằm gây quá tải hệ thống; (c) dò quét, khai thác lỗ hổng hoặc truy cập trái phép vào máy chủ, dữ liệu của người khác; " +
            "(d) vượt qua, vô hiệu hóa các biện pháp bảo vệ của Ứng dụng, sao chép, sửa đổi, đóng gói lại hoặc phân phối lại Ứng dụng khi chưa được phép bằng văn bản, " +
            "trong phạm vi pháp luật không cho phép ngoại lệ; (e) mạo danh người khác hoặc che giấu danh tính nhằm lừa đảo; (f) sử dụng Ứng dụng cho mục đích thương mại trái phép.",
        "Quyền quản trị và âm thanh meme" to
            "Thư viện âm thanh sự kiện (theo dõi, chia sẻ, tặng quà) do quản trị viên của Ứng dụng thêm, chỉnh sửa và gỡ bỏ. Người dùng thông thường chỉ có thể chọn bật, tắt " +
            "hoặc chọn âm thanh có sẵn cho từng loại sự kiện trên thiết bị của mình và không thể tải âm thanh lên thư viện chung. " +
            "Quản trị viên được thiết lập và kiểm tra ở phía máy chủ; Ứng dụng không chứa thông tin đăng nhập quản trị. " +
            "Nếu bạn là chủ sở hữu quyền đối với một âm thanh và cho rằng nó bị sử dụng không phù hợp, hãy liên hệ chúng tôi để được xem xét và gỡ bỏ kịp thời.",
        "Dịch vụ và thương hiệu của bên thứ ba" to
            "TikTok, Discord, Android, Google và các tên, biểu tượng khác thuộc về chủ sở hữu tương ứng và được nhắc đến chỉ nhằm mục đích nhận diện. " +
            "Ứng dụng là sản phẩm độc lập, không được TikTok, Discord hay bất kỳ bên nào nêu trên tài trợ, chứng thực hoặc liên kết chính thức. " +
            "Việc nhận bình luận LIVE phụ thuộc vào dịch vụ và giao thức của bên thứ ba có thể thay đổi bất kỳ lúc nào; bạn tự chịu trách nhiệm tuân thủ điều khoản của TikTok khi sử dụng.",
        "Quyền sở hữu trí tuệ" to
            "Ứng dụng, mã nguồn, giao diện, biểu tượng và tài liệu đi kèm thuộc quyền của nhà phát triển hoặc bên cấp phép. Chúng tôi cấp cho bạn quyền sử dụng cá nhân, " +
            "không độc quyền, không thể chuyển nhượng, có thể thu hồi để dùng Ứng dụng theo các điều khoản này. Mọi quyền không được cấp rõ ràng đều được bảo lưu.",
        "Tính sẵn sàng và thay đổi dịch vụ" to
            "Chúng tôi cố gắng duy trì dịch vụ ổn định nhưng không cam kết hoạt động liên tục, không gián đoạn hay không có lỗi. Dịch vụ có thể tạm ngừng để bảo trì, " +
            "hoặc bị ảnh hưởng bởi mạng, hệ điều hành, chế độ tiết kiệm pin và thay đổi từ nền tảng bên thứ ba. Chúng tôi có thể thêm, sửa hoặc ngừng tính năng khi cần thiết.",
        "Tạm khóa và chấm dứt" to
            "Chúng tôi có thể cảnh báo, hạn chế, tạm khóa hoặc chấm dứt tài khoản nếu bạn vi phạm điều khoản, gây rủi ro cho hệ thống hoặc người dùng khác, hoặc theo yêu cầu của cơ quan có thẩm quyền. " +
            "Bạn có thể ngừng sử dụng bất kỳ lúc nào và gửi yêu cầu xóa tài khoản trong mục Bảo mật.",
        "Miễn trừ bảo đảm" to
            "Trong phạm vi pháp luật cho phép, Ứng dụng được cung cấp \"nguyên trạng\" và \"như hiện có\", không kèm bảo đảm nào, rõ ràng hay ngụ ý, " +
            "về tính phù hợp cho mục đích cụ thể, độ chính xác của việc nhận diện hay đọc bình luận, hoặc việc không vi phạm quyền của bên thứ ba.",
        "Giới hạn trách nhiệm" to
            "Trong phạm vi pháp luật cho phép, nhà phát triển không chịu trách nhiệm đối với thiệt hại gián tiếp, ngẫu nhiên, đặc biệt hoặc mất lợi nhuận, dữ liệu, uy tín phát sinh từ việc sử dụng " +
            "hoặc không thể sử dụng Ứng dụng, kể cả nội dung bình luận do người xem đăng. Bạn tự chịu trách nhiệm về nội dung phát ra loa trong buổi LIVE của mình. " +
            "Giới hạn này không loại trừ trách nhiệm mà pháp luật không cho phép loại trừ.",
        "Thay đổi điều khoản" to
            "Chúng tôi có thể cập nhật điều khoản theo thời gian. Phiên bản mới được đăng trong Ứng dụng cùng ngày có hiệu lực. " +
            "Việc tiếp tục sử dụng sau thời điểm đó được hiểu là bạn chấp nhận bản cập nhật.",
        "Luật áp dụng và giải quyết tranh chấp" to
            "Điều khoản này được điều chỉnh bởi pháp luật nước Cộng hòa xã hội chủ nghĩa Việt Nam. Tranh chấp trước hết được giải quyết bằng thương lượng thiện chí; " +
            "nếu không đạt được thỏa thuận, tranh chấp được đưa ra cơ quan có thẩm quyền tại Việt Nam.",
        "Liên hệ" to
            "Mọi câu hỏi, phản ánh hoặc yêu cầu liên quan đến điều khoản này, vui lòng liên hệ nhà phát triển qua TikTok @minhtuyen523 hoặc cộng đồng Discord chính thức trong mục Giới thiệu ứng dụng.",
    ),
)

@Composable
fun PrivacyScreen() = LegalPage(
    "Chính sách này giải thích Voice Live TikTok thu thập, sử dụng, lưu trữ và bảo vệ dữ liệu của bạn như thế nào. " +
        "Chúng tôi chỉ xử lý lượng dữ liệu cần thiết để vận hành Ứng dụng và không bán dữ liệu cá nhân của bạn.",
    listOf(
        "Phạm vi áp dụng" to
            "Chính sách áp dụng cho Ứng dụng Android Voice Live TikTok và các dịch vụ máy chủ đi kèm (xác thực tài khoản, cấu hình dùng chung, thư viện âm thanh, máy chủ kết nối LIVE).",
        "Dữ liệu chúng tôi thu thập" to
            "Dữ liệu tài khoản: địa chỉ email, tên đăng nhập, trạng thái xác minh email, vai trò (người dùng hoặc quản trị viên), trạng thái tài khoản và thời điểm tạo. " +
            "Mật khẩu được hệ thống xác thực băm một chiều, chúng tôi không đọc được mật khẩu của bạn. " +
            "Yêu cầu xóa tài khoản: mã tài khoản và thời điểm gửi yêu cầu. " +
            "Khi bạn kết nối một kênh LIVE, tên kênh và mã định danh tài khoản của bạn được máy chủ kết nối xử lý để xác thực và giới hạn số kết nối đồng thời.",
        "Dữ liệu được xử lý nhưng không lưu trữ" to
            "Bình luận và sự kiện (theo dõi, chia sẻ, tặng quà) của buổi LIVE đi qua máy chủ kết nối đến Ứng dụng theo thời gian thực. Theo thiết kế, nội dung này không được ghi vào cơ sở dữ liệu; " +
            "trong Ứng dụng nó chỉ nằm trong bộ nhớ của phiên đang chạy và biến mất khi bạn ngắt kết nối hoặc đóng Ứng dụng. " +
            "Ứng dụng không yêu cầu, không nhận và không lưu mật khẩu hay thông tin đăng nhập TikTok của bạn.",
        "Dữ liệu lưu trên thiết bị" to
            "Cài đặt giọng đọc, bộ lọc, giao diện và lựa chọn âm thanh sự kiện; số liệu tổng hợp của tối đa 100 phiên gần nhất (kênh, thời lượng, số bình luận nhận và đã đọc, trạng thái cuối) mà không kèm nội dung bình luận; " +
            "tập tin âm thanh meme tạm lưu trong bộ nhớ đệm để phát nhanh; và phiên đăng nhập được mã hóa bằng khóa trong Android Keystore. Bạn có thể xóa lịch sử bất kỳ lúc nào trong mục Lịch sử hoạt động.",
        "Mục đích sử dụng dữ liệu" to
            "Chúng tôi dùng dữ liệu để tạo và bảo vệ tài khoản, xác minh email, khôi phục mật khẩu, vận hành chức năng đọc bình luận, phát âm thanh sự kiện, ngăn chặn lạm dụng và gian lận, " +
            "xử lý yêu cầu hỗ trợ hoặc xóa tài khoản, và tuân thủ nghĩa vụ pháp lý. Chúng tôi không dùng dữ liệu của bạn để quảng cáo và không xây dựng hồ sơ theo dõi hành vi.",
        "Chia sẻ dữ liệu" to
            "Chúng tôi không bán hay cho thuê dữ liệu cá nhân. Dữ liệu chỉ được xử lý bởi nhà cung cấp hạ tầng phục vụ Ứng dụng (dịch vụ xác thực và cơ sở dữ liệu Supabase, máy chủ kết nối LIVE) " +
            "theo hợp đồng bảo mật, hoặc được cung cấp cho cơ quan nhà nước có thẩm quyền khi có yêu cầu hợp pháp. " +
            "Âm thanh meme là nội dung công khai do quản trị viên đăng, không gắn với dữ liệu cá nhân của người dùng.",
        "Quyền của quản trị viên" to
            "Quản trị viên có thể xem thông tin hồ sơ cần thiết cho việc vận hành (ví dụ tên đăng nhập, trạng thái), khóa tài khoản vi phạm, xử lý yêu cầu xóa và quản lý thư viện âm thanh. " +
            "Quyền này được kiểm soát ở phía máy chủ và giới hạn trong mục đích vận hành, bảo mật và hỗ trợ người dùng.",
        "Liên kết và dịch vụ bên thứ ba" to
            "Các nút TikTok và Discord trong Ứng dụng mở trang tương ứng trên trình duyệt hoặc ứng dụng bên ngoài. Khi đó dữ liệu của bạn được điều chỉnh bởi chính sách của các nền tảng ấy; " +
            "chúng tôi không kiểm soát và không chịu trách nhiệm về chính sách đó. Công cụ đọc văn bản do hệ điều hành hoặc ứng dụng giọng nói bạn cài đặt cung cấp và hoạt động theo chính sách của nhà cung cấp tương ứng.",
        "Bảo mật dữ liệu" to
            "Chúng tôi áp dụng các biện pháp hợp lý: kết nối mã hóa HTTPS/WSS, băm mật khẩu, kiểm soát truy cập theo hàng dữ liệu ở máy chủ để mỗi người chỉ thấy dữ liệu của mình, " +
            "mã hóa phiên đăng nhập trên thiết bị, che giấu mã nguồn và phát hiện môi trường bị can thiệp (trình gỡ lỗi, công cụ hook, bản đóng gói lại). " +
            "Không có hệ thống nào an toàn tuyệt đối; hãy giữ thiết bị và mật khẩu của bạn an toàn, đồng thời đăng xuất khỏi mọi thiết bị khi nghi ngờ bị lộ.",
        "Thời gian lưu trữ" to
            "Dữ liệu tài khoản được giữ trong thời gian tài khoản còn hoạt động. Sau khi yêu cầu xóa được xử lý, dữ liệu tài khoản bị xóa hoặc ẩn danh hóa, " +
            "trừ phần pháp luật yêu cầu giữ lại. Dữ liệu cục bộ trên thiết bị được xóa khi bạn xóa lịch sử, đăng xuất hoặc gỡ cài đặt Ứng dụng.",
        "Quyền của bạn" to
            "Bạn có quyền được biết, truy cập, chỉnh sửa, yêu cầu xóa dữ liệu cá nhân, rút lại sự đồng ý và khiếu nại theo quy định pháp luật Việt Nam về bảo vệ dữ liệu cá nhân. " +
            "Trong Ứng dụng, bạn có thể đổi mật khẩu, đăng xuất khỏi mọi thiết bị, xóa lịch sử và gửi yêu cầu xóa tài khoản tại mục Bảo mật. Với yêu cầu khác, hãy liên hệ chúng tôi.",
        "Quyền riêng tư của trẻ em" to
            "Ứng dụng không dành cho người dưới 16 tuổi nếu chưa có sự đồng ý của cha mẹ hoặc người giám hộ. Nếu phát hiện đã thu thập dữ liệu của trẻ em ngoài quy định, chúng tôi sẽ xóa dữ liệu đó.",
        "Lưu trữ và chuyển dữ liệu qua biên giới" to
            "Dịch vụ xác thực, cơ sở dữ liệu và kho tệp có thể đặt tại trung tâm dữ liệu ngoài Việt Nam. Khi đó chúng tôi yêu cầu nhà cung cấp bảo vệ dữ liệu ở mức tương đương cam kết trong chính sách này.",
        "Cập nhật chính sách" to
            "Chúng tôi có thể cập nhật chính sách này khi tính năng hoặc quy định thay đổi. Phiên bản mới được đăng trong Ứng dụng kèm ngày có hiệu lực; với thay đổi quan trọng, chúng tôi sẽ thông báo rõ ràng trong Ứng dụng.",
        "Liên hệ" to
            "Để thực hiện quyền của bạn hoặc hỏi về chính sách, liên hệ nhà phát triển qua TikTok @minhtuyen523 hoặc cộng đồng Discord chính thức trong mục Giới thiệu ứng dụng.",
    ),
)
