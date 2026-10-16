#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Build seed-lessons-1-3.json cho dự án Master JP.

- Đọc research/minna-vocab-50.json (nguồn cộng đồng MinnaNoDS, đã có meaning_en).
- meaningVi + câu ví dụ do BIÊN SOẠN THỦ CÔNG (không dump bản dịch máy thô).
- Chỉ chạy 1 lần (hoặc khi sửa dữ liệu tay); output: data/seed/seed-lessons-1-3.json

Map section (theo yêu cầu):
  kind=expression        -> PHRASE
  kind=word              -> VOCAB
  kind=name/counter/suffix/prefix/title -> REFERENCE
  kind=None (tên công ty/trường GIẢ ĐỊNH trong sách) -> REFERENCE (đã đánh dấu)
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'research', 'minna-vocab-50.json')
OUT = os.path.join(ROOT, 'data', 'seed', 'seed-lessons-1-3.json')

LESSONS = [
    {'lessonNo': 1, 'level': 'N5', 'titleJa': '第1課', 'titleVi': 'Chào hỏi, giới thiệu'},
    {'lessonNo': 2, 'level': 'N5', 'titleJa': '第2課', 'titleVi': 'Đồ vật và cách chỉ định'},
    {'lessonNo': 3, 'level': 'N5', 'titleJa': '第3課', 'titleVi': 'Nơi chốn, số đếm và giá cả'},
]

SECTION_MAP = {
    'expression': 'PHRASE',
    'word': 'VOCAB',
    'name': 'REFERENCE',
    'counter': 'REFERENCE',
    'suffix': 'REFERENCE',
    'prefix': 'REFERENCE',
    'title': 'REFERENCE',
    None: 'REFERENCE',  # tên giả định trong sách -> REFERENCE
}

POS_MAP = {
    'n': 'danh từ',
    'pn': 'đại từ',
    'suf': 'hậu tố',
    'ctr': 'lượng từ',
    'int': 'thán từ',
    'exp': 'cụm từ',
    'adv': 'trạng từ',
    'conj': 'liên từ',
    'num': 'số từ',
    'adj-pn': 'tính từ chỉ định',
    None: 'danh từ riêng',  # tên giả định trong sách
}


def M(meaningVi, exampleJa=None, exampleVi=None, pos=None):
    """Một dòng dữ liệu biên soạn tay: nghĩa, (câu ví dụ), (ghi đè pos)."""
    return {'meaningVi': meaningVi, 'exampleJa': exampleJa,
            'exampleVi': exampleVi, 'pos': pos}


