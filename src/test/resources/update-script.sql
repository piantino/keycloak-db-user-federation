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

-- creating two group
insert into db_group_groups (gid, gid_parent, name, attr1, attr2, attr3) values (324,     32,'third group 2-4'    , 'tg attr1 2-4'    , 'tg attr2 2-4'    , 'tg attr3 2-4');
insert into db_group_groups (gid, gid_parent, name, attr1, attr2, attr3) values (325,     32,'third group 2-5'    , 'tg attr1 2-5'    , 'tg attr2 2-5'    , 'tg attr3 2-5');

-- updating one group
update db_group_groups set name = 'third group 3-1 updated', attr1 = 'tg attr1 3-1 updated', attr2='tg attr2 3-1 updated', attr3='tg attr3 3-1 updated' where gid = 331;
update db_group_groups set gid_parent= 331 where gid= 33111;

-- removing one group
delete from db_group_groups where gid = 3311;


-- changing all groups for one user
delete from db_user_groups where username = 'hank';
insert into db_user_groups (username, gid) values ('hank', 23);
insert into db_user_groups (username, gid) values ('hank', 24);

-- indirecting delete group for user
-- delete from db_user_groups where username = 'sheila' and gid=3311;

-- add another group to user
insert into db_user_groups (username, gid) values ('uni', 324);

-- removing all groups from user
delete from db_user_groups where username = 'master';
