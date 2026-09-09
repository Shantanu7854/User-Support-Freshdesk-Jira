-- =====================================================================
--  Seed data. Run AFTER 01_schema.sql.
--
--  The knowledge base is deliberately built with NEAR-MISS PAIRS:
--  articles that share vocabulary but describe opposite problems.
--  Keyword search cannot tell them apart; the reranking model can.
--  That contrast is what the demo is built to show.
-- =====================================================================

-- ---------------------------------------------------------------------
-- BILLING  — contains the key near-miss pair (#1 declined vs #2 twice)
-- ---------------------------------------------------------------------
insert into kb_articles (title, problem_text, resolution_text, category) values
('Payment declined at checkout',
 'My payment keeps getting declined when I try to upgrade my subscription. I have entered my card details several times and it says payment failed. The card works fine on other websites.',
 'A declined payment is returned by the card issuer, not by us. Check the card expiry date first, then confirm the billing address matches the one on file with your bank exactly, including postcode. International cards sometimes need online payments enabled by the bank. Once corrected, go to Settings > Billing > Retry payment. If it still fails, the bank decline code shown on the receipt will identify the reason.',
 'billing'),

('Charged twice for the same subscription',
 'I have been billed two times for my subscription this month. There are two identical charges on my statement on the same day for the same amount. I only have one account and one subscription.',
 'Duplicate charges are almost always a temporary authorisation hold sitting alongside the real charge, and the hold clears by itself in 5-7 business days. Open Settings > Billing > Invoices: if you see only ONE invoice, the second line on your statement is a hold and no money has left your account. If TWO invoices are listed, it is a genuine duplicate. Use Request refund on the second invoice and it is returned within 3 business days.',
 'billing'),

('Refund has not arrived yet',
 'I was told my refund was approved over a week ago but I still cannot see the money back in my account. The invoice in my dashboard already shows as refunded.',
 'Once a refund is issued on our side the invoice immediately shows Refunded, but the money moves on the card network schedule, which is 5-10 business days and depends on your bank. The refund is returned to the original card and cannot be redirected. If more than 10 business days have passed, quote the ARN reference on the refunded invoice to your bank and they can trace it.',
 'billing'),

('How do I change my billing cycle from monthly to annual?',
 'I want to switch my plan from monthly billing to annual billing to get the discount but I cannot find the option anywhere in settings.',
 'Go to Settings > Billing > Plan and choose Switch to annual. The change applies immediately: the unused portion of the current month is prorated and deducted from the annual price, so you are never charged twice for the same period. Annual billing gives roughly two months free versus paying monthly.',
 'billing'),

('Invoice is missing the company VAT number',
 'Our finance team rejected the invoice because it does not show our company VAT number and business address. We need this for our tax records.',
 'VAT details are added to invoices only when they are present on the account BEFORE the invoice is generated. Add them under Settings > Billing > Tax information. Existing invoices can be regenerated with the new details using Regenerate on each invoice, which reissues the PDF with the same invoice number.',
 'billing');

-- ---------------------------------------------------------------------
-- LOGIN / ACCESS  — near-miss pair (#6 reset loop vs #7 locked out)
-- ---------------------------------------------------------------------
insert into kb_articles (title, problem_text, resolution_text, category) values
('Password reset email never arrives',
 'I clicked forgot password and requested the reset link several times but the email never shows up in my inbox. I have waited more than an hour and checked spam.',
 'Reset emails are sent only to addresses that already exist on an account, and are silently dropped otherwise, so the most common cause is requesting the reset for a different address than the one you registered with. Try any alternative addresses you may have used. Also whitelist no-reply@example.com, since corporate mail filters frequently quarantine automated password mail. Reset links expire after 60 minutes and each new request invalidates the previous link, so use the most recent email only.',
 'login'),

('Account locked after too many login attempts',
 'My account is locked and shows a message saying too many failed sign in attempts. I know my password is correct but it will not let me try again at all.',
 'Accounts lock automatically for 30 minutes after five consecutive failed attempts. This is a timed lock and it clears itself; no action is needed and contacting support will not unlock it sooner. Wait the full 30 minutes without attempting to sign in, because each further attempt restarts the timer. If you are unsure of the password, use Forgot password instead, which clears the lock immediately.',
 'login'),

('Two-factor authentication codes are rejected',
 'The six digit codes from my authenticator app are always wrong when I try to sign in. The app is generating codes but the site says the code is invalid every time.',
 'Rejected TOTP codes are nearly always a clock drift problem: the authenticator generates codes from the device clock, and more than 30 seconds of drift invalidates them. Enable automatic date and time on the device, then in Google Authenticator use Settings > Time correction for codes > Sync now. If you cannot get in at all, each of your one-time backup codes works in place of a TOTP code.',
 'login'),

('Single sign-on redirect loop',
 'When I try to log in through our company SSO the page keeps bouncing between our identity provider and your login screen and never lands anywhere.',
 'A redirect loop means the SAML assertion is arriving without the email attribute we key accounts on. Ask your IdP administrator to confirm the NameID format is set to emailAddress and that an email attribute is mapped in the assertion. Clearing cookies for both domains is required after any change, because the stale session cookie will reproduce the loop even once the mapping is fixed.',
 'login');

-- ---------------------------------------------------------------------
-- DATA SYNC  — near-miss pair (#10 delayed vs #11 stuck/failed)
-- ---------------------------------------------------------------------
insert into kb_articles (title, problem_text, resolution_text, category) values
('Data sync is slower than usual',
 'My data is syncing but it is taking much longer than it normally does. Records do eventually show up but only after a long delay of an hour or more.',
 'Sync runs on a queue that is processed in order, so a large import placed ahead of your job delays everything behind it. Delays of up to two hours are expected behaviour during business hours and no data is lost. Settings > Sync > Queue position shows where your job currently sits. Scheduling recurring syncs outside 9am-5pm avoids the busy window entirely.',
 'sync'),

('Data sync stuck and not completing',
 'The sync has been showing as in progress for two days and has never finished. Nothing is updating at all and the progress indicator has not moved since it started.',
 'A sync that has not progressed for over 24 hours has failed rather than stalled, and it will not recover on its own. This is usually caused by a schema change on the source, such as a renamed or deleted column that the mapping still refers to. Open Settings > Sync > View error log for the exact failing field. Correct the field mapping and use Force restart sync; a forced restart safely resumes from the last confirmed checkpoint and does not duplicate records.',
 'sync'),

('Some records are missing after sync',
 'The sync completed successfully but a number of records that exist in the source system are not present here. The count does not match what I expect.',
 'Records are skipped rather than failed when they do not pass validation, which keeps one bad row from stopping the whole job. The skipped rows and the reason for each are listed in Settings > Sync > Skipped records. The two most common reasons are a missing required field and a duplicate unique key. Fix the rows at the source and the next sync picks them up automatically.',
 'sync');

-- ---------------------------------------------------------------------
-- EXPORT / REPORTS
-- ---------------------------------------------------------------------
insert into kb_articles (title, problem_text, resolution_text, category) values
('How do I export my data to CSV?',
 'I need to download all of my data as a CSV file so I can open it in Excel and share it with my team, but I cannot work out where the export option is.',
 'Open the Data tab, apply any filters you want reflected in the file, then use Export > CSV in the top right. The export honours your current filters and column layout, so arrange the view before exporting. Exports under 10,000 rows download immediately; anything larger is generated in the background and emailed to you as a link when ready. Choose Export > Excel instead if you need formatting and multiple sheets preserved.',
 'export'),

('CSV export opens with garbled characters in Excel',
 'When I open the exported CSV in Excel all the accented characters and symbols look wrong and appear as strange sequences of letters.',
 'The file is correct UTF-8; Excel on Windows just defaults to a legacy encoding when opening CSV directly. Rather than double-clicking the file, open Excel first and use Data > From Text/CSV, then set File Origin to 65001: Unicode (UTF-8). Google Sheets and LibreOffice detect the encoding automatically and show the file correctly with no configuration.',
 'export'),

('Scheduled report did not send',
 'My weekly scheduled report did not arrive by email this Monday. It has been arriving reliably every week before this and nothing was changed.',
 'Scheduled reports are skipped, and not retried, when the report returns zero rows, which most often happens when the filter references a date range that has moved out of scope. Check Settings > Reports > History, where a skipped run is logged with its reason. Enable Send even when empty on the schedule if you would rather receive an empty report than nothing.',
 'export'),

('Dashboard shows no data for the weekend',
 'Every Saturday and Sunday my dashboard appears completely empty even though I know activity happened. On weekdays it looks completely normal.',
 'The default dashboard date range is Last 5 business days, which excludes weekends by design; the data exists and is not lost. Switch the range selector to Last 7 days to include weekend activity, and use Save as default to make that your permanent view.',
 'reports');

-- ---------------------------------------------------------------------
-- NOTIFICATIONS / API / PERFORMANCE
-- ---------------------------------------------------------------------
insert into kb_articles (title, problem_text, resolution_text, category) values
('Not receiving any email notifications',
 'I have stopped getting notification emails from the system entirely. My colleagues on the same team are still receiving theirs normally.',
 'A single user losing mail while teammates still receive it points to a bounce suppression: after repeated hard bounces an address is suppressed automatically and all further mail to it is withheld. Settings > Notifications shows a Delivery suspended banner when this has happened, with a Reactivate button that clears the suppression. Ask your mail administrator to whitelist our sending domain to stop it recurring.',
 'notifications'),

('Webhook endpoint is not being called',
 'We configured a webhook for new events but our endpoint never receives anything. Our server logs show no incoming requests at all from your system.',
 'Webhook delivery is disabled automatically after 20 consecutive failures, and this is by far the most common cause of total silence. Settings > Webhooks shows the endpoint status and the last 50 delivery attempts with response codes. Endpoints must return a 2xx within 5 seconds; slow endpoints are recorded as failures even when they eventually succeed. Fix the endpoint, then use Send test event to re-enable delivery.',
 'api'),

('API returns 429 rate limit errors',
 'Our integration is receiving lots of 429 responses from the API during our nightly batch job. It works fine during the day with lighter traffic.',
 'The limit is 100 requests per minute per API key on Pro and 1000 on Enterprise, and it is enforced per key rather than per account. Every 429 response carries a Retry-After header giving the exact seconds to wait; honouring it with exponential backoff resolves this in almost all batch scenarios. Requesting several pages concurrently is the usual cause of a nightly job tripping the limit.',
 'api'),

('Application is slow to load',
 'The application takes a very long time to load for me, sometimes 30 seconds before anything appears on screen. It used to be fast.',
 'Sudden slowness for one user is usually a stale local cache rather than a server problem. Do a hard refresh first (Ctrl+Shift+R, or Cmd+Shift+R on Mac), and if that does not help clear site data for the domain in the browser settings. status.example.com confirms whether there is an active incident. Saved views with very wide date ranges also load slowly by nature; narrowing the range speeds them up considerably.',
 'performance'),

('Cannot invite new team members',
 'The invite button on the team page is greyed out and I am not able to add anyone new to our workspace.',
 'A greyed-out invite button means the workspace has reached its licensed seat count. Settings > Team shows seats used out of seats purchased. Either remove a deactivated member to free a seat, or add seats under Settings > Billing > Manage seats. Note that deactivated members continue to hold their seat until they are fully removed, which surprises most administrators.',
 'account');

-- =====================================================================
--  Demo personas. Each one is engineered to trigger a different branch.
-- =====================================================================
insert into app_users (email, full_name, plan, subscription_status, region) values
  ('alice@demo.com', 'Alice Chen',   'pro',        'active',   'us-east'),
  ('bob@demo.com',   'Bob Martinez', 'pro',        'past_due', 'eu-west'),
  ('carol@demo.com', 'Carol Okafor', 'enterprise', 'active',   'ap-south');

-- Alice: everything healthy. Her questions resolve from the KB or as explanations.
insert into app_orders (user_email, amount_cents, status, failure_code, created_at) values
  ('alice@demo.com', 4900, 'paid', null, now() - interval '3 days'),
  ('alice@demo.com', 4900, 'paid', null, now() - interval '33 days'),
  ('alice@demo.com', 4900, 'paid', null, now() - interval '63 days');

-- Bob: past_due with an expired card. This is the MCP DIAGNOSTIC story —
-- the answer to "why did my payment fail?" is sitting right here in the data.
insert into app_orders (user_email, amount_cents, status, failure_code, created_at) values
  ('bob@demo.com', 4900, 'failed', 'card_expired',       now() - interval '2 days'),
  ('bob@demo.com', 4900, 'failed', 'card_expired',       now() - interval '5 days'),
  ('bob@demo.com', 4900, 'failed', 'insufficient_funds', now() - interval '9 days'),
  ('bob@demo.com', 4900, 'paid',   null,                 now() - interval '35 days');

-- Carol: billing is fine, but her data_sync has failed repeatedly with a real
-- error. This is the ESCALATION story — no KB article covers it, and the error
-- text is what gets attached to the Jira issue for the developer.
insert into app_jobs (user_email, job_type, status, error_message, created_at) values
  ('carol@demo.com', 'data_sync', 'failed',
   'Column "customer_ref" not found in source schema (expected at position 4). Mapping references a column that no longer exists upstream.',
   now() - interval '2 days'),
  ('carol@demo.com', 'data_sync', 'failed',
   'Column "customer_ref" not found in source schema (expected at position 4). Mapping references a column that no longer exists upstream.',
   now() - interval '1 day'),
  ('carol@demo.com', 'data_sync', 'failed',
   'Column "customer_ref" not found in source schema (expected at position 4). Mapping references a column that no longer exists upstream.',
   now() - interval '6 hours'),
  ('carol@demo.com', 'export',    'success', null, now() - interval '4 days');

insert into app_jobs (user_email, job_type, status, error_message, created_at) values
  ('alice@demo.com', 'data_sync', 'success', null, now() - interval '1 day'),
  ('alice@demo.com', 'export',    'success', null, now() - interval '2 days'),
  ('bob@demo.com',   'data_sync', 'success', null, now() - interval '1 day');
