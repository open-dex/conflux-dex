const {executeSql} = require('../tool/mysql')
const fs = require('fs');
const JSBI = require('./WrapJSBI');
const program = require('commander');
var assert = require('assert');
const retry = require('async-retry')
const retryInterval = 1000
const retryMax = 5
const uploadMax = 1800

var PropertiesReader = require('properties-reader');
var properties = PropertiesReader(`${__dirname}/../../application.properties`);
const { Conflux, util } = require("js-conflux-sdk");

const crcl_contract = require('../CRCL.json');

let tokens = [];

function toHex(x) {
  return util.format.hex(x);
}

function send(fn, nonce) {
    return retry(async bail => {
            /*let estimate = await fn.estimateGasAndCollateral({from: owner});
            console.log(estimate.gasUsed.toString(), estimate.storageCollateralized.toString())*/

            let receipt = await fn.sendTransaction({
                from: owner,
                nonce: nonce,
            }).executed();

            if (receipt.outcomeStatus !== 0) {
                console.log("Transaction Hash: " + receipt.transactionHash)
                console.log("Transaction Status: " + receipt.outcomeStatus)
                // don't retry upon EVM execution error
                bail(new Error('EVM Execution error'))
                return
            }
            
            return receipt
      }, {
        retries: retryMax,
        onRetry: async(err, i) => {
            console.log('Retry:', i);
            if (err) {
                console.error('Retry error:', err.toString());
            }
            await sleep(retryInterval);
        }
    })
}

async function callWithRetry(fn) {
    return retry(async bail => {
        const res = await fn.call();
        return res
      }, {
        retries: retryMax,
        onRetry: async(err, i) => {
            console.log('Retry:', i);
            if (err) {
                console.error('Retry error:', err.toString());
            }
            await sleep(retryInterval);
        }
    })
}

const sleep = (ms) => {
    return new Promise(resolve => setTimeout(resolve, ms));
};

async function waitNonce(target, acc) {
    let x;
    for (;;) {
        x = await cfx.getNextNonce(acc);
        if (JSBI.lessThan(x, target)) {
        await sleep(5000);
        continue;
        }
        break;
    }
    return x;
}

async function isPaused(contract) {
    return (await callWithRetry(contract.paused()));
}

program.option('-r, --read', 'read data of old contract')
  .option('-m, --migrate', 'migrate data to new contract')
  .option('-p, --privatekey [type]', 'private key of owner')
  .option('-u, --url <type>', 'Conflux Web URL')
  .parse(process.argv);

let cfx_url = properties.get('blockchain.cfx.url')
if (program.url !== undefined) {
    cfx_url = program.url;
}
const cfx = new Conflux({
    url: cfx_url,
    defaultGasPrice: 1,
});

assert(program.privatekey !== undefined, "Private key is not an optional argument");
let owner = {}

async function finishTokensMigration() {
    let nonce = await cfx.getNextNonce(owner.address);
    let p = [];
    for (let i = 0; i < tokens.length; ++i) {
        let token = tokens[i];
        console.log(`finish migration of token ${token.name}, ${token.crclAddress}`);
        let crcl = cfx.Contract({
            abi: crcl_contract.abi,
            address: token.crclAddress,
        });
        p.push(send(crcl.finishMigration(), nonce));
        ++nonce;
        if (p.length % 10 === 0) {
            await Promise.all(p);
            p = [];
        }
    }
    await Promise.all(p);
    await waitNonce(nonce, owner.address);
}

async function loadCurrenciesFromDB() {
    const sql = `select name, contract_address from t_currency`;
    const results = await executeSql(sql);
    if (results.length === 0) {
        throw new Error(`Currency in database is empty`);
    }
    // tokens = ['lfc_addr', 'lbtc_addr', 'leth_addr', 'lusdt_addr', 'ldai_addr', 'lcomp_addr', 'ldf_addr', 'lknc_addr'];
    results.forEach(row=>{
        tokens.push({name: row['name'], crclAddress: row['contract_address']})
    })
    console.info(`crcl size ${tokens.length}`);
}

async function run() {
    await cfx.updateNetworkId();
    owner = cfx.wallet.addPrivateKey(program.privatekey);
    await loadCurrenciesFromDB();
    await finishTokensMigration();
}
run().catch(err=>console.info(`finishMigrateCRCL.js fail.`, err));
