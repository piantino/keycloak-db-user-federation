SET TIMEZONE TO 'America/Sao_Paulo';

UPDATE db_user_users SET first_name = 'Venger', last_name = 'Wizard', ability = 'spells', updated = now() WHERE username = 'uni';

INSERT INTO db_user_users (username, email, email_verified, first_name, last_name, enabled)
    VALUES ('tiamat', 'tiamat@dragon.com', 'y', 'Tiamat', 'Dragon', false);


DELETE FROM db_user_roles WHERE username = 'hank';
INSERT INTO db_user_roles (username, name) VALUES ('hank', 'ex-leader');
UPDATE db_user_users SET updated = now() WHERE username = 'hank';

DELETE FROM db_user_roles WHERE username = 'master' AND name = 'role2';

insert into db_user_roles (username, name)
    VALUES ('master', 'role4');
insert into db_user_roles (username, name)
    VALUES ('master', 'role5');
insert into db_user_roles (username, name)
    VALUES ('master', 'role6');

UPDATE db_user_users SET updated = now() WHERE username = 'master';
