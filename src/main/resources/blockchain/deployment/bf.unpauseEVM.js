const ethers = require('ethers')
const boomflowContract = require('../Boomflow.json');
const {getConfig,boomflowAddrKey} = require('../tool/mysql.js')
let sender,cfx = null;
let cfxUrl = process.env.EVM_RPC_URL;
let boomflowadminprivatekey = process.env.BOOMFLOW_ADMIN_PRIVATE_KEY;
async function run() {
    let boomflow_addr = await getConfig(boomflowAddrKey)
    log(`boomflow address is ${boomflow_addr}, cfx url ${cfxUrl}`)
    if (boomflow_addr === null || boomflow_addr === undefined || boomflow_addr.length === 0) {
        throw new Error('boomflow address not configured in database.')
    }
    cfx = ethers.getDefaultProvider(cfxUrl);
    let wallet = new ethers.Wallet(boomflowadminprivatekey, cfx);

    const contract = new ethers.Contract(boomflow_addr, boomflowContract.abi, wallet)
    await contract.Resume().then(tx=>tx.wait())
        .then(()=>log(`resumed.`))
        .catch(e=>console.log("resume error:", e));
}
function log(...data) {
    console.info(...data)
}


function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

run().catch(err=>{
    // unpause may fail if contract is not paused.
    console.error('unpause failed, is it paused before?')
    throw err
});