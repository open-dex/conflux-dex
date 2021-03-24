const { Conflux, format } = require("js-conflux-sdk");
const JSBI = require("./WrapJSBI");
const boomflowContract = require('../Boomflow.json');
const {getConfig,boomflowAddrKey} = require('../tool/mysql.js')
let sender,cfx = null;
let cfxUrl = process.env.CFX_URL;
let boomflowadminprivatekey = process.env.BOOMFLOW_ADMIN_PRIVATE_KEY;
async function run() {
    let boomflow_addr = await getConfig(boomflowAddrKey)
    log(`boomflow address is ${boomflow_addr}, cfx url ${cfxUrl}`)
    if (boomflow_addr === null || boomflow_addr === undefined || boomflow_addr.length === 0) {
        throw new Error('boomflow address not configured in database.')
    }
    cfx = new Conflux({
        url: cfxUrl,
        defaultGasPrice: 1,
        // log: console
    });
    await cfx.updateNetworkId();
    sender = cfx.wallet.addPrivateKey(boomflowadminprivatekey).address;
    boomflow_addr = format.address(boomflow_addr, cfx.networkId)


    var contracts = []

// Boomflow
    contracts.push(
        cfx.Contract({
            address: boomflow_addr,
            abi: boomflowContract.abi
        })
    )

    cfx.getNextNonce(sender).then(async (nonce) => {
        contracts.map(async (contract, index) => {
            //console.log(JSBI.add(nonce, JSBI.BigInt(contracts.indexOf(contract))))
            resume(contract, JSBI.add(nonce, JSBI.BigInt(index)))
        })
        await waitNonce(JSBI.add(nonce, JSBI.BigInt(contracts.length)), sender)
    })
}
function log(...data) {
    console.info(...data)
}
//===============================================
// Resume
function resume(token, nonce) {
    const txParams = {
        from: sender,
        nonce: nonce, // make nonce appropriate
    };
    log(`try to resume ${token.address} nonce ${nonce}`)
    return token.Resume().sendTransaction(txParams).executed()
        .then(result=>{log(`resume ok ${result.transactionHash}`)})
        .catch(err=>log(`resume fail.`, err));
}

async function waitNonce(target, acc) {
    let x;
    while (true) {
        x = await cfx.getNextNonce(acc);
        if (x < target) {
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

run().catch(err=>{
    // unpause may fail if contract is not paused.
    console.error('unpause failed, is it paused before?')
    throw err
});