const program = require('commander');
const JSBI = require('./WrapJSBI');
const ethers = require('ethers')
const tokenConfAll = require('./bf.erc20-erc777.js')
const {executeSql,executeDB,boomflowAddrKey} = require('../tool/mysql')
let tokenConf = tokenConfAll[process.env.DEPLOY_ENV];

function buildContract(path) {
    const contract = require(path);
    return new ethers.ContractFactory(contract.abi, contract.bytecode);
}

// variables from arguments or environment.
let boomflowPrivateKey = process.env.BOOMFLOW_ADMIN_PRIVATE_KEY;
let environment =        process.env.DEPLOY_ENV;
let cfxUrl =             process.env.EVM_RPC_URL;
const dexAdmin =         process.env.DEX_ADMIN_ADDRESS;
const custodianProxyAddress = process.env.CUSTODIAN_PROXY_ADDRESS;
const fcAdminForTest = "0x144aa8f554d2ffbc81e0aa0f533f76f5220db09c";
// FC address in PROD; under test environment, fc will be deployed.
let FC_ADDRESS = '0x87010faf5964d67ed070bc4b8dcafa1e1adc0997'
let boomflowAddr = undefined;

// action map when updating different parts.
// (bm or crcl) -> Add Boomflow to CRCL Whitelisted...
// (crcl)       -> Add DEX Admin to CRCL WhitelistAdmin and Whitelisted...
// (bm)         -> add dex admin to WhitelistAdmin and Whitelisted of boomflow contract
// (fc)         -> Add (hard coded)Admin to FC...  only for test environment.
let updateBoomflow = true;
let updateTokens = true;
let updateCRCL = true;
program
    .option('-t --token', 'deploy tokens')
    .option('-b --boomflow', 'deploy boomflow')
    .option('-ba --boomflowAdmin', 'add dex admin to boomflow white list')
    .option('-c --crcl', 'deploy crcl')
    .option('-ta --token_address <token_address>', 'token address used by crcl')
    .option('-ndb --no_database', 'do not interact with database')
    .option('-n --c_name <c_name>', 'deploy only one crcl/token with specified name')
    .option('-m --merge_address', 'merge contract_address.json')
    .parse(process.argv); // help option will be added automatically.
if (program.args.length !== 2 && program.args.length> 0) {
    console.info(`unknown arguments found, full arguments are:`, program.args)
    process.exit(1)
}
// Attention: Adding shortcut here to prevent deploying contract, if you only want to do other things.
if (program.token || program.boomflow || program.crcl
    || program.boomflowAdmin || program.c_name) {
    // deploy all by default, if specified, set everyone to false, then reset by specified.
    updateBoomflow = updateTokens = updateCRCL = false;
    updateBoomflow = program.boomflow;
    updateTokens = program.token;
    updateCRCL = program.crcl;
}
if (program.c_name) {
    log(`single currency name ${program.c_name}`)
    // deploy only one
    const currency = {name: program.c_name, id: 0 /*autoIncrement*/,
        decimal_digits: 18, cross_chain: 0, minimum_withdraw_amount: 0, isErc777: true}
    if (currency.name === 'usdt') {
        Object.assign(currency, tokenConfAll.currencyTemplates.USDT)
        currency.outerAddress = currency.name;
    } else if (currency.name.toUpperCase() === 'CFX') {
        currency.isErc777 = false;
        currency.isCFX = true;
    }
    tokenConf = {
        crcl_assets: [currency],
        erc777_assets: [currency]
    };
}

console.info(`EVM_RPC_URL is ${cfxUrl}`);
const cfx = ethers.getDefaultProvider(cfxUrl);

console.info(`boomflowPrivateKey ${boomflowPrivateKey.substr(0,10)}...`)

let crcl = buildContract('../CRCL.json');
let boomflow = buildContract('../Boomflow.json');
let erc777 = buildContract('../TokenBase.json');
let fc = buildContract('../FC.json');
let wcfxContract = buildContract('../WrappedCfx.json');

let sender = {}

let crcl_assets = tokenConf.crcl_assets;
let erc777_assets = tokenConf.erc777_assets;
let nonce = 0;

