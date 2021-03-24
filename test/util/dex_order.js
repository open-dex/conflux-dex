const http = require('./http.js');
const request = require('request');
const { expect } = require('chai');
const BN = require('bignumber.js');
const KeyringController = require('cfx-keyring-controller');
const algorithm = 'aes-192-cbc';
const crypto = require('crypto');
const Config = require('./config.js');
const cfx_sig = require('cfx-sig-util');
const cfx_util = require('cfx-util');
const mainnet = require('./mainnet.js');


module.exports = {
    place_order,
    cancel_order,
    withdraw
}

let chainId = -1;

// Copy and modify from conflux-dex-sdk/boomflow

const domain = [
    { name: "name", type: "string" },
    { name: "version", type: "string" },
    { name: "chainId", type: "uint256" },
    { name: "verifyingContract", type: "address" },
];

const order = [
    { name: 'userAddress', type: 'address' },
    { name: 'amount', type: 'uint256' },
    { name: 'price', type: 'uint256' },
    { name: 'orderType', type: 'uint256' },
    { name: 'side', type: 'bool' },
    { name: 'salt', type: 'uint256' },
    { name: 'baseAssetAddress', type: 'address' },
    { name: 'quoteAssetAddress', type: 'address' },
    { name: 'feeAddress', type: 'address' },
    { name: 'makerFeePercentage', type: 'uint256' },
    { name: 'takerFeePercentage', type: 'uint256' },
]

const cancelRequest = [
    { name: 'order', type: 'Order' },
    { name: 'nonce', type: 'uint256' },
]

const withdrawRequest = [
    { name: 'userAddress', type: 'address' },
    { name: 'amount', type: 'uint256' },
    { name: 'recipient', type: 'address' },
    { name: 'isCrosschain', type: 'bool' },
    { name: 'nonce', type: 'uint256' },
]

const withdrawCrossChainRequest = [
    { name: 'userAddress', type: 'address' },
    { name: 'amount', type: 'uint256' },
    { name: 'recipient', type: 'string' },
    { name: 'isCrosschain', type: 'bool' },
    { name: 'nonce', type: 'uint256' },
]

const transferRequest = [
    { name: 'userAddress', type: 'address' },
    { name: 'amounts', type: 'uint256[]' },
    { name: 'recipients', type: 'address[]' },
    { name: 'nonce', type: 'uint256' },
]

var templateTypeData = {
    types: {
        EIP712Domain: domain,
        Order: order
    },
    primaryType: "Order",
    domain: {},
    message: {}
};

var templateCancelRequestTypeData = {
    types: {
        EIP712Domain: domain,
        CancelRequest: cancelRequest,
        Order: order
    },
    primaryType: "CancelRequest",
    domain: {},
    message: {}
};

var templateWithdrawRequestTypeData = {
    types: {
        EIP712Domain: domain,
        WithdrawRequest: withdrawRequest
    },
    primaryType: "WithdrawRequest",
    domain: {},
    message: {}
};

var templateWithdrawCrossChainRequestTypeData = {
    types: {
        EIP712Domain: domain,
        WithdrawRequest: withdrawCrossChainRequest
    },
    primaryType: "WithdrawRequest",
    domain: {},
    message: {}
};

var templateTransferRequestTypeData = {
    types: {
        EIP712Domain: domain,
        TransferRequest: transferRequest
    },
    primaryType: "TransferRequest",
    domain: {},
    message: {}
};

OPCODE = {
    PlaceOrder: 0,
    Cancel: 1,
    Withdraw: 2,
    Transfer: 3,
}

