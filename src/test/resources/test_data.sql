USE conflux_dex;

INSERT INTO t_currency (name, contract_address, token_address, decimal_digits, cross_chain, minimum_withdraw_amount) VALUES
	('BTC', '0x88c86e6404ad54761135e4276898345debe11ddb', '0x8b5ba4ab6308cd9004856a18643aae4a4824d983', 18, 1, 0),
	('ETH', '0x823343c69d198ce07824ace19c11f2044ac391b9', '0x8c56194f7fb4f681ffe373237f8a4ef56e734fb1', 18, 1, 0),
	('USDT', '0x89c190016c485e91b11e60b995d6eeaffdc7cc18', '0x843411a9b22738d8adcdcecdf6066a4d2605c2ba', 18, 1, 0),
	('EOS', '0x87afac8e8a9cd7ab01f7249c63817ce1e46fef17', '0x81e417bc14b0f4c300ec8dd03c493c3c28a1d732', 18, 0, 0),
	('CNY', '0x89ccf8a09592518ffaeaae53371afdf165b0bd05', '0x88ca23cd98fd28669bd7c72d4eb031c84757e429', 18, 0, 0);

INSERT INTO t_product (name, base_currency_id, quote_currency_id, price_precision, amount_precision, funds_precision, min_order_amount, max_order_amount, min_order_funds) VALUES
	('BTC-USDT', 1, 3, 2, 6, 8, 0.000001, 99999999.999999, 0.00000001),
	('ETH-USDT', 2, 3, 2, 4, 8, 0.0001, 99999999.9999, 0.00000001),
	('EOS-CNY', 4, 5, 4, 4, 8, 0.0001, 99999999.9999, 0.00000001);
