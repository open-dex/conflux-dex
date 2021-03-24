const fs = require('fs');
const readline = require('readline');
const { expect } = require('chai');

module.exports = {
    get_admin,
    get_dex_url,
    get_cfx_url,
    get_cfx_confirm_epochs,
    get_btc_testnet_account,
    get_eth_testnet_account,
    get_eth_crosschain_amount,
    get_usdt_crosschain_amount,
    get_btc_crosschain_amount,
    get_benchmark_tps,
    get_benchmark_limit,
    get_timeout
}

let dex_url = process.env.DEX_URL || 'http://localhost:8080';
console.info(`dex url ${dex_url}`)

async function read_file(KEY) {
    return new Promise((resolve) => {
        let res = '';

        let fRead = fs.createReadStream('./src/main/resources/application.properties');
        let objReadline = readline.createInterface({
            input: fRead
        });
        objReadline.on('line', function(line) {
            let tokens = line.split('=');
            let key = tokens[0];
            let value = tokens[1];
            if (tokens.length == 2 && key == KEY) {
                res = value;
            }
        });
        objReadline.on('close', function() {
            resolve(res);
        });
    })
}

async function get_admin() {
    let admin_info = {
        address: '',
        privateKey: ''
    }
    admin_info.address = process.env.DEX_ADMIN_ADDRESS;
    admin_info.privateKey = process.env.DEX_ADMIN_PRIVATE_KEY;
    expect(admin_info.address != undefined).to.equal(true, 'no DEX_ADMIN_ADDRESS found in system env');
    expect(admin_info.privateKey != undefined).to.equal(true, 'no DEX_ADMIN_PRIVATE_KEY found in system env');
    return admin_info;
}

function get_dex_url() {
    return dex_url;
}

async function get_cfx_url() {
    let res = process.env.CFX_URL;
    expect(res != undefined).to.equal(true, 'no CFX_URL found in system env');
    return res;
}

async function get_cfx_confirm_epochs() {
    return 100;
}

function get_btc_testnet_account() {
    let btc_testnet_account = {
        address: '',
        wif: ''
    };
    btc_testnet_account.address = process.env.BTC_TESTNET_ADDRESS;
    btc_testnet_account.wif = process.env.BTC_TESTNET_WIF;
    expect(btc_testnet_account.address != undefined).to.equal(true, 'no BTC_TESTNET_ADDRESS found in system env');
    expect(btc_testnet_account.wif != undefined).to.equal(true, 'no BTC_TESTNET_WIF found in system env');
    return btc_testnet_account;
}

function get_eth_testnet_account() {
    let eth_testnet_account = {
        address: '',
        privateKey: ''
    };
    eth_testnet_account.address = process.env.ETH_TESTNET_ADDRESS;
    eth_testnet_account.privateKey = process.env.ETH_TESTNET_PRIVATEKEY;
    expect(eth_testnet_account.address != undefined).to.equal(true, 'no ETH_TESTNET_ADDRESS found in system env');
    expect(eth_testnet_account.privateKey != undefined).to.equal(true, 'no ETH_TESTNET_PRIVATEKEY found in system env');
    return eth_testnet_account;
}

function get_eth_crosschain_amount() {
    return 0.05;
}

function get_usdt_crosschain_amount() {
    return 100;
}

function get_btc_crosschain_amount() {
    return 0.01;
}

function get_benchmark_tps() {
    let tps = process.env.BENCHMARK_TPS;
    expect(tps != undefined).to.equal(true, 'no BENCHMARK_TPS found in system env');
    return tps;
}

function get_benchmark_limit() {
    let limit = process.env.BENCHMARK_LIMIT;
    expect(limit != undefined).to.equal(true, 'no BENCHMARK_LIMIT found in system env');
    return limit;
}

async function get_timeout() {
    return (await get_cfx_confirm_epochs() * 3 + 10) * 1000;
}
