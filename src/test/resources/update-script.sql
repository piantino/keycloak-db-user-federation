SET TIMEZONE TO 'America/Sao_Paulo';

UPDATE users SET first_name = 'Venger', last_name = 'Wizard', ability = 'spells', updated = now() WHERE username = 'uni';

INSERT INTO users (username, email, email_verified, first_name, last_name, enabled)
    VALUES ('tiamat', 'tiamat@dragon.com', 'y', 'Tiamat', 'Dragon', false);


DELETE FROM roles WHERE username = 'hank';
INSERT INTO roles (username, name) VALUES ('hank', 'ex-leader');
UPDATE users SET updated = now() WHERE username = 'hank';

DELETE FROM roles WHERE username = 'master' AND name = 'role2';

insert into roles (username, name)
    VALUES ('master', 'role4');
insert into roles (username, name)
    VALUES ('master', 'role5');
insert into roles (username, name)
    VALUES ('master', 'role6');

UPDATE users SET updated = now() WHERE username = 'master';