# key = (lesson, index) -> dữ liệu biên soạn tay
OV = {
    # ================= BÀI 1 (第1課 — Chào hỏi, giới thiệu) =================
    (1, 1): M('tôi', 'わたしは がくせいです。', 'Tôi là sinh viên.'),
    (1, 2): M('bạn, anh/chị (ngôi thứ hai)', 'あなたは せんせいですか。', 'Bạn có phải là giáo viên không?'),
    (1, 3): M('người kia, anh ấy / cô ấy (あの方 là cách nói lịch sự của あの人)',
              'あのひとは せんせいです。', 'Người kia là giáo viên.'),
    (1, 4): M('anh/chị, ông/bà (hậu tố lịch sự đặt sau tên người)'),
    (1, 5): M('hậu tố thân mật đặt sau tên, thường dùng cho trẻ em (thay cho ～さん)'),
    (1, 6): M('hậu tố chỉ quốc tịch, đặt sau tên nước (ví dụ: ベトナムじん: người Việt Nam)'),
    (1, 7): M('giáo viên, thầy/cô (cách gọi tôn trọng người khác; không dùng khi nói về nghề của mình)',
              'たなかせんせいは にほんじんです。', 'Thầy Tanaka là người Nhật.'),
    (1, 8): M('giáo viên (nghề nghiệp)', 'わたしは きょうしです。', 'Tôi là giáo viên.'),
    (1, 9): M('sinh viên, học sinh', 'わたしは がくせいです。', 'Tôi là sinh viên.'),
    (1, 10): M('nhân viên công ty', 'わたしは かいしゃいんです。', 'Tôi là nhân viên công ty.'),
    (1, 11): M('nhân viên (của công ty ~; dùng kèm tên công ty)',
              'たなかさんは IMCの しゃいんです。', 'Anh Tanaka là nhân viên của công ty IMC.'),
    (1, 12): M('nhân viên ngân hàng', 'わたしは ぎんこういんです。', 'Tôi là nhân viên ngân hàng.'),
    (1, 13): M('bác sĩ', 'たなかさんは いしゃです。', 'Anh Tanaka là bác sĩ.'),
    (1, 14): M('nhà nghiên cứu', 'あのひとは けんきゅうしゃです。', 'Người kia là nhà nghiên cứu.'),
    (1, 15): M('đại học, trường đại học', 'これは さくらだいがくです。', 'Đây là trường đại học Sakura.'),
    (1, 16): M('bệnh viện', 'あれは こうべびょういんです。', 'Kia là bệnh viện Kobe.'),
    (1, 17): M('ai (どなた là cách nói lịch sự)', 'あのひとは どなたですか。', 'Người kia là ai ạ?',
               pos='đại từ nghi vấn'),
    (1, 18): M('tuổi (đếm tuổi; ví dụ: ２０さい: 20 tuổi)'),
    (1, 19): M('mấy tuổi, bao nhiêu tuổi (おいくつ là cách nói lịch sự)',
              'おいくつですか。', 'Bạn bao nhiêu tuổi ạ?', pos='từ nghi vấn'),
    (1, 20): M('vâng, dạ (trả lời khẳng định)', 'はい、わたしは がくせいです。', 'Vâng, tôi là sinh viên.'),
    (1, 21): M('không (trả lời phủ định)', 'いいえ、わたしは がくせいじゃありません。',
               'Không, tôi không phải là sinh viên.'),
    (1, 22): M('Rất vui được gặp bạn (câu chào khi gặp lần đầu)'),
    (1, 23): M('Tôi đến từ ~ (quê quán, đất nước)'),
    (1, 24): M('Rất mong được giúp đỡ (câu chào kết thúc phần tự giới thiệu)'),
    (1, 25): M('Xin lỗi nhưng... (dùng khi hỏi thông tin cá nhân của người khác)'),
    (1, 26): M('Tên bạn là gì ạ? (cách hỏi tên lịch sự)'),
    (1, 27): M('Đây là anh/chị ~ (giới thiệu người khác)'),
    (1, 28): M('Mỹ, Hoa Kỳ', 'わたしは アメリカじんです。', 'Tôi là người Mỹ.'),
    (1, 29): M('Anh, Vương quốc Anh', 'たなかさんは イギリスじんです。', 'Anh Tanaka là người Anh.'),
    (1, 30): M('Ấn Độ', 'あのひとは インドじんです。', 'Người kia là người Ấn Độ.'),
    (1, 31): M('Indonesia', 'わたしは インドネシアじんです。', 'Tôi là người Indonesia.'),
    (1, 32): M('Hàn Quốc', 'あのひとは かんこくじんです。', 'Người kia là người Hàn Quốc.'),
    (1, 33): M('Thái Lan', 'わたしは タイじんです。', 'Tôi là người Thái Lan.'),
    (1, 34): M('Trung Quốc', 'たなかさんは ちゅうごくじんです。', 'Anh Tanaka là người Trung Quốc.'),
    (1, 35): M('Đức', 'あのひとは ドイツじんです。', 'Người kia là người Đức.'),
    (1, 36): M('Nhật Bản', 'わたしは にほんじんです。', 'Tôi là người Nhật.'),
    (1, 37): M('Brazil', 'たなかさんは ブラジルじんです。', 'Anh Tanaka là người Brazil.'),
    (1, 38): M('IMC / Điện lực Power / Brazil Air (tên công ty GIẢ ĐỊNH dùng trong bài học)'),
    (1, 39): M('AKC (tên học viện GIẢ ĐỊNH dùng trong bài học)'),
    (1, 40): M('Bệnh viện Kobe (tên bệnh viện GIẢ ĐỊNH dùng trong bài học)'),
    (1, 41): M('Đại học Sakura / Đại học Fuji (tên trường GIẢ ĐỊNH dùng trong bài học)'),
    (1, 42): M('chúng tôi, chúng ta', 'わたしたちは がくせいです。', 'Chúng tôi là sinh viên.'),
    (1, 43): M('quý vị, mọi người (cách gọi lịch sự)', 'みなさん、はじめまして。',
               'Chào quý vị, rất vui được gặp.'),
    (1, 44): M('hậu tố thân mật đặt sau tên, thường dùng cho nam giới'),
    (1, 45): M('kỹ sư', 'たなかさんは エンジニアです。', 'Anh Tanaka là kỹ sư.'),
    (1, 46): M('điện; ánh sáng điện', 'たなかさんは でんきの エンジニアです。', 'Anh Tanaka là kỹ sư điện.'),
    (1, 47): M('Pháp', 'たなかさんは フランスから きました。', 'Anh Tanaka đến từ Pháp.'),
}

