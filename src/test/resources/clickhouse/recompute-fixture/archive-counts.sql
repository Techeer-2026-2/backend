SELECT uniqExact(_path) AS file_count, count() AS line_count, uniqExact(notification_id) AS unique_count
FROM s3({{s3_url}}, NOSIGN, 'JSONEachRow', 'notification_id String')
SETTINGS s3_throw_on_zero_files_match = 0
