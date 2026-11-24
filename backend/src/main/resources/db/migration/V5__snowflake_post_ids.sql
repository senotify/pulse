-- Post ids now come from the application's Snowflake generator (time-ordered, not a guessable
-- 1, 2, 3 sequence). The identity default stays only for rows inserted by hand; existing small
-- ids sort before every Snowflake id, which matches their creation order.
COMMENT ON COLUMN posts.id IS 'Snowflake id: 41-bit ms since 2026-01-01 | 2-bit worker | 10-bit sequence';
