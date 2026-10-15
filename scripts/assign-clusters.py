#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gán cụm chủ đề (topic cluster) cho 2.692 từ vựng Master JP.
Spec: docs/05-feature-pack-2.md — F-A. Cụm chủ đề.

Cách chạy:
    python3 assign-clusters.py            # sinh data/seed/clusters.json + in thống kê
    python3 assign-clusters.py --stats-only

Luồng gán (rule đầu tiên khớp sẽ thắng):
  1. OVERRIDES: từ gây tranh cãi gán tay theo (kana, lessonNo) hoặc kana.
  2. so-dem: pos số từ / lượng từ (+ từ khóa số đếm).
  3. mau-sac: từ khóa màu sắc.
  4. Từ chức năng ngữ pháp (trợ từ, thán từ, liên từ, từ nghi vấn,
     tính từ chỉ định, hậu tố, tiền tố, đại từ) -> giao-tiep / con-nguoi.
  5. Động từ: định tuyến theo nghĩa (ăn uống, giao thông, du lịch, mua sắm,
     học tập, công việc, giao tiếp, sức khỏe, cảm xúc, thể thao, giải trí,
     khoa học...) — không khớp -> hanh-dong.
  6. Danh từ / tính từ: từ khóa theo cụm (thứ tự ưu tiên cụ thể -> chung).
  7. Fallback theo pos: đại từ -> con-nguoi, danh từ riêng -> dia-diem,
     tính từ -> tinh-chat, động từ/cụm từ -> hanh-dong, PHRASE -> giao-tiep,
     còn lại -> khac.

