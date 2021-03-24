const Web3 = require('web3');
const usdt_abi = require('../util/usdt_abi.json');
const Custodian = require('../util/custodian.js');
const Contract = require('../../src/main/resources/blockchain/contract_address.json');
const { Account } = require('js-conflux-sdk');
const http = require('../util/http.js');
const bitcoin = require('bitcoinjs-lib');
const { expect } = require('chai');
const Config = require('../util/config.js');

describe('Crosschain deposit', () => {
    let web3 = new Web3("https://rinkeby.infura.io/v3/2e4c1bcd2d6741e2bc9f84ebc80ca18f");
    let usdt_decimals = 6;

    let eth_address = '';
    let eth_privateKey = '';
    let eth_recv_address = '';

    let usdt_contract = new web3.eth.Contract(usdt_abi, Contract.usdt_addr);

    let btc_address;
    let btc_wif;

    let eth_amount = Math.trunc(Config.get_eth_crosschain_amount() * 1.1 * Math.pow(10, 18));
    let usdt_amount = Math.trunc(Config.get_usdt_crosschain_amount()  * 1.1 * Math.pow(10, usdt_decimals));
    let btc_amount = Math.trunc(Config.get_btc_crosschain_amount() * 1.1 * Math.pow(10, 8));

    let you = Account.random();
    
    before(async function() {
        this.timeout(10000);

        eth_address = Config.get_eth_testnet_account().address;
        eth_privateKey = Config.get_eth_testnet_account().privateKey;
        let eth_balance = await web3.eth.getBalance(eth_address);
        console.log('eth crosschain balance: '+ eth_balance / Math.pow(10, 18));

        let usdt_balance = await usdt_contract.methods.balanceOf(eth_address).call();
        console.log('usdt crosschain balance: ' + usdt_balance / Math.pow(10, usdt_decimals));

        btc_address = Config.get_btc_testnet_account().address;
        btc_wif = Config.get_btc_testnet_account().wif;
        let res = await http.get('https://api.blockcypher.com/v1/btc/test3/addrs/' + btc_address); 
        let btc_balance = JSON.parse(res).balance;
        console.log('btc crosschain balance: ' + btc_balance / Math.pow(10, 8));

        console.log('your cfx addr: ' + you);
    })

    it('eth', async function() {
        this.timeout(40000);

        let eth_recv_addr = await Custodian.get_user_receive_wallet(you.address, 'Eth', 'conflux dex');
        let nonce = await web3.eth.getTransactionCount(eth_address);
        
        let tx = {
            'from': you.address,
            'to': eth_recv_addr,
            'value': eth_amount,
            'nonce': nonce,
            'chainId': 4
        }
        tx = await web3.eth.accounts.signTransaction(tx, eth_privateKey);
        let res = await web3.eth.sendSignedTransaction(tx.rawTransaction);
        console.log('eth tx: ' + res.transactionHash);
    })

    it('usdt', async function() {
        this.timeout(40000);

        let eth_recv_addr = await Custodian.get_user_receive_wallet(you.address, 'Eth', 'conflux dex');
        let nonce = await web3.eth.getTransactionCount(eth_address);

        let data = await usdt_contract.methods.transfer(eth_recv_addr, usdt_amount).encodeABI();
        let tx = {
            'from': you.address,
            'to': Contract.usdt_addr,
            'value': 0,
            'nonce': nonce,
            'chainId': 4,
            'data': data
        }
        tx = await web3.eth.accounts.signTransaction(tx, eth_privateKey);
        let res = await web3.eth.sendSignedTransaction(tx.rawTransaction);
        console.log('usdt tx: ' + res.transactionHash);
    })

    it('btc', async function() {
        this.timeout(40000);

        let btc_recv_addr = await Custodian.get_user_receive_wallet(you.address, 'Btc', 'conflux dex');

        let key = bitcoin.ECPair.fromWIF(btc_wif, bitcoin.networks.testnet);
        let tx = new bitcoin.TransactionBuilder(bitcoin.networks.testnet);
        tx.setVersion(2);

        let res = await http.get('https://api.blockcypher.com/v1/btc/test3/addrs/' + btc_address + '?unspentOnly=true');
        let utxos = JSON.parse(res).txrefs;

        let balance = 0, index = 0;
        for (const utxo of utxos) {
            balance += utxo.value;
            tx.addInput(utxo.tx_hash, utxo.tx_output_n);
        }
        let btc_fee = 10000;
        balance -= btc_amount + btc_fee;
        tx.addOutput(btc_recv_addr, btc_amount);
        expect(balance >= 0).to.equal(true, 'btc balance is not enough');
        tx.addOutput(btc_address, balance);
        for (let i = 0; i < utxos.length; ++i) {
            tx.sign(i, key);
        }

        tx = tx.build().toHex();

        res = await http.post('https://api.blockcypher.com/v1/btc/test3/txs/push', {tx});
        res = JSON.parse(res);
        console.log('btc tx: ' + res.tx.hash);
    })
})
