SELECT coalesce(sum(cnt), 0) FROM {{facts}} WHERE toDate(minute) = {{date}}
