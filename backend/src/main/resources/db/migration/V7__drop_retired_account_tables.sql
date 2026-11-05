-- Drop the retired email/password/OTP account model created by V2 and V3.
--
-- V4 replaced accounts with administrator-granted device licenses and kept
-- these objects in place, unread by any code path since. All they can still
-- hold is personal data from the old sign-up flow: email addresses, password
-- hashes, session token hashes, and pending verification codes. Nothing needs
-- that data, so it should not be kept. Back the database up before deploying
-- this migration if that data must be preserved anywhere.
--
-- Order matters: the scan_history foreign key and index go before the column
-- they cover, and auth_session goes before the user_account table it
-- references. IF EXISTS keeps a baselined database that never had them safe.
DROP INDEX IF EXISTS scan_history_owner_analyzed_at_idx;
ALTER TABLE scan_history DROP CONSTRAINT IF EXISTS scan_history_owner_user_fk;
ALTER TABLE scan_history DROP COLUMN IF EXISTS owner_user_id;

DROP TABLE IF EXISTS auth_session;
DROP TABLE IF EXISTS auth_registration_verification;
DROP TABLE IF EXISTS user_account;
