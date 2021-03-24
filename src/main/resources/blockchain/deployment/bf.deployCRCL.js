const { Conflux } = require("js-conflux-sdk");
const JSBI = require('./WrapJSBI');
const editJsonFile = require("edit-json-file");
let contract_addr = editJsonFile(`${__dirname}/../contract_address.json`, { autosave: true });
const crclContract = require('../CRCL.json');

const cfx = new Conflux({
    url: process.argv[7],
    defaultGasPrice: 1,
});
let sender = {};

const crcl = cfx.Contract({
    bytecode: crclContract.bytecode,
    abi: crclContract.abi
});

// node src/main/resources/blockchain/deployment/bf.deployCRCL.js erc777_address_1 crcl_address_1 CRCL-EXP CRCL-EXP 0x0d3abc09a80f6748112877260ceddb52855cd87bedd23417eed356f2b060f5d6 http://wallet-mainnet-jsonrpc.conflux-chain.org:12537 0xC3F1102173449E94e3CE5d9BB5a8A6a251027247

const boomflowAddr = contract_addr.get("boomflow_addr");
const dexAdmin = process.argv[8];

async function run() {
    await cfx.updateNetworkId();
    sender = cfx.wallet.addPrivateKey(process.argv[6]).address;
    cfx.getNextNonce(sender).then((nonce) => {
        deployCRCL(
            process.argv[2], process.argv[3], process.argv[4], process.argv[5], nonce
        ).then(async (nonce) => {
            let wait_list = [];

            // add boomflow address to Whitelisted of CRCL contract
            wait_list.push(addWhitelisted(process.argv[3], boomflowAddr, nonce))
            nonce = JSBI.add(nonce, JSBI.BigInt(1));

            // add dex admin to Whitelisted of CRCL contract
            wait_list.push(addWhitelisted(process.argv[3], dexAdmin, nonce));
            nonce = JSBI.add(nonce, JSBI.BigInt(1));

            // add dex admin to WhitelistAdmin of CRCL contract
            wait_list.push(addWhitelistAdmin(process.argv[3], dexAdmin, nonce));
            nonce = JSBI.add(nonce, JSBI.BigInt(1));

            await Promise.all(wait_list);

            return waitNonce(nonce, sender)
        })
    })
}

//=========================================================================================================
async function deployCRCL(erc777_name, crcl_name, name, symbol, nonce) {
    const erc777_addr = contract_addr.get(erc777_name);

    const txParams = { 
        from: sender,
        nonce: nonce,
    }
    
    // 259200: lock 3 days
    await crcl.constructor(name, symbol, 18, erc777_addr, 259200)
        .sendTransaction(txParams)
        .executed()
        .then((receipt) => {
            console.log(crcl_name + ":", receipt.contractCreated)
            contract_addr.set(crcl_name, receipt.contractCreated)
        })
        .catch(error => {console.log(error); process.exit(1)})

    return waitNonce(JSBI.add(nonce, JSBI.BigInt(1)), sender)
}

async function addWhitelisted(crclName, addr, nonce) {
    const crclAddr = contract_addr.get(crclName);

    const txParams = {
        from: sender,
        nonce: nonce,
        value: 0,
        to: crclAddr,
        data: crcl.addWhitelisted(addr).data,
    };

    return cfx.sendTransaction(txParams).executed()
}

async function addWhitelistAdmin(crclName, addr, nonce) {
    const crclAddr = contract_addr.get(crclName);

    const txParams = {
        from: sender,
        nonce: nonce,
        value: 0,
        to: crclAddr,
        data: crcl.addWhitelistAdmin(addr).data,
    };

    return cfx.sendTransaction(txParams).executed()
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

run().then()