OV.update({
    # ================= BÀI 2 (第2課 — Đồ vật và cách chỉ định) =================
    (2, 1): M('cái này (vật ở gần người nói)', 'これは ほんです。', 'Đây là quyển sách.'),
    (2, 2): M('cái đó (vật ở gần người nghe)', 'それは じしょです。', 'Đó là cuốn từ điển.'),
    (2, 3): M('cái kia (vật ở xa cả hai người)', 'あれは とけいです。', 'Kia là cái đồng hồ.'),
    (2, 4): M('~ này (chỉ vật gần người nói; đứng trước danh từ)'),
    (2, 5): M('~ đó (chỉ vật gần người nghe; đứng trước danh từ)'),
    (2, 6): M('~ kia (chỉ vật ở xa; đứng trước danh từ)'),
    (2, 7): M('sách', 'この ほんは わたしの ほんです。', 'Quyển sách này là sách của tôi.'),
    (2, 8): M('từ điển', 'それは じしょです。', 'Đó là cuốn từ điển.'),
    (2, 9): M('tạp chí', 'この ざっしは にほんごの ざっしです。', 'Tạp chí này là tạp chí tiếng Nhật.'),
    (2, 10): M('báo, nhật báo', 'あれは しんぶんです。', 'Kia là tờ báo.'),
    (2, 11): M('vở ghi chép', 'この ノートは あなたの ノートですか。', 'Quyển vở này có phải là vở của bạn không?'),
    (2, 12): M('sổ tay', 'これは てちょうです。', 'Đây là sổ tay.'),
    (2, 13): M('danh thiếp', 'これは わたしの めいしです。', 'Đây là danh thiếp của tôi.'),
    (2, 14): M('thẻ (thẻ tín dụng)', 'あれは カードです。', 'Kia là cái thẻ.'),
    (2, 15): M('bút chì', 'この えんぴつは たなかさんの えんぴつです。', 'Cây bút chì này là của anh Tanaka.'),
    (2, 16): M('bút bi', 'それは ボールペンです。', 'Đó là cây bút bi.'),
    (2, 17): M('bút chì bấm, bút chì kim', 'これは シャープペンシルです。', 'Đây là bút chì bấm.'),
    (2, 18): M('chìa khóa', 'それは くるまの かぎです。', 'Đó là chìa khóa xe hơi.'),
    (2, 19): M('đồng hồ', 'この とけいは にほんの とけいです。', 'Chiếc đồng hồ này là đồng hồ của Nhật.'),
    (2, 20): M('cái ô, cái dù', 'あの かさは わたしの かさです。', 'Cái ô kia là ô của tôi.'),
    (2, 21): M('cái cặp, cái túi xách', 'この かばんは だれの かばんですか。', 'Cái cặp này là của ai?'),
    (2, 22): M('đĩa CD', 'これは CDです。', 'Đây là đĩa CD.'),
    (2, 23): M('ti-vi, truyền hình', 'あれは テレビです。', 'Kia là cái ti-vi.'),
    (2, 24): M('đài radio', 'この ラジオは わたしの ラジオです。', 'Cái radio này là của tôi.'),
    (2, 25): M('máy ảnh', 'それは カメラです。', 'Đó là cái máy ảnh.'),
    (2, 26): M('máy vi tính', 'この コンピューターは かいしゃの コンピューターです。',
               'Máy tính này là máy tính của công ty.'),
    (2, 27): M('xe hơi, xe ô tô', 'あれは わたしの くるまです。', 'Kia là xe hơi của tôi.'),
    (2, 28): M('cái bàn', 'この つくえは せんせいの つくえです。', 'Cái bàn này là bàn của thầy giáo.'),
    (2, 29): M('cái ghế', 'その いすは わたしの いすです。', 'Cái ghế đó là ghế của tôi.'),
    (2, 30): M('sô-cô-la', 'この チョコレートは おみやげです。', 'Sô-cô-la này là quà lưu niệm.'),
    (2, 31): M('cà phê', 'その コーヒーは だれの コーヒーですか。', 'Ly cà phê đó là của ai?'),
    (2, 32): M('quà lưu niệm, quà tặng', 'これは にほんの おみやげです。', 'Đây là quà lưu niệm của Nhật.'),
    (2, 33): M('tiếng Anh', 'これは えいごの ほんです。', 'Đây là sách tiếng Anh.'),
    (2, 34): M('tiếng Nhật', 'わたしは にほんごの がくせいです。', 'Tôi là học viên tiếng Nhật.'),
    (2, 35): M('hậu tố chỉ ngôn ngữ, đặt sau tên nước (ví dụ: にほんご: tiếng Nhật)'),
    (2, 36): M('cái gì', 'それは なんですか。', 'Đó là cái gì?', pos='từ nghi vấn'),
    (2, 37): M('như vậy, như thế', 'はい、そうです。', 'Vâng, đúng vậy.'),
    (2, 38): M('ừm..., ờ... (từ ngập ngừng khi nói)', 'あのう、これは なんですか。', 'Ừm, đây là cái gì vậy?'),
    (2, 39): M('hả? cái gì cơ? (thốt lên khi nghe điều bất ngờ)', 'えっ、そうですか。', 'Hả, vậy à?'),
    (2, 40): M('Xin mời. (dùng khi mời ai đó nhận vật gì)'),
    (2, 41): M('Cảm ơn (rất nhiều).'),
    (2, 42): M('Vậy à. / Ra là thế. (đáp lại khi nghe thông tin mới)'),
    (2, 43): M('Không phải. / Không đúng. (cách phủ định lịch sự)'),
    (2, 44): M('à! (thốt lên khi nhận ra điều gì)', 'あ、わたしの かぎです。', 'À, chìa khóa của tôi đây rồi.'),
    (2, 45): M('Từ nay mong được giúp đỡ. (câu chào khi bắt đầu ở nơi mới)'),
    (2, 46): M('Tôi cũng rất mong được giúp đỡ. (đáp lại lời chào よろしくおねがいします)'),
    (2, 47): M('thẻ điện thoại', 'これは テレホンカードです。', 'Đây là thẻ điện thoại.'),
    (2, 48): M('băng (cát-xét)', 'この テープは えいごの テープです。', 'Cuộn băng này là băng tiếng Anh.'),
    (2, 49): M('máy ghi âm', 'あれは テープレコーダーです。', 'Kia là máy ghi âm.'),
    (2, 50): M('xe hơi (cách nói trang trọng của くるま)',
              'あの じどうしゃは にほんの じどうしゃです。', 'Chiếc xe hơi kia là xe của Nhật.'),
    (2, 51): M('Đây chỉ là chút lòng thành. (nói khiêm tốn khi tặng quà)'),
    (2, 52): M('Cảm ơn nhé. (cách nói cảm ơn ngắn gọn, thân mật)'),
})