async function loadBoomflowContractAddr() {
    // noinspection SqlResolve
    const result = await executeSql(`select value from t_config where name = '${boomflowAddrKey}'`);
    if (result.length === 0) throw new Error(`Boomflow address not found.`)
    boomflowAddr = result[0]['value'];
    log(`load boomflow address from database ${boomflowAddr}`);
}
async function run() {
    let wallet = new ethers.Wallet(boomflowPrivateKey, cfx);
    deployerAccount = await wallet.getAddress();
    console.log(`boomflow address: ${deployerAccount}`)

    crcl = crcl.connect(wallet);
    boomflow = boomflow.connect(wallet);
    erc777 = erc777.connect(wallet);
    fc = fc.connect(wallet);
    wcfxContract = wcfxContract.connect(wallet);

    const network = await cfx.getNetwork();
    console.log(`network `, network)
    sender = deployerAccount;
    let boomBan = await wallet.getBalance().then(res=>{
        // console.log(`formatEther`, res)
        return ethers.utils.formatEther(res)
    })
    log('net work id:', network.chainId, 'boom address', deployerAccount, 'balance', boomBan)
    if (parseFloat(boomBan) < 100) {
        console.log(`Boom address's balance is insufficient. ${boomBan} < 100`)
        process.exit(1)
    }
    nonce = await wallet.getTransactionCount().then(res=>parseInt(res.toString())).catch(err=>{
        log('get nonce fail.', err.data, err)
        process.exit(1)
    });
    console.info(`next nonce ${nonce}, environment ${environment}, sender ${deployerAccount}`)

    if (updateBoomflow) {
        // will get new boomflow address.
        await deployBoomflow(nonce++);
    } else if(updateCRCL || program.boomflowAdmin){
        // after updating crcl, we need to add white list.
        await loadBoomflowContractAddr()
    }
    if (erc777_assets.length === 0) {
        log(`no erc 777 to deploy.`)
    } else if (updateTokens) {
        log(`enter update tokens`)
        // deploy erc777, and fc (test environment).
        await Promise.all(
            erc777_assets.map(async (token, index) => {
                // console.log(`preset token : `, token)
                const useNonce = nonce ++;
                console.info(`begin deploying token ${token.name} , use nonce ${useNonce}`)
                let instance = undefined;
                if (token.isErc777) {
                    instance = await erc777.deploy(`Evm Dex ${token.name}`, `K-${token.name}`, [], {nonce: useNonce})
                } else if (token.isCFX) {
                    instance = await wcfxContract.deploy([], {nonce: useNonce})
                } else if (token.isFC) {
                    instance = await fc.deploy({nonce: useNonce})
                } else {
                    throw new Error(`Token configuration error, ${token.name}`)
                }
                return deployCfxToken(token, instance)
            })
        ).catch(err=>{
            delete err.transaction?.data;
//            err.error = err.error?.substring(0, (err.error?.indexOf(`"data":`) || 1000));
            console.log(`deploy erc777 fail.`, err)
            process.exit(1)
        })
    }
    if (updateCRCL) {
        // deployCRCL
        await Promise.all(
            crcl_assets.map(async (token, index) => {
                return deployCRCL(token, nonce++)
            })
        )
        console.info(`deploy crcl done`);
        //
    }
    if (updateCRCL) {
        // add dex admin to WhitelistAdmin, for withdraw.
        console.log(`\nAdd DEX Admin to CRCL WhitelistAdmin, current nonce ${nonce}`)
        let wait_list = [];
        for (let i = 0; i < crcl_assets.length; ++i) {
            wait_list.push(addWhitelisted(crcl_assets[i], dexAdmin, nonce++, 'addWhitelistAdmin'));
        }
        log()
        await Promise.all(wait_list);
    }
    if (updateBoomflow || program.boomflowAdmin) {
//        await callBoomflow(boomflowAddr, 'Resume')
//        return
        // add dex admin to WhitelistAdmin and Whitelisted of boomflow contract
        await callBoomflow(boomflowAddr, 'addWhitelisted', dexAdmin);

        await callBoomflow(boomflowAddr, 'addWhitelistAdmin', dexAdmin);
        log(`add dex admin ${dexAdmin} to WhitelistAdmin and Whitelisted of boomflow contract ${boomflowAddr} begin.`)

        if (program.boomflow) {
            // when deploying boomflow standalone, unpause it
            await callBoomflow(boomflowAddr, 'Resume')
        }
    }
}

function log(...data) {
    console.info(...data)
}

