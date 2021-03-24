const commander = require('commander');

const exec = require('child_process').exec;

commander
    .option('-p --port [port]', 'dex port')
    .option('-t --threshold <threshold>', 'threshold of percent to stop, 1 for 1%')
    .parse(process.argv,{ from: 'user' });

if (commander.args.length !== 2) {
    console.info(`unknown arguments:`, commander.args.slice(2))
    process.exit(1)
}

const port=commander.port || 8080;
console.info(`check dex at port ${port}`)

async function run() {
    let p2 = undefined;
    const p1 = new Promise(resolve => {
        const dir = exec(`lsof -t -i:${port}`, function (err, stdout, stderr) {
            if (err) {
                // should have err.code here?
                // console.info(`error occur:`, err.toString())
                resolve("");
                return;
            }
            // console.log(`stdout:${stdout}`);
            resolve(stdout)
        });
        // console.info('before exit')
        p2 = new Promise(resolve => {
            dir.on('exit', function (code) {
                // exit code is code
                // console.info(`exit code ${code}`)
                resolve(code)
            });
        });
    });
    // console.info(`p2, p2`, p1, p2)
    return Promise.all([p1, p2]).then(arr=>{
        console.info(`${dt()} check port ${port}, result `, arr)
        if (arr[1] === 0) {
            const pid = arr[0].match(/\d+/)[0]
            whetherStop(pid)
        }
    })
}

function stopIt(pid) {
    const b = process.kill(parseInt(pid), 9);
    console.info(`stop it ${pid}, return ${b}`);
}

function whetherStop(pid) {
    const rnd = Math.round(Math.random() * 100);
    const threshold = commander.threshold || 1;
    // every second, THRESHOLD percent chance to stop
    console.info(`random got ${rnd}, threshold ${threshold}`)
    if (rnd < threshold) {
        stopIt(pid)
    }
}

function dt() {
    return new Date().toLocaleString()
}

async function loop() {
    while(true) {
        await run();
        await new Promise(resolve => setTimeout(resolve, 1000*60*5))
    }
}
loop().then()