Key assignment: "<kana>|<lessonNo>" (đã kiểm chứng unique cho 2.692 từ;
không có kana nào lặp 2 lần trong cùng 1 bài).
"""
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
SEED = BASE / "data" / "seed"
OUT = SEED / "clusters.json"

CLUSTERS = [
    ("gia-dinh", "Gia đình"),
    ("con-nguoi", "Con người"),
    ("nghe-nghiep", "Nghề nghiệp"),
    ("hoc-tap", "Học tập"),
    ("cong-viec", "Công việc"),
    ("do-dung", "Đồ dùng"),
    ("an-uong", "Ăn uống"),
    ("mua-sam", "Mua sắm"),
    ("tien-bac", "Tiền bạc"),
    ("dia-diem", "Địa điểm"),
    ("du-lich", "Du lịch"),
    ("giao-thong", "Giao thông"),
    ("thoi-gian", "Thời gian"),
    ("thien-nhien", "Thiên nhiên & thời tiết"),
    ("dong-vat", "Động vật"),
    ("co-the", "Cơ thể"),
    ("suc-khoe", "Sức khỏe"),
    ("quan-ao", "Quần áo"),
    ("mau-sac", "Màu sắc"),
    ("so-dem", "Số đếm"),
    ("vi-tri", "Vị trí & hướng"),
    ("giao-tiep", "Giao tiếp"),
    ("cam-xuc", "Cảm xúc & tính cách"),
    ("tinh-chat", "Tính chất"),
    ("hanh-dong", "Hành động"),
    ("the-thao", "Thể thao"),
    ("giai-tri", "Giải trí"),
    ("khoa-hoc", "Khoa học"),
    ("khac", "Khác"),
]

# ---------------------------------------------------------------- overrides
# (kana, lessonNo|None) -> cluster. lessonNo=None nghĩa là áp cho mọi bài.
OVERRIDES = {
    ("－えん", None): "tien-bac",        # yên (đơn vị tiền tệ) — dù pos là lượng từ
    ("－さい", None): "so-dem",         # tuổi (đếm tuổi)
    ("かぜ", 17): "suc-khoe",           # cảm, cúm (L32 かぜ = gió -> thien-nhien)
    ("せんせい", None): "hoc-tap",      # giáo viên (cách gọi)
    ("きょうし", None): "hoc-tap",      # giáo viên (nghề)
    ("がくせい", None): "hoc-tap",      # sinh viên, học sinh
    ("けんきゅうしゃ", None): "khoa-hoc",  # nhà nghiên cứu
    ("いしゃ", None): "suc-khoe",       # bác sĩ
    ("かんごふ", None): "suc-khoe",     # y tá
    ("やくざいし", None): "suc-khoe",   # dược sĩ
    ("ぎんこう", None): "dia-diem",     # ngân hàng (địa điểm)
    ("としょかん", None): "hoc-tap",    # thư viện (nơi học)
    ("ほんや", None): "mua-sam",        # hiệu sách (cửa hàng)
    ("レストラン", None): "dia-diem",   # nhà hàng (địa điểm)
    ("きっさてん", None): "dia-diem",   # quán cà phê
    ("つきます", 29): "do-dung",        # [đèn] bật sáng
    ("けします", None): "do-dung",      # tắt [điện/đèn]
    ("つけます", None): "do-dung",      # bật [điện/đèn] (nếu nghĩa là bật điện)
    ("はなみ", None): "giai-tri",       # ngắm hoa anh đào (hoạt động giải trí)
    ("さかな", None): "dong-vat",       # cá (động vật; món cá cụ thể có từ riêng)
    ("うまく いきます", None): "hanh-dong",  # thuận lợi, suôn sẻ
    ("お", None): "giao-tiep",          # tiền tố lịch sự お
    ("ご", None): "giao-tiep",          # tiền tố lịch sự ご
    ("～き", None): "do-dung",          # hậu tố chỉ máy móc
    ("こと", None): "khac",             # việc/chuyện (danh từ trừu tượng)
    ("もの", None): "do-dung",          # vật, đồ vật
    ("ところ", None): "vi-tri",         # nơi, chỗ (vị trí trừu tượng)
    ("わけ", None): "khac",             # lý do (trừu tượng)
    ("はず", None): "khac",             # hẳn là (trừu tượng)
    ("ため", None): "khac",             # vì, để (trừu tượng)
    ("みたい", None): "khac",           # giống như (trừu tượng)
    ("らしい", None): "khac",           # có vẻ (trừu tượng)
    ("ようだ", None): "khac",           # dường như
    ("ほしであきひこ", None): "con-nguoi",  # tên phi hành gia
    ("おうしざ", 32): "khac",            # cung Kim Ngưu (hoàng đạo: không cụm nào hợp)
    ("うちゅうせん", 26): "khoa-hoc",    # tàu vũ trụ
    ("まえ", 32): "thoi-gian",          # trước đây (L10 まえ = phía trước -> vi-tri)
    ("むり［な］", 19): "tinh-chat",     # quá sức, không thể (tính chất)
    ("～ご", 2): "giao-tiep",           # hậu tố chỉ ngôn ngữ
    ("ねむりねこ", 37): "giai-tri",      # "Mèo ngủ": tác phẩm điêu khắc, không phải con mèo
    ("やじるし", 34): "giao-tiep",       # mũi tên (ký hiệu)
    ("めがね", 22): "do-dung",          # kính đeo mắt (đồ vật)
    ("おちゃ", 19): "giai-tri",         # trà đạo (nghệ thuật; L6 おちゃ = trà uống -> an-uong)
    ("きせつ", 12): "thoi-gian",        # mùa (trong năm)
    ("［お］はなみ", 6): "giai-tri",      # ngắm hoa anh đào (L7 はなみ đã override)
    ("ワールドカップ", 37): "the-thao",  # World Cup
    ("ひゃくとおばん", 45): "giao-tiep", # số điện thoại khẩn cấp (số, không phải nghề)
    ("～ご", 27): "thoi-gian",           # hậu tố "sau ~" (khoảng thời gian)
    ("フィルム", 10): "do-dung",        # phim chụp ảnh (cuộn phim, đồ vật)
}

VERB_POS = {"động từ nhóm 1", "động từ nhóm 2", "động từ nhóm 3"}
ADJ_POS = {"tính từ đuôi i", "tính từ đuôi na"}

# ---------------------------------------------------------------- exact map
# meaningVi (lower, strip) khớp TOÀN BỘ -> cluster. Dùng cho từ đơn âm tiết
# mà substring matching sẽ gây nhiễu (kem/kém, dao, tay...).
EXACT = {
    "sách": "hoc-tap",
    "chữ hán": "hoc-tap",
    "tiền": "tien-bac",
    "vé": "giao-thong",
    "phim": "giai-tri",
    "trứng": "an-uong",
    "thịt": "an-uong",
    "rau": "an-uong",
    "sữa": "an-uong",
    "bia": "an-uong",
    "rượu": "an-uong",
    "nước": "an-uong",
    "táo": "an-uong",
    "quýt": "an-uong",
    "kem": "an-uong",
    "thư": "giao-tiep",
    "tem": "giao-tiep",
    "tay": "co-the",
    "người": "con-nguoi",
    "đũa": "do-dung",
    "dao": "do-dung",
    "nĩa": "do-dung",
    "kéo": "do-dung",
    "giấy": "do-dung",
    "hộp": "do-dung",
    "pin": "do-dung",
    "bàn": "do-dung",
    "giường": "do-dung",
    "cửa": "do-dung",
    "ảnh": "do-dung",
    "giày": "quan-ao",
    "hoa": "thien-nhien",
    "cây": "thien-nhien",
    "mưa": "thien-nhien",
    "tuyết": "thien-nhien",
    "biển": "thien-nhien",
    "núi": "thien-nhien",
    "chó": "dong-vat",
    "mèo": "dong-vat",
    "voi": "dong-vat",
    "gần": "vi-tri",
    "giữa": "vi-tri",
    "tỉnh": "dia-diem",
    "cái đĩa": "do-dung",
    "cái cốc": "do-dung",
    "cái túi": "do-dung",
    "cái ví": "do-dung",
    "tai": "co-the",
    "chân": "co-the",
    "răng": "co-the",
    "vị": "an-uong",
    "góc": "vi-tri",
    "(lâu) đài": "dia-diem",
    "hai ba ngày": "thoi-gian",
    "lá (cây)": "thien-nhien",
    "ao": "thien-nhien",
    "lượng": "so-dem",
    "kiến": "dong-vat",
    "nước nóng": "an-uong",
    "văn học": "hoc-tap",
    "ga": "do-dung",
    "đường": "an-uong",
    "ho": "suc-khoe",
}

# ------------------------------------------------- keyword rules (có thứ tự)
# Mỗi rule: (cluster, [keywords]) — khớp nếu keyword xuất hiện trong meaningVi.
# Thứ tự: cụ thể trước, chung sau.
KW_RULES = [
    # --- số đếm (từ khóa bổ sung cho danh từ chỉ số) ---
    ("so-dem", ["trăm triệu", "một nghìn", "mười nghìn", "chữ số", "con số",
                "thứ nhất", "thứ hai", "đếm số", "số lượng", "số thứ tự",
                "lần thứ", "cặp", "đôi", "tá (12", "gấp đôi", "gấp ba",
                "phần trăm", "tỷ lệ", "một nửa", "một phần",
                "tổng cộng",
                  "thứ tự",]),
    # --- màu sắc ---
    ("mau-sac", ["màu ", "màu sắc", "màu đỏ", "màu xanh", "màu vàng",
                "màu trắng", "màu đen", "màu hồng", "màu tím", "màu nâu",
                "màu xám", "màu cam", "màu be", "màu da", "màu bạc",
                "màu vàng kim", "đỏ thẫm", "xanh dương", "xanh lá",
                "xanh da trời", "xanh navy", "hồng phấn", "tím than"]),
    # --- tiền bạc ---
    ("tien-bac", ["tiền lương", "tiền công", "tiền thưởng", "tiền thuế",
                 "tiền thuê", "tiền điện", "tiền nước", "tiền gas",
                 "tiền học", "tiền vé", "tiền tip", "tiền boa",
                 "tiền mặt", "tiền lẻ", "tiền thối", "tiền thừa",
                 "tài khoản", "tiết kiệm", "gửi tiền", "rút tiền",
                 "chuyển khoản", "vay tiền", "nợ", "lãi suất",
                 "đầu tư", "cổ phiếu", "trái phiếu", "chứng khoán",
                 "thanh toán", "trả góp", "thẻ tín dụng", "miễn phí",
                 "chi phí", "ngân sách", "giàu có", "nghèo",
                 "đơn vị tiền tệ", "ngoại tệ", "tỷ giá", "quỹ",
                 "kinh phí", "lương hưu", "trợ cấp", "học bổng",
                 "tiền bảo hiểm", "phí ",
                 "atm", "máy atm",
                 "lương", "số tiền", "tiền phạt", "thu nhập", "tờ tiền", "cước", "mã pin", "mã số ",]),
    # --- mua sắm: cửa hàng trước (đè địa điểm chung) ---
    ("mua-sam", ["cửa hàng", "siêu thị", "chợ ", "trung tâm thương mại",
                "bách hóa", "hiệu sách", "hiệu thuốc", "cửa hiệu",
                "quầy hàng", "gian hàng", "kệ hàng", "giỏ hàng",
                "xe đẩy", "mua sắm", "đi chợ", "mặc cả", "trả giá",
                "giảm giá", "khuyến mãi", "khuyến mại", "ưu đãi",
                "đại hạ giá", "xả hàng", "quà tặng", "gói quà",
                "hóa đơn", "biên lai", "đổi trả", "bảo hành",
                "đặt hàng", "giao hàng", "thu ngân", "quầy thanh toán",
                "giờ mở cửa", "ngày nghỉ (của cửa hàng)", "thử đồ",
                "mặc thử", "vừa (quần áo)", "quà lưu niệm",
                "hàng hóa", "hàng mẫu", "hàng mới về", "hàng tồn",
                "mặt hàng", "sản phẩm", "nhãn hiệu", "thương hiệu",
                "hàng hiệu", "hàng nhái", "giá cả", "giá bán",
                "giá gốc", "giá niêm yết", "bảng giá",
                 "catalog", "giặt khô", "hàng thật", "đồ thật", ]),
    # --- giao thông (danh từ; động từ đã định tuyến ở VERB_RULES) ---
    ("giao-thong", ["xe hơi", "xe ô tô", "ô tô", "xe máy",
                   "xe đạp", "xe buýt", "xe buýt (đường dài)",
                   "tàu điện", "tàu điện ngầm", "tàu cao tốc",
                   "tàu thường", "tàu nhanh", "tàu ",
                   "máy bay", "tàu thủy", "thuyền ", "phà ",
                   "taxi ", "xe tải", "xe cứu thương",
                   "xe cảnh sát", "xe cứu hỏa",
                   "trực thăng", "khinh khí cầu",
                   "nhà ga", "sân ga", "sân bay",
                   "bến xe", "bến cảng", "bến tàu",
                   "trạm xăng", "bãi đỗ xe", "bãi đậu xe",
                   "đèn giao thông", "biển báo", "biển chỉ dẫn",
                   "ngã tư", "vòng xuyến", "làn đường",
                   "vỉa hè", "đường cao tốc", "đường một chiều",
                   "hành khách", "tài xế", "lái xe", "phi công",
                   "tiếp viên", "phụ xe",
                   "vé ", "vé tàu", "vé xe", "vé máy bay",
                   "vé tháng", "thẻ (đi tàu)",
                   "chuyến ", "chuyến bay", "chuyến tàu",
                   "hành lý", "hành lý xách tay",
                   "cửa lên máy bay", "cổng khởi hành",
                   "tai nạn giao thông", "va chạm",
                   "kẹt xe", "tắc đường", "tắc nghẽn",
                   "tốc độ", "giới hạn tốc độ", "vượt tốc độ",
                   "bằng lái", "giấy phép lái xe",
                   "phạt ", "bị phạt", "cảnh sát giao thông",
                   "đi nhờ xe", "quá giang",
                   "chỗ ngồi", "ghế (trên xe)", "toa tàu",
                   "khoang ", "boong tàu",
                   "lịch trình (tàu xe)", "giờ khởi hành",
                   "giờ đến", "đúng giờ (tàu xe)", "trễ chuyến",
                 "cảng", "phương tiện", "xăng", "tai nạn", "đỗ xe",
                "giao thông",
                "dây an toàn", ]),
    # --- địa điểm ---
    ("dia-diem", ["bệnh viện", "phòng khám", "trạm xá",
                 "bưu điện", "ngân hàng", "cơ quan",
                 "đồn cảnh sát", "trạm cứu hỏa", "tòa thị chính",
                 "ủy ban", "đại sứ quán", "lãnh sự quán",
                 "trường học", "nhà trẻ", "nhà mẫu giáo",
                 "bảo tàng", "triển lãm", "phòng trưng bày",
                 "rạp chiếu phim", "rạp hát", "sở thú",
                 "công viên", "vườn bách thảo", "khu vui chơi",
                 "nhà hàng", "quán ăn", "quán cà phê", "quán bar",
                 "khách sạn", "nhà nghỉ", "nhà trọ", "ký túc xá",
                 "chung cư", "tòa nhà", "cao ốc", "biệt thự",
                 "nhà vệ sinh", "nhà tắm công cộng",
                 "chùa", "đền", "nhà thờ", "thánh đường",
                 "lâu đài", "cung điện",
                 "sân vận động", "nhà thi đấu", "bể bơi",
                 "sân bay", "nhà ga", "bến xe", "bến cảng", "bến tàu",
                 "trạm xăng", "bãi đỗ xe", "bãi đậu xe",
                 "đường phố", "con đường", "đại lộ", "ngõ", "hẻm",
                 "cây cầu", "đường hầm", "ngã tư", "vòng xuyến",
                 "quảng trường", "bờ sông", "bến ",
                 "thành phố", "thị trấn", "thị xã", "làng",
                 "thủ đô", "tỉnh ", "huyện", "quận", "phường", "xã ",
                 "đất nước", "quốc gia", "vương quốc",
                 "châu á", "châu âu", "châu mỹ", "châu phi", "châu úc",
                 "miền bắc", "miền nam", "miền trung",
                 "vùng ", "khu vực", "địa phương",
                 "quê hương", "quê quán",
                 "địa chỉ", "bản đồ",
                 "phòng khách", "phòng ngủ", "phòng bếp", "phòng tắm",
                 "phòng ăn", "phòng làm việc", "phòng họp",
                 "ban công", "sân thượng", "tầng hầm",
                 "hành lang", "cầu thang", "thang máy",
                 "cổng ", "cửa ra vào", "lối vào", "lối ra",
                 "trong nhà", "ngoài trời",
                 "nơi ", "chỗ ", "địa điểm", "vị trí (địa lý)",
                 "phòng ", "căn phòng", "nhà ăn", "căng-tin",
                 "sảnh", "tiền sảnh", "nhà (của",
                 "nhà ăn", "căng-tin", "sảnh ", "tiền sảnh",
                 "căn phòng", "phòng ",
                 "nhà (của",
                 # tên nước (danh từ thường trong Minna)
                 "nhật bản", "hoa kỳ", "hàn quốc", "triều tiên",
                 "trung quốc", "đài loan", "hồng kông",
                 "ấn độ", "indonesia", "thái lan", "malaysia",
                 "singapore", "philippines", "việt nam", "lào",
                 "campuchia", "myanmar", "mông cổ",
                 "nước úc", "châu úc", "australia", "new zealand",
                 "nước nga",
                 "đức", "pháp", "anh quốc", "italia", "ý (nước",
                 "tây ban nha", "bồ đào nha", "hà lan", "bỉ",
                 "thụy sĩ", "thụy điển", "na uy", "đan mạch",
                 "phần lan", "ukraina", "ba lan",
                 "séc", "hungary", "hy lạp", "thổ nhĩ kỳ",
                 "brazil", "mexico", "canada", "ai cập",
                 "nam phi", "argentina", "chile", "peru",
                 "thế giới", "toàn cầu", "khắp thế giới",
                 "nhà bếp", "ngôi nhà", "hội trường", "ngoại ô", "tượng ", "cửa thoát hiểm", "thoát hiểm",
                "tiệm ",
                "kim tự tháp",
                "khu phố", "trang trại",]),
    # --- sức khỏe ---
    ("suc-khoe", ["sức khỏe", "bệnh ", "căn bệnh", "mắc bệnh", "ốm",
                 "đau ", "cơn đau", "nhức", "mỏi ",
                 "cảm cúm", "cảm lạnh", "bị cảm", "sốt", 
                 "cơn ho", "hắt hơi", "sổ mũi", "nghẹt mũi",
                 "chóng mặt", "buồn nôn", "nôn ", "tiêu chảy",
                 "táo bón", "dị ứng", "hen suyễn", "tiểu đường",
                 "huyết áp", "tim mạch", "đột quỵ", "ung thư",
                 "viêm ", "nhiễm trùng", "vết thương", "chảy máu",
                 "gãy xương", "bong gân", "trật khớp", "bỏng",
                 "vết bỏng", "vết cắt", "vết bầm",
                 "say xe", "say sóng", "say nắng", "say rượu",
                 "mất ngủ", "khó ngủ", "buồn ngủ", "ngáp",
                 "mệt mỏi", "kiệt sức", "căng thẳng (sức khỏe)",
                 "khám bệnh", "khám sức khỏe", "tái khám",
                 "chẩn đoán", "điều trị", "chữa bệnh", "chữa trị",
                 "đơn thuốc", "toa thuốc", "uống thuốc",
                 "tiêm ", "mũi tiêm", "truyền dịch", "truyền nước",
                 "phẫu thuật", "mổ ", "ca mổ", "băng bó", "bó bột",
                 "cấp cứu", "xe cứu thương", "phòng cấp cứu",
                 "hồi phục", "bình phục", "khỏi bệnh",
                 "bảo hiểm y tế", "sổ khám bệnh",
                 "ăn kiêng", "giảm cân", "tăng cân", "béo phì",
                 "dinh dưỡng", "vitamin", "calo",
                 "tập thể dục", "rèn luyện sức khỏe",
                 "khỏe mạnh", "cường tráng", "dẻo dai",
                 "thuốc", "cúm ", "stress", "quá sức", "nguy kịch", "thẻ bảo hiểm", "khuyết tật",]),
    # --- cơ thể ---
    ("co-the", ["cơ thể", "đầu (bộ phận)", "cái đầu", "tóc",
               "mái tóc", "khuôn mặt", "gương mặt", "nét mặt",
               "con mắt", "đôi mắt", "nhãn cầu", "lông mày",
               "lông mi", "mí mắt", "tròng mắt", "đồng tử",
               "cái mũi", "lỗ mũi", "cánh mũi",
               "cái miệng", "môi ", "cái lưỡi", 
               "cái răng", "lợi (nướu)", "cổ họng", "họng",
               "cái cổ", "gáy ", "vai ", "bờ vai",
               "cánh tay", "bắp tay", "khuỷu tay", "cổ tay",
               "bàn tay", "lòng bàn tay", "mu bàn tay",
               "ngón tay", "ngón cái", "ngón trỏ", "móng tay",
               "ngực", "bộ ngực", "bụng", "cái bụng", "rốn",
               "lưng", "tấm lưng",  "vòng eo", 
               "cái chân", "đôi chân", "bắp chân", "bắp đùi",
               "đầu gối", "mắt cá", "cổ chân", "gót chân",
               "bàn chân", "lòng bàn chân", "ngón chân", "móng chân",
               "mông ", "nách ",
               "làn da",  "lỗ chân lông", "nốt ruồi",
               "tàn nhang", "mụn ", "sẹo ", "vết sẹo",
               "xương", "khớp xương", "cơ bắp", "bắp thịt",
               "máu ", "mạch máu", "huyết quản",
               "trái tim", "quả tim", "lá phổi", "lá gan",
               "quả thận", "dạ dày", "ruột ", "mật ",
               "não ", "bộ não", "dây thần kinh", "tủy ",
               "mồ hôi", "nước mắt", "nước bọt", "nước mũi",
               "hơi thở", "nhịp tim", "mạch đập",
               "giọng nói", "thanh quản",
               "râu ", "bộ râu", "ria mép",
               "má ", "cằm ", "trán ", "thái dương",
               "chiều cao", "cân nặng", "vóc dáng", "thể hình",
               "tay trái", "tay phải", "chân trái", "chân phải",
               "toàn thân", "nội tạng",
                 "mắt", "mũi", "miệng", "tóc", "vai", "tim", "đầu, đầu óc", "hoóc-môn", "hormone",]),
    # --- quần áo ---
    ("quan-ao", ["quần áo", "trang phục", "y phục",
                "cái áo", "áo sơ mi", "áo thun", "áo phông",
                "áo len", "áo khoác", "áo choàng", "áo vest",
                "áo dài", "áo bà ba",
                "cái quần", "quần dài", "quần short", "quần jean",
                "quần âu", "quần lót", "quần đùi",
                "váy ", "chân váy", "đầm ",
                "đồ bơi", "áo tắm", "đồ lót", "áo ngực",
                "bộ vest", "bộ com lê", "đồng phục",
                "đôi giày", "giày ", "dép ", "sandal", "guốc",
                "bốt ", "giày cao gót", "giày thể thao",
                "đôi tất", "tất ", "vớ ",
                "cái mũ", "mũ ", "nón ", "mũ bảo hiểm",
                "khăn quàng", "khăn choàng", "khăn tay",
                "găng tay", "bao tay",
                "thắt lưng", "dây lưng", "cà vạt", "nơ ",
                "đồng hồ đeo tay",
                "trang sức", "nhẫn ", "vòng cổ", "dây chuyền",
                "vòng tay", "lắc tay", "bông tai", "hoa tai",
                "kính râm", "kính mát",
                "mặc ", "cởi ", "thay đồ", "thay quần áo",
                "kích cỡ", "cỡ áo", "size ",
                "thời trang", "mốt ", "hợp mốt", "lỗi mốt",
                "vải ", "tấm vải", "lụa ", "vải cotton",
                "vải len", "vải lanh", "da (chất liệu)",
                "cúc áo", "khuy áo", "khóa kéo", "dây kéo",
                "túi áo", "túi quần", "cổ áo", "tay áo",
                "gấu áo", "gấu quần", "lai áo",
                 "mũ", "trang điểm", "mỹ phẩm",]),
    # --- ăn uống ---
    ("an-uong", ["món ăn", "thức ăn", "đồ ăn", "thực phẩm",
                "lương thực", "khẩu phần",
                "bữa ăn", "bữa sáng", "bữa trưa", "bữa tối",
                "bữa xế", "bữa phụ", "ăn sáng", "ăn trưa", "ăn tối",
                "cơm ", "cơm trắng", "cơm nắm", "cơm hộp", "cơm chiên",
                "cháo ", "bánh mì", "bánh bao", "bánh chưng",
                "bánh tét", "bánh cuốn", "bánh xèo", "bánh ướt",
                "mì ", "mì ý", "mì ramen", "mì udon", "mì soba",
                "phở ", "bún ", "miến ", "hủ tiếu",
                "thịt ", "thịt heo", "thịt bò", "thịt gà",
                "thịt vịt", "thịt cừu", "thịt hun khói",
                "xúc xích", "giăm bông", "thịt nguội",
                "cá hồi", "cá ngừ", "cá thu", "sashimi", "món sushi","sushi ",
                "tôm ", "cua ", "mực ", "bạch tuộc", "sò ", "ốc ",
                "trứng ", "trứng gà", "trứng vịt", "trứng ốp",
                "trứng luộc", "trứng chiên",
                "rau ", "rau xanh", "rau củ", "rau sống",
                "củ ", "củ cải", "khoai ", "khoai tây",
                "khoai lang", "cà rốt", "cà chua", "cà tím",
                "dưa chuột", "dưa leo", "bắp cải", "xà lách",
                "hành tây","hành lá","củ hành", "tỏi ", "ớt ", "gừng ", "sả ",
                "nấm ", "đậu ", "đậu phụ", "đậu nành",
                "trái cây", "hoa quả", "quả ",
                "táo ", "cam ", "quýt ", "chuối ", "xoài ",
                "dứa ", "thơm ", "đu đủ", "dưa hấu", "dưa gang",
                "nho ", "dâu ", "đào ", "lê ", "mận ",
                "chanh ", "bưởi ", "vải ", "nhãn ", "chôm chôm",
                "sầu riêng", "măng cụt", "thanh long", 
                "sữa ", "sữa tươi", "sữa chua", "sữa đặc",
                "phô mai", "bơ ", "kem ", "sữa đậu nành",
                 "muối ", "nước mắm", "nước tương",
                "tương ớt", "tương cà", "dầu ăn", "dầu hào",
                "giấm ", "tiêu ", "bột ngọt", "bột nêm",
                "gia vị", "nêm nếm",
                "cà phê", "trà ", "trà đá", "trà sữa",
                "nước ngọt", "nước ép", "sinh tố", "nước mía",
                "bia ", "rượu ", "rượu sake", "rượu vang",
                "cocktail", "nước lọc", "nước khoáng",
                "đồ uống", "nước uống", "thức uống",
                "món tráng miệng", "món khai vị", "món chính",
                "đồ ăn nhẹ", "ăn vặt", "đồ ăn nhanh",
                "thực đơn", "menu ",
                "nấu ăn", "nấu nướng", "vào bếp",
                "nướng ", "chiên ", "xào ", "luộc ",
                "hấp ", "kho ", "rim ", "om ",
                "gọt ", "thái ", "băm ", "xay ",
                "đói ", "no (ăn)", "ăn no", "khát ",
                "ngon ", "dở ", "ngọt ", "cay ",
                "đắng ", "chua ", "mặn ", "nhạt (vị)",
                "béo ", "ngậy ", "thơm ngon", "hấp dẫn (món ăn)",
                "cay nồng", "đậm đà", "vừa miệng",
                "dị ứng (thức ăn)", "ngộ độc thực phẩm",
                "hết hạn (thực phẩm)", "tươi sống", "tươi ngon",
                "bánh sandwich", "sô-cô-la", "chocolate",
                 "suất ăn", "bánh ngọt", "bánh kem", "gạo", "lúa mì", "lúa mạch", "đồ hộp", "sợi mì", "thèm ăn",
                "nguyên liệu",
                "uống",
                "kẹo cao su",]),
    # --- động vật ---
    ("dong-vat", ["động vật", "con vật", "thú vật", "thú cưng",
                 "gia súc", "gia cầm", "thú hoang",
                 "con chó", "chó ", "chó con", "chó sói",
                 "con mèo", "mèo ", "mèo con",
                 "con chim", "chim ", "chim sẻ", "chim bồ câu",
                 "chim cánh cụt", "chim ưng", "đại bàng", "cú mèo",
                 "quạ ", "chim sáo", "vẹt ", "chim ruồi",
                 "con gà", "gà ", "gà trống", "gà mái", "gà con",
                 "con vịt", "vịt ", "vịt trời", "ngỗng ", "thiên nga",
                 "gà tây", "đà điểu",
                 "con lợn", "lợn ", "heo ", "lợn rừng",
                 "con bò", "bò ", "bò sữa", "trâu ",
                 "con ngựa", "ngựa ", "ngựa vằn", "lừa ",
                 "con dê", "dê ", "cừu ", "lạc đà ",
                 "con thỏ", "thỏ ",
                 "con chuột", "chuột ", "chuột lang", "hamster",
                 "con khỉ", "khỉ ", "đười ươi", "vượn ", "tinh tinh",
                 "con gấu", "gấu ", "gấu trúc", "gấu koala", "koala",
                 "con hổ", "hổ ", "sư tử ",  "báo đốm",
                 "con voi", "voi ", "tê giác ", "hà mã ",
                 "con hươu", "hươu ", "hươu cao cổ", "nai ",
                 "con cáo", "cáo ", "sói ", "linh cẩu",
                 "con rắn", "rắn ", "con trăn", "ếch ", "cóc ",
                 "thằn lằn", "tắc kè", "kỳ nhông",
                 "con rùa", "rùa ", "rùa biển", "ba ba",
                 "cá sấu ", "cá heo", "cá voi", "cá mập",
                 "cá ngừ", "cá hồi", "cá chép", "cá vàng",
                 "cá kiếm", "cá đuối", "lươn ", "cá chình",
                 "tôm hùm", "cua hoàng đế", "sò điệp", "ngao ",
                 "sao biển", "sứa ", "hải sâm", "ốc biển",
                 "con ong",  "ong mật", "ong bắp cày",
                 "con bướm", "bướm ", "bướm đêm", "ngài ",
                 "con muỗi", "muỗi ", "ruồi ", 
                 "gián ", "nhện ", "bọ ", "bọ cánh cứng",
                 "bọ hung", "ve sầu", "đom đóm", "chuồn chuồn",
                 "châu chấu", "dế ", "tằm ", "sâu ",
                 "giun ", "đỉa ", "sán ",
                 "con dơi", "dơi ", "nhím ", "sóc ",
                 "chồn ", "rái cá", "hải cẩu", "hải ly",
                 "thỏ rừng", "lửng ", "cầy ",
                 "kangaroo", "chuột túi",
                 "sủa ", "kêu (động vật)", "gáy (gà)", "gầm ",
                 "vẫy đuôi", "tổ chim", "chuồng ", "máng ăn",
                 "dây xích", "vòng cổ (thú cưng)"]),
    # --- thiên nhiên & thời tiết ---
    ("thien-nhien", ["thiên nhiên", "tự nhiên", "hoang dã",
                    "thời tiết", "khí hậu", "dự báo thời tiết",
                    "trời mưa", "trời nắng", "trời âm u",
                    "cơn mưa", "mưa ", "mưa rào", "mưa đá",
                    "mưa phùn", "mưa bão",
                    "nắng ", "nắng gắt", "nắng nóng",
                    "tuyết ", "tuyết rơi", "bão tuyết",
                    "gió ", "cơn gió", "gió bão", "gió mùa",
                    "bão ", "cơn bão", "áp thấp",
                    "sấm ", "sét ", "chớp ", "tia chớp",
                    "mây ", "đám mây", "mây đen",
                    "sương ", "sương mù", "sương giá",
                    "cầu vồng ", "mặt trời", "ánh nắng",
                    "mặt trăng", "ánh trăng", "ngôi sao",
                    "bầu trời", "không trung", "tầng mây",
                    "biển ", "đại dương", "bờ biển", "bãi biển",
                    "sóng biển", "thủy triều",
                    "con sông", "dòng sông", "bờ sông",
                    "cái hồ", "hồ nước",  "đầm ",
                    "ngọn núi", "dãy núi", "đỉnh núi", "chân núi",
                    "sườn núi", "đồi ", "thung lũng",
                    "hòn đảo", "quần đảo", "bán đảo",
                    "khu rừng", "rừng ", "rừng rậm",
                    "cái cây", "gốc cây", "thân cây", "cành cây",
                    "tán cây", "bóng cây",
                    "bông hoa", "cánh hoa", "nụ hoa", "hoa nở",
                    "hoa anh đào", "hoa sen", "hoa hồng",
                    "ngọn cỏ", "bãi cỏ", "thảm cỏ",
                    "chiếc lá", "lá cây", "lá vàng", "lá rụng",
                    "hòn đá", "tảng đá", "sỏi ", "cát ",
                    "bãi cát", "sa mạc ", "đất ", "mảnh đất",
                    "bùn ", "đất sét",
                    "ngọn lửa", "đám cháy", "khói ", "tro ",
                    "băng ", "tảng băng", "nước đá",
                    "độ ẩm", "nhiệt độ", "khí áp",
                    "bình minh", "rạng đông", "hoàng hôn",
                    "chạng vạng", "nửa đêm",
                    "động đất", "dư chấn", "sóng thần",
                    "núi lửa", "dung nham", "lũ lụt", "lũ quét",
                    "hạn hán ", "sạt lở",
                    "môi trường", "ô nhiễm", "bảo vệ môi trường",
                    "không khí", "bầu không khí", "oxy ",
                    "trái đất", "hành tinh", "vũ trụ",
                    "dải ngân hà", "thiên hà",
                    "nhật thực", "nguyệt thực",
                    "sao hỏa", "sao kim", "sao thổ", "sao mộc",
                    "sao thủy", "sao băng", "sao chổi",
                    "cực quang", "thủy triều",
                    "sân vườn", "vườn ", "nhiều mây",
                 "đảo", "lửa", "hỏa hoạn", "dầu mỏ",
                "phong cảnh",]),
    # --- gia đình ---
    ("gia-dinh", ["gia đình", "họ hàng", "thân thích", "dòng họ",
                 "tổ tiên", "gia phả",
                 "bố ", "ba (bố)", "cha ", "bố vợ", "bố chồng",
                 "mẹ ", "má (mẹ)", "mẹ vợ", "mẹ chồng",
                 "anh trai", "em trai", "anh rể", "em rể",
                 "chị gái", "em gái", "chị dâu", "em dâu",
                 "ông nội", "ông ngoại", "bà nội", "bà ngoại",
                 "ông (cách gọi)", "bà (cách gọi)",
                 "cháu trai", "cháu gái", "đứa cháu",
                 "người vợ", "người chồng", "vợ chồng",
                 "con trai", "con gái", "đứa con", "con cái",
                 "con dâu", "con rể",
                 "chú ", "bác ", "cô ", "dì ",
                 "cậu ", "mợ ", "thím ",
                 "trẻ em", "trẻ con", "em bé", "bé sơ sinh",
                 "trẻ sơ sinh", "song sinh", "sinh đôi",
                 "kết hôn", "đám cưới", "lễ cưới", "hôn lễ",
                 "ly hôn", "ly dị", "tái hôn",
                 "mang thai", "sinh nở", "sinh con",
                 "nuôi dạy", "nuôi dưỡng", "chăm sóc (gia đình)",
                 "mồ côi", "con nuôi", "bố mẹ nuôi",
                 "gia trưởng", "nội trợ", "việc nhà",
                 "sum họp", "đoàn tụ", "giỗ ", "thờ cúng",
                 "vợ", "chồng", "bé ", "anh chị em",
                 "ông cụ", "bà cụ", "ông (của", "bà (của", "cháu (của", "bọn trẻ", "đám tang", "xem mặt", "độc thân",]),
    # --- nghề nghiệp ---
    ("nghe-nghiep", ["nghề nghiệp", "nghề ", "công việc (nghề)",
                    "nhân viên", "công nhân", "viên chức",
                    "giám đốc", "tổng giám đốc", "chủ tịch",
                    "phó giám đốc", "trưởng phòng", "phó phòng",
                    "quản lý", "tổ trưởng", "đội trưởng",
                    "kỹ sư", "kiến trúc sư", "thiết kế",
                    "luật sư", "thẩm phán", "kiểm sát",
                    "kế toán", "kiểm toán", "thủ quỹ",
                    "cảnh sát", "lính cứu hỏa", "bảo vệ",
                    "quân nhân", "bộ đội", "sĩ quan",
                    "đầu bếp", "phụ bếp", "phục vụ (nhà hàng)",
                    "bán hàng", "thu ngân", "tiếp tân",
                    "thợ ", "thợ mộc", "thợ điện", "thợ may",
                    "thợ sửa xe", "thợ xây", "thợ hàn",
                    "nông dân", "ngư dân", "chăn nuôi",
                    "nhà văn", "nhà thơ", "nhà báo", "phóng viên",
                    "nghệ sĩ", "diễn viên", "ca sĩ", "nhạc sĩ",
                    "người mẫu", "vũ công", "họa sĩ",
                    "vận động viên", "huấn luyện viên", "trọng tài",
                    "phi công", "tiếp viên", "thuyền trưởng",
                    "lái xe", "tài xế",
                    "hướng dẫn viên", "phiên dịch viên", "thông dịch",
                    "thư ký", "trợ lý", "thực tập sinh",
                    "công chức", "viên chức nhà nước",
                    "tự kinh doanh", "chủ doanh nghiệp", "khởi nghiệp",
                    "thất nghiệp", "tìm việc", "xin việc",
                    "tuyển dụng", "đơn xin việc", "sơ yếu lý lịch",
                    "phỏng vấn xin việc", "thử việc",
                    "đồng nghiệp", "cấp trên", "cấp dưới",
                    "chủ (công ty)", "ông chủ", "bà chủ",
                 "thủ tướng", "tổng thống",]),
    # --- học tập ---
    ("hoc-tap", ["học tập", "việc học", "đi học",
                "giáo viên", "thầy giáo", "cô giáo", "thầy cô",
                "giảng viên", "hiệu trưởng", "hiệu phó",
                "học sinh", "sinh viên", "học viên",
                "bạn học", "bạn cùng lớp", "đàn anh", "đàn em",
                "năm nhất", "năm hai", "năm ba", "năm cuối",
                "lớp học", "phòng học", "giảng đường",
                "bài học", "tiết học", "giờ học",
                "bài tập", "bài tập về nhà", "bài kiểm tra",
                "kỳ thi", "thi cử", "phòng thi", "đề thi",
                "điểm số", "điểm thi", "bảng điểm",
                "thi đỗ", "đỗ đạt", "thi trượt", "thi lại",
                "tốt nghiệp", "lễ tốt nghiệp", "nhập học",
                "lễ khai giảng", "lễ nhập học",
                "sách giáo khoa", "giáo trình",
                "vở ", "quyển vở", "vở ghi", "sổ tay",
                "bút ", "cây bút", "bút chì", "bút mực",
                "bút bi", "tẩy ", "cục tẩy", "thước kẻ",
                "từ điển", "sách tham khảo",
                "thư viện", "phòng đọc",
                "ôn tập", "ôn thi", "luyện thi",
                "ghi chép", "chép bài", "ghi nhớ",
                "học thuộc", "học vẹt",
                "tra từ", "tra cứu",
                "ngoại ngữ", "tiếng nhật", "tiếng anh",
                "tiếng việt", "tiếng trung", "tiếng hàn",
                "hiragana", "katakana", "kanji", "romaji",
                "ngữ pháp", "từ vựng", "phát âm",
                "hội thoại", "đàm thoại",
                "bài văn", "đoạn văn", "câu văn",
                "thuyết trình", "bài thuyết trình",
                "nghiên cứu (học thuật)", "luận văn", "luận án",
                "khóa luận", "đồ án",
                "học bổng", "du học", "du học sinh",
                "khoa ", "chuyên ngành", "ngành học",
                "bằng cấp", "chứng chỉ", "bằng tốt nghiệp",
                "học kỳ", "năm học",
                "nghỉ hè", "nghỉ đông", "nghỉ lễ (học sinh)",
                "câu lạc bộ (trường)", "hoạt động ngoại khóa",
                "trường mẫu giáo", "trường tiểu học",
                "trường trung học", "trường cấp 3",
                "trường đại học", "cao đẳng", "trường nghề",
                "sách", "chữ cái", "chữ (", "chữ hán",
                 "giáo dục", "văn hóa", "bài giảng", "nhật ký", "tiến sĩ", "sơ đồ", "cách đọc", "cách viết", "trường cấp",
                "cao học", "thuyết giảng",
                "kiến thức",]),
    # --- công việc ---
    ("cong-viec", ["công việc", "việc làm", "đi làm",
                  "nơi làm việc", "công sở", "văn phòng",
                  "chi nhánh", "trụ sở", "công ty con",
                  "cuộc họp", "họp ", "phòng họp",
                  "báo cáo", "bản báo cáo", "tài liệu",
                  "hồ sơ", "giấy tờ", "văn bản",
                  "hợp đồng", "ký hợp đồng", "đóng dấu",
                  "dự án", "kế hoạch", "lịch trình",
                  "tiến độ", "hạn chót", "thời hạn",
                  "khách hàng", "đối tác", "nhà cung cấp",
                  "kinh doanh", "buôn bán",
                  "tiếp thị", "marketing", "quảng cáo",
                  "bán hàng", "doanh số", "doanh thu",
                  "lợi nhuận", "thua lỗ", "phá sản",
                  "thị trường", "cạnh tranh", "đối thủ",
                  "tăng ca", "làm thêm giờ", "làm thêm",
                  "nghỉ phép", "nghỉ ốm", "nghỉ thai sản",
                  "nghỉ việc", "thôi việc", "sa thải",
                  "nghỉ hưu", "hưu trí", "về hưu",
                  "chuyển công tác", "thuyên chuyển",
                  "công tác", "đi công tác",
                  "sếp ", "cấp trên", "đồng nghiệp",
                  "nhân sự", "phòng nhân sự",
                  "fax ", "máy fax",
                  "chấm công", "giờ làm việc", "ca làm việc",
                  "danh thiếp", "công ty",
                  "ca ngày", "ca đêm",
                  "đồng phục (công ty)", "thẻ nhân viên",
                  "máy chấm công", "văn phòng phẩm",
                 "kinh tế", "dịch vụ", "nhà máy", "tòa soạn", "con dấu", "đơn đăng ký", "đăng ký", "lý lịch", "nguyện vọng", "khảo sát", "điều tra", "quy định", "đang tuyển", "tuyển ",
                "xác nhận",]),
    # --- giao tiếp ---
    ("giao-tiep", ["giao tiếp", "trò chuyện", "nói chuyện",
                  "cái này", "cái đó", "cái kia",
                  "người này", "người đó", "người kia",
                  "~ này", "~ đó", "~ kia",
                  "như thế này", "như thế đó", "như thế kia",
                  "ở đây", "ở đó", "ở kia",
                  "điện thoại", "gọi điện", "nghe máy",
                  "cuộc gọi", "cuộc gọi nhỡ",
                  "tin nhắn", "nhắn tin", "email", "thư điện tử",
                  "bức thư", "lá thư", "viết thư", "gửi thư",
                  "bưu thiếp", "phong bì", "tem thư",
                  "liên lạc", "liên hệ",
                  "giới thiệu", "tự giới thiệu",
                  "chào hỏi", "lời chào", "chào buổi sáng",
                  "chào buổi tối", "tạm biệt", "hẹn gặp lại",
                  "cảm ơn", "lời cảm ơn", "biết ơn",
                  "xin lỗi", "lời xin lỗi", "thứ lỗi",
                  "chúc mừng", "lời chúc", "chúc sức khỏe",
                  "mời ", "lời mời", "rủ ",
                  "nhờ vả", "nhờ ", "đề nghị",
                  "từ chối", "khước từ", "đồng ý",
                  "phản đối", "tranh luận", "tranh cãi",
                  "bàn bạc", "thảo luận", "hội ý",
                  "hứa ", "lời hứa", "thề ",
                  "khen ", "lời khen", "chê ", "phê bình",
                  "phàn nàn", "khiếu nại",
                  "hỏi thăm", "thăm hỏi",
                  "giải thích", "giảng giải",
                  "thông báo", "tuyên bố", "báo tin",
                  "kể chuyện", "tâm sự",
                  "nói đùa", "đùa ", "trêu ",
                  "nói dối", "nói thật",
                  "thì thầm", "hét ", "la ",
                  "gọi tên", "đáp lại", "trả lời",
                  "câu hỏi", "câu trả lời",
                  "ý kiến", "quan điểm", "lập trường",
                  "alo ", "nghe (điện thoại)",
                  "vâng ", "dạ ", "không (từ chối)",
                  "xin mời", "mời vào", "mời ngồi",
                  "làm ơn", "giúp đỡ", "giúp ",
                  "câu chào khi gặp", "câu chào tạm biệt",
                  "thiệp", "chuyển phát nhanh", "thư bảo đảm",
                 "từ ngữ", "ngôn ngữ", "ý nghĩa", "bài phát biểu", "phát biểu", "điện báo", "tờ rơi", "ký hiệu", "tán thành", "đồng tình", "cách diễn đạt", "diễn đạt",
                "áp phích",
                "tục ngữ", "thành ngữ", "thông tin",
                "đường thư", "cho phép",]),
    # --- con người ---
    ("con-nguoi", ["con người", "loài người", "nhân loại",
                  "người ", "mọi người", "ai đó", "không ai",
                  "đàn ông", "phụ nữ", "nam giới", "nữ giới",
                  "người già", "người cao tuổi", "người trẻ",
                  "thanh niên", "thiếu niên", "trẻ vị thành niên",
                  "người lớn", "trẻ nhỏ",
                  "bạn bè", "người bạn", "bạn thân",
                  "hàng xóm", "láng giềng",
                  "người lạ", "người quen",
                  "khách ", "vị khách", "khách mời",
                  "chủ nhà", "chủ hộ",
                  "tên ", "họ tên", "đặt tên",
                  "tuổi ", "số tuổi",
                  "giới tính", "quốc tịch", "dân tộc",
                  "tôn giáo", "tín ngưỡng",
                  "tôi ", "bạn ", "anh ấy", "cô ấy",
                  "chúng tôi", "các bạn", "họ (ngôi thứ ba)",
                  "bản thân", "chính mình",
                  "sinh ra", "ra đời", "chào đời",
                  "lớn lên", "trưởng thành",
                  "sống ", "cuộc sống", "đời sống",
                  "chết ", "qua đời", "từ trần",
                  "kết bạn", "làm quen",
                  "tên người", "phi hành gia", "họa sĩ (tên",
                  "nhà văn (tên", "danh nhân",
                 "thiên tài", "tội phạm", "thủ phạm", "kẻ trộm", "mối quan hệ", "nhóm ", "đông người", "nhiều (người)", "tình nguyện", "hội viên", "dân số", "nhà điêu khắc", "họa sĩ",]),
    # --- du lịch ---
    ("du-lich", ["du lịch", "chuyến du lịch", "đi du lịch",
                "chuyến đi", "chuyến tham quan",
                "kỳ nghỉ", "kỳ nghỉ hè", "kỳ nghỉ đông",
                "đặt phòng", "đặt chỗ", "hủy đặt phòng",
                "nhận phòng", "trả phòng",
                "hộ chiếu", "visa ", "thị thực",
                "vé máy bay", "vé tàu", "vé khứ hồi",
                "hành lý", "va li", "ba lô", "túi du lịch",
                "bản đồ du lịch", "sách hướng dẫn",
                "hướng dẫn viên du lịch", "tour ",
                "tham quan", "ngắm cảnh", "ngắm ",
                "chụp ảnh", "máy ảnh (du lịch)",
                "quà lưu niệm", "đồ lưu niệm",
                "suối nước nóng", "onsen",
                "lễ hội", "lễ hội hoa", "lễ hội tuyết",
                "lễ hội mùa hè", "pháo hoa",
                "cắm trại", "lều trại", "đốt lửa trại",
                "leo núi (du lịch)", "đi biển",
                "nước ngoài", "trong nước",
                "xuất cảnh", "nhập cảnh", "hải quan",
                "sân bay quốc tế", "cửa khẩu",
                "khách du lịch", "đoàn khách",
                "lịch trình (du lịch)", "điểm đến",
                "kỷ niệm", "kỷ vật",
                 "homestay",]),
    # --- thể thao ---
    ("giai-tri", ["giải trí", "vui chơi", "tiêu khiển",
                 "bộ phim", "xem phim", "phim truyền hình",
                 "phim hoạt hình", "anime", "phim hài",
                 "phim kinh dị", "phim tình cảm", "phim hành động",
                 "phim tài liệu", "rạp chiếu phim (giải trí)",
                 "âm nhạc", "nghe nhạc", "bản nhạc", "bài hát",
                 "ca khúc", "giai điệu",
                 "nhạc cụ", "đàn piano", "đàn guitar",
                 "trống ", "đàn violin", "kèn ",
                 "ca hát", "hát karaoke", "nhảy múa",
                 "khiêu vũ", "buổi hòa nhạc", "liveshow",
                 "nhiếp ảnh", "chụp hình",
                 "trò chơi", "chơi game", "game ",
                 "truyện tranh", "manga", "tiểu thuyết",
                 "tạp chí", "tờ báo", "nhật báo",
                 "chương trình", "gameshow", "tin tức",
                 "sở thích", "đam mê",
                 "sưu tầm", "bộ sưu tập", "tem ",
                 "đọc sách (giải trí)", "vẽ tranh",
                 "câu lạc bộ (giải trí)", "quán karaoke",
                 "công viên giải trí", "khu vui chơi (giải trí)",
                 "ngắm hoa anh đào", "dã ngoại", "picnic",
                 "lễ hội (vui chơi)", "xem pháo hoa",
                 "nhạc ", "kịch ", "tranh", "karaoke",
                 "bữa tiệc", "tiệc ", "nghệ thuật ", "cắm hoa", "mỹ thuật", "búp bê", "đồ chơi", "điệu tango", "tango", "rạp xiếc", "xiếc", "tiết mục", "tác phẩm", "sáng tác nhạc", "điêu khắc", "truyện cổ tích", "truyền thuyết", "cuộc thi", "sự kiện", "nhân vật", "khúc nhạc", "hẹn hò", "bói ", "tử vi",
                "công chúa",
                "điệu múa",
                "buổi lễ",
                "phim",]),
    # --- giải trí ---
    ("the-thao", ["thể thao", "môn thể thao",
                 "bóng đá", "đá bóng", "sân bóng",
                 "bóng chày", "bóng rổ", "bóng chuyền",
                 "bóng bàn", "tennis", "cầu lông",
                 "golf ", "sân golf",
                 "bơi lội", "đi bơi", "bể bơi (thể thao)",
                 "chạy bộ", "chạy marathon", "điền kinh",
                 "đạp xe", "đua xe",
                 "trượt tuyết", "trượt băng", "trượt ván",
                 "lướt sóng", "lặn biển",
                 "câu cá", "đi câu",
                 "võ thuật", "karate", "judo", "kendo",
                 "sumo", "quyền anh", "đấu vật", "taekwondo",
                 "thể dục", "thể hình", "tập gym", "yoga",
                 "vận động", "khởi động", "tập luyện",
                 "vận động viên", "huấn luyện viên",
                 "trọng tài", "đội bóng", "đội tuyển",
                 "trận đấu", "hiệp đấu", "bàn thắng",
                 "điểm số", "tỷ số",
                 "thắng ", "thua ", "hòa ",
                 "vô địch", "quán quân", "á quân",
                 "huy chương", "cúp vàng", "kỷ lục",
                 "cổ động viên", "khán giả",
                 "sân vận động", "nhà thi đấu (thể thao)",
                 "dụng cụ thể thao", "quả bóng", "vợt ",
                 "gậy đánh golf", "ván trượt",
                 "olympic", "thế vận hội", "giải đấu", "đại hội", "đội ", "hồ bơi",
                "leo núi",]),
    # --- khoa học ---
    ("khoa-hoc", ["khoa học", "nhà khoa học",
                 "vật lý", "hóa học", "sinh học",
                 "toán học", "số học", "hình học", "đại số",
                 "thí nghiệm", "phòng thí nghiệm",
                 "nghiên cứu", "công trình nghiên cứu",
                 "phát minh", "sáng chế", "phát hiện",
                 "lý thuyết", "định luật", "công thức",
                 "nguyên tử", "phân tử", "tế bào", "gen ",
                 "năng lượng", "điện năng", "từ trường",
                 "trọng lực", "lực hấp dẫn",
                 "ánh sáng", "âm thanh", "sóng ",
                 "nhiệt ", "áp suất", "chân không",
                 "máy móc", "thiết bị", "robot ",
                 "trí tuệ nhân tạo", "máy tính (khoa học)",
                 "internet", "mạng ", "phần mềm", "lập trình",
                 "dữ liệu", "cơ sở dữ liệu",
                 "vệ tinh", "tên lửa", "tàu vũ trụ",
                 "khám phá vũ trụ", "thám hiểm",
                 "khí hậu (khoa học)", "biến đổi khí hậu",
                 "tiến hóa", "hóa thạch",
                 "kính hiển vi", "kính thiên văn",
                 "kỹ thuật", "công nghệ", "y học", "hệ thống", "từ khóa", "tập tin", "file ", "giải nobel", "nobel",
                "kim loại",
                "đường thẳng",]),
    # --- vị trí & hướng ---
    ("vi-tri", ["vị trí", "phương hướng", "hướng ",
               "phía trên", "phía dưới", "bên trên", "bên dưới",
               "phía trước", "phía sau", "đằng trước", "đằng sau",
               "bên trong", "bên ngoài", "trong ", "ngoài ",
               "bên cạnh", "kề bên", "sát bên",
               "bên trái", "bên phải", "tay trái", "tay phải",
               "ở giữa", "trung tâm", "chính giữa",
               "đối diện", "chéo ", "xung quanh",
               "phía đông", "phía tây", "phía nam", "phía bắc",
               "đông bắc", "đông nam", "tây bắc", "tây nam",
               "hướng đông", "hướng tây",
               "lên ", "xuống ", "vào ", "ra khỏi","đi ra","ra ngoài",
               "qua ", "ngang qua", "băng qua",
               "dọc theo", "men theo",
               "khắp nơi", "mọi nơi", "khắp ",
               "gần ", "xa ", "kề ", "sát ",
               "tầng một", "tầng hai", "tầng hầm", "tầng thượng",
               "đi thẳng", "rẽ trái", "rẽ phải",
               "quay lại", "vòng lại", "rẽ vào",
               "điểm xuất phát", "điểm đến", "đích đến",
               "nơi này", "nơi đó", "nơi kia",
               "chỗ này", "chỗ đó", "chỗ kia",
               "đây ", "đó ", "kia ",
               "trên ", "dưới ", "trước ", "sau ",
                 "góc phố", "góc đường", "hướng", "phía bên kia", "đằng kia", "mặt trước", "mặt sau",]),
    # --- thời gian ---
    ("thoi-gian", ["thời gian", "thời điểm", "lúc ",
                  "giờ ", "đồng hồ", "xem giờ",
                  "phút ", "giây ",
                  "buổi sáng", "sáng sớm", "bình minh (thời gian)",
                  "buổi trưa", "giữa trưa",
                  "buổi chiều", "xế chiều",
                  "buổi tối", "tối muộn", "ban đêm", "nửa đêm",
                  "hôm nay", "hôm qua", "hôm kia",
                  "ngày mai", "ngày kia", "ngày mốt",
                  "tuần này", "tuần trước", "tuần sau",
                  "tháng này", "tháng trước", "tháng sau",
                  "năm nay", "năm ngoái", "năm sau", "năm kia",
                  "thứ hai", "thứ ba", "thứ tư", "thứ năm",
                  "thứ sáu", "thứ bảy", "chủ nhật",
                  "ngày lễ", "ngày nghỉ", "ngày quốc khánh",
                  "giáng sinh", "noel", "tết ",
                  "tết nguyên đán", "tết dương lịch",
                  "sinh nhật", "ngày cưới", "kỷ niệm ngày",
                  "mùa xuân", "mùa hè", "mùa thu", "mùa đông",
                  "đầu năm", "cuối năm", "đầu tháng", "cuối tháng",
                  "đầu tuần", "cuối tuần",
                  "lịch ", "quyển lịch", "lịch trình (thời gian)",
                  "kỳ nghỉ", "kỳ nghỉ lễ",
                  "hạn chót", "thời hạn", "kỳ hạn",
                  "đúng giờ", "trễ giờ", "sớm ",
                  "muộn ", "kịp giờ", "lỡ ",
                  "hiện tại", "bây giờ", "lúc này",
                  "trước đây", "ngày xưa", "xưa kia",
                  "tương lai", "mai sau",
                  "mỗi ngày", "hằng ngày", "mỗi tuần",
                  "hằng tuần", "mỗi tháng", "mỗi năm",
                  "thường xuyên", "thỉnh thoảng", "hiếm khi",
                  "lâu lâu", "đôi khi",
                  "lần ", "lượt ", "đợt ",
                  "khoảng thời gian", "trong lúc", "trong khi",
                  "đồng thời", "trước khi", "sau khi",
                  "kể từ", "cho đến", "đến khi",
                  "vừa mới", "sắp ", "sắp tới",
                  "thế kỷ", "thập kỷ", "thập niên",
                  "niên đại", "thời đại", "thời kỳ",
                  "sáng nay", "tối nay", "đêm nay",
                  "chiều nay", "trưa nay", "mỗi sáng", "mỗi tối",
                  "mồng ", "ngày ",
                 "nửa năm", "hôm nọ", "đêm qua", "chiều tối", "ban ngày", "thời edo",]),
    # --- đồ dùng ---
    ("do-dung", ["đồ dùng", "vật dụng", "dụng cụ",
                "cái bàn", "bàn ", "bàn làm việc", "bàn ăn",
                "cái ghế", "ghế ", "ghế sofa", "ghế bành",
                "giường ", "giường ngủ", "nệm ", "chăn ",
                "gối ", "màn ", "rèm cửa", "thảm ",
                "tủ ", "tủ quần áo", "tủ lạnh", "tủ sách",
                "kệ ", "kệ sách", "giá sách", "ngăn kéo",
                "đèn ", "đèn bàn", "đèn ngủ", "đèn pin",
                "bóng đèn", "công tắc",
                "điện thoại", "điện thoại di động", "smartphone",
                "máy tính", "laptop", "máy tính bảng",
                "tivi ", "điều khiển", "remote",
                "máy giặt", "máy sấy", "máy hút bụi",
                "điều hòa", "máy lạnh", "quạt ", "quạt máy",
                "lò vi sóng", "lò nướng", "bếp ", "bếp ga",
                "nồi ", "chảo ", "ấm ", "bình đun",
                "dao ", "kéo ", "nĩa ", "thìa ", "đũa ",
                "bát ", "đĩa ", "cốc ", "ly ", "tách ",
                "chai ", "lọ ", "hộp ", "túi ",
                "chìa khóa", "ổ khóa",
                "cái ô", "ô (dù)", "dù ",
                "ví ", "bóp ", "túi xách",
                "kính ", "mắt kính", "gương ",
                "lược ", "bàn chải", "kem đánh răng",
                "xà phòng", "khăn ", "khăn mặt", "khăn tắm",
                "giấy ", "tờ giấy", "giấy vệ sinh",
                "bút (văn phòng)", "máy in", "máy photocopy",
                "máy ảnh", "máy quay",
                "radio ", "đài ", "loa ", "tai nghe",
                "đồng hồ treo tường", "đồng hồ báo thức",
                "lịch treo tường",
                "pin ", "cục pin", "sạc ", "cáp sạc",
                "vali ", "ba lô (đồ dùng)",
                "xe đẩy (đồ dùng)", "thang ",
                "búa ", "đinh ", "ốc vít", "kìm ",
                 "chỉ ", "cúc ",
                "xô ", "chậu ", "gáo ",
                "chổi ", "cây lau nhà", "giẻ lau",
                "thùng rác", "túi rác",
                "bình hoa", "chậu hoa",
                "tranh ", "bức tranh", "bức ảnh",
                "khung ảnh", "album ảnh",
                "sách (đồ vật)", "quyển sách",
                "báo (tờ)", "tờ báo",
                "máy điều hòa", "quạt trần",
                "bình cứu hỏa", "máy lọc nước",
                "máy rửa bát", "tủ đông",
                "điện (năng lượng)", "ổ điện", "dây điện",
                "ti-vi", "truyền hình", "máy vi tính", "máy tính",
                "máy ghi âm", "máy ", "thìa", "muỗng",
                "hòm thư", "cửa sổ",
                 "kệ", "nút ", "vòi sen", "bồn tắm",  "rác", "bức tường", "tường ", "sàn nhà", "nắp ", "sợi dây", "dây", "cần gạt", "tua vít", "chuông", "núm ", "thủy tinh", "động cơ", "nội thất", "sưởi ấm", "cái cốc", "đồ bỏ quên", "bỏ quên", "mở nắp", "gạt tàn",
                "nước máy", "đường ống",]),
    # --- cảm xúc & tính cách (tính từ + danh từ) ---
    ("cam-xuc", ["cảm xúc", "tâm trạng", "cảm giác",
                "vui vẻ", "vui mừng", "hân hoan",
                "buồn ", "buồn bã", "buồn rầu", "sầu ",
                "giận ", "tức giận", "bực mình", "cáu ",
                "sợ hãi", "sợ ", "hoảng sợ", "kinh hãi",
                "ngạc nhiên", "bất ngờ", "sửng sốt",
                "xấu hổ", "ngượng ", "e thẹn",
                "lo lắng", "bồn chồn", "bất an",
                "yên tâm", "an tâm", "thanh thản",
                "thất vọng", "chán nản", "tuyệt vọng",
                "hối hận", "ân hận", "hối tiếc",
                "ghen tị", "đố kỵ",
                "tự hào", "hãnh diện",
                "biết ơn", "cảm kích",
                "yêu ", "tình yêu", "thương ",
                "nhớ ", "nhung nhớ", "thương nhớ",
                "cô đơn", "lẻ loi",
                "chán ", "buồn chán", "nhàm chán",
                "hồi hộp", "phấn khích", "hào hứng",
                "tò mò ", "hiếu kỳ",
                "bình tĩnh", "điềm tĩnh", "trầm tĩnh",
                "thích ", "yêu thích", "mê ",
                "ghét ", "căm ghét", "khó chịu",
                "tính cách", "tính tình",
                "hiền ", "dịu dàng", "tốt bụng", "nhân hậu",
                "thân thiện", "cởi mở", "hòa đồng",
                "lạnh lùng", "khó gần",
                "nghiêm túc", "chăm chỉ", "siêng năng",
                "lười ", "lười biếng",
                "dũng cảm", "can đảm", "nhút nhát",
                "tự tin", "tự ti",
                "lạc quan", "bi quan",
                "khiêm tốn", "kiêu ngạo", "tự cao",
                "trung thực", "thật thà", "dối trá",
                "vui tính", "hài hước", "dí dỏm",
                "trầm tính", "ít nói", "sôi nổi",
                "nhiệt tình", "sốt sắng",
                "sáng sủa", "tươi sáng", "rạng rỡ",
                "đáng yêu", "dễ thương", "dễ mến",
                "đáng ghét", "đáng sợ",
                "tuyệt vời", "tuyệt diệu",
                "kinh khủng", "khủng khiếp",
                "kỳ lạ", "bí ẩn", "lạ lùng",
                "ổn ", "không sao", "yên ổn",
                "vất vả", "khó khăn (cảm xúc)",
                "mệt mỏi (tinh thần)", "uể oải",
                "căng thẳng (tinh thần)", "áp lực",
                "thư giãn", "thoải mái", "dễ chịu",
                "hạnh phúc", "may mắn", "xui xẻo",
                "hy vọng", "thất vọng", "mong đợi",
                "quyết tâm", "kiên trì", "bền bỉ",
                "nản lòng", "bỏ cuộc",
                 "nỗi buồn", "hứng thú", "giấc mơ", "ước mơ",
                "nỗi lo", "trăn trở",
                "tẻ nhạt",]),
    ("tinh-chat", ["nguy hiểm", "an toàn", "thói quen", "sức lực",
                "tiếng ồn", "gu ", "dáng vẻ", "hình dáng", "hình dạng",
                "hình tròn", "chiều dài", "trọng lượng", "kích thước",
                "giống nhau", "lãng phí", "thuận lợi", "mùi"]),
    ("hanh-dong", ["việc bận", "việc cần làm", "việc gấp",
                "công chuyện", "việc riêng",
                "bắt đầu", "kết thúc", "hủy bỏ", "hoạt động", "cách làm", "cách (làm",]),
]

# --- động từ: định tuyến theo nghĩa (trước khi rơi về hanh-dong) ---
VERB_RULES = [
    ("an-uong", ["ăn", "uống", "nấu", "nướng", "chiên", "xào", "luộc",
                "hấp ", "nêm", "khát", "đói", "gọt", "thái lát",
                "nhai ", "nuốt ", "nếm ", "dọn (món ăn)"]),
    ("giao-thong", ["đi [", "đi bộ", "đến nơi", "tới nơi",
                   "về [", "trở về", "quay về",
                   "lên [", "xuống [", "lên xe", "xuống xe",
                   "chuyển [tàu", "đổi [tàu", "chuyển tuyến",
                   "dừng [", "đỗ [", "dừng lại",
                   "lái [", "điều khiển [",
                   "bay [", "cất cánh", "hạ cánh",
                   "khởi hành", "xuất phát",
                   "đi qua", "đi ngang", "đi thẳng",
                   "rẽ ", "vòng ", "quay đầu",
                   "đi nhờ", "quá giang",
                   "trễ [tàu", "lỡ [tàu", "kịp [tàu"]),
    ("du-lich", ["du lịch", "đi du lịch", "tham quan", "ngắm cảnh",
                "đặt phòng", "đặt chỗ", "hủy đặt",
                "nhận phòng", "trả phòng", "chụp ảnh",
                "cắm trại", "đi biển", "đi chơi xa"]),
    ("mua-sam", ["mua ", "bán ", "mua sắm", "đi chợ",
                "đặt hàng", "giao hàng", "đổi trả", "bảo hành",
                "mặc cả", "trả giá", "thử ", "mặc thử",
                "gói quà", "tặng quà"]),
    ("hoc-tap", ["học ", "dạy ", "giảng ",
                "nhớ ", "quên ", "ghi nhớ", "học thuộc",
                "hiểu ", "ôn tập", "ôn thi", "luyện thi",
                "ghi chép", "chép ", "tra [từ", "tra cứu",
                "đọc ", "viết (chữ)", "luyện viết",
                "thi ", "dự thi", "đỗ ", "trượt ",
                "tốt nghiệp", "nhập học",
                "biết đến", "quen biết", "biết (khiêm",]),
    ("cong-viec", ["làm việc", "đi làm", "họp ",
                  "báo cáo", "nghỉ phép", "nghỉ việc",
                  "tăng ca", "làm thêm", "làm thêm giờ",
                  "ký ", "đóng dấu", "gửi [fax", "fax ",
                  "tuyển dụng", "phỏng vấn", "sa thải",
                  "nghỉ hưu", "về hưu",
                  "kinh doanh", "quảng cáo", "tiếp thị",
                  "chấm công"]),
    ("giao-tiep", ["nói ", "nói chuyện", "trò chuyện",
                  "hỏi ", "hỏi thăm", "trả lời", "đáp ",
                  "nghe [", "lắng nghe",
                  "viết ", "viết thư",
                  "gọi điện", "gọi [", "liên lạc", "liên hệ",
                  "gặp ", "gặp gỡ", "giới thiệu",
                  "chào ", "tạm biệt",
                  "cảm ơn", "xin lỗi",
                  "mời ", "rủ ", "nhờ ", "đề nghị",
                  "hứa ", "khen ", "chê ", "phàn nàn",
                  "bàn bạc", "thảo luận", "tranh luận",
                  "giải thích", "thông báo", "báo tin",
                  "kể ", "tâm sự", "đùa ", "trêu ",
                  "gửi thư", "gửi email", "nhắn tin"]),
    ("suc-khoe", ["nghỉ ngơi", "ngủ ", "đi ngủ",
                 "dậy ", "thức dậy", "thức giấc",
                 "đau ", "nhức ", "ốm ", "bệnh ",
                 "mệt ", "kiệt sức",
                 "khám ", "chữa bệnh","chữa trị", "điều trị",
                 "tiêm ", "uống thuốc",
                 "tập thể dục", "rèn luyện",
                 "giảm cân", "tăng cân", "ăn kiêng",
                 "say ", "chóng mặt", "buồn nôn",
                 "bị cảm", "sốt ", "ho [",
                 "hồi phục", "khỏi bệnh"]),
    ("cam-xuc", ["cười ", "khóc ", "giận ", "bực ",
                "ngạc nhiên", "xấu hổ", "ngượng ",
                "lo lắng", "yên tâm", "an tâm",
                "thất vọng", "hối hận", "ghen tị",
                "tự hào", "biết ơn",
                "thương ", "nhớ ", "yêu ",
                "cô đơn", "chán ",
                "hồi hộp", "phấn khích", "tò mò ",
                "bình tĩnh", "thư giãn"]),
    ("the-thao", ["chơi ", "đá [bóng", "đá bóng",
                 "bơi ", "đi bơi",
                 "chạy [", "chạy bộ", "chạy marathon",
                 "đạp xe", "đua xe",
                 "leo núi", "trượt tuyết", "trượt băng",
                 "lướt sóng", "lặn ", "câu cá", "đi câu",
                 "tập [thể", "vận động", "khởi động",
                 "tập luyện", "rèn luyện (thể thao)",
                 "thắng ", "thua ", "hòa [",
                 "thi đấu", "tranh tài"]),
    ("giai-tri", ["xem [phim", "xem phim", "xem kịch",
                 "nghe [nhạc", "nghe nhạc",
                 "hát (","nhạc cụ","đàn ", "ca hát", "nhảy ", "múa ",
                 "khiêu vũ", "vẽ [tranh", "vẽ tranh",
                 "chụp ảnh", "chụp hình",
                 "ngắm hoa", "đi dã ngoại",
                 "chơi [game", "chơi game", "chơi cờ",
                 "xem pháo hoa", "đi xem "]),
    ("khoa-hoc", ["nghiên cứu", "thí nghiệm",
                 "phát minh", "phát hiện",
                 "lập trình", "khám phá"]),
    ("con-nguoi", ["sinh ra", "ra đời", "chào đời",
                  "lớn lên", "trưởng thành",
                  "sống ", "kết hôn", "ly hôn",
                  "chết ", "qua đời",
                  "kết bạn", "làm quen"]),
    ("tien-bac", ["trả tiền", "thanh toán", "trả góp",
                 "gửi tiền", "rút tiền", "chuyển khoản",
                 "vay ", "cho vay", "nợ ",
                 "tiết kiệm", "đầu tư"]),
    ("thoi-gian", ["chờ ", "đợi ", "trễ ", "muộn ",
                  "kịp giờ", "đúng giờ"]),
]

# --- tính từ: kiểm tra trước khi rơi về tinh-chat ---
ADJ_RULES = [
    ("an-uong", ["ngon", "dở ", "ngọt", "cay ", "đắng",
                "chua ", "mặn", "nhạt (vị)", "béo ", "ngậy "]),
    ("suc-khoe", ["đau ", "mệt ", "khỏe", "ốm ",
                 "buồn ngủ", "chóng mặt", "say "]),
    ("cam-xuc", None),  # dùng chung KW_RULES cam-xuc
]


KW_MAP = dict(KW_RULES)
# Thứ tự ưu tiên khi dò từ khóa (hoc-tap/cong-viec đứng trước dia-diem
# để "phòng học"/"phòng họp" không bị "phòng " của dia-diem nuốt).
KW_PRIORITY = ["tien-bac", "mua-sam", "giao-thong", "hoc-tap", "cong-viec",
               "dia-diem", "suc-khoe", "co-the", "quan-ao", "an-uong",
               "dong-vat", "thien-nhien", "gia-dinh", "nghe-nghiep",
               "giao-tiep", "con-nguoi", "du-lich", "the-thao", "giai-tri",
               "khoa-hoc", "vi-tri", "thoi-gian", "do-dung", "cam-xuc",
               "tinh-chat", "hanh-dong"]


def load_words():
    words = []
    for name in ("seed-lessons-1-3.json", "seed-lessons-4-50.json"):
        data = json.loads((SEED / name).read_text(encoding="utf-8"))
        words.extend(data["words"])
    return words


def norm(s):
    return (s or "").lower()


def match_any(text, keywords):
    t = text.strip()
    return any(k in text or t == k.strip() for k in keywords)


def assign(word):
    kana = word.get("kana", "")
    lesson = word.get("lessonNo")
    pos = word.get("pos", "")
    mv = norm(word.get("meaningVi", ""))
    section = word.get("section", "")

    # 1. overrides
    if (kana, lesson) in OVERRIDES:
        return OVERRIDES[(kana, lesson)]
    if (kana, None) in OVERRIDES:
        return OVERRIDES[(kana, None)]

    # 1b. khớp nguyên nghĩa (từ đơn gây nhiễu substring)
    if mv.strip() in EXACT:
        return EXACT[mv.strip()]

    # 2. số đếm
    if pos in ("số từ", "lượng từ"):
        return "so-dem"
    if match_any(mv, KW_RULES[0][1]):
        return "so-dem"

    # 3. màu sắc
    if match_any(mv, KW_RULES[1][1]):
        return "mau-sac"

    # 4. từ chức năng ngữ pháp
    if pos in ("trợ từ", "thán từ", "liên từ", "tính từ chỉ định",
               "từ nghi vấn", "đại từ nghi vấn"):
        return "giao-tiep"
    if pos == "đại từ":
        # đại từ chỉ định (cái này/đó/kia, người này...) -> giao tiếp
        if match_any(mv, ["cái này", "cái đó", "cái kia",
                          "người này", "người đó", "người kia",
                          "~ này", "~ đó", "~ kia",
                          "như thế này", "như thế đó", "như thế kia",
                          "ở đây", "ở đó", "ở kia",
                          "bên này", "bên đó", "bên kia",
                          "chỗ anh/chị", "phía anh/chị"]):
            return "giao-tiep"
        return "con-nguoi"
    if pos in ("hậu tố", "tiền tố"):
        return "con-nguoi"  # 〜さん/〜ちゃん/〜じん...; お/ご/〜き đã override

    # 5. động từ / cụm động từ -> định tuyến theo nghĩa
    if pos in VERB_POS or pos == "cụm từ":
        for code, kws in VERB_RULES:
            if match_any(mv, kws):
                return code
        # cụm từ không phải động từ (PHRASE hội thoại) -> giao tiếp
        if pos == "cụm từ" and section == "PHRASE":
            return "giao-tiep"
        if pos == "cụm từ":
            return "hanh-dong"
        return "hanh-dong"

    # 6. tính từ: kiểm tra nhóm đặc thù trước
    if pos in ADJ_POS:
        for code, kws in ADJ_RULES:
            if kws is None:
                # dùng chung từ khóa cam-xuc
                kws = next(k for c, k in KW_RULES if c == "cam-xuc")
            if match_any(mv, kws):
                return code
        # rơi tiếp xuống từ khóa chung (tinh-chat sẽ bắt ở fallback)

    # 4b. trạng từ: định tuyến riêng (thời gian / nghi vấn / cách thức)
    if pos == "trạng từ":
        if match_any(mv, ["đi bộ"]):
            return "giao-thong"
        if match_any(mv, ["thế nào", "tại sao", "bằng cách nào",
                          "như vậy", "như thế", "làm sao"]):
            return "giao-tiep"
        if match_any(mv, ["mỗi ", "luôn luôn", "đã ", "rồi", "vẫn chưa",
                          "vẫn còn", "ngay lập tức", "lát nữa",
                          "lại ", "nữa", "tiếp theo", "một lần",
                          "nhiều lần", "dần dần", "thường",
                          "phần lớn", "có lẽ", "chắc là",
                          "một ngày nào đó", "suốt ", "mãi ",
                          "sớm", "muộn", "nhanh", "chậm rãi",
                          "từ từ", "gần đây", "dạo này", "đột nhiên",
                          "bất chợt", "vừa mới", "sắp ",
                          "cuối cùng", "đầu tiên", "trong lúc",
                          "đồng thời", "trước đây", "từ trước",
                          "sau ", "trước "]):
            return "thoi-gian"
        return "tinh-chat"

    # 5b. danh từ riêng chỉ người (nghệ sĩ, nhà khoa học...) -> con người
    if pos == "danh từ riêng" and match_any(mv, ["họa sĩ", "nhạc sĩ",
            "phi hành gia", "nhạc trưởng", "danh nhân", "ban nhạc",
            "tên người", "nhà thiên văn"]):
        return "con-nguoi"

    # 7. từ khóa chung theo cụm (so-dem, mau-sac đã xử lý ở trên)
    for code in KW_PRIORITY:
        if match_any(mv, KW_MAP[code]):
            return code

    # 8. fallback theo pos
    if pos in ADJ_POS:
        return "tinh-chat"
    if pos == "danh từ riêng":
        return "dia-diem"
    if section == "PHRASE":
        return "giao-tiep"
    return "khac"


def main():
    stats_only = "--stats-only" in sys.argv
    words = load_words()

    # kiểm tra key unique
    seen = {}
    dup = []
    for w in words:
        key = f"{w['kana']}|{w['lessonNo']}"
        if key in seen:
            dup.append((key, seen[key], w))
        seen[key] = (w.get("section"), w.get("meaningVi"))
    if dup:
        print(f"CẢNH BÁO: {len(dup)} key trùng!")
        for d in dup[:10]:
            print("  ", d)
    else:
        print(f"OK: {len(words)} key '<kana>|<lessonNo>' đều unique.")

    assignments = {}
    cluster_of = {}
    for w in words:
        code = assign(w)
        key = f"{w['kana']}|{w['lessonNo']}"
        assignments[key] = code
        cluster_of[key] = (code, w["lessonNo"], w["kana"],
                           w.get("meaningVi", ""), w.get("pos", ""))

    assert len(assignments) == len(words), "sót từ!"
    print(f"OK: 100% từ được gán ({len(assignments)}/{len(words)}).")

    # thống kê
    per_cluster = Counter(assignments.values())
    per_lesson = defaultdict(Counter)
    for key, code in assignments.items():
        lesson = int(key.rsplit("|", 1)[1])
        per_lesson[lesson][code] += 1

    print("\n== Số từ mỗi cụm ==")
    for code, label in CLUSTERS:
        print(f"  {code:12s} {per_cluster.get(code, 0):4d}  {label}")
    khac_n = per_cluster.get("khac", 0)
    print(f"\nkhac: {khac_n} từ ({khac_n/len(words)*100:.1f}%)")

    print("\n== Số cụm mỗi bài (min/max/avg) ==")
    counts = [len(c) for c in per_lesson.values()]
    print(f"  min={min(counts)} max={max(counts)} avg={sum(counts)/len(counts):.1f}")
    thin = {ln: c for ln, c in per_lesson.items() if len(c) < 5}
    if thin:
        print("  Bài có <5 cụm:", {ln: dict(c) for ln, c in sorted(thin.items())})
    tiny = [(ln, code, n) for ln, c in per_lesson.items()
            for code, n in c.items() if n == 1]
    print(f"  Cụm chỉ có 1 từ trong bài: {len(tiny)} trường hợp")

    if "--khac" in sys.argv or khac_n > 0:
        print("\n== Từ rơi vào 'khac' ==")
        for key, (code, ln, kana, mv, pos) in sorted(
                cluster_of.items(), key=lambda x: x[1][1]):
            if code == "khac":
                print(f"  L{ln:2d} {kana} | {pos} | {mv[:70]}")

    if not stats_only:
        payload = {
            "clusters": [{"code": c, "label": l} for c, l in CLUSTERS],
            "assignments": assignments,
        }
        OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=1)
                       + "\n", encoding="utf-8")
        print(f"\nĐã ghi {OUT} ({len(assignments)} assignments)")


if __name__ == "__main__":
    main()