//=========================================================================================================
async function deployCfxToken(token, contract) {
    return contract.deployTransaction.wait()
        .then((receipt) => {
            const retAddr = receipt.contractAddress;
            console.log('new address, ' + token.name + ":", retAddr)
            token.cfxTokenAddress = retAddr;
            updateTokenAddress(token);
            if (token.isFC) {
                FC_ADDRESS = token.cfxTokenAddress;
            }
        })
        .catch(error => {console.log(`deploy contract for ${token.name
        } fail, nonce ${nonce}, params #`,error); process.exit(1)})
}

function resumeFC(token, txParams) {
    const contract = buildContract(cfx,'../FC.json', format.address(token.cfxTokenAddress, cfx.networkId));
    return contract.unpause().sendTransaction(txParams).executed()
        .then(ret=>{console.info(`unpause ${token.name} ok`); return ret;})
        .catch(err=>{
        console.info(`unpause failed, ${token.name} #`, err)
    });
}

async function deployBoomflow(nonce) {
    log(`begin deploying boomflow, nonce ${nonce}`);
    const contract = await boomflow.deploy();
    return contract.deployTransaction.wait()
        .then((receipt) => {
            const retAddr = receipt.contractAddress;
            boomflowAddr = retAddr;
            console.log("deploy boomflow, got addr:", boomflowAddr)
            // noinspection SqlResolve
            executeSql(`replace into t_config (name, value) values ('${boomflowAddrKey}', '${boomflowAddr}') `)
                .then(results=>{
                    log(`update boomflow address, return ${JSON.stringify(results)}`)
                })
        })
        .catch(error => {console.log('deploy boomflow fail', error); process.exit(1)})
}

async function deployCRCL(token, useNonce) {
    let erc777_addr = token.cfxTokenAddress;
    console.info(`prepare deploying crcl, use nonce ${useNonce} , ${token.name} erc777 address ${erc777_addr}`)
    if (erc777_addr === undefined || erc777_addr === '') {
        if (token.outerAddress) {
            erc777_addr = token.outerAddress
            console.log(`use configured outer address [${erc777_addr}] for ${token.name}`)
            token.cfxTokenAddress = erc777_addr;
            await updateTokenAddress(token);// insert or update non-cfx-chain currency.
        } else if (program.no_database) {
            if (program.token_address) {
                erc777_addr = program.token_address;
                log(`use specified token address ${erc777_addr}`)
            } else {
                log(`no database mode, must specify token address by -ta XXX`)
                process.exit(1)
            }
        } else {
            // load from db
            console.info(`load cfx token address from db ${token.name}`);
            // noinspection SqlResolve
            const results = await executeSql(`select * from t_currency where name = '${token.name}'`);
            if (results.length === 0) {
                throw new Error(`erc 777 token address not found, consider deploy it first.
             token ${token.name}, id ${token.id}`)
            }
            erc777_addr = results[0].token_address;
            token.id = results[0].id;
            console.info(`loaded cfx token address from db ok, ${token.name} ${erc777_addr}, id ${token.id}`);
        }
    }

    const name = token.name;
    log(`begin deploying crcl ${name} with nonce ${useNonce}`)
    // 259200: lock 3 days
    // there is a hard coded checking in CRCL contract when withdraw, that requires DEX-CFX as name.
    const contract = await crcl.deploy(`CRCL ${name}`/*name*/, `${name}`/*symbol*/,
        18, erc777_addr, boomflowAddr, 259200, token.isCFX, {nonce: useNonce})
    return contract.deployTransaction.wait()
        .then((receipt) => {
            const retAddr = receipt.contractAddress;
            console.log('set crcl address for ' + name + ":", retAddr)
            token.crclAddress = retAddr;
            updateTokenAddress(token)
        })
        .catch(error => {console.log(`deploy crcl ${token.name} fail`, error); process.exit(1)})
}
function buildTxParam(nonce) {
    return {
        from: sender,
        nonce: nonce,
    }
}

/**
 * Add <addr> to the white list of <token>
 * @param token
 * @param addr
 * @param nonce
 * @returns {Promise<unknown>}
 */
