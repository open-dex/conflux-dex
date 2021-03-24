const {executeSql} = require('../tool/mysql')
const fs = require('fs');
const JSBI = require('./WrapJSBI');
const program = require('commander');
var assert = require('assert');
const retry = require('async-retry')
const retryInterval = 1000
const retryMax = 5

var PropertiesReader = require('properties-reader');
var properties = PropertiesReader(`${__dirname}/../../application.properties`);
const { Conflux, format } = require("js-conflux-sdk");

const crcl_contract = require('../CRCL.json');

let tokens = [];

function toHex(x) {
  return format.hex(x);
}

async function readData(old_addr, token) {
    console.log(`reading data from old ${token}`);
    let crcl = cfx.Contract({
        abi: crcl_contract.abi,
        address: old_addr
    });
    let nonce = await cfx.getNextNonce(owner);
    let res = {};

    if (!(await isPaused(crcl))) {
        // Pause the old CRCL contract for migration
        console.log(`pausing ${token} before migration`);
        await send(crcl.Pause(), nonce);
        nonce = JSBI.add(nonce, JSBI.BigInt('1'));
        await waitNonce(nonce, owner);
    }

    // totalSupply
    res.address = old_addr;
    res.totalSupply = toHex(await callWithRetry(crcl.totalSupply()));

    let accountLength = await callWithRetry(crcl.accountTotal());
    res.accounts = [];
    res.balances = {};
    for (let i = 0; i < accountLength; i += 100) {
        let accounts = await callWithRetry(crcl.accountList(i));

        for (let j = 0; j < accounts.length; ++j) {
            if (accounts[j] != "0x0000000000000000000000000000000000000000") {
                res.accounts.push(accounts[j])
                res.balances[accounts[j]] = toHex(await callWithRetry(crcl.balanceOf(accounts[j])));
            }
        }
    }
    
    //console.log(res)
    let path = __dirname + `/data/${token}.dat`;
    console.log(`${token}, size ${res.accounts.length}, save at ${path}\n`)
    fs.writeFileSync(path, JSON.stringify(res, null, 4));
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
// Use it to download user list and balance in crcl contract.
// handle one token : [-f PATH_TO_contract_address.json] --crcl lfc_addr
//                     or  : --crcl lfc_addr --address 0x00000
// handle all token : [-f PATH_TO_contract_address.json]
program.option('-r, --read', 'read data of old contract')
  .option('-p, --privatekey [type]', 'private key of owner')
  .option('-u, --url <type>', 'Conflux Web URL')
  .option('-f, --contract_file <contract_file>', 'Contract file that contains addresses')
  .option('-addr, --address <contract_address>', 'Contract addresses')
  .option('-c, --crcl <crcl>', 'crcl name in contract_address.json, or just a name')
  .option('-db, --use_db', 'use database')
  .option('-t, --test', 'test mode, only load currencies')
  .parse(process.argv);

let cfx_url = program.url || process.env.CFX_URL || properties.get('blockchain.cfx.url')
console.info(`cfx url ${cfx_url}`)
const cfx = program.test ? {} : new Conflux({
    url: cfx_url,
    defaultGasPrice: 1,
});

const privatekey = program.privatekey || process.env.BOOMFLOW_ADMIN_PRIVATE_KEY;
assert(privatekey !== undefined || program.test, "Private key is not an optional argument");
let owner = {}

async function getToken() {
    for (let i = 0; i < tokens.length; ++i) {
        let token = tokens[i];
        console.log(`get token ${token.name}, ${token.crclAddress}`);
        await readData(token.crclAddress, token.name);
    }
}

async function loadCurrencies() {
    //
    let contract_address = {};
    if (program.address) {
        // specified crcl address
    } else if (program.contract_file) {
        console.info(`load currencies from ${program.contract_file}`)
        contract_address = JSON.parse(fs.readFileSync(program.contract_file));
    } else if (program.use_db) {
        return loadCurrenciesFromDB()
    } else {
        // by default use contract_address.json
        contract_address = JSON.parse(fs.readFileSync(__dirname + "/../contract_address.json"));
        console.info(`load currencies from default file.`)
    }
    //
    if (program.crcl && program.address) {
        // specified crcl name and address
        tokens.push({name: program.crcl, crclAddress: program.address});
    } else if (program.crcl) {
        // specified crcl name, load address from file.
        tokens.push({name: program.crcl, crclAddress: contract_address[program.crcl]});
    } else {
        // load all l***_address fields.
        Object.keys(contract_address).forEach(k=>{
            if (k.startsWith("l") && k.endsWith("_addr")) {
                tokens.push({name: k, crclAddress: contract_address[k]})
            }
        })
    }
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
    console.info(`load from database, crcl size ${tokens.length}`);
}

async function run() {
    await cfx.updateNetworkId();
    owner = program.test ? {} : cfx.wallet.addPrivateKey(privatekey).address;
    await loadCurrencies();
    if (program.test) {
        console.info(`currencies is \n`,JSON.stringify(tokens, null , 4))
        console.info(`exit under test mode.`)
        return;
    }
    if (program.read !== undefined) {
        getToken().then();
    }
}
run().catch(err=>console.info(`migrateCRCL.js fail.`, err));
