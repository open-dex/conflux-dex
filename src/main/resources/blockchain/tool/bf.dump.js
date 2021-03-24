const {
    OrderService
} = require('boomflow');

const boomflow = require('../Boomflow.json');
const fs = require('fs');
const { Conflux } = require("js-conflux-sdk");
const editJsonFile = require("edit-json-file");
// argv[4] : file name to save result, it's a relative path.
let obj = editJsonFile(`${__dirname}/${process.argv[4]}`, { autosave: true });

const STATUS = [
    "INVALID",                    // Default value
    "INVALID_AMOUNT",             // Order does not have a valid amount
    "INVALID_PRICE",              // Order does not have a valid price
    "FILLABLE",                   // Order is fillable
    "EXPIRED",                    // Order has already expired
    "FULLY_FILLED",               // Order is fully filled
    "CANCELLED",                  // Order is cancelled
    "INVALID_TYPE"
]

const cfx = new Conflux({
    // argv[2] : cfx url
    url: process.argv[2],
});

let hashMap = new Map()

/** 
 * 用法：
 * 1. 从环境变量中获得boomflow地址：node src/main/resources/blockchain/tool/bf.dump.js http://wallet-mainnet-jsonrpc.conflux-chain.org:12537 0x2b2f8507749c31c9a3e0c078b88ed1cd9529c606786d17a3fed2993258dcaa53 exp-5324080.dat
 * 2. 从DEX API获得boomflow地址：node src/main/resources/blockchain/tool/bf.dump.js <cfx url> <tx hash> <download file> <dex url>
*/

async function main() {
    let boomflowAddr = process.env.BOOMFLOW_ADMIN_ADDRESS;

    if (process.argv[5]) { // dex url
        var orderService = new OrderService.OrderService({ dexURL: process.argv[5] })
        await orderService.init({
            name: "Boomflow",   // Contract name
            version: "1.0",     // Contract version
            chainId: 0,         // Chain ID
        })

        boomflowAddr = await orderService.getLocalBoomflow()
        console.log("Get Boomflow Address:", boomflowAddr)
    }

    const bf = cfx.Contract({
        address: boomflowAddr,
        abi: boomflow.abi
    });
    let hash = process.argv[3];
    obj.set('boomflowAddr', boomflowAddr);
    obj.set('hash', hash);

    let executionOrders = []
    const tx = await cfx.getTransactionByHash(hash) // hash
    const blockHashes = await cfx.getBlocksByEpochNumber(tx.epochHeight)

    for(let i = 0; i < blockHashes.length; i++) {
        //console.log(blockHashes[i]);

        const block = await cfx.getBlockByHash(blockHashes[i], true)
        //console.log(block)

        for (let j = 0; j < block.transactions.length; j++) {
            const msg = await bf.abi.decodeData(block.transactions[j].data)
            console.log(msg.name, block.transactions[j].to)
            if (block.transactions[j].to === bf.address && (msg.name === 'batchExecuteTrade' || msg.name === 'executeTrade')) {
                let makerOrders = [], takerOrders = [], makerSignatures = [], takerSignatures = []
                if (msg.name === 'batchExecuteTrade') {
                    makerOrders = msg.object.makerOrders
                    takerOrders = msg.object.takerOrders
                    makerSignatures = msg.object.makerSignatures
                    takerSignatures = msg.object.takerSignatures
                } else if (msg.name === 'executeTrade') {
                    makerOrders = [msg.object.makerOrder]
                    takerOrders = [msg.object.takerOrder]
                    makerSignatures = [msg.object.makerSignature]
                    takerSignatures = [msg.object.takerSignature]
                }
                
                for (let k = 0; k < makerOrders.length; k++) {
                    let makerOrderInfo = await bf.getOrderInfo(makerOrders[k])

                    if (!hashMap.has(makerOrderInfo.orderHash.toString("hex"))) {
                        //console.log('===================================')

                        const key = '0x' + makerOrderInfo.orderHash.toString("hex")
                        console.log("Order Hash:\t", key)
                        /*console.log("Order Status:\t", STATUS[makerOrderInfo.orderStatus.toString()]) 
                        console.log("Filled Amount:\t", makerOrderInfo.filledAmount.toString())*/
                        
                        let orderData = await bf.getOrderData(makerOrderInfo.orderHash).call({}, tx.epochHeight-1);
                        obj.set(key, {
                            'epoch': tx.epochHeight-1,
                            'filled': orderData.filled.toString(),
                            'max': orderData.max.toString(),
                            'cancelled': orderData.cancelled,
                            'flag': orderData.flag,
                            'order': makerOrders[k],
                            'signature': "0x" + Buffer.from(makerSignatures[k], "hex").toString("hex")
                        })

                        hashMap.set(makerOrderInfo.orderHash.toString("hex"), 'a')
                    }

                    let takerOrderInfo = await bf.getOrderInfo(takerOrders[k])

                    if (!hashMap.has(takerOrderInfo.orderHash.toString("hex"))) {
                        //console.log('===================================')

                        const key = '0x' + takerOrderInfo.orderHash.toString("hex")
                        console.log("Order Hash:\t", key)
                        /*console.log("Order Status:\t", STATUS[takerOrderInfo.orderStatus.toString()]) 
                        console.log("Filled Amount:\t", takerOrderInfo.filledAmount.toString())*/
                        
                        let orderData = await bf.getOrderData(takerOrderInfo.orderHash).call({}, tx.epochHeight-1);

                        obj.set(key, {
                            'epoch': tx.epochHeight-1,
                            'filled': orderData.filled.toString(),
                            'max': orderData.max.toString(),
                            'cancelled': orderData.cancelled,
                            'flag': orderData.flag,
                            'order': takerOrders[k],
                            'signature': "0x" + Buffer.from(takerSignatures[k], "hex").toString("hex")
                        })

                        hashMap.set(takerOrderInfo.orderHash.toString("hex"), 'a')
                    }
                    executionOrders.push({
                        "maker": "0x" + makerOrderInfo.orderHash.toString("hex"),
                        "taker": "0x" + takerOrderInfo.orderHash.toString("hex")
                    })
                }
            }
        }
    }

    obj.set("params", executionOrders)

    // argv[4] : file name to save result.
    fs.writeFileSync(__dirname + '\\' + process.argv[4], JSON.stringify(obj))
}

main()