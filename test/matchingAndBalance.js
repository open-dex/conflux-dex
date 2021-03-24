const { expect } = require('chai');
const cfx_sdk = require('js-conflux-sdk');
const http = require('./util/http.js');
const Order = require('./util/dex_order.js');
const Config = require('./util/config.js');
const Dex = require('./util/dex.js');
const { Account } = require('js-conflux-sdk');
const util = require('util');
const Mainnet = require('./util/mainnet.js');
const JSBI = require('./WrapJSBI');
const BigNumber = require('bignumber.js');

describe('Core matching & balance sync on chain', () => {
    let timeout = 0;

    async function wait_confirm() {
        await Mainnet.wait_epoch(await Config.get_cfx_confirm_epochs() * 2);
        await Mainnet.wait_ms(5000);
    }

    async function balance_equal(balance, currency, address) {
        let crcl_balance = await Mainnet.get_crcl_balance(currency, address);

        let res = await Dex.query_currency(currency);
        let digits = res.decimalDigits;
        balance = balance.times(BigNumber(10).pow(digits));

        return balance.toFixed(0) === crcl_balance.toString();
    }

    // account
    let you = Account.random();
    let other = Account.random();

    before(async function() {
        timeout = await Config.get_timeout();
        this.timeout(timeout);

        BigNumber.set({ DECIMAL_PLACES: 20 });

        await Dex.pause_cancel_resume();

        console.log('account you: ' + you.address);
        console.log('account other: ' + other.address);

        // check fake deposit
        await Dex.query_balance(you.address, 'btc');
        await Dex.query_balance(other.address, 'btc');
    });

    afterEach(async() => {
        await Dex.pause_cancel_resume();
    });

    describe('Place limit order, total filled', function() {
        var tests_1 = [
        {
            other_order: {
                address: other.address,
                product: 'BTC-USDT',
                amount: BigNumber('1'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Buy',
                feeAddress: other.address,
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_order: {
                address: you.address,
                product: 'BTC-USDT',
                amount: BigNumber('1'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Sell',
                feeAddress: you.address,
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_delta1: BigNumber('-1'),
            your_delta2: BigNumber('6123.4')
        }
        ];

        tests_1.forEach(function(test) {
            let o1 = test.other_order;
            let o2 = test.your_order;
            let desc = util.format('other %s %d %s @ %d, you %s %d %s @ %d', o1.side, o1.amount, o1.product, o1.price, o2.side, o2.amount, o2.product, o2.price);

            it(desc, async function() {
                this.timeout(timeout);

                let pair = o2.product.split('-');
                let m1 = pair[0];
                let m2 = pair[1];
                let your_m1 = await Dex.query_balance(you.address, m1);
                let your_m2 = await Dex.query_balance(you.address, m2);

                await Order.place_order(o1, other.privateKey);
                await Order.place_order(o2, you.privateKey);

                await wait_confirm();

                let your_m1_av = BigNumber(your_m1.availableString).plus(test.your_delta1);
                let your_m2_av = BigNumber(your_m2.availableString).plus(test.your_delta2);

                expect(await balance_equal(your_m1_av, m1, you.address)).to.equal(true);
                expect(await balance_equal(your_m2_av, m2, you.address)).to.equal(true);
            })
        });
    });

    describe('Place limit order, partial filled => wait for someone placing order => total filled', function() {
        let test_1 = [
        {
            other_order_1: {
                address: other.address,
                product: 'BTC-USDT',
                amount: BigNumber('0.4'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Buy',
                feeAddress: other.address,
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_order: {
                address: you.address,
                product: 'BTC-USDT',
                amount: BigNumber('1'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Sell',
                feeAddress: you.address,
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            other_order_2: {
                address: other.address,
                product: 'BTC-USDT',
                amount: BigNumber('0.6'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Buy',
                feeAddress: other.address,
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_delta1: BigNumber('-1'),
            your_delta2: BigNumber('6123.4'),
        }
        ];

        test_1.forEach(function(test) {
            let o1 = test.other_order_1;
            let o2 = test.your_order;
            let o3 = test.other_order_2;
            let desc = util.format('other %s %d %s @ %d, you %s %d %s @ %d, other %s %d %s @ %d', o1.side, o1.amount, o1.product, o1.price, o2.side, o2.amount, o2.product, o2.price, o3.side, o3.amount, o3.product, o3.price);

            it(desc, async function() {
                this.timeout(timeout);

                let pair = o2.product.split('-');
                let m1 = pair[0];
                let m2 = pair[1];
                let your_m1 = await Dex.query_balance(you.address, m1);
                let your_m2 = await Dex.query_balance(you.address, m2);

                await Order.place_order(o1, other.privateKey);
                await Order.place_order(o2, you.privateKey);
                await Order.place_order(o3, other.privateKey);

                await wait_confirm();

                let your_m1_av = BigNumber(your_m1.availableString).plus(test.your_delta1);
                let your_m2_av = BigNumber(your_m2.availableString).plus(test.your_delta2);

                expect(await balance_equal(your_m1_av, m1, you.address)).to.equal(true);
                expect(await balance_equal(your_m2_av, m2, you.address)).to.equal(true);
            });
        });
    });

    describe('Place limit order, partial filled => cancel order => canceled', function() {
        let test_1 = [
        {
            other_order_1: {
                address: other.address,
                product: 'BTC-USDT',
                amount: BigNumber('0.4'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Buy',
                feeAddress: other.address,
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_order: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('1'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Sell',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            other_order_2: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('0.6'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Buy',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_delta1: BigNumber('-0.4'),
            your_delta2: BigNumber('0.4').times(BigNumber('6123.4')),
        }
        ];

        test_1.forEach(function(test) {
            beforeEach(() => {
                test.other_order_1.address = other.address;
                test.other_order_1.feeAddress = other.address;
                test.your_order.address = you.address;
                test.your_order.feeAddress = you.address;
                test.other_order_2.address = other.address;
                test.other_order_2.feeAddress = other.address;
                });

            let o1 = test.other_order_1;
            let o2 = test.your_order;
            let o3 = test.other_order_2;
            let desc = util.format('other %s %d %s @ %d, you %s %d %s @ %d, you cancel, other %s %d %s @ %d', o1.side, o1.amount, o1.product, o1.price, o2.side, o2.amount, o2.product, o2.price, o3.side, o3.amount, o3.product, o3.price);

            it(desc, async function() {
                this.timeout(timeout);

                let pair = o2.product.split('-');
                let m1 = pair[0];
                let m2 = pair[1];
                let your_m1 = await Dex.query_balance(you.address, m1);
                let your_m2 = await Dex.query_balance(you.address, m2);

                await Order.place_order(o1, other.privateKey);

                let id = await Order.place_order(o2, you.privateKey);
                await Order.cancel_order(id, o2, you.privateKey);

                await Order.place_order(o3, other.privateKey);

                await wait_confirm();

                let your_m1_av = BigNumber(your_m1.availableString).plus(test.your_delta1);
                let your_m2_av = BigNumber(your_m2.availableString).plus(test.your_delta2);

                expect(await balance_equal(your_m1_av, m1, you.address)).to.equal(true);
                expect(await balance_equal(your_m2_av, m2, you.address)).to.equal(true);
            });
        });
    });

    describe('Place limit order, no filled => cancel => canceled', function() {
        let test_1 = [
        {
            your_order: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('1'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Sell',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            other_order: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('0.6'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Buy',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_delta1: BigNumber('0'),
            your_delta2: BigNumber('0'),
        }
        ];

        test_1.forEach(function(test) {
            beforeEach(() => {
                test.your_order.address = you.address;
                test.your_order.feeAddress = you.address;
                test.other_order.address = other.address;
                test.other_order.feeAddress = other.address;
            });

            let o1 = test.your_order;
            let o2 = test.other_order;
            let desc = util.format('you %s %d %s @ %d, you cancel, other %s %d %s @ %d', o1.side, o1.amount, o1.product, o1.price, o2.side, o2.amount, o2.product, o2.price);

            it(desc, async function() {
                this.timeout(timeout);

                let pair = o1.product.split('-');
                let m1 = pair[0];
                let m2 = pair[1];
                let your_m1 = await Dex.query_balance(you.address, m1);
                let your_m2 = await Dex.query_balance(you.address, m2);

                let id = await Order.place_order(o1, you.privateKey);
                await Order.cancel_order(id, o1, you.privateKey);

                await Order.place_order(o2, other.privateKey);

                await wait_confirm();

                let your_m1_av = BigNumber(your_m1.availableString).plus(test.your_delta1);
                let your_m2_av = BigNumber(your_m2.availableString).plus(test.your_delta2);

                expect(await balance_equal(your_m1_av, m1, you.address)).to.equal(true);
                expect(await balance_equal(your_m2_av, m2, you.address)).to.equal(true);
            });
        });
    });

    describe('Place market order, total filled', function() {
        var tests_1 = [
        {
            other_order: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('1'),
                price: BigNumber('3'),
                type: 'Limit',
                side: 'Sell',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_order: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('1'),
                price: BigNumber('0'),
                type: 'Market',
                side: 'Buy',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_delta1: BigNumber('1').dividedBy(BigNumber('3')),
            your_delta2: BigNumber('-1').plus(BigNumber('10').pow(BigNumber('-18')))
        }
        ];

        tests_1.forEach(function(test) {
            beforeEach(() => {
                test.other_order.address = other.address;
                test.other_order.feeAddress = other.address;
                test.your_order.address = you.address;
                test.your_order.feeAddress = you.address;
            });

            let o1 = test.other_order;
            let o2 = test.your_order;
            let desc = util.format('other %s %d %s @ %d, you %s %d %s @ market', o1.side, o1.amount, o1.product, o1.price, o2.side, o2.amount, o2.product);

            it(desc, async function() {
                this.timeout(timeout);

                let pair = o2.product.split('-');
                let m1 = pair[0];
                let m2 = pair[1];
                let your_m1 = await Dex.query_balance(you.address, m1);
                let your_m2 = await Dex.query_balance(you.address, m2);

                await Order.place_order(o1, other.privateKey);
                await Order.place_order(o2, you.privateKey);

                await wait_confirm();

                let your_m1_av = BigNumber(your_m1.availableString).plus(test.your_delta1);
                let your_m2_av = BigNumber(your_m2.availableString).plus(test.your_delta2);

                expect(await balance_equal(your_m1_av, m1, you.address)).to.equal(true);
                expect(await balance_equal(your_m2_av, m2, you.address)).to.equal(true);
            })
        });
    });

    describe('Place market order, partial filled, auto cancelled by dex => canceled', function() {
        let test_1 = [
        {
            other_order_1: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('0.4'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Buy',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_order: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('1'),
                price: BigNumber('0'),
                type: 'Market',
                side: 'Sell',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            other_order_2: {
                address: '',
                product: 'BTC-USDT',
                amount: BigNumber('0.6'),
                price: BigNumber('6123.4'),
                type: 'Limit',
                side: 'Buy',
                feeAddress: '',
                feeRateMaker: BigNumber('0'),
                feeRateTaker: BigNumber('0.01')
            },
            your_delta1: BigNumber('-0.4'),
            your_delta2: BigNumber('0.4').times(BigNumber('6123.4')),
        }
        ];

        test_1.forEach(function(test) {
            beforeEach(() => {
                test.other_order_1.address = other.address;
                test.other_order_1.feeAddress = other.address;
                test.your_order.address = you.address;
                test.your_order.feeAddress = you.address;
                test.other_order_2.address = other.address;
                test.other_order_2.feeAddress = other.address;
            });

            let o1 = test.other_order_1;
            let o2 = test.your_order;
            let o3 = test.other_order_2;
            let desc = util.format('other %s %d %s @ %d, you %s %d %s @ market, other %s %d %s @ %d', o1.side, o1.amount, o1.product, o1.price, o2.side, o2.amount, o2.product, o3.side, o3.amount, o3.product, o3.price);

            it(desc, async function() {
                this.timeout(timeout);

                let pair = o2.product.split('-');
                let m1 = pair[0];
                let m2 = pair[1];
                let your_m1 = await Dex.query_balance(you.address, m1);
                let your_m2 = await Dex.query_balance(you.address, m2);

                await Order.place_order(o1, other.privateKey);
                await Order.place_order(o2, you.privateKey);
                await Order.place_order(o3, other.privateKey);

                await wait_confirm();

                let your_m1_av = BigNumber(your_m1.availableString).plus(test.your_delta1);
                let your_m2_av = BigNumber(your_m2.availableString).plus(test.your_delta2);

                expect(await balance_equal(your_m1_av, m1, you.address)).to.equal(true);
                expect(await balance_equal(your_m2_av, m2, you.address)).to.equal(true);
            });
        });
    });
})
