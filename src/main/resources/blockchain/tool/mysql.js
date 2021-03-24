const mysql      = require('mysql');
function executeSql(sql) {
    return new Promise(r2=>{
        executeDB(con=>{
            con.query(sql, (error, results, fields)=>{
                if (error) {
                    console.error(`execute sql fail ${sql}`)
                    throw error;
                }
                r2(results);
            })
        })
    })
}
async function getTokenAddress(name) {
    const result = await executeSql(`select token_address from t_currency where name = '${name}'`);
    if (result.length === 0) throw new Error(`token_address not found for ${name}.`)
    return result[0]['token_address'];
}
async function getConfig(name) {
    const result = await executeSql(`select value from t_config where name = '${name}'`);
    if (result.length === 0) {
        return null
    }
    return result[0]['value'];
}
function executeDB(fun) {
    const connection = connectDB();
    connection.connect();
    fun(connection);
    connection.end();
}
function connectDB() {
    var connection = mysql.createConnection({
        host     : process.env.DEX_MYSQL_HOST,
        port     : process.env.DEX_MYSQL_PORT,
        user     : process.env.DEX_MYSQL_USER,
        password : process.env.MYSQL_PWD,
        database : process.env.DB_NAME
    });
    return connection;
}
module.exports = {
    executeSql, executeDB, connectDB, getConfig,
    getTokenAddress,
    boomflowAddrKey: "contract.boomflow.address"
}