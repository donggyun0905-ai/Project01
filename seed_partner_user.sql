-- PARTNER 16개 (공급처 6 + 거래처 10), APP_USER 7명 (ADMIN 1 + STAFF 6), USER_WAREHOUSE 배정
-- 테스트 비밀번호는 전부 '1234' (PasswordUtil.hash()로 SHA-256 해시한 값)
USE dmart;

INSERT INTO PARTNER (partner_id, name, type, contact) VALUES
(1, '신선냉장유통(주)', 'SUPPLIER', '02-1544-2201'),
(2, '한아름냉동식품(주)', 'SUPPLIER', '02-1544-2202'),
(3, '미소베이커리원료(주)', 'SUPPLIER', '02-1544-2203'),
(4, '생활공감유통(주)', 'SUPPLIER', '02-1544-2204'),
(5, '오피스원 문구주방(주)', 'SUPPLIER', '02-1544-2205'),
(6, '키즈앤테크(주)', 'SUPPLIER', '02-1544-2206'),
(7, '이마트 성수점', 'CUSTOMER', '02-2000-1001'),
(8, '롯데마트 잠실점', 'CUSTOMER', '02-2000-1002'),
(9, '홈플러스 강서점', 'CUSTOMER', '02-2000-1003'),
(10, 'GS25 물류센터', 'CUSTOMER', '02-2000-1004'),
(11, 'CU 편의점 유통', 'CUSTOMER', '02-2000-1005'),
(12, '쿠팡 풀필먼트', 'CUSTOMER', '02-2000-1006'),
(13, '마켓컬리', 'CUSTOMER', '02-2000-1007'),
(14, '세븐일레븐 유통', 'CUSTOMER', '02-2000-1008'),
(15, '노브랜드 화곡점', 'CUSTOMER', '02-2000-1009'),
(16, '코스트코 양재점', 'CUSTOMER', '02-2000-1010');

-- 비밀번호 = '1234' (SHA-256 : PasswordUtil.hash("1234")와 동일한 값)
INSERT INTO APP_USER (user_id, username, password, name, role) VALUES
(1, 'admin', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', '관리자', 'ADMIN'),
(2, 'staff1', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', '김민준', 'STAFF'),
(3, 'staff2', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', '이서연', 'STAFF'),
(4, 'staff3', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', '박도윤', 'STAFF'),
(5, 'staff4', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', '최지우', 'STAFF'),
(6, 'staff5', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', '정하윤', 'STAFF'),
(7, 'staff6', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', '강시우', 'STAFF');

-- ADMIN(user_id=1)은 배정 없이 전체 접근이라 USER_WAREHOUSE 레코드 없음
-- staff1~5는 창고 2개씩, staff6은 1/10번 창고를 겸임 담당 (N:M 관계 시연용)
INSERT INTO USER_WAREHOUSE (user_id, warehouse_id) VALUES
(2, 1), (2, 2),
(3, 3), (3, 4),
(4, 5), (4, 6),
(5, 7), (5, 8),
(6, 9), (6, 10),
(7, 1), (7, 10);
