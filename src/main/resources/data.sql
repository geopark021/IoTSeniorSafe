/* 1. 부서(DEPT) 샘플 ---------------------------------------------- */
INSERT INTO dept (dept_id, name)
VALUES (10, '안전관리팀')
    ON DUPLICATE KEY UPDATE name = VALUES(name);

/* 2. 담당자(MANAGER) 샘플 ------------------------------------------ */
INSERT INTO manager (manager_id, dept_id, name, contact_number)
VALUES (1001, 10, '홍길동', '02-1234-5678')
    ON DUPLICATE KEY UPDATE name = VALUES(name);

/* 3. 가구(HOUSEHOLD) 샘플 ----------------------------------------- */
INSERT INTO household (household_id, manager_id, name, contact_number, address)
VALUES (1234567, 1001, '김철수', '010-9876-4321', '서울특별시 용산구 한강대로 3길 15')
    ON DUPLICATE KEY UPDATE name = VALUES(name);

/* 4. 신고(REPORT) 더미 3건 ---------------------------------------- */
INSERT INTO report (manager_id, household_id, status_code,
                    created_at, updated_at, agency_name)
VALUES
-- (High-risk, 방금 접수)
(1001, 1234567, 0,
 NOW(), NOW(), '서울복지재단'),
-- (처리 중 : status_code 1)
(1001, 1234567, 1,
 DATE_SUB(NOW(), INTERVAL 3 HOUR),
 DATE_SUB(NOW(), INTERVAL 2 HOUR),
 '용산구청 사회복지과'),
-- (처리 완료 : status_code 2)
(1001, 1234567, 2,
 DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 22 HOUR),
 '서울남부경찰서');
