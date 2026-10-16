#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pass 1: phân loại 988 kanji (N5+N4) theo chủ đề bằng rules từ khóa tiếng Việt.
- EXPLICIT: gán tay các chữ mơ hồ (check trước rules).
- RULES: list (topic, [keywords]) theo thứ tự ưu tiên; keyword match whole-word (\b).
- Chữ nào không khớp rule nào -> 'khac' (để rà soát tay ở pass 2).

Output:
  work/mapping.tsv  (character, meaningVi, jlpt, topic) — để review
  stdout: phân bố số lượng từng chủ đề + danh sách chữ vào 'khac'
"""
import json
import os
import re
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORK = os.path.join(ROOT, 'work')

TOPICS = [
    ('so_dem', 'Số đếm'),
    ('thoi_gian', 'Thời gian'),
    ('thien_nhien', 'Thiên nhiên'),
    ('dong_thuc_vat', 'Động vật & thực vật'),
    ('con_nguoi_co_the', 'Con người & cơ thể'),
    ('gia_dinh_xa_hoi', 'Gia đình & xã hội'),
    ('an_uong', 'Ăn uống'),
    ('sinh_hoat', 'Sinh hoạt hàng ngày'),
    ('giao_tiep', 'Giao tiếp'),
    ('hoc_tap_cong_viec', 'Học tập & công việc'),
    ('kinh_te', 'Kinh tế'),
    ('tinh_chat', 'Tính chất'),
    ('mau_sac', 'Màu sắc'),
    ('noi_chon_huong', 'Nơi chốn & phương hướng'),
    ('cam_xuc', 'Cảm xúc'),
    ('truu_tuong', 'Khái niệm trừu tượng'),
    ('khac', 'Khác'),
]
TOPIC_CODES = [c for c, _ in TOPICS]

# Gán tay các chữ mơ hồ / nhiều nghĩa (ưu tiên cao nhất).
EXPLICIT = {
    # --- số đếm ---
    '一': 'so_dem', '二': 'so_dem', '三': 'so_dem', '四': 'so_dem', '五': 'so_dem',
    '六': 'so_dem', '七': 'so_dem', '八': 'so_dem', '九': 'so_dem', '十': 'so_dem',
    '百': 'so_dem', '千': 'so_dem', '万': 'so_dem', '億': 'so_dem',
    '半': 'so_dem', '倍': 'so_dem', '数': 'so_dem',
    '枚': 'so_dem', '匹': 'so_dem', '冊': 'so_dem', '杯': 'so_dem', '個': 'so_dem',
    '番': 'so_dem', '号': 'so_dem', '両': 'so_dem', '余': 'so_dem',
    # --- màu sắc ---
    '白': 'mau_sac', '黒': 'mau_sac', '赤': 'mau_sac', '青': 'mau_sac',
    '黄': 'mau_sac', '緑': 'mau_sac', '紫': 'mau_sac', '紺': 'mau_sac',
    '紅': 'mau_sac', '朱': 'mau_sac',
    # --- thời gian (tránh nhầm với nghĩa khác) ---
    '年': 'thoi_gian', '日': 'thoi_gian', '月': 'thoi_gian', '曜': 'thoi_gian',
    '分': 'thoi_gian', '刻': 'thoi_gian', '間': 'thoi_gian', '度': 'thoi_gian',
    '回': 'thoi_gian', '頃': 'thoi_gian', '暮': 'sinh_hoat',  # 暮らす = sinh sống
    '才': 'thoi_gian', '歳': 'thoi_gian', '齢': 'thoi_gian', '寿': 'thoi_gian',
    '予': 'thoi_gian', '将': 'thoi_gian', '過': 'thoi_gian', '次': 'thoi_gian',
    '現': 'thoi_gian', '前': 'thoi_gian', '後': 'thoi_gian', '先': 'thoi_gian',
    # --- thiên nhiên ---
    '洋': 'thien_nhien',
    '玉': 'thien_nhien',
    # --- động/thực vật ---
    '釣': 'sinh_hoat',  # câu cá là hành động
    # --- con người ---
    '息': 'con_nguoi_co_the',  # hơi thở
    '緒': 'truu_tuong',  # đầu mối
    '方': 'con_nguoi_co_the',  # người (lịch sự)
    '君': 'con_nguoi_co_the',
    '様': 'con_nguoi_co_the',
    '亡': 'con_nguoi_co_the',
    '額': 'kinh_te',  # số tiền (金額 phổ biến hơn ひたい)
    # --- gia đình & xã hội ---
    '英': 'gia_dinh_xa_hoi', '韓': 'gia_dinh_xa_hoi',
    '題': 'hoc_tap_cong_viec',  # chủ đề/đề tài (問題・課題)
    '正': 'tinh_chat',  # đúng
    '盗': 'gia_dinh_xa_hoi',  # ăn trộm (tránh nhầm 'ăn' -> an_uong)
    '位': 'noi_chon_huong',  # vị trí (tránh nhầm 'vị' -> an_uong)
    '苦': 'tinh_chat',  # khổ (tránh nhầm 'đắng' -> an_uong)
    # --- cảm xúc ---
    '楽': 'giao_tiep',  # âm nhạc
    '眠': 'sinh_hoat',  # ngủ (tránh nhầm 'buồn' -> cam_xuc)
    # --- ăn uống ---
    '米': 'an_uong',
    # --- học tập & công việc ---
    '産': 'hoc_tap_cong_viec',  # sản xuất
    '塾': 'hoc_tap_cong_viec',
    # --- kinh tế ---
    '金': 'kinh_te', '円': 'kinh_te', '銀': 'kinh_te',
    # --- nơi chốn ---
    '住': 'sinh_hoat',  # sinh sống
    # --- trừu tượng ---
    '啓': 'truu_tuong',
    '気': 'truu_tuong',
    '夢': 'truu_tuong',
    '志': 'truu_tuong',
    # --- pass 2: 139 chữ không khớp rule (rà soát tay từng chữ) ---
    '女': 'con_nguoi_co_the', '男': 'con_nguoi_co_the',
    '元': 'truu_tuong', '弁': 'truu_tuong', '名': 'truu_tuong',
    '合': 'truu_tuong', '交': 'truu_tuong', '当': 'truu_tuong',
    '考': 'truu_tuong', '何': 'truu_tuong', '利': 'truu_tuong',
    '忘': 'truu_tuong', '知': 'truu_tuong', '治': 'truu_tuong',
    '段': 'truu_tuong', '点': 'truu_tuong', '思': 'truu_tuong',
    '連': 'truu_tuong', '符': 'truu_tuong', '済': 'truu_tuong',
    '隆': 'truu_tuong', '然': 'truu_tuong', '証': 'truu_tuong',
    '集': 'truu_tuong', '電': 'truu_tuong', '慣': 'truu_tuong',
    '領': 'truu_tuong', '障': 'truu_tuong', '反': 'truu_tuong',
    '収': 'truu_tuong', '比': 'truu_tuong', '功': 'truu_tuong',
    '占': 'truu_tuong', '仙': 'truu_tuong', '叶': 'truu_tuong',
    '成': 'truu_tuong', '再': 'truu_tuong', '形': 'truu_tuong',
    '均': 'truu_tuong', '例': 'truu_tuong', '混': 'truu_tuong',
    '絶': 'truu_tuong', '惑': 'truu_tuong', '敬': 'truu_tuong',
    '源': 'truu_tuong', '続': 'truu_tuong', '彰': 'truu_tuong',
    '慮': 'truu_tuong', '賞': 'truu_tuong', '爆': 'truu_tuong',
    '魔': 'truu_tuong', '迷': 'truu_tuong', '誰': 'truu_tuong',
    '容': 'truu_tuong', '格': 'truu_tuong', '害': 'truu_tuong',
    '接': 'truu_tuong', '復': 'truu_tuong',
    '布': 'sinh_hoat', '吸': 'sinh_hoat', '待': 'sinh_hoat',
    '封': 'sinh_hoat', '発': 'sinh_hoat', '旅': 'sinh_hoat',
    '配': 'sinh_hoat', '船': 'sinh_hoat', '筒': 'sinh_hoat',
    '結': 'sinh_hoat', '遊': 'sinh_hoat', '換': 'sinh_hoat',
    '鍵': 'sinh_hoat', '鏡': 'sinh_hoat', '皿': 'sinh_hoat',
    '灯': 'sinh_hoat', '包': 'sinh_hoat', '針': 'sinh_hoat',
    '吹': 'sinh_hoat', '並': 'sinh_hoat', '倒': 'sinh_hoat',
    '紐': 'sinh_hoat', '捕': 'sinh_hoat', '棒': 'sinh_hoat',
    '蓋': 'sinh_hoat', '網': 'sinh_hoat', '噛': 'sinh_hoat',
    '整': 'sinh_hoat',
    '伝': 'giao_tiep', '便': 'giao_tiep', '紹': 'giao_tiep',
    '弾': 'giao_tiep', '叱': 'giao_tiep', '拝': 'giao_tiep',
    '芸': 'giao_tiep', '絡': 'giao_tiep', '御': 'giao_tiep',
    '鳴': 'giao_tiep', '褒': 'giao_tiep', '賛': 'giao_tiep',
    '役': 'hoc_tap_cong_viec', '専': 'hoc_tap_cong_viec',
    '師': 'hoc_tap_cong_viec', '部': 'hoc_tap_cong_viec',
    '試': 'hoc_tap_cong_viec', '印': 'hoc_tap_cong_viec',
    '協': 'hoc_tap_cong_viec', '係': 'hoc_tap_cong_viec',
    '募': 'hoc_tap_cong_viec', '操': 'hoc_tap_cong_viec',
    '講': 'hoc_tap_cong_viec', '績': 'hoc_tap_cong_viec',
    '特': 'tinh_chat', '雑': 'tinh_chat', '駄': 'tinh_chat',
    '腐': 'tinh_chat',
    '表': 'noi_chon_huong', '面': 'noi_chon_huong',
    '院': 'noi_chon_huong', '寮': 'noi_chon_huong',
    '丁': 'noi_chon_huong',
    '姫': 'gia_dinh_xa_hoi', '聖': 'gia_dinh_xa_hoi',
    '伊': 'gia_dinh_xa_hoi', '娘': 'gia_dinh_xa_hoi',
    '孫': 'gia_dinh_xa_hoi', '葬': 'gia_dinh_xa_hoi',
    '剣': 'gia_dinh_xa_hoi',
    '渇': 'con_nguoi_co_the', '咳': 'con_nguoi_co_the',
    '悩': 'cam_xuc', '懐': 'cam_xuc', '諦': 'cam_xuc',
    '鉛': 'thien_nhien', '鉄': 'thien_nhien', '煙': 'thien_nhien',
    '景': 'thien_nhien', '燃': 'thien_nhien',
    '麦': 'an_uong', '油': 'an_uong',
    '郎': 'khac',
    # --- pass 3: chữ tên riêng / ghi chú meta (tránh rule khớp nhầm) ---
    '甲': 'khac', '伏': 'khac', '伎': 'khac', '祇': 'khac',
    '弥': 'khac', '彦': 'khac',
    '束': 'truu_tuong', '爾': 'truu_tuong',
    '呂': 'sinh_hoat', '笥': 'sinh_hoat', '箪': 'sinh_hoat',
    '鹸': 'sinh_hoat', '粧': 'sinh_hoat',
    '斎': 'gia_dinh_xa_hoi',
    '琵': 'giao_tiep', '琶': 'giao_tiep',
    '挨': 'giao_tiep', '拶': 'giao_tiep',  # chỉ dùng trong 挨拶 (lời chào)
    '葛': 'dong_thuc_vat',
    '閣': 'noi_chon_huong', '澤': 'thien_nhien',
    '甚': 'tinh_chat',
}

# (topic, [keywords]) — check theo thứ tự; keyword match whole-word.
RULES = [
    ('so_dem', ['một', 'hai', 'ba', 'bốn', 'sáu', 'bảy', 'tám', 'chín', 'mười',
                'trăm', 'nghìn', 'vạn', 'triệu', 'số', 'đếm', 'nửa',
                'gấp đôi', 'cả hai', 'dư thừa']),
    ('mau_sac', ['trắng', 'đen', 'đỏ', 'xanh', 'vàng', 'tím', 'màu']),
    ('thoi_gian', ['ngày', 'tháng', 'giờ', 'phút', 'giây', 'tuần', 'buổi',
                   'đêm', 'ban đêm', 'hôm qua', 'bây giờ', 'lâu', 'mỗi',
                   'trước', 'sau', 'xưa', 'đầu tiên', 'bắt đầu', 'kết thúc',
                   'cuối', 'khoảng thời gian', 'khoảng', 'mùa', 'tuổi',
                   'dịp lễ', 'hiện tại', 'sắp tới', 'chiều', 'lần', 'vòng']),
    ('an_uong', ['ăn', 'uống', 'cơm', 'trà', 'rượu', 'thịt', 'trứng',
                 'trái cây', 'bánh kẹo', 'rau', 'đậu', 'gạo', 'ngọt', 'muối',
                 'vị', 'cay', 'say', 'bát cơm', 'nấu', 'nướng', 'sôi', 'sữa',
                 'tương', 'đũa', 'nồi']),
    ('con_nguoi_co_the', ['người', 'tôi', 'cơ thể', 'tay', 'chân', 'mắt', 'tai',
                          'miệng', 'răng', 'mũi', 'tóc', 'khuôn mặt', 'đầu',
                          'cổ', 'họng', 'lưng', 'bụng', 'dạ dày', 'trái tim',
                          'tâm', 'mồ hôi', 'nước mắt', 'giọng nói', 'hơi thở',
                          'tế bào', 'trán', 'ngón tay', 'sức mạnh', 'dáng vẻ',
                          'bệnh', 'bác sĩ', 'thuốc', 'khám bệnh', 'vết thương',
                          'mệt', 'khỏe mạnh', 'gầy', 'đau', 'não', 'chết',
                          'sống', 'sinh', 'bạn', 'anh ấy', 'tự', 'bản thân',
                          'ngài', 'thân']),
    ('gia_dinh_xa_hoi', ['cha', 'mẹ', 'anh trai', 'em trai', 'chị gái',
                         'em gái', 'trẻ em', 'vợ', 'chồng', 'gia đình',
                         'gia tộc', 'dân tộc', 'tổ tiên', 'hôn nhân',
                         'bạn bè', 'mối quan hệ', 'khách', 'hàng xóm',
                         'đất nước', 'quốc gia', 'thế giới', 'thủ đô',
                         'kinh đô', 'thành phố', 'thị trấn', 'tỉnh',
                         'công ty', 'nhân viên', 'thành viên', 'chính trị',
                         'quan chức', 'bưu điện', 'chiến tranh', 'cảnh sát',
                         'pháp luật', 'luật', 'quy tắc', 'kỷ luật', 'phạt',
                         'cấm', 'phạm tội', 'ăn trộm', 'nghi thức',
                         'lịch sử', 'thần', 'chùa', 'lễ hội', 'lễ nghi',
                         'công cộng', 'đoàn', 'nhóm', 'hội', 'gặp gỡ',
                         'võ sĩ', 'chủ', 'chính', 'Anh', 'Hàn', 'Trung Quốc',
                         'Nhật Bản', 'Phật', 'thời đại', 'kỷ']),
    ('cam_xuc', ['thích', 'ghét', 'tình yêu', 'vui', 'buồn', 'sợ', 'xấu hổ',
                 'ham muốn', 'mong muốn', 'cầu nguyện', 'cười', 'khóc',
                 'hạnh phúc', 'may mắn', 'tình cảm', 'cảm giác', 'hứng thú',
                 'hoảng hốt', 'yêu']),
    ('dong_thuc_vat', ['bò', 'chó', 'mèo', 'ngựa', 'heo', 'gà', 'chim', 'cá',
                       'voi', 'rùa', 'khỉ', 'đực', 'cây', 'hoa', 'lá', 'cỏ',
                       'rễ', 'cành', 'thân', 'anh đào', 'tử đằng',
                       'chăn nuôi', 'mồi']),
    ('giao_tiep', ['nói', 'nghe', 'hỏi', 'trả lời', 'gọi', 'hát', 'dịch',
                   'bàn', 'đàm phán', 'tin đồn', 'nói dối', 'nhờ vả', 'mời',
                   'rủ', 'chào', 'chúc mừng', 'cảm ơn', 'xin lỗi', 'ngôn ngữ',
                   'nhìn', 'xem', 'đọc', 'tranh', 'âm thanh', 'âm nhạc',
                   'khúc', 'biểu diễn', 'nhảy múa', 'múa', 'chơi (đàn)',
                   'ghi', 'báo cáo', 'tin tức', 'đăng', 'chiếu', 'giải thích',
                   'thuyết', 'chụp', 'sao chép']),
    ('hoc_tap_cong_viec', ['học', 'dạy', 'trường', 'sách', 'bài học',
                           'luyện tập', 'nghiên cứu', 'giáo dục',
                           'tốt nghiệp', 'lớp học thêm', 'kiểm tra',
                           'kinh nghiệm', 'chữ', 'văn', 'từ điển', 'tạp chí',
                           'sổ', 'bút', 'giấy', 'thư viện', 'truyền thụ',
                           'nuôi dưỡng', 'làm việc', 'công việc', 'nghề',
                           'nhiệm vụ', 'kinh doanh', 'điều hành', 'phục vụ',
                           'lao động', 'chăm chỉ', 'đi làm', 'kỹ thuật',
                           'tài nghệ', 'máy móc', 'dụng cụ', 'cơ hội',
                           'vật liệu', 'tài năng', 'tư liệu', 'tài nguyên',
                           'sản phẩm', 'di sản', 'chủ đề', 'đề tài']),
    ('kinh_te', ['tiền', 'yên', 'giá', 'mua', 'bán', 'buôn bán', 'thương mại',
                 'trả', 'cho mượn', 'cho thuê', 'mượn', 'vay', 'gửi',
                 'tiết kiệm', 'tích trữ', 'lương', 'tài sản', 'giàu có',
                 'thịnh vượng', 'báu vật', 'kinh tế', 'phí', 'nguyên liệu']),
    ('noi_chon_huong', ['nhà', 'trường', 'cửa hàng', 'nơi', 'chỗ', 'địa điểm',
                        'phòng', 'tầng', 'cổng', 'cửa', 'tường', 'sàn', 'vườn',
                        'công viên', 'cảng', 'nhà ga', 'đường', 'cầu',
                        'cung điện', 'đền', 'lâu đài', 'kho', 'hành lang',
                        'góc', 'vùng', 'xung quanh', 'phía', 'bên', 'hướng',
                        'đông', 'tây', 'nam', 'bắc', 'trên', 'dưới', 'phải',
                        'trái', 'trong', 'ngoài', 'giữa', 'làng', 'quê hương',
                        'ngoại ô', 'mặt sau', 'sâu bên trong', 'ngang',
                        'bên cạnh', 'Osaka', 'Nara', 'vị trí', 'ngôi']),
    ('sinh_hoat', ['ngủ', 'dậy', 'tắm', 'rửa', 'quét', 'giặt', 'lau', 'mặc',
                   'cởi', 'đội', 'mũ', 'giày', 'quần áo', 'thắt lưng', 'ô',
                   'dù', 'ghế', 'bàn', 'kệ', 'hộp', 'túi', 'chai', 'lọ',
                   'lon', 'nồi', 'cư trú', 'sinh sống', 'đi', 'đến', 'về',
                   'ra', 'vào', 'lên', 'xuống', 'đứng', 'ngồi', 'chạy', 'bơi',
                   'bay', 'leo', 'cưỡi', 'rơi', 'vận chuyển', 'gửi', 'tiễn',
                   'đón', 'trốn', 'tiến', 'rút lui', 'về hưu', 'qua', 'vượt',
                   'thông qua', 'đi lại', 'rời', 'quay lại', 'ghé', 'treo',
                   'chen chúc', 'bao gồm', 'làm', 'dùng', 'sử dụng', 'lấy',
                   'cầm', 'mang theo', 'đặt', 'để', 'thả', 'vứt bỏ', 'nhặt',
                   'cắt', 'gập', 'bẻ', 'vỡ', 'phá', 'đâm', 'chích', 'bắn',
                   'chiếu', 'ném', 'đầu tư', 'đánh', 'giẫm', 'bước', 'chạm',
                   'ấn', 'đẩy', 'kéo', 'rút ra', 'dán', 'căng', 'mở', 'đóng',
                   'trải', 'chất đống', 'chôn', 'lấp', 'trồng', 'câu',
                   'nuôi', 'dừng', 'nghỉ ngơi', 'chọn', 'tìm kiếm',
                   'thăm dò', 'cứu', 'giúp đỡ', 'hỗ trợ', 'cứu viện',
                   'bảo vệ', 'giữ', 'chuẩn bị', 'trang bị', 'thiết lập',
                   'thành lập', 'xây dựng', 'chế tạo', 'sản xuất',
                   'trang trí', 'tặng', 'chuyển động', 'lăn', 'chuyển',
                   'thay đổi']),
    ('tinh_chat', ['lớn', 'to', 'nhỏ', 'ít', 'nhiều', 'cao', 'thấp', 'dài',
                   'ngắn', 'rộng', 'hẹp', 'dày', 'mỏng', 'nhạt', 'mảnh',
                   'chi tiết', 'mập', 'nặng', 'nhẹ', 'mạnh', 'yếu', 'cứng',
                   'mềm', 'nhanh', 'chậm', 'muộn', 'sớm', 'gấp', 'vội', 'mới',
                   'cũ', 'trẻ', 'non nớt', 'trẻ thơ', 'đẹp', 'xấu', 'ác',
                   'tốt', 'đúng', 'sai', 'thật', 'chắc chắn', 'xác nhận',
                   'sáng', 'rõ ràng', 'tối', 'xa', 'gần', 'ấm', 'lạnh',
                   'nóng', 'mát mẻ', 'nhiệt', 'khô', 'ướt', 'thơm', 'mùi',
                   'yên tĩnh', 'ồn ào', 'trống rỗng', 'toàn bộ', 'đơn',
                   'giản', 'phức tạp', 'khó', 'dễ dàng', 'nguy hiểm',
                   'hiểm trở', 'bằng phẳng', 'bình thường', 'thẳng',
                   'trực tiếp', 'cong', 'tròn', 'đậm', 'nghiêm khắc',
                   'dịu dàng', 'ưu tú', 'vĩ đại', 'sang trọng', 'bẩn',
                   'trơn', 'trượt', 'nhất', 'tối đa', 'giống', 'khác',
                   'hiếm', 'quý', 'kỳ lạ', 'quái', 'bận rộn', 'rảnh rỗi',
                   'thông thường', 'thường']),
    ('thien_nhien', ['trời', 'đất', 'núi', 'sông', 'biển', 'bầu trời', 'mưa',
                     'tuyết', 'gió', 'lửa', 'nước', 'đá', 'suối', 'sóng',
                     'hồ', 'mây', 'sấm sét', 'ngôi sao', 'nắng', 'vũ trụ',
                     'ao', 'thung lũng', 'bờ', 'mũi đất', 'dốc', 'đảo',
                     'vịnh', 'giếng', 'đồng hoang', 'đồng ruộng', 'dã', 'bùn',
                     'cát', 'tro', 'đất liền', 'đại dương', 'ánh sáng',
                     'ngọc', 'nhiều mây', 'trời quang', 'đầm lầy', 'ruộng',
                     'thổ']),
    ('truu_tuong', ['lý', 'lẽ', 'nghĩa', 'đức', 'nguyên nhân', 'mục tiêu',
                    'tính', 'tương', 'lẫn nhau', 'đối', 'cặp', 'biến hóa',
                    'hóa', 'cần thiết', 'nhất định', 'có thể', 'được', 'phi',
                    'không', 'toàn bộ', 'phải', 'hứa hẹn', 'tin tưởng',
                    'công nhận', 'cho phép', 'tha thứ', 'tồn tại', 'mất',
                    'thất bại', 'thắng', 'thua', 'chất lượng', 'chất',
                    'lượng', 'loại', 'đồ vật', 'vụ việc', 'món', 'việc',
                    'sự việc', 'ý', 'nhận thức', 'kiến thức', 'quan sát',
                    'đoán', 'phán đoán', 'quyết định', 'đo', 'tính toán',
                    'kế hoạch', 'điều tra', 'điều chỉnh', 'kinh qua', 'dịp',
                    'quốc tế', 'vẻ', 'khí', 'tinh thần', 'giấc mơ', 'chí',
                    'đầu mối', 'duyên', 'viền', 'vận mệnh', 'mệnh lệnh',
                    'sinh mệnh', 'thế', 'khai sáng', 'còn lại', 'tàn dư',
                    'loại trừ', 'thêm vào', 'cộng', 'giảm', 'tăng']),
]


def load_all():
    items = []
    for f in ['kanji-n5.json', 'kanji-n4.json']:
        path = os.path.join(ROOT, 'data', 'seed', f)
        with open(path, encoding='utf-8') as fh:
            d = json.load(fh)
        items.extend(d['kanji'])
    return items


def kw_match(kw, text):
    # Keyword 1 token (\w+) -> match whole-word (tránh 'cá' khớp 'cái').
    # Keyword có khoảng trắng/ngoặc -> match substring literal
    # (tránh bug \b sau ký tự non-word như ')').
    if re.fullmatch(r'\w+', kw, re.UNICODE):
        return re.search(r'\b' + re.escape(kw) + r'\b', text) is not None
    return kw in text


def strip_meta_note(m):
    # Bỏ ghi chú "(dùng trong ...)" / "; dùng trong ..." khỏi meaning trước khi
    # match rule — tránh từ trong ghi chú (vd. "trong", "phòng", "chùa") làm
    # lệch phân loại. Giữ nguyên cho file review/export.
    m = re.sub(r'\(dùng trong[^)]*\)', '', m)
    m = re.sub(r';\s*dùng trong[^;]*', '', m)
    return m


def classify_one(ch, meaning):
    if ch in EXPLICIT:
        return EXPLICIT[ch], 'explicit'
    m = strip_meta_note(meaning or '')
    for topic, keywords in RULES:
        for kw in keywords:
            if kw_match(kw, m):
                return topic, f'rule:{kw}'
    return 'khac', 'unmatched'


def main():
    items = load_all()
    rows = []
    for k in items:
        ch = k['character']
        topic, why = classify_one(ch, k.get('meaningVi'))
        rows.append((ch, k.get('meaningVi'), k.get('jlpt'), topic, why))

    # Ghi file review
    out = os.path.join(WORK, 'mapping.tsv')
    with open(out, 'w', encoding='utf-8') as f:
        f.write('character\tmeaningVi\tjlpt\ttopic\twhy\n')
        for ch, m, j, t, why in rows:
            f.write(f'{ch}\t{m}\t{j}\t{t}\t{why}\n')

    cnt = Counter(t for _, _, _, t, _ in rows)
    print(f'Tổng: {len(rows)} chữ')
    for code, label in TOPICS:
        n = cnt.get(code, 0)
        pct = 100.0 * n / len(rows)
        print(f'  {code:20s} {n:4d}  ({pct:4.1f}%)  {label}')
    unmatched = [(ch, m) for ch, m, _, t, why in rows
                 if t == 'khac' and why == 'unmatched']
    print(f'\nKhông khớp rule nào (khac/unmatched): {len(unmatched)}')
    for ch, m in unmatched:
        print(f'  {ch}\t{m}')
    print(f'\nReview file: {out}')


if __name__ == '__main__':
    main()
