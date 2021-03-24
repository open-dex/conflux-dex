const { Conflux } = require("js-conflux-sdk");
const cfx = new Conflux({
  url: "http://",
});

const boomflowContract = require('../../src/main/resources/blockchain/Boomflow.json');
const boomflowABI = boomflowContract.abi;
const boomflow = cfx.Contract({ abi: boomflowABI });

const crclContract = require('../../src/main/resources/blockchain/CRCL.json');
const crclABI = crclContract.abi;
const crcl_token = cfx.Contract({ abi: crclABI });

const program = require('commander');

const express = require('express');
const bodyParser = require('body-parser');
const jayson = require('jayson');
const cors = require('cors');

const sigUtil = require('eth-sig-util')

function toBuffer(x) {
  return Buffer.from(x.substring(2), 'hex');
}

function cancelOrders(requests, signatures) {
  signatures = signatures.map((x) => toBuffer(x));
  return boomflow.cancelOrders(requests, signatures).data;
}

function executeInstantExchangeTrade(takerOrder, takerSignature, threshold) {
  takerSignature = toBuffer(takerSignature);
  return boomflow.executeInstantExchangeTrade(takerOrder, takerSignature, threshold).data;
}

function executeTrade(makerOrder, takerOrder, makerSignature, takerSignature, makerContractFee, takerContractFee, contractFeeAddress) {
  makerSignature = toBuffer(makerSignature);
  takerSignature = toBuffer(takerSignature);
  return boomflow.executeTrade(makerOrder, takerOrder, makerSignature, takerSignature, makerContractFee, takerContractFee, contractFeeAddress).data;
}

function batchExecuteTrade(makerOrders, takerOrders, makerSignatures, takerSignatures, makerContractFees, takerContractFees, contractFeeAddresses) {
  makerSignatures = makerSignatures.map((x) => toBuffer(x));
  takerSignatures = takerSignatures.map((x) => toBuffer(x));
  return boomflow.batchExecuteTrade(makerOrders, takerOrders, makerSignatures, takerSignatures, makerContractFees, takerContractFees, contractFeeAddresses).data;
}

function finalizeOrder(order, signature) {
  signature = toBuffer(signature);
  return boomflow.finalizeOrder(order, signature).data;
}

function recordInstantExchangeOrders(takerOrder, makerOrders, takerSignature, makerSignatures) {
  takerSignature = toBuffer(takerSignature);
  makerSignatures = makerSignatures.map((x) => toBuffer(x));
  return boomflow.recordInstantExchangeOrders(takerOrder, makerOrders, takerSignature, makerSignatures).data;
}

function transferFor(request, signature) {
  signature = toBuffer(signature);
  return crcl_token.transferFor(request, signature).data;
}

function withdraw(request, signature) {
  signature = toBuffer(signature);
  return crcl_token.withdraw(request, signature).data;
}


function withdrawCrossChain(request, signature) {
  signature = toBuffer(signature);
  return crcl_token.withdrawCrossChain(request, signature).data;
}

function hash(message) {
  return '0x' + Buffer.from(sigUtil.TypedDataUtils.sign(message)).toString('hex');
}

function setOrderData(orderHash, orderData) {
  return boomflow.setOrderData(orderHash, orderData).data;
}

function setOrderRecordedMakerOrders(orderHash, base, quote) {
  return boomflow.setOrderRecordedMakerOrders(orderHash, base, quote).data;
}

// storage optimization
function recordOrders(orderHashes) {
  orderHashes = orderHashes.map((x) => toBuffer(x));
  return boomflow.recordOrders(orderHashes).data;
}

function removeObsoleteData(orderHashes) {
  orderHashes = orderHashes.map((x) => toBuffer(x));
  return boomflow.removeObsoleteData(orderHashes).data;
}

function setTimestamp(ts) {
  return boomflow.setTimestamp(ts).data;
}

// for ping/pong purpose only
function ping() {
  return "pong";
}

class blockchainService {
  constructor(port) {

    const apis = {
      cancelOrders: function(args, callback) {
        callback(null, cancelOrders(args[0], args[1]));
      },

      executeInstantExchangeTrade: function(args, callback) {
        callback(null, executeInstantExchangeTrade(args[0], args[1], args[2]));
      },

      executeTrade: function(args, callback) {
        callback(null, executeTrade(args[0], args[1], args[2], args[3], args[4], args[5], args[6]));
      },

      batchExecuteTrade: function(args, callback) {
        callback(null, batchExecuteTrade(args[0], args[1], args[2], args[3], args[4], args[5], args[6]));
      },

      finalizeOrder: function(args, callback) {
        callback(null, finalizeOrder(args[0], args[1]));
      },

      recordInstantExchangeOrders: function(args, callback) {
        callback(null, recordInstantExchangeOrders(args[0], args[1], args[2], args[3]));
      },

      transferFor: function(args, callback) {
        callback(null, transferFor(args[0], args[1]));
      },

      withdraw: function(args, callback) {
        callback(null, withdraw(args[0], args[1]));
      },

      withdrawCrossChain: function(args, callback) {
        callback(null, withdrawCrossChain(args[0], args[1]));
      },

      setOrderData: function(args, callback) {
        callback(null, setOrderData(args[0], args[1]));
      },

      setOrderRecordedMakerOrders: function(args, callback) {
        callback(null, setOrderRecordedMakerOrders(args[0], args[1], args[2]));
      },

      ping: function(args, callback) {
        callback(null, ping());
      },

      hash: function(args, callback) {
        callback(null, hash(args[0]));
      },

      recordOrders: function(args, callback) {
        callback(null, recordOrders(args[0]));
      },

      removeObsoleteData: function(args, callback) {
        callback(null, removeObsoleteData(args[0]));
      },

      setTimestamp: function(args, callback) {
        callback(null, setTimestamp(args[0]));
      },
    };

    const app = express();
    app.use(bodyParser.urlencoded({extended: true}));
    app.use(bodyParser.json());
    app.use(cors());
    app.post('/', jayson.server(apis).middleware());
    app.listen(port, "localhost", function() {});
  }
}

program.option('-p, --port [type]', 'listening port')
  .parse(process.argv);

let service = new blockchainService(parseInt(program.port, 10));
