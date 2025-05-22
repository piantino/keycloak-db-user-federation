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

Insert into db_group_groups (gid, gid_parent, name, lot_numloc, lot_nome, lot_sigla) values (5700, 5585, 'KC', '23527','DTI/DSGA/SAS - KEYCLOAK','DTI/DSGA/SAS/KC');
Insert into db_group_groups (gid, gid_parent, name, lot_numloc, lot_nome, lot_sigla) values (5701, 5585, 'K8S','23527','DTI/DSGA/SAS - KUBERNETES','DTI/DSGA/SAS/K8S');

DELETE FROM db_group_groups WHERE gid = 5589;
DELETE FROM db_group_groups WHERE gid = 5600;
DELETE FROM db_group_groups WHERE gid = 5579;

UPDATE db_group_groups SET name = 'DTI/DSGA/SSM2'  WHERE gid = 599;


delete from db_user_groups where username='hank' and gid = 5585;
insert into db_user_groups (username, gid) VALUES ('hank', 5594);

delete from db_user_groups where username='sheila';
insert into db_user_groups (username, gid) VALUES ('sheila', 5593);

insert into db_user_groups (username, gid) VALUES ('uni', 5585);
insert into db_user_groups (username, gid) VALUES ('uni', 5700);
insert into db_user_groups (username, gid) VALUES ('uni', 5701);