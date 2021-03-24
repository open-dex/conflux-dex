const {Conflux, format} = require('js-conflux-sdk');
const { Account } = require('js-conflux-sdk');
const Mainnet = require('../util/mainnet.js');
const Order = require('../util/dex_order.js');
const Custodian = require('../util/custodian.js');
const Dex = require('../util/dex.js');
const http = require('../util/http.js');
const Config = require('../util/config.js');
const BigNumber = require('bignumber.js');
const { expect } = require('chai');
const fs = require('fs');

describe('Benchmark', function ()  {
    this.timeout(0);
    let accounts = [];
    let account_size = 10;

    function random_int(min, max) {
        return Math.round(Math.random() * (max - min)) + min;
    }

    function random_bigfloat(min, max, digits) {
        let x = Math.random() * (max - min) + min;
        return BigNumber(x.toFixed(digits));
    }

    async function balance_equal(balance, currency, address) {
        let crcl_balance = await Mainnet.get_crcl_balance(currency, address);
        let res = await Dex.query_currency(currency);
        let digits = res.decimalDigits;
        balance = balance.times(BigNumber(10).pow(digits));

        let crclV = crcl_balance.toString();
        let dbV = balance.toFixed(0);
        let eq = dbV === crclV;
        if (!eq) {
            console.warn(`balance not eq, address ${address}, crcl ${crclV} vs dex DB ${dbV}`)
        }
        return eq;
    }
    const products = ['BTC-USDT', 'ETH-USDT']
    let order = {
        address: '',
        product: '',
        amount: BigNumber(1),
        price: BigNumber(100),
        type: 'Limit',
        side: 'Buy',
        feeAddress: '',
        feeRateMaker: BigNumber(0),
        feeRateTaker: BigNumber(0.01)
    }

    async function wait_confirm() {
        let epochNum = await Config.get_cfx_confirm_epochs() * 2;
        console.info(`wait epoch ${epochNum}`)
        await Mainnet.wait_epoch(epochNum);

        let waitMS = 5000;
        console.info(`wait ms ${waitMS}`)
        await Mainnet.wait_ms(waitMS);
    }

    before(async function() {
        this.timeout(await Config.get_timeout());

        BigNumber.set({ DECIMAL_PLACES: 20 });

        // await Dex.pause_cancel_resume();
        let keepAccount = process.env.keepAccount || false;
        let accountPath = "account4benchmark.json";
        if (!fs.existsSync(accountPath)) {
            keepAccount = false;
            console.info(`account file not exists`);
        }
        const cfx = new Conflux({url:'', networkId: 1})
        const wallet = cfx.wallet;
        if (keepAccount) {
            const privateKeys = JSON.parse(fs.readFileSync(accountPath));
            privateKeys.forEach(pk => {
                const account = wallet.addPrivateKey(pk);
                // change the base32 address to hex
                account.address = format.hexAddress(account.address);
                return accounts.push(account);
            })
            account_size = accounts.length;
            console.info(`load accounts from file, size ${account_size}`)
        }
        for (let i = 0; i < account_size && !keepAccount; ++i) {
            const accRnd = wallet.addRandom();
            accRnd.address = format.hexAddress(accRnd.address);
            accounts.push(accRnd)
        }
        if (!keepAccount) {
            fs.writeFileSync(accountPath, JSON.stringify(
                accounts.map(acc => acc.privateKey), null, 4))
        }
        for (const acc of accounts) {
            if (process.env.SKIP_DEPOSIT) {
                break;
            }
            console.log('do fake deposit, address', acc.address);
            await Custodian.fake_deposit(acc.address);
        }

        // await wait_confirm();
        let confirmed = new Set();
        let i = -1;
        while(true) {
            if (confirmed.size === accounts.length || process.env.SKIP_DEPOSIT) {
                console.log(`all confirmed.`)
                break;
            }
            i ++;
            i = i % accounts.length;
            let address = accounts[i].address;
            if (confirmed.has(address)) {
                i++;
                continue;
            }
            let str = await http.get(Config.get_dex_url() + '/accounts/' + address + '/btc', false);
            console.log(`query balance return ${str}`)
            const json = JSON.parse(str);
            if (json.success) {
                confirmed.add(address);
                console.log(`confirmed by dex, address ${address}`);
            } else {
                console.info(`not confirmed by dex, address ${address}, sleep now.`)
                await new Promise(resolve => setTimeout(resolve, 2000));
            }
        }
    })

    it('Place and cancel in random time with high TPS', async function() {
        async function work(type) {
            let o = Object.assign({}, order);
            let who = process.env.TRADER_INDEX || random_int(0, accounts.length - 1);

            o.address = accounts[who].address;
            o.product = products[random_int(0, products.length - 1)];
            o.feeAddress = accounts[who].address;
            o.amount = random_bigfloat(1 - 0.05, 1 + 0.05, 4);
            o.side = random_int(0, 1) === 0 ? 'Buy' : 'Sell';
            o.type = 'Limit';
            o.price = random_bigfloat(100 - 0.05, 100 + 0.05, 2);

            // type 3 - market price
            if (type === 3) {
                o.type = 'Market';
                o.price = BigNumber('0');
            }
            if (o.type === 'Limit' && process.env.SELF_TRADE) {
                const oppositeOrder = Object.assign({}, o)
                oppositeOrder.side = oppositeOrder.side === 'Buy' ? 'Sell' : 'Buy'
                Order.place_order(oppositeOrder, accounts[who].privateKey).then().catch(err=>{
                    console.info(`self trade mode, pre-place opposite order fail`, JSON.stringify(err))
                })
            }
            return Order.place_order(o, accounts[who].privateKey).then((orderId) =>
                {
                    // type 0 cancel in 2~20ms
                    // type 1 cancel in 500~1000ms
                    let delay = 10_000; // By default cancel order after 10s.
                    if (type === 0) {
                        delay = random_int(2, 20);
                    }
                    else if (type === 1) {
                        delay = random_int(500, 1000);
                    }
                    // console.info(`place order got id ${orderId}`)
                    if (type !== 3) {
                        setTimeout(
                            async function() {
                                const cancelRet = await  Order.cancel_order(orderId, o, accounts[who].privateKey, false);
                                if (!cancelRet.success && cancelRet.data !== 503) {
                                    // 503, order is not open.
                                    console.info(`cancel order ${orderId} ret:`, cancelRet)
                                }
                            },
                            delay
                        )
                    }
                }).catch(err=>{
                    console.warn(`${new Date().toLocaleString()} place order fail`, err)
                });
        }

        let tps = Config.get_benchmark_tps();
        let limit = Config.get_benchmark_limit();

        let count = 0;

        while (count < limit || limit <= 0) {
            ++count;
            work(random_int(0, 3));
            if (count % 10 === 1) {
                console.log(`${new Date().toLocaleString()} place-cancel order count ${count}`);
            }
            await Mainnet.wait_ms(1000.0 / tps);
        }

        await wait_confirm();

        let pair = order.product.split('-');
        let m1 = pair[0];
        let m2 = pair[1];
        for (let i = 0; i < account_size; ++i) {
            let your_m1 = await Dex.query_balance(accounts[i].address, m1);
            let your_m2 = await Dex.query_balance(accounts[i].address, m2);
            let your_m1_bal = BigNumber(your_m1.balanceString);
            let your_m2_bal = BigNumber(your_m2.balanceString);
            expect(await balance_equal(your_m1_bal, m1, accounts[i].address)).to.equal(true);
            expect(await balance_equal(your_m2_bal, m2, accounts[i].address)).to.equal(true);
        }
    })
})