async function dex2Chain(order, baseAssetAddress, quoteAssetAddress, nonce) { 
    return {
        name: '',
        userAddress: order.address,
        amount: BN(order.amount).times(BN(1e18)).toFixed(),
        price: order.type == "Limit" ? BN(order.price).times(BN(1e18)).toFixed() : 0,
        orderType: order.type == "Limit" ? 0 : 1,
        side: order.side == "Buy",
        salt: nonce,
        baseAssetAddress: baseAssetAddress,
        quoteAssetAddress: quoteAssetAddress,
        feeAddress: order.feeAddress,
        makerFeePercentage: BN(order.feeRateMaker).times(BN(1e18)).toFixed(),
        takerFeePercentage: BN(order.feeRateTaker).times(BN(1e18)).toFixed()
    }
}

async function construct(order, baseAssetAddress, quoteAssetAddress, nonce, op) {
    var typeData = Object.assign({}, templateTypeData)
    if (chainId === -1) {
      chainId = await mainnet.get_chainId();
    }

    switch (op) {
        case OPCODE.PlaceOrder:
            var blockchainOrder =  await dex2Chain(order, baseAssetAddress, quoteAssetAddress, nonce, op)
            typeData.message = blockchainOrder
            break;
        case OPCODE.Cancel:
            typeData = templateCancelRequestTypeData
            var blockchainOrder =  await dex2Chain(order, baseAssetAddress, quoteAssetAddress, order.timestamp, op)
            typeData.message = {
                order: blockchainOrder,
                nonce: nonce
            }
            break;
        case OPCODE.Withdraw:
            typeData = templateWithdrawRequestTypeData
            if (order.isCrosschain) {
                typeData = templateWithdrawCrossChainRequestTypeData
            }
            typeData.domain = {
                name: "CRCL",
                version: "1.0",
                chainId: chainId,
                verifyingContract: baseAssetAddress,
            }
            typeData.message = {
                userAddress: order.userAddress,
                amount: BN(order.amount).times(BN(1e18)).toFixed(),
                recipient: order.recipient,
                isCrosschain: order.isCrosschain,
                relayContract: order.relayContract,
                nonce: nonce
            }
            break;
            case OPCODE.Transfer:
                typeData = templateTransferRequestTypeData
                typeData.domain = {
                    name: "CRCL",
                    version: "1.0",
                    chainId: chainId,
                    verifyingContract: baseAssetAddress,
                }

                var amounts = []
                for (let i = 0; i < order.amounts.length; i++) {
                    amounts.push(BN(order.amounts[i]).times(BN(1e18)).toFixed())
                }
                typeData.message = {
                    userAddress: order.userAddress,
                    amounts: amounts,
                    recipients: order.recipients,
                    nonce: nonce
                }
                break;
            default:
                throw new Error("Invalid Operation")
        }

    return typeData
}

async function sign(typeData, op, privateKey) {
    return cfx_sig.signTypedData_v4(cfx_util.toBuffer(privateKey), {data: typeData});
}

async function submit(order, timestamp, signature, clientOrderId=null) {
    var signedOrder = {
        address: order.address,
        product: order.product,
        type: order.type,
        side: order.side,
        price: order.price,
        amount: order.amount,
        feeAddress : order.feeAddress,
        feeRateTaker : order.feeRateTaker,
        feeRateMaker : order.feeRateMaker,
        timestamp: timestamp,
        signature: signature
    }

    if (clientOrderId) {
        signedOrder.clientOrderId = clientOrderId
    }

    const promise = http.postNoCheck(Config.get_dex_url() + '/orders/place', signedOrder)
    let res = await promise.catch(err=>{
        console.info(`place order fail ${err}`)
        return {data: {message: "Place order fail, dex_order.js"}}
    })
    return res;
}

async function cancel(orderId, timestamp, signature, need_parse_result=true) {
    let signedRequest = {
        timestamp: timestamp,
        signature: signature
    }

    let res = await http.postNoCheck(Config.get_dex_url() + '/orders/' + String(orderId) + '/cancel', signedRequest)
        .catch(err=>{
            console.info(`cancel order fail, ${err}`)
            return {"message": `${err}`}
        });
    return res;
}

