SET TIMEZONE TO 'America/Sao_Paulo';

UPDATE db_user_users SET first_name = 'Missing', enabled = false WHERE username = 'master';
