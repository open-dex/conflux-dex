const Config = require('./config.js');
const http = require('./http.js');
const { Conflux } = require('js-conflux-sdk');
const {
    erc777ABI,
    crclABI,
} = require('boomflow');

module.exports = {
    wait_ms,
    wait_epoch,
    get_crcl_balance,
    get_chainId,
}

async function get_cfx() {
    return new Conflux({
        url: await Config.get_cfx_url(),
        defaultGasPrice:1,
    });
}

async function get_chainId() {
    let cfx = await get_cfx();
    let status = await cfx.getStatus();
    return status.chainId;
}

async function wait_ms(time) {
    let sleep = new Promise((resolve) => {
        setTimeout(() => {
            resolve();
        }, time);
    })
    await sleep;
}

async function wait_epoch(epoch) {
    return new Promise(async (resolve) => {
        let cfx = await get_cfx();
        let start = await cfx.getEpochNumber();
        if (epoch === null) {
            epoch = await Config.get_cfx_confirm_epochs() + 5;
        }
        while (true) {
            await wait_ms(200);
            let cur = await cfx.getEpochNumber();
            if (cur - start >= epoch) {
                resolve();
                break;
            }
        }
    });
}

async function get_crcl_balance(name, address) {
    let cfx = await get_cfx();

    let res = await http.get(Config.get_dex_url() + '/currencies/' + name, true);
    let crcl_addr = res.data.contractAddress;

    let crcl = cfx.Contract({
        address: crcl_addr,
        abi: crclABI
    });
    let crcl_balance = await crcl.balanceOf(address);
    return crcl_balance;
}

async function get_erc777_balance(name) {
    // todo
}

