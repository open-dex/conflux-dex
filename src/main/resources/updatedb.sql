USE conflux_dex;
-- 2020.12.09
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

-- Changes since Oceanus release on 2020/09
create table if not exists t_qps_limit
(
    id     int auto_increment        primary key,
    ip_url varchar(255) null,
    rate   double       not null,
    constraint t_ip_url_uindex
        unique (ip_url)
)ENGINE=InnoDB,
    comment 'qps limit conf';


