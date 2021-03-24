const { expect } = require('expect');
const cfx_sdk = require('js-conflux-sdk');
const { Message } = require('js-conflux-sdk');
const Config = require('./config.js');
const http = require('./http.js');

module.exports = {
    pause_cancel_resume,
    query_balance,
    query_currency
}

async function query_balance(address, currency) {
    let res = await http.get(Config.get_dex_url() + '/accounts/' + address + '/' + currency, true);
    return res.data;
}

async function query_currency(currency) {
    let res = await http.get(Config.get_dex_url() + '/currencies/' + currency, true);
    return res.data;
}

async function rlp_sign(bufferList) {
    let rlp_encode = cfx_sdk.util.sign.rlpEncode(bufferList);

    let msg = new Message(rlp_encode);
    let admin = await Config.get_admin();
    let signature = msg.sign(admin.privateKey).signature;
    // let V += 27
    let suffix = signature.charAt(131) == '0' ? '1b' : '1c';
    signature = signature.substr(0, 130) + suffix;

    return signature;
}

async function pause_cancel_resume() {
    let timestamp = Date.now();

    let res = await http.get(Config.get_dex_url() + '/system/suspend', true);
    if (res.data) {
        console.log('system already paused before test');
    }
    else {
        let data = {
            command: 'dex.suspend',
            comment: '',
            timestamp: timestamp,
            signature: await rlp_sign(
                [
                    Buffer.from('dex.suspend'),
                    Buffer.from(''),
                    Buffer.from(timestamp.toString())
                ]
            )
        }
        await http.post(Config.get_dex_url() + '/system/suspend', data, true);
    }

    let data = {
        userAddress: '',
        product: '',
        timestamp: timestamp,
        signature: await rlp_sign(
            [
                Buffer.from(''),
                Buffer.from(''),
                Buffer.from(timestamp.toString())
            ]
        )
    }
    await http.post(Config.get_dex_url() + '/system/orders/cancel', data, true);

    data = {
        command: 'dex.resume',
        comment: '',
        timestamp: timestamp,
        signature: await rlp_sign(
            [
                Buffer.from('dex.resume'),
                Buffer.from(''),
                Buffer.from(timestamp.toString())
            ]
        )
    }
    await http.post(Config.get_dex_url() + '/system/resume', data, true);
}