OV.update({
    # ================= BÀI 3 (第3課 — Nơi chốn, số đếm và giá cả) =================
    (3, 1): M('ở đây, chỗ này', 'ここは ロビーです。', 'Đây là sảnh.'),
    (3, 2): M('ở đó (chỗ gần người nghe)', 'そこは うけつけです。', 'Đó là quầy tiếp tân.'),
    (3, 3): M('ở đằng kia (chỗ xa cả hai người)', 'あそこは トイレです。', 'Đằng kia là nhà vệ sinh.'),
    (3, 4): M('ở đâu', 'トイレは どこですか。', 'Nhà vệ sinh ở đâu?', pos='đại từ nghi vấn'),
    (3, 5): M('phía này, chỗ này (cách nói lịch sự của ここ)', 'こちらは じむしょです。', 'Đây là văn phòng.'),
    (3, 6): M('phía đó (cách nói lịch sự của そこ)', 'そちらは かいぎしつですか。', 'Đó có phải là phòng họp không?'),
    (3, 7): M('phía đằng kia (cách nói lịch sự của あそこ)', 'あちらは エレベーターです。', 'Đằng kia là thang máy.'),
    (3, 8): M('phía nào, ở đâu (cách nói lịch sự của どこ)', 'おてあらいは どちらですか。',
              'Nhà vệ sinh ở đâu ạ?', pos='đại từ nghi vấn'),
    (3, 9): M('phòng học, lớp học', 'ここは きょうしつです。', 'Đây là phòng học.'),
    (3, 10): M('nhà ăn, căng-tin', 'しょくどうは あそこです。', 'Nhà ăn ở đằng kia.'),
    (3, 11): M('văn phòng', 'じむしょは どこですか。', 'Văn phòng ở đâu?'),
    (3, 12): M('phòng họp', 'かいぎしつは そこです。', 'Phòng họp ở đó.'),
    (3, 13): M('quầy tiếp tân, bàn lễ tân', 'うけつけは こちらです。', 'Quầy tiếp tân ở đây.'),
    (3, 14): M('sảnh, tiền sảnh', 'ロビーは どこですか。', 'Sảnh ở đâu?'),
    (3, 15): M('căn phòng', 'ここは わたしの へやです。', 'Đây là phòng của tôi.'),
    (3, 16): M('nhà vệ sinh (おてあらい là cách nói lịch sự)', 'トイレは あそこです。', 'Nhà vệ sinh ở đằng kia.'),
    (3, 17): M('cầu thang bộ', 'かいだんは どこですか。', 'Cầu thang bộ ở đâu?'),
    (3, 18): M('thang máy', 'エレベーターは そこです。', 'Thang máy ở đó.'),
    (3, 19): M('thang cuốn', 'エスカレーターは あちらです。', 'Thang cuốn ở phía đằng kia.'),
    (3, 20): M('máy bán hàng tự động', 'じどうはんばいきは そこです。', 'Máy bán hàng tự động ở đó.'),
    (3, 21): M('điện thoại; cuộc gọi điện thoại', 'でんわは あそこです。', 'Điện thoại ở đằng kia.'),
    (3, 22): M('đất nước, quê hương (おくに là cách nói lịch sự)', 'おくには どこですか。',
               'Quê hương của bạn ở đâu?'),
    (3, 23): M('công ty', 'かいしゃは あそこです。', 'Công ty ở đằng kia.'),
    (3, 24): M('nhà (của mình)', 'ここは わたしの うちです。', 'Đây là nhà của tôi.'),
    (3, 25): M('giày', 'この くつは わたしの くつです。', 'Đôi giày này là của tôi.'),
    (3, 26): M('cà vạt', 'この ネクタイは いくらですか。', 'Cái cà vạt này giá bao nhiêu?'),
    (3, 27): M('rượu vang', 'この ワインは フランスの ワインです。', 'Chai rượu vang này là rượu của Pháp.'),
    (3, 28): M('quầy hàng, khu bán hàng (trong trung tâm thương mại)',
               'うりばは ちかですか。', 'Quầy hàng ở tầng hầm phải không?'),
    (3, 29): M('tầng hầm', 'うりばは ちかです。', 'Quầy hàng ở tầng hầm.'),
    (3, 30): M('tầng (đếm tầng lầu; đọc là がい sau なん)'),
    (3, 31): M('tầng mấy', 'うりばは なんがいですか。', 'Quầy hàng ở tầng mấy?', pos='từ nghi vấn'),
    (3, 32): M('yên (đơn vị tiền tệ Nhật; dùng sau số tiền)'),
    (3, 33): M('bao nhiêu tiền', 'これは いくらですか。', 'Cái này bao nhiêu tiền?', pos='từ nghi vấn'),
    (3, 34): M('trăm', 'この ほんは ひゃくえんです。', 'Quyển sách này giá 100 yên.'),
    (3, 35): M('nghìn', 'この かばんは せんえんです。', 'Cái cặp này giá 1000 yên.'),
    (3, 36): M('vạn, mười nghìn', 'この とけいは いちまんです。', 'Chiếc đồng hồ này giá 10.000 yên.'),
    (3, 37): M('Xin lỗi. (dùng để xin lỗi hoặc gọi ai đó)'),
    (3, 38): M('Cảm ơn. (cách nói cảm ơn ngắn gọn, thân mật)'),
    (3, 39): M('Kính chào quý khách! (lời chào khi khách vào cửa hàng)'),
    (3, 40): M('Cho tôi xem ~. (dùng khi mua sắm)'),
    (3, 41): M('vậy thì, thế thì', 'じゃ、これを ください。', 'Vậy thì, cho tôi lấy cái này.'),
    (3, 42): M('Cho tôi ~. (dùng khi mua hàng, gọi món)'),
    (3, 43): M('Ý (nước Ý)'),
    (3, 44): M('Thụy Sĩ'),
    (3, 45): M('Pháp', 'たなかさんは フランスから きました。', 'Anh Tanaka đến từ Pháp.'),
    (3, 46): M('Jakarta (thủ đô Indonesia)'),
    (3, 47): M('Bangkok (thủ đô Thái Lan)'),
    (3, 48): M('Berlin (thủ đô Đức)'),
    (3, 49): M('Shin-Osaka (tên nhà ga ở Osaka)'),
    (3, 50): M('thuốc lá', 'それは たばこですか。', 'Đó có phải là thuốc lá không?'),
    (3, 51): M('(cách nói lịch sự của です)'),
    (3, 52): M('MT / Yonen / Akikkusu (tên công ty GIẢ ĐỊNH dùng trong bài học)'),
})