async function addWhitelisted(token, addr, useNonce, what) {
    const crclAddr = token.crclAddress;
    const realContract = await crcl.attach(crclAddr);
    console.info(`LOG_MARK_01 #  nonce ${useNonce} , add ${addr} to ${what} of CRCL ${token.name} ${crclAddr}, begin`);
    return realContract[what](addr, {nonce: useNonce}).then(tx=>tx.wait())
        .then(obj=>console.info(`add ${addr} to ${what} of CRCL ${token.name}, ok`))
        .catch(err=>{console.error(`add ${addr} to ${what} of CRCL ${token.name} fail`, err); process.exit(1)})
}

async function callBoomflow(boomflow_addr, what, ...args) {
    const realContract = await boomflow.attach(boomflow_addr);
    return realContract[what](...args).then(tx=>tx.wait())
}

async function addAdmin(addr, nonce) {
    // const txParams = {
    //     from: sender,
    //     nonce: nonce,
    //     value: 0,
    //     to: format.address(FC_ADDRESS, cfx.networkId),
    //     data: fc.addAdmin(addr).data,
    // };
    const realContract = await fc.attach(FC_ADDRESS);
    return realContract.addAdmin(addr).then(tx=>tx.wait()).catch(err=>log(`add admin fail`, err))
}

async function waitNonce(target, acc) {
    let x;
    while (true) {
        x = await cfx.getTransactionCount(acc);
        if (JSBI.lessThan(x, target)) {
            console.log(`wait nonce. sleep.`)
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

function updateTokenAddress(token) {
    if (program.no_database) {
        log(`no database mode, skip update.`)
        return;
    }
    if (token.save2db === false) {
        log(`token has save2db=false, skip saving. ${token.name}`);
        return;
    }
    return new Promise(resolve => {
        executeDB( connection => {
            connection.query(`SELECT * from t_currency where name='${token.name}'`, function (error, results, fields) {
                if (error) throw error;
                resolve(results.length);
            })
        });
    }).then(count=>{
        executeDB( connection => {
            const crclAddr = token.crclAddress || `crclNotSet${token.id}`; //non null and unique
            const cfxTokenAddr = token.cfxTokenAddress || `cfxTokenAddrNotSet${token.id}`
            if (count === 0) {
                connection.query(
                    `insert into t_currency
                (id, name, contract_address, token_address, decimal_digits, cross_chain, minimum_withdraw_amount)
                values (${token.id}, '${token.name}', '${crclAddr}', '${cfxTokenAddr}',
                ${token.decimal_digits},${token.cross_chain},${token.minimum_withdraw_amount})`,
                    function (err, result) {
                        if (err) throw err;
                        token.id = result.insertId;
                        console.log(`insert currency ${token.name} result ${JSON.stringify(result.message)}`);
                    })
            } else {
                const fields = []
                if (token.crclAddress !== undefined) {
                    fields.push(`contract_address = '${crclAddr}'`)
                }
                if (token.cfxTokenAddress !== undefined) {
                    fields.push(`token_address = '${cfxTokenAddr}'`)
                }
                const setStr = fields.join(',');
                console.log(`set fields ${setStr}, id ${token.id}`)
                connection.query(
                    `update t_currency set ${setStr} where id=${token.id}`,
                    function (err, result) {
                        if (err) throw err;
                        console.log(`update currency ${token.name}, id ${token.id} result ${JSON.stringify(result.message)}`);
                    })
            }
        });
    });
}

async function buildContractAddressJson() {
    if (boomflowAddr === undefined) {
        loadBoomflowContractAddr()
    }
    // merge contract_address.json with CRCL address.
    const editJsonFile = require("edit-json-file");
    let contract_addr = editJsonFile(`${__dirname}/../contract_address.json`, { autosave: false });
    const sql = `select name, contract_address from t_currency`;
    const results = await executeSql(sql);
    console.info(`currencies size ${results.length}`);
    results.forEach(row=>{
        const crclName = `l${row['name'].toLowerCase()}_addr`
        let addr = row['contract_address'];
        contract_addr.set(crclName, addr);
        console.info(`put crcl ${crclName}, addr ${addr}`)
    })
    contract_addr.set("boomflow_addr", boomflowAddr);
    console.info(`put boomflow_addr, addr ${boomflowAddr}`)
    contract_addr.save()
    console.info(`merge contract_address.json done`)
}

if (program.merge_address) {
    buildContractAddressJson();
} else {
    run().catch(error => {
        delete error.transaction?.data;
        console.log(`deployment failed.`, error)
        process.exit(1)
    });
}