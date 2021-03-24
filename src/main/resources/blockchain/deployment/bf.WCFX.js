const { Conflux } = require("js-conflux-sdk");
const JSBI = require("./WrapJSBI");
const editJsonFile = require("edit-json-file");
let contract_addr = editJsonFile(`${__dirname}/../contract_address.json`, { autosave: true });
const wcfxContract = require('../WrappedCfx.json');

var PropertiesReader = require('properties-reader');
var properties = PropertiesReader(`${__dirname}/../../application.properties`);
const CFX_URL = process.argv[3] || properties.get('blockchain.cfx.url')
console.info(`CFX URL ${CFX_URL}`)
const cfx = new Conflux({
    url: CFX_URL,
    defaultGasPrice: 1,
});
let sender = {}

const wcfx = cfx.Contract({
    bytecode: wcfxContract.bytecode,
    abi: wcfxContract.abi
});
async function run(){
    await cfx.updateNetworkId();
    sender = cfx.wallet.addPrivateKey(process.argv[2]).address;
    cfx.getNextNonce(sender).then(async (nonce) => {
        const name = "wcfx";
        console.info(`nounce is ${nonce}`)
        if (JSBI.equal(nonce, JSBI.BigInt('0')) || true) {
            deploy(wcfx, sender, name)
            await waitNonce(JSBI.add(nonce, JSBI.BigInt('1')), sender)
        } else {
            console.log("Skip")
        }
        console.log(`wcfx deployed, ${name}`);
    })
}

//=========================================================================================================
async function deploy(contract, account, name) {
    console.info(`begin deploy ${name}`);
    const receipt = await contract.constructor([])
        .sendTransaction({ from: account })
        .executed()
        .catch(error => {console.log(`wcfx, deploy ${name} failed`,error); process.exit(1)})

    console.log(`wcfx, deploy ${name}, address ${receipt.contractCreated}`);
    contract_addr.set(name, receipt.contractCreated)
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