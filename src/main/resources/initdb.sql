# DROP DATABASE IF EXISTS __DATABASE_NAME;
# USE sed -i.bak 's/pattern/result/g' initdb.sql to change database name.
CREATE DATABASE IF NOT EXISTS __DATABASE_NAME DEFAULT CHARACTER SET utf8;

USE __DATABASE_NAME;

CREATE TABLE IF NOT EXISTS t_currency (
	id INT NOT NULL AUTO_INCREMENT,
	name VARCHAR(32) NOT NULL UNIQUE,
	contract_address VARCHAR(64) NOT NULL UNIQUE comment 'crcl contract address',
	token_address VARCHAR(64) NOT NULL UNIQUE comment 'token contract address',
	decimal_digits INT NOT NULL,
	cross_chain BOOLEAN NOT NULL,
	minimum_withdraw_amount DECIMAL(32,18) NOT NULL,
	PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_product (
	id INT NOT NULL AUTO_INCREMENT,
	name VARCHAR(32) NOT NULL UNIQUE,
	base_currency_id INT NOT NULL,
	quote_currency_id INT NOT NULL,
	price_precision INT NOT NULL,
	amount_precision INT NOT NULL,
	funds_precision INT NOT NULL,
	min_order_amount DECIMAL(32,18) NOT NULL,
	max_order_amount DECIMAL(32,18) NOT NULL,
	min_order_funds DECIMAL(32,18) NOT NULL,
    disabled BOOLEAN NOT NULL DEFAULT false,
	instant_exchange BOOLEAN DEFAULT FALSE,
	base_product_id INT,
	quote_product_id INT,
	base_is_base_side BOOLEAN,
	quote_is_base_side BOOLEAN,
	PRIMARY KEY (id),
	FOREIGN KEY (base_currency_id) REFERENCES t_currency(id),
	FOREIGN KEY (quote_currency_id) REFERENCES t_currency(id),
	UNIQUE KEY uk_bcid_qcid (base_currency_id, quote_currency_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_dailylimit (
	id INT NOT NULL AUTO_INCREMENT,
	product_id INT NOT NULL,
	start_time TIME NOT NULL,
	end_time TIME NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY (product_id) REFERENCES t_product(id),
	KEY idx_product_id (product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_dailylimitrate (
	product_id INT NOT NULL,
	upper_limit_rate DECIMAL(32, 18) NOT NULL,
	lower_limit_rate DECIMAL(32, 18) NOT NULL,
	initial_price DECIMAL(32, 18) NOT NULL,
	PRIMARY KEY (product_id),
	FOREIGN KEY (product_id) REFERENCES t_product(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_user (
	id BIGINT NOT NULL AUTO_INCREMENT,
	name VARCHAR(64) NOT NULL UNIQUE,
	PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_account (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	currency VARCHAR(32) NOT NULL,
	hold DECIMAL(32,18) NOT NULL,
	available DECIMAL(32,18) NOT NULL,
	status VARCHAR(32) NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY (user_id) REFERENCES t_user(id),
	FOREIGN KEY (currency) REFERENCES t_currency(name),
	UNIQUE KEY uk_uid_currency (user_id, currency)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_order (
	id BIGINT NOT NULL AUTO_INCREMENT,
	product_id INT NOT NULL,
	user_id BIGINT NOT NULL,
	client_order_id VARCHAR(64),
	type VARCHAR(32) NOT NULL,
	side VARCHAR(32) NOT NULL,
	status VARCHAR(32) NOT NULL,
	phase TINYINT NOT NULL,
	phase_side TINYINT NOT NULL,
	price DECIMAL(32,18),
	amount DECIMAL(32,18),
	fee_address VARCHAR(64) NOT NULL,
	fee_rate_taker DOUBLE NOT NULL,
	fee_rate_maker DOUBLE NOT NULL,
	filled_amount DECIMAL(32,18),
	filled_funds DECIMAL(32,18),
	timestamp BIGINT NOT NULL,
	hash VARCHAR(128) NOT NULL UNIQUE,
	signature VARCHAR(256) NOT NULL,
	create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	FOREIGN KEY (product_id) REFERENCES t_product(id),
	FOREIGN KEY (user_id) REFERENCES t_user(id),
	UNIQUE KEY uk_uid_coid (user_id, client_order_id),
	KEY idx_uid_pid_ct (user_id, product_id, create_time),
	KEY idx_uid_ct (user_id, create_time),
	KEY idx_uid_pid_status (user_id, product_id, status),
	KEY idx_uid_status (user_id, status),
	KEY idx_uid_pid_phase_ct (user_id, product_id, phase, create_time),
	KEY idx_uid_phase_ct (user_id, phase, create_time),
	KEY idx_uid_pid_ps_ct (user_id, product_id, phase_side, create_time),
	KEY idx_uid_ps_ct (user_id, phase_side, create_time),
	KEY idx_status (status),
	KEY idx_pid_status (product_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_order_archive (
      id BIGINT NOT NULL AUTO_INCREMENT,
      product_id INT NOT NULL,
      user_id BIGINT NOT NULL,
      client_order_id VARCHAR(64),
      type VARCHAR(32) NOT NULL,
      side VARCHAR(32) NOT NULL,
      status VARCHAR(32) NOT NULL,
      phase TINYINT NOT NULL,
      phase_side TINYINT NOT NULL,
      price DECIMAL(32,18),
      amount DECIMAL(32,18),
      fee_address VARCHAR(64) NOT NULL,
      fee_rate_taker DOUBLE NOT NULL,
      fee_rate_maker DOUBLE NOT NULL,
      filled_amount DECIMAL(32,18),
      filled_funds DECIMAL(32,18),
      timestamp BIGINT NOT NULL,
      hash VARCHAR(128) NOT NULL UNIQUE,
      signature VARCHAR(256) NOT NULL,
      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      FOREIGN KEY (product_id) REFERENCES t_product(id),
      FOREIGN KEY (user_id) REFERENCES t_user(id),
#    UNIQUE KEY uk_uid_coid (user_id, client_order_id),
#    KEY idx_uid_pid_ct (user_id, product_id, create_time),
      KEY idx_uid_ct (user_id, create_time)
#  ,  KEY idx_uid_pid_status (user_id, product_id, status),
#    KEY idx_uid_status (user_id, status),
#    KEY idx_uid_pid_phase_ct (user_id, product_id, phase, create_time),
#    KEY idx_uid_phase_ct (user_id, phase, create_time),
#    KEY idx_uid_pid_ps_ct (user_id, product_id, phase_side, create_time),
#    KEY idx_uid_ps_ct (user_id, phase_side, create_time)
#   , KEY idx_status (status),
#    KEY idx_pid_status (product_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_order_prune (
	timestamp BIGINT NOT NULL,
	order_id BIGINT NOT NULL,
	PRIMARY KEY (timestamp, order_id),
	FOREIGN KEY (order_id) REFERENCES t_order(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_trade (
	id BIGINT NOT NULL AUTO_INCREMENT,
	product_id INT NOT NULL,
	taker_order_id BIGINT NOT NULL,
	maker_order_id BIGINT NOT NULL,
	price DECIMAL(32,18) NOT NULL,
	amount DECIMAL(32,18) NOT NULL,
	side VARCHAR(32) NOT NULL,
	taker_fee DECIMAL(32,18) NOT NULL,
	maker_fee DECIMAL(32,18) NOT NULL,
	status VARCHAR(32) NOT NULL,
	tx_hash VARCHAR(128),
	tx_nonce BIGINT NOT NULL DEFAULT 0,
	create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	FOREIGN KEY (product_id) REFERENCES t_product(id),
	FOREIGN KEY (taker_order_id) REFERENCES t_order(id),
	FOREIGN KEY (maker_order_id) REFERENCES t_order(id),
	UNIQUE KEY uk_toid_moid (taker_order_id, maker_order_id),
	KEY idx_status (status),
	KEY idx_nonce (tx_nonce)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_trade_archive (
      id BIGINT NOT NULL AUTO_INCREMENT,
      product_id INT NOT NULL,
      taker_order_id BIGINT NOT NULL,
      maker_order_id BIGINT NOT NULL,
      price DECIMAL(32,18) NOT NULL,
      amount DECIMAL(32,18) NOT NULL,
      side VARCHAR(32) NOT NULL,
      taker_fee DECIMAL(32,18) NOT NULL,
      maker_fee DECIMAL(32,18) NOT NULL,
      status VARCHAR(32) NOT NULL,
      tx_hash VARCHAR(128),
      tx_nonce BIGINT NOT NULL DEFAULT 0,
      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      FOREIGN KEY (product_id) REFERENCES t_product(id),
      FOREIGN KEY (taker_order_id) REFERENCES t_order_archive(id),
      FOREIGN KEY (maker_order_id) REFERENCES t_order_archive(id)
#   , UNIQUE KEY uk_toid_moid (taker_order_id, maker_order_id)
#   , KEY idx_status (status)
#   , KEY idx_nonce (tx_nonce)
    , KEY idx_maker_oid (maker_order_id) # added
    , KEY idx_taker_oid (taker_order_id) # added
    , KEY idx_create_time (create_time) # added
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_trade_order_map (
	order_id BIGINT NOT NULL,
	trade_id BIGINT NOT NULL,
	FOREIGN KEY (trade_id) REFERENCES t_trade(id),
	UNIQUE KEY uk_oid_tid (order_id, trade_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_trade_user_map (
	user_id BIGINT NOT NULL,
	product_id BIGINT NOT NULL,
	create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	trade_id BIGINT NOT NULL,
	FOREIGN KEY (trade_id) REFERENCES t_trade(id),
	UNIQUE KEY uk_uid_pid_ct_tid (user_id, product_id, create_time, trade_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_order_cancel (
	id BIGINT NOT NULL AUTO_INCREMENT,
	order_id BIGINT NOT NULL UNIQUE,
	reason VARCHAR(32) NOT NULL,
	timestamp BIGINT NOT NULL,
	signature VARCHAR(256) NOT NULL,
	status VARCHAR(32) NOT NULL,
	tx_hash VARCHAR(128),
	tx_nonce BIGINT NOT NULL DEFAULT 0,
	create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	FOREIGN KEY (order_id) REFERENCES t_order(id) ON DELETE CASCADE,
	KEY idx_status (status),
	KEY idx_nonce (tx_nonce)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_tick (
	id BIGINT NOT NULL AUTO_INCREMENT,
	product_id INT NOT NULL,
	granularity INT NOT NULL,
	open DECIMAL(32,18) NOT NULL,
	high DECIMAL(32,18) NOT NULL,
	low DECIMAL(32,18) NOT NULL,
	close DECIMAL(32,18) NOT NULL,
	base_currency_volume DECIMAL(32,18) NOT NULL,
	quote_currency_volume DECIMAL(32,18) NOT NULL,
	count INT NOT NULL,
	create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
	FOREIGN KEY (product_id) REFERENCES t_product(id),
	UNIQUE KEY uk_pid_g_ct (product_id, granularity, create_time)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_config (
	name VARCHAR(255) NOT NULL UNIQUE,
	value VARCHAR(255) NOT NULL
) ENGINE=InnoDB;
insert ignore into t_config value ('t_order_archive','t_order_archive');
insert ignore into t_config value ('t_trade_archive','t_trade_archive');

CREATE TABLE IF NOT EXISTS t_deposit(
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_address VARCHAR(64) NOT NULL,
	currency VARCHAR(32) NOT NULL,
	amount DECIMAL(32,18) NOT NULL,
	tx_sender VARCHAR(64) NOT NULL,
	tx_hash VARCHAR(128) NOT NULL,
	create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	FOREIGN KEY (user_address) REFERENCES t_user(name),
	FOREIGN KEY (currency) REFERENCES t_currency(name),
	KEY idx_u_c (user_address, currency),
	KEY idx_tx_hash (tx_hash)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_withdraw (
	id BIGINT NOT NULL AUTO_INCREMENT,
	type VARCHAR(32) NOT NULL,
	user_address VARCHAR(64) NOT NULL,
	currency VARCHAR(32) NOT NULL,
	amount DECIMAL(32,18),
	destination VARCHAR(64),
	burn BOOLEAN NOT NULL,
	relay_contract VARCHAR(64),
	fee DECIMAL(32,18) NOT NULL DEFAULT 0,
	timestamp BIGINT,
	hash VARCHAR(128) UNIQUE,
	signature VARCHAR(256),
	status VARCHAR(32) NOT NULL,
	tx_hash VARCHAR(128),
	tx_nonce BIGINT NOT NULL DEFAULT 0,
	create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	FOREIGN KEY (user_address) REFERENCES t_user(name),
	FOREIGN KEY (currency) REFERENCES t_currency(name),
	KEY idx_u_c (user_address, currency),
	KEY idx_status (status),
	KEY idx_nonce (tx_nonce)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS t_transfer (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_address VARCHAR(64) NOT NULL,
	currency VARCHAR(32) NOT NULL,
	recipients VARCHAR(2048) NOT NULL,
	timestamp BIGINT NOT NULL,
	hash VARCHAR(128) UNIQUE,
	signature VARCHAR(256) NOT NULL,
	status VARCHAR(32) NOT NULL,
	tx_hash VARCHAR(128),
	tx_nonce BIGINT NOT NULL DEFAULT 0,
	create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	FOREIGN KEY (user_address) REFERENCES t_user(name),
	FOREIGN KEY (currency) REFERENCES t_currency(name),
	KEY idx_u_c (user_address, currency),
	KEY idx_status (status),
	KEY idx_nonce (tx_nonce)
) ENGINE=InnoDB;

create table if not exists t_qps_limit
(
    id     int auto_increment        primary key,
    ip_url varchar(255) not null unique ,
    rate   double       not null
)ENGINE=InnoDB, comment 'qps limit conf';