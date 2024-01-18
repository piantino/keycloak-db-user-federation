SET TIMEZONE TO 'America/Sao_Paulo';

UPDATE users SET first_name = 'Missing', enabled = false WHERE username = 'master';