def main():
    src = json.load(open(SRC, encoding='utf-8'))
    by_lesson = {}
    for e in src:
        if e['lesson'] in (1, 2, 3):
            by_lesson.setdefault(e['lesson'], []).append(e)

    words = []
    seen = set()
    missing = []
    for L in LESSONS:
        ln = L['lessonNo']
        entries = sorted(by_lesson.get(ln, []), key=lambda e: e['index'])
        for e in entries:
            key = (ln, e['index'])
            ov = OV.get(key)
            if not ov or not ov['meaningVi']:
                missing.append(key)
                continue
            section = SECTION_MAP.get(e['kind'], 'REFERENCE')
            pos = ov['pos'] or POS_MAP.get(e['pos'], 'danh từ')
            if section == 'VOCAB' and (not ov['exampleJa'] or not ov['exampleVi']):
                missing.append(key + ('example',))
                continue
            dedup = (ln, e['kana'], section)
            if dedup in seen:
                print(f'CẢNH BÁO trùng lặp tuyệt đối, bỏ qua: bài {ln} index {e["index"]} ({e["kana"]})',
                      file=sys.stderr)
                continue
            seen.add(dedup)
            words.append({
                'lessonNo': ln,
                'section': section,
                'kana': e['kana'],
                'kanji': e['kanji'] if e['kanji'] else '',
                'romaji': e['romaji'] or '',
                'meaningVi': ov['meaningVi'],
                'pos': pos,
                'exampleJa': ov['exampleJa'],
                'exampleVi': ov['exampleVi'],
                'orderInLesson': e['index'],
                'audioFile': f'b{ln:02d}-{e["index"]:03d}.mp3',
            })

    if missing:
        print(f'LỖI: thiếu dữ liệu biên soạn tay cho {len(missing)} mục: {missing}', file=sys.stderr)
        sys.exit(1)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump({'version': 1, 'lessons': LESSONS, 'words': words}, open(OUT, 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)

    for L in LESSONS:
        ln = L['lessonNo']
        ws = [w for w in words if w['lessonNo'] == ln]
        secs = {}
        for w in ws:
            secs[w['section']] = secs.get(w['section'], 0) + 1
        print(f'Bài {ln}: {len(ws)} từ {secs}')
    print(f'-> {OUT}')


if __name__ == '__main__':
    main()
