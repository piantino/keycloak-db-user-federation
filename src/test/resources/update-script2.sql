SET TIMEZONE TO 'America/Sao_Paulo';

UPDATE db_user_users SET first_name = 'Missing', enabled = false, ability = null WHERE username = 'master';
