// conflux-js-sdk uses native bigint since version 1.1.6,
// make a fake JSBI to keep code consistency.
const JSBI = {
    add: function (n, m) {
        return n + m;
    },
    BigInt: function (n) {
        return BigInt(n)
    },
    lessThan(a,b) {
        return a<b;
    },
    equal: function (a, b) {
        return a === b;
    }
}
module.exports = JSBI