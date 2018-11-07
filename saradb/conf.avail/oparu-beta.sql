CREATE TEMP TABLE args(basedir text);
INSERT INTO args (basedir) VALUES (:'basedir');

DO $$

DECLARE base_dir text := (SELECT basedir from args LIMIT 1);

DECLARE oparu_logo oid := lo_import(base_dir || '/dspace.svg');

DECLARE oparu_demo text     := 'https://oparu-beta.sara-service.org';

DECLARE demo_dspace_help text := 'Your publication has been created in the Institutional Repository of Demo University (IRDU). Please login, click "Resume" and submit the publication. You can edit metadata if necessary. Your submission will then be reviewed by the IRDU team, and you will be notified as soon as it has been approved.';

DECLARE userhint text := 'Please use demo-user@sara-service.org to submit. You can then log into IRDU as demo-user@sara-service.org using password "SaraTest" to finish your submission. Note that it will not be approved automatically!';

DECLARE rRef UUID;

BEGIN

-- Stefan's OPARU Stand-In in bwCloud 
INSERT INTO repository(display_name, adapter, url, contact_email, help, enabled, logo_url, user_hint) VALUES
	('Institutional Repository of Demo University (IRDU)', 'DSpace_v6', oparu_demo || '/xmlui',
		'project-sara+oparu-beta@uni-konstanz.de', demo_dspace_help, TRUE,
		'data:image/svg+xml;base64,' || encode(lo_get(oparu_logo), 'base64'), userhint)
	RETURNING uuid INTO rRef;
INSERT INTO repository_params(id, param, value) VALUES
	(rRef, 'rest_user', 'project-sara@uni-konstanz.de'),
	(rRef, 'rest_pwd', '__OPARUBETA_PASSWORD__'),
	(rRef, 'rest_api_endpoint', oparu_demo || '/rest'),
	(rRef, 'sword_user', 'project-sara@uni-konstanz.de'),
	(rRef, 'sword_pwd', '__OPARUBETA_PASSWORD__'),
	(rRef, 'sword_api_endpoint', oparu_demo || '/swordv2'),
	(rRef, 'deposit_type', 'workspace'),
        (rRef, 'check_license', 'false'),
	(rRef, 'publication_type', 'Software');

-- erase the temporary large objects
PERFORM lo_unlink(oparu_logo);

END $$;