async function withdraw_2(request, timestamp, signature) {
    let signedRequest = {
        userAddress: request.userAddress,
        currency: request.currency,
        amount : request.amount,
        recipient: request.recipient,
        crossChain: request.isCrosschain,
        relayContract: request.relayContract,
        timestamp: timestamp,
        signature: signature
    }

    let res = await http.post(Config.get_dex_url() + '/accounts/withdraw', signedRequest, true);
    return res;
}

async function transfer(request, timestamp, signature) {
    var requests = {}
    for (let i = 0; i < request.recipients.length; i++) {
        requests[request.recipients[i]] = request.amounts[i];
    }

    let signedRequest = {
        userAddress: request.userAddress,
        currency: request.currency,
        recipients: requests,
        timestamp: timestamp,
        signature: signature
    }

    let res = await http.post(Config.get_dex_url() + 'accounts/transfer', signedRequest, true);
    return res;
}
const addressCache = {}
async function get_contract_address(name) {
    let addr = addressCache[name];
    if (addr) {
        return addr;
    }
    let res = await http.get(Config.get_dex_url() + '/currencies/' + name, true);
    addr = res.data.contractAddress;
    addressCache[name] = addr;
    return addr;
}

async function get_boomflow_address() {
    let res = await http.get(Config.get_dex_url() + '/common/boomflow', true);
    return res.data;
}

async function place_order(order, privateKey) {
    const pair = order.product.split('-');
    let addr_1 = await get_contract_address(pair[0]);
    let addr_2 = await get_contract_address(pair[1]);

    let timestamp = new Date().getTime();
    let typeData = await construct(order, addr_1, addr_2, timestamp, OPCODE.PlaceOrder);
    let signature = await sign(typeData, OPCODE.PlaceOrder, privateKey);

    let res = await submit(order, timestamp, signature);
    if (!res.success) {
        return Promise.reject(res)
    }
    return res.data;
}

async function cancel_order(order_id, order, privateKey, need_parse_result=true) {
    const pair = order.product.split('-');
    let addr_1 = await get_contract_address(pair[0]);
    let addr_2 = await get_contract_address(pair[1]);

    let res = await new Promise((resolve, reject) =>
        request(Config.get_dex_url() + '/orders/' + String(order_id), (err, res, body) => {
            if (err) {
                reject(err)
            } else if(body.startsWith('<')) {
                reject(body)
            } else {
                const json = JSON.parse(body);
                resolve(json)
            }
        })
    ).catch(err=>{
        console.info(`get order info fail, ${err}`)
        return undefined;
    });
    if (res === undefined) {
        return {"message": "query order info fail"};
    }else if (!res.success) {
        return res;
    }
    order.timestamp = res.data.timestamp;

    let timestamp = new Date().getTime();
    let typeData = await construct(order, addr_1, addr_2, timestamp, OPCODE.Cancel);
    let signature = await sign(typeData, OPCODE.Cancel, privateKey);

    res = await cancel(order_id, timestamp, signature, need_parse_result);
    return res;
}

async function withdraw(req, privateKey) {
    let timestamp = new Date().getTime();

    let contract_addr = await get_contract_address(req.currency);
    let typeData = await construct(req, contract_addr, "", timestamp, OPCODE.Withdraw);
    let signature = await sign(typeData, OPCODE.Withdraw, privateKey);

    let res = await withdraw_2(req, timestamp, signature);
    return res;
}

async function init_domain() {
    let addr = await get_boomflow_address();

    if (chainId === -1) {
        chainId = await mainnet.get_chainId();
    }

    let domain = {
        name: 'Boomflow',
        version: '1.0',
        chainId: chainId,
        verifyingContract: addr
    }

    templateTypeData.domain = domain;
    templateCancelRequestTypeData.domain = domain;
}

let inited = false;

async function init() {
    if (inited) return;
    await init_domain().catch((err)=>{
        console.info(`init domain fail`, err)
        process.exit(1)
    });
    inited = true;
}

init();
