const Web3 = require('web3');
const usdt_abi = require('../util/usdt_abi.json');
const Custodian = require('../util/custodian.js');
const Contract = require('../../src/main/resources/blockchain/contract_address.json');
const { Account } = require('js-conflux-sdk');
const http = require('../util/http.js');
const bitcoin = require('bitcoinjs-lib');
const { expect } = require('chai');
const Config = require('../util/config.js');
const Dex = require('../util/dex.js');
const Order = require('../util/dex_order.js');
const Mainnet = require('../util/mainnet.js');

describe('Crosschain withdraw', () => {
    let web3 = new Web3("https://rinkeby.infura.io/v3/2e4c1bcd2d6741e2bc9f84ebc80ca18f");
    let usdt_decimals = 6;

    let eth_address = '';
    let eth_privateKey = '';
    let eth_recv_address = '';

    let usdt_contract = new web3.eth.Contract(usdt_abi, Contract.usdt_addr);

    let btc_address;
    let btc_wif;

    let eth_amount = Config.get_eth_crosschain_amount();
    let usdt_amount = Config.get_usdt_crosschain_amount();
    let btc_amount = Config.get_btc_crosschain_amount();

    let you = Account.random();

    async function wait_confirm() {
        await Mainnet.wait_epoch(await Config.get_cfx_confirm_epochs() * 2);
        await Mainnet.wait_ms(5000);
    }
    
    before(async function() {
        this.timeout(40000);

        await Custodian.fake_deposit(you.address);
        await wait_confirm();

        eth_address = Config.get_eth_testnet_account().address;
        eth_privateKey = Config.get_eth_testnet_account().privateKey;

        btc_address = Config.get_btc_testnet_account().address;
        btc_wif = Config.get_btc_testnet_account().wif;

        console.log('your cfx addr: ' + you);

        await Dex.query_balance(you.address, 'btc');
    })

    it('eth', async function() {
        this.timeout(40000);

        let request = {
            userAddress: you.address,
            currency: 'ETH',
            amount: eth_amount,
            recipient: eth_address,
            isCrosschain: true,
            relayContract: "0x0000000000000000000000000000000000000000",
        }

        let res = await Order.withdraw(request, you.privateKey);
        console.log(res);
    })

    it('usdt', async function() {
        this.timeout(40000);

        let request = {
            userAddress: you.address,
            currency: 'USDT',
            amount: usdt_amount,
            recipient: eth_address,
            isCrosschain: true,
            relayContract: "0x0000000000000000000000000000000000000000",
        }

        let res = await Order.withdraw(request, you.privateKey);
        console.log(res);
    })

    it('btc', async function() {
        this.timeout(40000);

        let request = {
            userAddress: you.address,
            currency: 'BTC',
            amount: btc_amount,
            recipient: btc_address,
            isCrosschain: true,
            relayContract: "0x0000000000000000000000000000000000000000",
        }

        let res = await Order.withdraw(request, you.privateKey);
        console.log(res);
    })
})
