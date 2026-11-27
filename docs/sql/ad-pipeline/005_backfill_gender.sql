-- 005: 연령대·성별 값 정리. 여러 번 실행해도 결과가 같다 (이미 바뀐 행은 조건에 안 걸림).
UPDATE ad_impressions SET gender = 'male'    WHERE gender = 'M';
UPDATE ad_impressions SET gender = 'female'  WHERE gender = 'F';
UPDATE ad_impressions SET gender = 'unknown' WHERE gender IS NULL;
UPDATE ad_impressions SET age_group = 'unknown' WHERE age_group IS NULL;
UPDATE click_events   SET gender = 'male'    WHERE gender = 'M';
UPDATE click_events   SET gender = 'female'  WHERE gender = 'F';
UPDATE click_events   SET gender = 'unknown' WHERE gender IS NULL;
UPDATE click_events   SET age_group = 'unknown' WHERE age_group IS NULL;
