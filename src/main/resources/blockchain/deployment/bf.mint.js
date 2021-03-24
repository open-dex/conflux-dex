"use strict";
const { Conflux, format:fmt } = require("js-conflux-sdk");
const JSBI = require('./WrapJSBI');
const {getTokenAddress} = require('../tool/mysql')
const erc777Contract = require('../ERC777.json');
const fcContract = require('../FC.json');

const cfx = new Conflux({
    url: process.env.CFX_URL,
    defaultGasPrice: 1,
    // logger: console
});

let sender = {}

async function buildContract(name, abi, chainId) {
    const addr = await getTokenAddress(name);
    const contract = cfx.Contract({
        address: fmt.address(addr, chainId),
        abi: abi
    });
    contract._name = name;
    console.info(`mint, contract ${name} ${addr}`)
    return contract;
}
async function run() {
    await cfx.updateNetworkId();
    sender = cfx.wallet.addPrivateKey(process.env.BOOMFLOW_ADMIN_PRIVATE_KEY).address;

    const EOS_contract = await buildContract('EOS', erc777Contract.abi, cfx.networkId);
    const KCoin_contract = await buildContract('KCoin', erc777Contract.abi, cfx.networkId);
    const FC_contract = await buildContract('FC', fcContract.abi, cfx.networkId);
    const USDT_contract = await buildContract('USDT', erc777Contract.abi, cfx.networkId);
    var assets = [
        EOS_contract,
        KCoin_contract,
        USDT_contract,
    ];

    const users = [
        '0x13f1102173449494e3c35d9bb5a2a6a251127247',
    ]
    const targetAddr = process.argv[2];
// mint for one address.
    if (targetAddr) {
        users.splice(0, users.length);
        users.push(fmt.hexAddress(targetAddr));
    }

    cfx.getNextNonce(sender).then(async (nonce) => {
        console.info(`nonce is ${nonce}`)
        users.forEach(user=>{
            mintFC(user, "1000000000000000000000000", FC_contract, nonce)
            nonce = JSBI.add(nonce, JSBI.BigInt(1));
        })
        for (var i = 0; i < assets.length; i++) {
            for (var j = 0; j < users.length; j++) {
                const asset = assets[i];
                const userAddr = users[j];
                mintCFXToken(userAddr, "100000000000000000000000000", asset, nonce).catch(err=>{
                    console.log(`mint ${asset._name} for ${userAddr} fail:`, err)
                })
                nonce = JSBI.add(nonce, JSBI.BigInt(1));
            }
        }
        console.info(`wait nonce.`)
        await waitNonce(nonce, sender)
    }).catch(err=>{
        console.log(`fail get next nonce. sender ${sender} #`, err)
    })
}
/*var assets = [
    erc777_token_1,
    erc777_token_2,
]

const users = [
    contract_addr.get("crcl_address_1"),
    contract_addr.get("crcl_address_2")
]

cfx.getNextNonce(sender).then(async (nonce) => {
    for (var i = 0; i < assets.length; i++) {
        mint(users[i], "10000000000000000000000000000", "0", assets[i], JSBI.add(nonce, JSBI.BigInt(i)))
    }
    
    await waitNonce(JSBI.add(nonce, JSBI.BigInt(assets.length)), sender)
})*/
//===============================================
// Mint
async function mintCFXToken(account, amount, token, nonce) {
    const txParams = buildTxParams(nonce);

    console.info(`mint ${token._name} for ${account}, nonce ${nonce}`);
    const zero = "0x0000000000000000000000000000000000000000";
    return token.mint(account.toLowerCase(), amount, zero, 0, zero, "0")
        .sendTransaction(txParams).executed().catch(reject => {
            console.info(`mint fail ${token._name}.`, reject)
            // process.exit(2);
        })
}
async function mintFC(account, amount, token, nonce) {
    const txParams = buildTxParams(nonce);
    console.info(`mint ${token._name} for ${account}, nonce ${nonce}`)
    return token.mint(account.toLowerCase(), amount).sendTransaction(txParams).executed().catch(reject=>{
        console.info(`mint ${token._name} fail.`, reject)
        // process.exit(1);
    })
}

function buildTxParams(nonce) {
    return {
        from: sender,
        nonce: nonce,
    };
}

async function waitNonce(target, acc) {
    let x;
    while (true) {
        x = await cfx.getNextNonce(acc);
        if (JSBI.lessThan(x, target)) {
            await sleep(1000);
            continue;
        }
        break;
    }
    return x;
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

run()
    .then(()=>console.info('mint finished.'))
    .catch(err=>console.info(`mint fail`, err));