// configure known btc/eth tokens.
function buildCurrency(name, outerAddress) {
    let template = currencyTemplates[name];
    if (template === undefined) {
        throw new Error(`Can not found currency id for ${name}`);
    }
    return {
        id: template.id,
        name: name,
        decimal_digits:          template.decimal_digits,
        cross_chain:             template.cross_chain,
        minimum_withdraw_amount: template.minimum_withdraw_amount,
        isErc777: false,
        isFC: false,
        isCFX: false,
        save2db: true,
        cfxTokenAddress: undefined, // do not change default value.
        crclAddress: undefined, // do not change default value.
        // if a token has outer address, then it must have been deployed already.
        outerAddress: outerAddress //btc or eth or erc20 token address
    };
}

const currencyTemplates = {
    'BTC':  {id: 1, decimal_digits: 18, cross_chain: 1, minimum_withdraw_amount: 0.01},
    'ETH':  {id: 2, decimal_digits: 18, cross_chain: 1, minimum_withdraw_amount: 0.05},
    'USDT': {id: 3, decimal_digits: 18, cross_chain: 1, minimum_withdraw_amount: 2},
    'FC':   {id: 4, decimal_digits: 18, cross_chain: 0, minimum_withdraw_amount: 0},
    'CFX': {id:  9, decimal_digits: 18, cross_chain: 0, minimum_withdraw_amount: 0},
    'EOS':  {id: 10, decimal_digits: 18, cross_chain: 0, minimum_withdraw_amount: 0},
    'KCoin': {id: 11, decimal_digits: 18, cross_chain: 0, minimum_withdraw_amount: 0},
}

// prod
const fcProd = buildCurrency('FC', '0x8e2f2e68eb75bb8b18caafe9607242d4748f8d98'); fcProd.isFC = true;
const cfxProd = buildCurrency('CFX', '0x8d7df9316faa0586e175b5e6d03c6bda76e3d950'); cfxProd.isCFX = true;
const btcProd = buildCurrency('BTC', '0x821c636dfc85d0612fb8ebf34acf84771ba4c344');
const ethProd = buildCurrency('ETH', '0x86d2fb177eff4be03a342951269096265b98ac46');
const usdtProd = buildCurrency('USDT', '0x8b8689c7f3014a4d86e4d1d0daaf74a47f5e0f27');
// stage, all tokens without outer address will be deployed by config in module.exports.
const fcStage = buildCurrency('FC');  fcStage.isFC = true;
const cfxStage = buildCurrency('CFX');cfxStage.isCFX = true;
// there are testnet for btc and eth(rinkeby)
const btcStage = buildCurrency('BTC', '0x83f692c6c18c0a4a0eac8aa428a1f4b5b596e3db');
const ethStage = buildCurrency('ETH', '0x8442bc8b5d01bf635bb12e6c63a379cb167ab5bb');
const usdtStage = buildCurrency('USDT'); usdtStage.isErc777 = true;
// test
const eosTest = buildCurrency('EOS'); eosTest.isErc777 = true;
const kCoinTest = buildCurrency('KCoin'); kCoinTest.isErc777 = true;
//
const testConf = {
    "erc777_assets":[
        // fcStage,
        // cfxStage,
        eosTest,
        kCoinTest, usdtStage
    ],
    crcl_assets: [
           // fcStage,
        // cfxStage,
        eosTest,
        kCoinTest, usdtStage,
        //btcStage, ethStage,
    ]
}
module.exports = {
    currencyTemplates,
    dev: testConf,
    test: testConf,
    stage: {
        "erc777_assets":[fcStage, cfxStage, usdtStage, eosTest, kCoinTest],
        "crcl_assets": [usdtStage,
            fcStage, btcStage, ethStage, cfxStage, eosTest, kCoinTest]
    },
    prod: {
        "erc777_assets": [/*cfxProd*//*wcfx is shared*/eosTest, kCoinTest],
        "crcl_assets": [usdtProd,
            fcProd, btcProd, ethProd, cfxProd, eosTest, kCoinTest]
    }
}
